#!/usr/bin/env python3
"""Integration acceptance against real HTTP, MySQL and Redis. No mocks."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import time
from loadtest import request, stats, burst

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'evidence'
checks, observations = [], {}

def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()

def redis(*args):
    return command('docker','compose','exec','-T','redis','redis-cli','--raw',*map(str,args))

def sql(query):
    return command('docker','compose','exec','-T','-e','MYSQL_PWD=assignment7-local',
                   'mysql','mysql','-u','bookstore','-N','-B','bookstore_cache','-e',query)

def check(name, condition, detail=None):
    checks.append({'name':name,'passed':bool(condition),'detail':detail})
    print(('PASS' if condition else 'FAIL') + ' ' + name, flush=True)
    assert condition, (name, detail)

def get(book_id):
    time.sleep(.26)  # Functional checks stay below the configured 5 QPS.
    return request(f'/api/books/{book_id}')

def header(response, name):
    return next((v for k,v in response['headers'].items() if k.lower()==name.lower()),None)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--outage', action='store_true', help='Temporarily stop ONLY assignment7 Redis, then restore it')
    args = parser.parse_args()
    EVIDENCE.mkdir(exist_ok=True)
    test_id = int(time.time() * 1000)
    key = f'a7:book:{test_id}'
    completed = False
    try:
        check('MySQL 和 Redis 健康', request('/demo/health')['status'] == 200)
        rule = stats()['rules'][0]
        check('Sentinel 统一资源及 QPS 5 直接拒绝规则', rule['resource']=='GET:/api/books/{id}' and rule['count']==5 and rule['grade']==1 and rule['controlBehavior']==0, rule)
        redis('DEL','a7:book:1')
        time.sleep(1.3)
        before = stats()['metrics']
        first, second = get(1), get(1)
        ttl = int(redis('TTL','a7:book:1'))
        observations['cache'] = {'first':first,'second':second,'ttl':ttl,'redis':redis('GET','a7:book:1')}
        check('首次查库回填缓存', first['status']==200 and header(first,'X-Cache')=='MISS', first)
        check('第二次命中缓存且数据相同', second['status']==200 and header(second,'X-Cache')=='HIT' and first['body']==second['body'])
        check('两次详情请求仅一次 SQL 查询', stats()['metrics']['dbReads']-before['dbReads']==1)
        check('正常缓存包含过期时间和抖动', 118<=ttl<=150, ttl)
        before = stats()['metrics']
        missing1, missing2 = get(test_id), get(test_id)
        null_ttl = int(redis('TTL',key))
        observations['negative'] = {'first':missing1,'second':missing2,'value':redis('GET',key),'ttl':null_ttl}
        check('不存在的图书首次返回 404 并写空对象', missing1['status']==404 and header(missing1,'X-Cache')=='NULL_MISS' and redis('GET',key)=='__NULL__')
        check('空对象命中返回 404 且不再查库', missing2['status']==404 and header(missing2,'X-Cache')=='NULL_HIT' and stats()['metrics']['dbReads']-before['dbReads']==1)
        check('空对象设置 30 秒短 TTL', 28<=null_ttl<=30, null_ttl)
        redis('EXPIRE',key,1)
        time.sleep(1.3)
        check('空对象到期后允许再次查库', header(get(test_id),'X-Cache')=='NULL_MISS')
        body = {'title':'缓存验收专用图书','author':'assignment7','price':42.50,'stock':7}
        write = request(f'/api/books/{test_id}','PUT',body)
        check('新增提交后清除原空对象', write['status']==200 and redis('EXISTS',key)=='0', write)
        check('新增数据真实写入 MySQL', sql(f'SELECT stock FROM books WHERE id={test_id}')=='7')
        created = get(test_id)
        check('新图书可见且从数据库回填', created['status']==200 and header(created,'X-Cache')=='MISS' and created['body']['title']==body['title'])
        body['stock'] = 9
        update = request(f'/api/books/{test_id}','PUT',body)
        check('更新先提交数据库再删除缓存', update['status']==200 and sql(f'SELECT stock FROM books WHERE id={test_id}')=='9' and redis('EXISTS',key)=='0')
        updated = get(test_id)
        check('更新后读到新值并重建缓存', updated['body']['stock']==9 and header(updated,'X-Cache')=='MISS')
        observations['write'] = {'create':write,'update':update,'readAfterUpdate':updated}
        redis('SET',key,'broken-json','EX',30)
        check('损坏缓存自动回源修复', header(get(test_id),'X-Cache')=='MISS' and json.loads(redis('GET',key))['stock']==9)
        bad = request(f'/api/books/{test_id}','PUT',{**body,'price':-1})
        check('非法写入返回 400 且数据库不变', bad['status']==400 and sql(f'SELECT stock FROM books WHERE id={test_id}')=='9')
        for invalid in ('0','-1','abc'):
            check(f'非法图书 ID {invalid} 返回 400', request(f'/api/books/{invalid}')['status']==400)
        time.sleep(1.3)
        get(1)
        time.sleep(1.3)
        load = burst()
        observations['loadtest'] = load
        (EVIDENCE/'loadtest.json').write_text(json.dumps(load, ensure_ascii=False, indent=2)+'\n')
        check('50 并发产生 HTTP 429', load['statusCounts'].get('429',0)>0, load['statusCounts'])
        check('空闲后短突发仅放行 5 个请求', load['statusCounts']=={'200':5,'429':45} and load['durationMs']<1000, {'counts':load['statusCounts'],'durationMs':load['durationMs']})
        check('限流响应包含错误码与 Retry-After', all(r['body'].get('code')=='TOO_MANY_REQUESTS' and header(r,'Retry-After')=='1' for r in load['responses'] if r['status']==429))
        check('被限流请求未进入缓存与数据库', load['metricDelta']['admitted']==5 and load['metricDelta']['blocked']==45 and load['metricDelta']['cacheHits']==5 and load['metricDelta']['dbReads']==0, load['metricDelta'])
        time.sleep(1.3)
        check('流量下降后恢复正常请求', get(1)['status']==200)
        time.sleep(1.3)
        mixed = burst(ids=(1,2,3))
        observations['mixedIds'] = mixed
        check('更换图书 ID 仍共用同一个限流额度', mixed['statusCounts']=={'200':5,'429':45}, mixed['statusCounts'])
        if args.outage:
            time.sleep(1.3)
            before = stats()['metrics']['dbReads']
            try:
                command('docker','compose','stop','redis')
                unavailable = request('/api/books/1')
                observations['redisOutage'] = unavailable
                check('Redis 故障返回 503 而非绕过缓存查库', unavailable['status']==503 and stats()['metrics']['dbReads']==before, unavailable)
            finally:
                command('docker','compose','up','-d','--wait','redis')
            for _ in range(20):
                if request('/demo/health')['status']==200: break
                time.sleep(1)
            time.sleep(1.3)
            check('Redis 恢复后详情接口正常', get(1)['status']==200)
        completed = True
    finally:
        failure = str(sys.exc_info()[1]) if sys.exc_info()[1] else None
        sql(f'DELETE FROM books WHERE id={test_id}')
        redis('DEL',key)
        result={'time':datetime.now(timezone.utc).isoformat(),'completed':completed,'failure':failure,'checks':checks,'passed':sum(c['passed'] for c in checks),'total':len(checks),'observations':observations}
        (EVIDENCE/'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
        (EVIDENCE/'verification.txt').write_text('\n'.join(('PASS' if c['passed'] else 'FAIL')+' '+c['name'] for c in checks)+f"\n{result['passed']}/{result['total']} passed; completed={completed}; failure={failure}\n")
    print(f'{len(checks)} checks passed. Evidence: {EVIDENCE}')

if __name__=='__main__': main()
