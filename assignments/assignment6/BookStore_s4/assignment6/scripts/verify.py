#!/usr/bin/env python3
"""End-to-end assertions against real HTTP services, MySQL and Seata (no mocks)."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
import json
import re
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'evidence'
CLIENT = urllib.request.build_opener(urllib.request.ProxyHandler({}))
RESULT = {'startedAt': datetime.now(timezone.utc).isoformat(), 'checks': [], 'scenarios': {}}


def check(name, condition, detail=None):
    RESULT['checks'].append({'name': name, 'passed': bool(condition), 'detail': detail})
    print(('PASS ' if condition else 'FAIL ') + name, flush=True)
    if not condition:
        raise AssertionError(f'{name}: {detail}')


def sql(statement):
    command = ['docker', 'compose', 'exec', '-T', '-e', 'MYSQL_PWD=assignment6-local-root',
               'mysql', 'mysql', '-uroot', '--batch', '--skip-column-names', '--default-character-set=utf8mb4', '-e', statement]
    return subprocess.check_output(command, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def scalar(statement):
    return int(sql(statement))


def http(path, data=None, port=18080):
    request = urllib.request.Request(f'http://127.0.0.1:{port}{path}',
        data=None if data is None else json.dumps(data).encode(),
        headers={'Content-Type': 'application/json'})
    try:
        response = CLIENT.open(request, timeout=45)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read())


def request(book_id=1, quantity=2, **extra):
    return dict(requestId='test-' + uuid.uuid4().hex, bookId=book_id, quantity=quantity,
                failOrder=False, pauseAfterInventoryMs=0) | extra


def snapshot(book_id, request_id):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', request_id):
        raise ValueError('unsafe request ID')
    return {
        'stock': scalar(f'SELECT stock FROM inventory_db.inventory WHERE book_id={int(book_id)}'),
        'orders': scalar(f"SELECT COUNT(*) FROM order_db.purchase_order WHERE request_id='{request_id}'"),
        'inventoryUndoLogs': scalar('SELECT COUNT(*) FROM inventory_db.undo_log'),
        'orderUndoLogs': scalar('SELECT COUNT(*) FROM order_db.undo_log'),
    }


def eventually(predicate, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(.15)
    return None


def run():
    for port, service in [(18080, 'checkout'), (18081, 'inventory'), (18082, 'orders')]:
        code, body = http('/health', port=port)
        check(service + ' 独立进程可访问', code == 200 and body['service'] == service)
    databases = sql("SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('inventory_db','order_db') ORDER BY schema_name")
    check('两套独立业务数据库', databases.splitlines() == ['inventory_db', 'order_db'])
    grants = sql("SHOW GRANTS FOR 'inventory_app'@'%'") + '\n' + sql("SHOW GRANTS FOR 'order_app'@'%'")
    RESULT['databaseGrants'] = grants
    check('库存与订单分别授权', 'ON `inventory_db`.*' in grants and 'ON `order_db`.*' in grants)

    normal = request()
    before = snapshot(1, normal['requestId'])
    code, body = http('/api/orders', normal)
    after = snapshot(1, normal['requestId'])
    RESULT['scenarios']['success'] = dict(request=normal, httpStatus=code, response=body, before=before, after=after)
    check('正常下单返回成功和 XID', code == 200 and bool(body.get('xid')), body)
    check('正常下单库存与订单同时提交', after['stock'] == before['stock'] - 2 and after['orders'] == 1, after)
    stored_xid = sql(f"SELECT xid FROM order_db.purchase_order WHERE request_id='{normal['requestId']}'")
    check('订单服务收到同一个 XID', stored_xid == body['xid'])
    check('成功后两个库的 Undo 最终清理', eventually(lambda: scalar('SELECT COUNT(*) FROM inventory_db.undo_log') == 0 and scalar('SELECT COUNT(*) FROM order_db.undo_log') == 0))

    failed = request(quantity=3, failOrder=True, pauseAfterInventoryMs=3000)
    before = snapshot(1, failed['requestId'])
    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(http, '/api/orders', failed)
        middle = eventually(lambda: (s if s['stock'] == before['stock'] - 3 else None)
                            if (s := snapshot(1, failed['requestId'])) else None, timeout=5)
        check('异常前库存分支已独立提交', middle is not None, middle)
        undo = sql('SELECT xid,branch_id,log_status FROM inventory_db.undo_log ORDER BY log_created DESC LIMIT 1')
        check('已提交库存存在 Seata Undo 记录', middle['inventoryUndoLogs'] > 0 and bool(undo), undo)
        code, body = future.result()
    restored = eventually(lambda: snapshot(1, failed['requestId'])['stock'] == before['stock'])
    after = snapshot(1, failed['requestId'])
    RESULT['scenarios']['rollback'] = dict(request=failed, httpStatus=code, response=body,
        before=before, intermediate=middle, undoDuringTransaction=undo, after=after)
    check('订单入库 RuntimeException 传播至入口', code == 500 and body.get('code') == 'SIMULATED_ORDER_FAILURE', body)
    check('失败响应 XID 等于库存 Undo XID', undo.split('\t')[0] == body.get('xid'))
    check('Seata 自动恢复已提交库存', restored and after['stock'] == before['stock'], after)
    check('失败订单没有残留', after['orders'] == 0)
    check('回滚后 Undo 清理完成', eventually(lambda: scalar('SELECT COUNT(*) FROM inventory_db.undo_log') == 0 and scalar('SELECT COUNT(*) FROM order_db.undo_log') == 0))

    old_stock = after['stock']
    code, replay = http('/api/orders', normal)
    check('相同请求重试返回原订单', code == 200 and replay.get('replayed') and replay['orderId'] == RESULT['scenarios']['success']['response']['orderId'], replay)
    check('重试不重复扣减库存', snapshot(1, normal['requestId'])['stock'] == old_stock)
    code, conflict = http('/api/orders', normal | {'quantity': 3})
    check('同一请求号禁止更改商品数量', code == 409 and conflict['code'] == 'IDEMPOTENCY_CONFLICT')
    code, _ = http('/api/orders', request(quantity=0))
    check('非法数量在入口拒绝', code == 400)
    absent = request(book_id=9223372036854775000)
    code, body = http('/api/orders', absent)
    check('不存在的图书返回 404', code == 404 and body['code'] == 'BOOK_NOT_FOUND')
    insufficient = request(quantity=100000)
    code, body = http('/api/orders', insufficient)
    check('库存不足返回 409', code == 409 and body['code'] == 'INSUFFICIENT_STOCK')
    check('校验失败未改变库存或生成订单', snapshot(1, insufficient['requestId'])['stock'] == old_stock and snapshot(1, insufficient['requestId'])['orders'] == 0)
    for port, path in [(18081, '/internal/inventory/deduct'), (18082, '/internal/orders')]:
        code, body = http(path, request(), port=port)
        check(f'{port} 拒绝未携带 XID 的分支写入', code == 400 and body['code'] == 'XID_REQUIRED')

    # Add a fresh fixture, without resetting or removing existing user/demo records.
    book_id = 1000000000 + int(time.time())
    sql(f"INSERT INTO inventory_db.inventory VALUES ({book_id},'验收测试专用图书',1)")
    competing = [request(book_id, 1), request(book_id, 1)]
    with ThreadPoolExecutor(max_workers=2) as executor:
        responses = list(executor.map(lambda data: http('/api/orders', data), competing))
    remaining = scalar(f'SELECT stock FROM inventory_db.inventory WHERE book_id={book_id}')
    count = scalar(f'SELECT COUNT(*) FROM order_db.purchase_order WHERE book_id={book_id}')
    RESULT['scenarios']['concurrency'] = dict(bookId=book_id, requests=competing, responses=responses, stock=remaining, orders=count)
    check('最后一本并发请求仅一笔成功', sum(status == 200 for status, _ in responses) == 1, responses)
    check('并发竞争无超卖且订单仅一笔', remaining == 0 and count == 1)

    check('测试结束无遗留 Undo 分支', eventually(lambda: scalar('SELECT COUNT(*) FROM inventory_db.undo_log') == 0 and scalar('SELECT COUNT(*) FROM order_db.undo_log') == 0))


if __name__ == '__main__':
    EVIDENCE.mkdir(exist_ok=True)
    try:
        run()
    finally:
        RESULT['finishedAt'] = datetime.now(timezone.utc).isoformat()
        RESULT['passed'] = sum(item['passed'] for item in RESULT['checks'])
        RESULT['total'] = len(RESULT['checks'])
        (EVIDENCE / 'verification.json').write_text(json.dumps(RESULT, ensure_ascii=False, indent=2) + '\n')
        lines = [f"{c['name']}: {'PASS' if c['passed'] else 'FAIL'}" for c in RESULT['checks']]
        lines += ['', json.dumps(RESULT['scenarios'], ensure_ascii=False, indent=2)]
        (EVIDENCE / 'verification.txt').write_text('\n'.join(lines) + '\n')
        for name in ('checkout', 'inventory', 'orders'):
            log = ROOT / 'logs' / f'{name}.log'
            if log.exists():
                selected = [line for line in log.read_text(errors='replace').splitlines()
                            if any(word in line for word in ('GLOBAL_', 'INVENTORY_', 'ORDER_', 'SIMULATED_', 'rollback', 'Rollback', 'Branch', 'Undo'))]
                (EVIDENCE / f'{name}-transactions.log').write_text('\n'.join(selected) + '\n')
        tc = subprocess.run(['docker','compose','logs','--no-color','seata'],cwd=ROOT,capture_output=True,text=True)
        (EVIDENCE / 'seata-server.log').write_text(tc.stdout + tc.stderr)
    print(f"\n{RESULT['passed']}/{RESULT['total']} checks passed. Evidence: {EVIDENCE}")
