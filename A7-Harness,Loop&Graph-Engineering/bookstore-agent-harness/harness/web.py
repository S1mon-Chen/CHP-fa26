"""本地 FastAPI 课堂服务。只装配现有 Harness；记录是 SSE 与页面的事实来源。"""
import argparse
import asyncio
import difflib
import json
import re
import secrets
import subprocess
import threading
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt, model_validator

from .core import digest
from .service import RunService, ServiceError
from .runtime import atomic
from .runner import SCENARIOS, execute

ROOT = Path(__file__).resolve().parent.parent
RUN_ID = re.compile(r'^[a-f0-9]{32}$')


class StartRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    engine: Literal['python', 'langgraph'] = 'python'
    mode: Literal['live', 'scripted', 'replay'] = 'scripted'
    scenario: Literal['success', 'repeated', 'forbidden', 'budget'] = 'success'
    model: str | None = Field(default=None, pattern=r'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$')
    max_candidates: StrictInt = Field(default=3, ge=1, le=5)
    simulate: StrictBool = False
    source: str | None = Field(default=None, pattern=r'^[a-f0-9]{32}$')
    request_id: str = Field(pattern=r'^[a-f0-9]{32}$')

    @model_validator(mode='after')
    def modes(self):
        if self.simulate and self.mode != 'scripted':
            raise ValueError('只有 scripted 可以显式使用模拟结果')
        if self.mode == 'replay' and not self.source:
            raise ValueError('replay 必须选择历史运行')
        if self.model is not None and self.mode != 'live':
            raise ValueError('仅 live 模式可设置模型名称')
        return self


class ApprovalRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    run_id: str = Field(pattern=r'^[a-f0-9]{32}$')
    approval_id: str = Field(pattern=r'^[a-f0-9]{32}$')
    code_hash: str = Field(pattern=r'^[a-f0-9]{64}$')
    approved: StrictBool
    note: str = Field(default='',max_length=1000)


class ForkRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    checkpoint_id: str = Field(pattern=r'^[0-9a-f-]{36}$')
    reason: str = Field(min_length=1,max_length=1000)
    model: str | None = Field(default=None,pattern=r'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$')
    max_candidates: StrictInt = Field(default=3,ge=1,le=5)


class RunStore:
    """只读白名单产物；兼容第一阶段的平铺目录和上一轮的嵌套目录。"""
    def __init__(self, root):
        self.root = root
        self.base = (root / 'runs').resolve()
        self.base.mkdir(exist_ok=True)

    def safe(self, path):
        resolved = path.resolve()
        if not resolved.is_relative_to(self.base):
            raise HTTPException(400, '路径越界被拒绝')
        return resolved

    def folder(self, run_id):
        if not RUN_ID.fullmatch(run_id):
            raise HTTPException(400, '非法 run_id')
        direct = self.base / run_id
        if direct.exists():
            return self.safe(direct)
        for path in self.base.glob(f'**/{run_id}/events.jsonl'):
            return self.safe(path.parent)
        raise HTTPException(404, '未找到运行记录')

    def read(self, folder, name, default=None):
        path = self.safe(folder / name)
        if not path.exists():
            return default
        try:
            return json.loads(path.read_text())
        except json.JSONDecodeError:
            return default  # 原子写入前的旧版产物可能处于写入中。

    def text(self, folder, name, default=''):
        path = self.safe(folder / name)
        return path.read_text() if path.exists() else default

    def events(self, run_id, after=0):
        folder = self.folder(run_id)
        result = []
        for line in self.text(folder, 'events.jsonl').splitlines():
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event['seq'] > after:
                result.append(event)
        return result

    def detail(self, run_id, active=False, historical=False):
        folder = self.folder(run_id)
        metadata_ = self.read(folder, 'metadata.json', {})
        graph = RunService(self.root).inspect(run_id) if not historical and metadata_.get('engine') == 'langgraph' and folder.parent == self.base else None
        events = self.events(run_id)
        summary = self.read(folder, 'summary.json')
        launch = self.read(folder, 'launch.json', {})
        metadata = self.read(folder, 'metadata.json', launch)
        recording = self.read(folder, 'recording.json', {})
        config = recording.get('manifest', {}).get('config', {})
        last = events[-1] if events else {}
        return {'run_id': run_id, 'active': active, 'summary': summary, 'events': events,
                'engine': metadata.get('engine','python'), 'graph': graph,
                'operation_error': self.read(folder,'operation-error.json'),
                'mode': metadata.get('mode') or (summary or {}).get('mode'),
                'max_candidates': metadata.get('max_candidates', config.get('max_candidates')),
                'observation': (summary or {}).get('observation', metadata.get('observation')),
                'source_observation': (summary or {}).get('source_observation'),
                'state': summary['status'] if summary else last.get('node', 'LOAD') if active else 'INTERRUPTED',
                'remaining_candidates': last.get('remaining_candidates',
                    max(0, config['max_candidates'] - last.get('candidate', 0)) if config else None)}

    def listing(self, active_id=None):
        folders = {p.parent for pattern in ('**/summary.json', '**/launch.json', '**/metadata.json')
                   for p in self.base.glob(pattern)}
        result = []
        for folder in sorted(folders, key=lambda p: p.stat().st_mtime, reverse=True):
            if not RUN_ID.fullmatch(folder.name):
                continue
            try:
                detail = self.detail(folder.name, active_id == folder.name)
                detail.pop('events')
                result.append(detail)
            except (HTTPException, OSError, KeyError):
                continue
        return result

    def artifacts(self, run_id):
        folder = self.folder(run_id)
        recording = self.read(folder, 'recording.json', {})
        calls = recording.get('calls', [])
        first = next((c['request'] for c in calls if c['kind'] == 'model'), {})
        initial = self.text(folder, 'initial.py', first.get('initial_code', ''))
        task = self.text(folder, 'task.md', first.get('task', ''))
        events = self.events(run_id)
        proposals, tests = {}, {}
        for path in folder.glob('proposal-*.json'):
            proposals[path.stem.split('-')[1]] = self.read(folder, path.name)
        # 老记录没有 proposal 产物，从已录制响应恢复，不调用任何适配器。
        for call in calls:
            if call['kind'] != 'model' or not call.get('response'):
                continue
            number = str(call['request']['candidate'])
            if number in proposals:
                continue
            try:
                value = json.loads(call['response']['raw'])
                if not isinstance(value, dict):
                    continue
                code = value.get('code')
                relevant = [e for e in events if str(e['candidate']) == number]
                rejected = next((e for e in relevant if e['event_type'] == 'rejected'), None)
                proposals[number] = {'path': value.get('path'), 'code': code,
                                     'code_hash': digest(code) if isinstance(code, str) else None,
                                     'decision': 'rejected' if rejected else 'applied',
                                     'reason': rejected['test_summary'] if rejected else None}
            except (ValueError, KeyError, TypeError):
                continue
        test_events = [e for e in events if e['event_type'] == 'test_result']
        old_tests = [c['response'] for c in calls if c['kind'] == 'test' and c.get('response')]
        for index, event in enumerate(test_events):
            number = str(event['candidate'])
            tests[number] = self.read(folder, f'test-{number}.json',
                                      old_tests[index] if index < len(old_tests) else event.get('details') or {})
        for number, proposal in proposals.items():
            code = proposal.get('code')
            proposal['diff'] = ''.join(difflib.unified_diff(initial.splitlines(True), code.splitlines(True),
                fromfile='initial/bookstore.py', tofile=f'proposal-{number}/bookstore.py')) if isinstance(code, str) else ''
        return {'initial': initial, 'task': task, 'proposals': proposals, 'tests': tests,
                'diff': self.text(folder, 'changes.diff')}


def dependency_status(root, mode, simulate=False, model=None):
    """只检查本模式的依赖；replay 不触发 Docker 或 HTTP 探测。"""
    config = json.loads((root / 'config.json').read_text())
    result = {'docker': {'available': None, 'message': '本模式无需检查'},
              'ollama': {'available': None, 'message': '本模式无需检查'}}
    if mode == 'replay' or simulate:
        return result
    try:
        info = subprocess.run(['docker', 'info'], capture_output=True, timeout=4)
        image = subprocess.run(['docker', 'image', 'inspect', config['image']], capture_output=True, timeout=4) if info.returncode == 0 else None
        ready = info.returncode == 0 and image is not None and image.returncode == 0
        result['docker'] = {'available': ready, 'message': 'Docker 与教学镜像可用' if ready else 'Docker 未运行或教学镜像缺失，请启动 Docker 并构建镜像'}
    except (OSError, subprocess.TimeoutExpired):
        result['docker'] = {'available': False, 'message': 'Docker 不可用或检查超时'}
    if mode == 'live':
        try:
            with urllib.request.urlopen(config['url'].rstrip('/') + '/api/tags', timeout=4) as response:
                body = response.read(262145)
            if len(body) > 262144:
                raise ValueError('响应过大')
            names = [x['name'] for x in json.loads(body).get('models', [])]
            chosen = model or config['model']
            ready = chosen in names or chosen + ':latest' in names
            result['ollama'] = {'available': ready, 'models': names,
                                'message': f'模型 {chosen} 可用' if ready else f'Ollama 未安装模型 {chosen}'}
        except (OSError, ValueError, KeyError):
            result['ollama'] = {'available': False, 'message': 'Ollama 不可用，请检查服务和可信配置中的地址'}
    return result


class Classroom:
    def __init__(self, root=ROOT):
        self.root = root
        self.store = RunStore(root)
        self.service = RunService(root)
        self.token = secrets.token_urlsafe(32)
        self.lock = threading.RLock()
        self.changed = threading.Condition()
        self.sequences = {}
        self.active_id = None
        self.starts = {}
        self.pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix='harness')
        # 同一请求 ID 跨服务重启也不会重复创建运行。
        for path in self.store.base.glob('*/launch.json'):
            launch = self.store.read(path.parent, 'launch.json', {})
            if launch.get('request_id'):
                self.starts[launch['request_id']] = (launch['request_hash'], launch['run_id'])

    def notify(self, event):
        with self.changed:
            self.sequences[event['run_id']] = event['seq']
            self.changed.notify_all()

    def wait(self, run_id, after):
        with self.changed:
            self.changed.wait_for(lambda: self.sequences.get(run_id, 0) > after, timeout=1)

    def start(self, body):
        if body.mode == 'replay':
            detail = self.store.detail(body.source,historical=True)
            if detail['summary'] is None:
                raise HTTPException(409, '只能回放已完成的运行')
            return {'run_id': body.source, 'replay': True}
        request_hash = digest(body.model_dump(exclude={'request_id'}))
        with self.lock:
            if body.request_id in self.starts:
                previous_hash, run_id = self.starts[body.request_id]
                if previous_hash != request_hash:
                    raise HTTPException(409, '同一请求 ID 的参数不能改变')
                return {'run_id': run_id, 'replay': False, 'duplicate': True}
            if self.active_id or self.service.active():
                raise HTTPException(409, '已有活动运行，请等待完成；本阶段不支持取消')
            deps = dependency_status(self.root, body.mode, body.simulate, body.model)
            errors = [v['message'] for v in deps.values() if v['available'] is False]
            if errors:
                raise HTTPException(409, '；'.join(errors))
            config = json.loads((self.root / 'config.json').read_text())
            config['max_candidates'] = body.max_candidates
            config['engine'] = body.engine
            if body.model:
                config['model'] = body.model
            run_id = uuid.uuid4().hex
            folder = self.store.base / run_id
            folder.mkdir()
            launch = {'run_id': run_id, 'request_id': body.request_id, 'request_hash': request_hash,
                      'mode': body.mode, 'max_candidates': body.max_candidates,
                      'observation': 'simulated' if body.simulate else 'docker'}
            (folder / 'launch.json').write_text(json.dumps(launch))
            self.active_id = run_id
            self.starts[body.request_id] = (request_hash, run_id)
            self.pool.submit(self.worker, run_id, body, config)
            return {'run_id': run_id, 'replay': False, 'duplicate': False}

    def worker(self, run_id, body, config):
        try:
            execute(self.root, config, body.mode, body.scenario, body.simulate,
                    out=self.store.base, run_id=run_id, on_event=self.notify)
        except Exception as exc:
            # 启动级异常也持久化，不伪造业务测试或成功事件。
            folder = self.store.folder(run_id)
            summary = {'run_id': run_id, 'status': 'EXECUTION_ERROR', 'mode': body.mode,
                       'error': str(exc), 'observation': 'simulated' if body.simulate else 'docker',
                       'max_candidates': body.max_candidates}
            (folder / 'summary.json').write_text(json.dumps(summary))
        finally:
            with self.lock:
                self.active_id = None
            with self.changed:
                self.changed.notify_all()


    def operation(self, run_id, action, body=None):
        """所有写操作仍在共享服务内持锁；Web 只提供非阻塞调度。"""
        with self.lock:
            if self.active_id or self.service.active():
                raise HTTPException(409, '已有活动执行，请稍后重试')
            self.store.folder(run_id)
            if action == 'approval':
                answer = body.model_dump()
                if answer['run_id'] != run_id:
                    raise ServiceError('审批 run_id 与 URL 不匹配')
                self.service.approval_check(run_id,answer)
                target = run_id
                work = lambda: self.service.approve(run_id,answer,on_event=self.notify)
            elif action == 'resume':
                view = self.service.inspect(run_id)
                if not view['next']:
                    raise ServiceError('已结束的运行不能恢复')
                target = run_id
                work = lambda: self.service.resume(run_id,on_event=self.notify)
            else:
                checkpoints = self.service.checkpoints(run_id)
                if not any(c['checkpoint_id']==body.checkpoint_id and c['safe_to_fork'] for c in checkpoints):
                    raise ServiceError('所选 checkpoint 不是允许分支的安全边界')
                if not body.reason.strip():
                    raise ServiceError('请输入分支原因')
                target = uuid.uuid4().hex
                folder = self.store.base/target
                folder.mkdir()
                parent = self.service.metadata(run_id)
                atomic(folder/'launch.json',{'run_id':target,'engine':'langgraph','mode':parent['mode'],
                    'observation':parent['observation'],'max_candidates':body.max_candidates,'parent_run_id':run_id})
                work = lambda: self.service.fork(run_id,body.checkpoint_id,body.reason,body.model,
                    body.max_candidates,on_event=self.notify,child_id=target)
            self.active_id = target
            self.pool.submit(self.operation_worker,target,work)
            return {'run_id':target,'action':action}

    def operation_worker(self,run_id,work):
        try:
            work()
        except Exception as exc:
            # 不能用启动错误覆盖已有审批/检查点的真实报告。
            folder=self.store.folder(run_id)
            atomic(folder/'operation-error.json',{'error':str(exc)})
            if not (folder/'metadata.json').exists():
                atomic(folder/'summary.json',self.store.read(folder,'launch.json',{}) |
                       {'status':'EXECUTION_ERROR','error':str(exc)})
        finally:
            with self.lock:
                self.active_id = None
            with self.changed:
                self.changed.notify_all()


def create_app(root=ROOT):
    manager = Classroom(root)

    @asynccontextmanager
    async def lifespan(_):
        yield
        # 关网页不会进入这里；关服务时等待现有 Harness 自行完成并清理 Docker。
        await asyncio.to_thread(manager.pool.shutdown, wait=True)

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None)
    app.state.classroom = manager

    @app.exception_handler(ServiceError)
    async def service_error(request, exc):
        return JSONResponse({'detail': str(exc)}, status_code=409)

    @app.middleware('http')
    async def local_only(request, call_next):
        if request.url.hostname not in ('127.0.0.1', 'localhost'):
            return JSONResponse({'detail': '仅接受本机访问'}, status_code=403)
        if request.method == 'POST' and request.headers.get('x-classroom-token') != manager.token:
            return JSONResponse({'detail': '请从本地课堂页面启动运行'}, status_code=403)
        response = await call_next(request)
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['Content-Security-Policy'] = "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'"
        return response

    @app.get('/api/bootstrap')
    def bootstrap():
        config = json.loads((root / 'config.json').read_text())
        return {'token': manager.token, 'model': config['model'], 'task': (root / 'TASK.md').read_text(),
                'active_run_id': manager.active_id or manager.service.active(), 'cancellation_supported': False}

    @app.get('/api/status')
    def status(mode: Literal['live', 'scripted', 'replay'] = 'scripted', simulate: bool = False, model: str | None = None):
        return {'active_run_id': manager.active_id or manager.service.active(), 'dependencies': dependency_status(root, mode, simulate, model),
                'cancellation_supported': False}

    @app.get('/api/scenarios')
    def scenarios():
        return [{'id': key, 'label': value} for key, value in SCENARIOS.items()]

    @app.get('/api/runs')
    def runs():
        return manager.store.listing(manager.active_id or manager.service.active())

    @app.post('/api/runs', status_code=202)
    def start(body: StartRequest):
        return manager.start(body)

    @app.get('/api/runs/{run_id}')
    def detail(run_id: str, historical: bool = False):
        return manager.store.detail(run_id, (manager.active_id or manager.service.active()) == run_id, historical)

    @app.get('/api/runs/{run_id}/checkpoints')
    def checkpoints(run_id: str):
        manager.store.folder(run_id)
        return manager.service.checkpoints(run_id)

    @app.post('/api/runs/{run_id}/approval', status_code=202)
    def approval(run_id: str, body: ApprovalRequest):
        return manager.operation(run_id,'approval',body)

    @app.post('/api/runs/{run_id}/resume', status_code=202)
    def resume(run_id: str):
        return manager.operation(run_id,'resume')

    @app.post('/api/runs/{run_id}/fork', status_code=202)
    def fork(run_id: str, body: ForkRequest):
        return manager.operation(run_id,'fork',body)

    @app.get('/api/runs/{run_id}/artifacts')
    def artifacts(run_id: str):
        return manager.store.artifacts(run_id)

    @app.get('/api/runs/{run_id}/events')
    async def events(run_id: str, request: Request, after: int = 0):
        manager.store.folder(run_id)
        try:
            header_cursor = int(request.headers.get('last-event-id', '0'))
            cursor = max(after, header_cursor)
            if after < 0 or header_cursor < 0:
                raise ValueError()
        except ValueError:
            raise HTTPException(400, '事件序号必须为非负整数')

        async def stream():
            nonlocal cursor
            while not await request.is_disconnected():
                batch = await asyncio.to_thread(manager.store.events, run_id, cursor)
                for event in batch:
                    cursor = event['seq']
                    yield f'id: {cursor}\nevent: harness\ndata: {json.dumps(event, ensure_ascii=False)}\n\n'
                detail = await asyncio.to_thread(manager.store.detail, run_id, (manager.active_id or manager.service.active()) == run_id)
                if not detail['active']:
                    # 再读一次，避免恰好在前次读取后写入 finished 时漏掉末尾事件。
                    tail = await asyncio.to_thread(manager.store.events, run_id, cursor)
                    for event in tail:
                        cursor = event['seq']
                        yield f'id: {cursor}\nevent: harness\ndata: {json.dumps(event, ensure_ascii=False)}\n\n'
                    yield f'event: complete\ndata: {json.dumps(detail["summary"])}\n\n'
                    return
                yield ': heartbeat\n\n'
                await asyncio.to_thread(manager.wait, run_id, cursor)
        return StreamingResponse(stream(), media_type='text/event-stream', headers={'X-Accel-Buffering': 'no'})

    @app.get('/')
    def index():
        return FileResponse(root / 'web/index.html')

    @app.get('/assets/{name}')
    def asset(name: str):
        if name not in ('app.js', 'state.js', 'style.css'):
            raise HTTPException(404)
        return FileResponse(root / 'web' / name)
    return app


def main():
    import uvicorn
    parser = argparse.ArgumentParser(description='本地 Agent Harness 课堂网页')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--root', type=Path, default=ROOT, help='可信本地项目目录（集成验证使用）')
    args = parser.parse_args()
    print(f'课堂网页：http://127.0.0.1:{args.port}；不支持取消，关闭服务会等待当前运行完成。')
    uvicorn.run(create_app(args.root.resolve()), host='127.0.0.1', port=args.port)


if __name__ == '__main__':
    main()
