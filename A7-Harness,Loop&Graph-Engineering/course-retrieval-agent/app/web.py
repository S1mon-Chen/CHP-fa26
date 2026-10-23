"""本地课堂后端：固定 API、结构化日志 SSE、子进程执行同一 Harness。"""
import argparse
import asyncio
from contextlib import asynccontextmanager
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import uuid
from typing import Literal
from fastapi import FastAPI,HTTPException,Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse,JSONResponse,StreamingResponse
from pydantic import Field
from .schemas import StrictModel
from .agent_types import AgentConfig
from .cli import Settings
from .ollama import OllamaClient
from .scripted import SCENARIOS
from .runner import service_factory
from .recording import replay,ReplayError,write_json,sha

ROOT=Path(__file__).resolve().parent.parent
ID=re.compile(r'^[a-f0-9]{32}$')
EXAMPLES=['HDFS 文件块与副本有什么关系？','比较 Spark 和 Flink 的事件时间与水印。','Spark 某个具体版本的精确吞吐量是多少？']


class Start(StrictModel):
    request_id: str = Field(pattern=r'^[a-f0-9]{32}$')
    question: str = Field(min_length=1,max_length=2000)
    mode: Literal['live','scripted'] = 'scripted'
    scenario: Literal['single','compare','insufficient','unknown_tool','unauthorized','stalled','budget','bad_citation','injection'] = 'single'
    model: str | None = Field(default=None,pattern=r'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$')
    max_decisions: int = Field(default=8,ge=1,le=32)
    max_tool_attempts: int = Field(default=10,ge=1,le=100)


class Manager:
    def __init__(self,root):
        self.root=Path(root).resolve();self.base=self.root/'runs'
        if self.base.is_symlink():raise ValueError('运行目录不能是符号链接')
        self.base.mkdir(exist_ok=True)
        self.control=self.root/'.web';self.control.mkdir(exist_ok=True)
        self.lock=threading.Lock();self.child=None;self.active_id=None;self.guard=None;self.server_lock=None;self.execution_slot=None

    def acquire(self):
        self.server_lock=(self.control/'server.lock').open('a')
        try:fcntl.flock(self.server_lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise RuntimeError('本项目已有课堂服务；请使用原服务') from None

    def shutdown(self):
        if self.child:self.child.wait()  # 正常退出等候当前 Harness 有限任务完成。
        if self.guard is not None:os.close(self.guard);self.guard=None
        if self.server_lock:self.server_lock.close()
        if self.execution_slot:self.execution_slot.close()

    def active(self):
        if self.child and self.child.poll() is None:return self.active_id
        if self.execution_slot:self.execution_slot.close();self.execution_slot=None
        return None

    def safe(self,run_id,name):
        if not ID.fullmatch(run_id):raise HTTPException(400,'非法 run_id')
        folder=self.base/run_id;path=folder/name
        if folder.is_symlink() or path.is_symlink() or not path.resolve().is_relative_to(self.base.resolve()):raise HTTPException(400,'运行产物路径越界')
        return path

    def read(self,run_id,name,default=None):
        path=self.safe(run_id,name)
        return json.loads(path.read_text()) if path.exists() else default

    def raw_events(self,run_id):
        path=self.safe(run_id,'events.jsonl')
        if not path.exists():return []
        rows=[]
        for line in path.read_bytes().splitlines(keepends=True):
            if not line.endswith(b'\n'):break  # 写入中的最后半行不算事件。
            e=json.loads(line)
            if e['seq']!=len(rows)+1 or e['run_id']!=run_id:raise ValueError('事件不连续')
            rows.append(e)
        return rows

    def detail(self,run_id):
        folder=self.safe(run_id,'').resolve()
        pending=self.control/(run_id+'.json')
        if not folder.exists() and not pending.exists():raise HTTPException(404,'运行不存在')
        active=self.active()==run_id
        try:
            cfg=self.read(run_id,'config.json',{})
            if not cfg and pending.exists():
                job=json.loads(pending.read_text());cfg={'question':job['question'],'mode':job['mode'],'scenario':job['scenario'],'agent':job['agent']}
            events=self.raw_events(run_id)
            if (folder/'manifest.json').exists():
                saved=replay(self.base,run_id)
                return {**saved,'active':active,'config':cfg,'complete':True}
            status='RUNNING' if active else 'INTERRUPTED'
            last=events[-1] if events else {}
            summary={'run_id':run_id,'execution_status':status,'answer_status':None,
                     'decision_rounds':last.get('decision_round',0),'tool_attempts':last.get('tool_attempts',0),
                     'model_requests':sum(e['type']=='model_request' for e in events),'elapsed_seconds':last.get('elapsed_seconds',0),
                     'mode':cfg.get('mode'),'model':cfg.get('agent',{}).get('model') if cfg.get('mode')=='live' else None,
                     'corpus_snapshot_id':cfg.get('tools',{}).get('corpus_snapshot_id'),
                     'answer_repairs':sum(e['type']=='answer_rejected' for e in events),'token_statistics':[]}
            return {'summary':summary,'config':cfg,'active':active,'complete':False,'events':events,'answer':None,'evidence':{},
                    'notice':None if active else '运行中断：未完成完整落盘，不支持续跑或完整回放。'}
        except (ReplayError,ValueError,KeyError,TypeError,OSError):
            return {'summary':{'run_id':run_id,'execution_status':'CORRUPT','answer_status':None},'config':{},
                    'active':active,'complete':False,'events':[],'answer':None,'evidence':{},'notice':'记录或产物损坏，拒绝补齐或替换证据。'}

    def event_data(self,run_id,event):
        """只解析已登记事件引用，不接受浏览器提供文件名。"""
        ref=event.get('artifact')
        if not ref:return None
        if not re.fullmatch(r'artifacts/[a-f0-9]{64}\.json',ref.get('path','')):raise HTTPException(409,'非法产物引用')
        path=self.safe(run_id,ref['path'])
        try:
            raw=path.read_bytes()
            if sha(raw)!=ref['sha256']:raise ValueError()
            value=json.loads(raw)
        except (OSError,ValueError,KeyError):raise HTTPException(409,'引用产物缺失或损坏') from None
        # 不将完整模型请求上下文作为页面事件详情，避免重复展示大文本。
        return value if event['type'] in ('action_proposed','tool_observation','answer_rejected','answer_accepted','snapshot_loaded') else None

    def enriched(self,run_id,events):
        return [{'event':e,'data':self.event_data(run_id,e)} for e in events]

    def status(self):
        result={'active_run_id':self.active(),'corpus':{'available':False},'ollama':{'available':False,'models':[]}}
        try:
            settings=Settings.model_validate_json((self.root/'config.json').read_text())
            service=service_factory(self.root,settings,'scripted','single',None)()
            result['corpus']={'available':True,'documents':len(service.allowed),'snapshot_id':service.snapshot.corpus_snapshot_id}
        except Exception:result['corpus']['error']='资料或可信配置无法加载'
        try:
            config=AgentConfig.model_validate_json((self.root/'agent-config.json').read_text())
            result['default_model']=config.model
            data=OllamaClient(config).http('/api/tags',None,2)
            result['ollama']={'available':True,'models':[m['name'] for m in data['models']]}
        except Exception:result['ollama']['error']='Ollama 无法连接或配置无效；可使用 scripted / 历史回放'
        return result

    def start(self,args):
        with self.lock:
            request_file=self.control/('request-'+args.request_id+'.json')
            payload=args.model_dump()
            if request_file.exists():
                old=json.loads(request_file.read_text())
                if old['request']!=payload:raise HTTPException(409,'同一请求 ID 的参数发生变化')
                return {'run_id':old['run_id'],'duplicate':True}
            if self.active():raise HTTPException(409,'已有活动运行，请等待其结束')
            if not args.question.strip():raise HTTPException(422,'问题不能为空白')
            if args.mode!='live' and args.model is not None:raise HTTPException(422,'仅 live 可指定模型')
            slot=(self.control/'execution.lock').open('a')
            try:fcntl.flock(slot,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:
                slot.close();raise HTTPException(409,'已有活动执行（CLI/网页共用执行槽）') from None
            try:
                settings=Settings.model_validate_json((self.root/'config.json').read_text())
                config=AgentConfig.model_validate_json((self.root/'agent-config.json').read_text())
                config=AgentConfig.model_validate({**config.model_dump(),'model':args.model or config.model,
                      'max_decisions':args.max_decisions,'max_tool_attempts':args.max_tool_attempts})
                from .scripted import ScriptedModel
                service_factory(self.root,settings,args.mode,args.scenario,ScriptedModel(args.scenario))()
                if args.mode=='live':OllamaClient(config).preflight(min(config.model_timeout,3))
            except Exception:
                slot.close();raise HTTPException(503,'启动条件不满足：检查资料、Ollama 连接及所选模型是否安装') from None
            run_id=uuid.uuid4().hex
            job={'run_id':run_id,'root':str(self.root),'question':args.question,'mode':args.mode,
                 'scenario':args.scenario if args.mode=='scripted' else None,'agent':config.model_dump(),'settings':settings.model_dump()}
            job_file=self.control/(run_id+'.json');write_json(job_file,job)
            read_fd,write_fd=os.pipe()
            try:
                child=subprocess.Popen([sys.executable,'-m','app.web_worker',str(job_file),str(read_fd),str(slot.fileno())],
                    cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
                    pass_fds=(read_fd,slot.fileno()),start_new_session=True)
            except Exception:
                slot.close();os.close(write_fd);raise HTTPException(503,'后台执行进程无法启动') from None
            finally:os.close(read_fd)
            if self.guard is not None:os.close(self.guard)
            self.child=child;self.guard=write_fd;self.active_id=run_id;self.execution_slot=slot
            def release_slot():
                child.wait();slot.close()
            threading.Thread(target=release_slot,daemon=True).start()
            write_json(request_file,{'request':payload,'run_id':run_id})
            return {'run_id':run_id,'duplicate':False}


def create_app(root=ROOT,port=8770):
    manager=Manager(root)
    @asynccontextmanager
    async def lifespan(app):
        manager.acquire()
        yield
        await asyncio.to_thread(manager.shutdown)
    app=FastAPI(lifespan=lifespan,docs_url=None,redoc_url=None,openapi_url=None)
    app.state.manager=manager
    hosts={f'127.0.0.1:{port}',f'localhost:{port}'}
    @app.middleware('http')
    async def local_only(request,call_next):
        host=request.headers.get('host','')
        origin=request.headers.get('origin')
        if host not in hosts or (origin is not None and origin!='http://'+host):return JSONResponse({'detail':'拒绝非本地同源请求'},403)
        if request.method not in ('GET','POST'):return JSONResponse({'detail':'方法不允许'},405)
        if request.method=='POST':
            if request.headers.get('content-type','').split(';')[0]!='application/json':return JSONResponse({'detail':'只接受 JSON'},415)
            if len(await request.body())>8192:return JSONResponse({'detail':'请求过大'},413)
        response=await call_next(request)
        response.headers['Cache-Control']='no-store'
        response.headers['Content-Security-Policy']="default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; object-src 'none'"
        response.headers['X-Content-Type-Options']='nosniff'
        return response
    @app.exception_handler(RequestValidationError)
    async def invalid(request,exc):return JSONResponse({'detail':'字段、类型或范围不符合接口约定'},422)
    @app.get('/api/status')
    def status():return manager.status()
    @app.get('/api/scenarios')
    def scenarios():return {'scenarios':SCENARIOS,'examples':EXAMPLES}
    @app.post('/api/runs')
    def start(args:Start):return manager.start(args)
    @app.get('/api/runs')
    def runs():
        ids={p.name for p in manager.base.iterdir() if ID.fullmatch(p.name)}
        ids|={p.stem for p in manager.control.glob('*.json') if ID.fullmatch(p.stem)}
        rows=[]
        for run_id in ids:
            d=manager.detail(run_id);cfg=d['config'];ev=d['events']
            rows.append({**d['summary'],'question':cfg.get('question','记录不可读'),'time':ev[0].get('time') if ev else '',
                         'active':d['active'],'complete':d['complete']})
        return sorted(rows,key=lambda r:r.get('time') or '',reverse=True)
    @app.get('/api/runs/{run_id}')
    def detail(run_id):return manager.detail(run_id)
    @app.get('/api/runs/{run_id}/answer')
    def answer(run_id):
        d=manager.detail(run_id);return {'answer':d['answer'],'status':d['summary']['execution_status'],'notice':d.get('notice')}
    @app.get('/api/runs/{run_id}/evidence')
    def evidence(run_id):
        d=manager.detail(run_id);return {'evidence':d['evidence'],'complete':d['complete'],'notice':d.get('notice')}
    @app.get('/api/runs/{run_id}/evidence/{evidence_id}')
    def one_evidence(run_id,evidence_id):
        if not re.fullmatch(r'E[1-9][0-9]{0,5}',evidence_id):raise HTTPException(400,'非法 evidence_id')
        d=manager.detail(run_id)
        if evidence_id not in d['evidence']:raise HTTPException(404,'本次记录中没有该证据')
        return d['evidence'][evidence_id]
    @app.get('/api/runs/{run_id}/replay')
    def history(run_id):
        d=manager.detail(run_id)
        if not d['complete']:raise HTTPException(409,d.get('notice') or '运行尚未完成')
        return {**d,'timeline':manager.enriched(run_id,d['events'])}
    @app.get('/api/runs/{run_id}/events')
    async def subscribe(run_id,request:Request,after:int=0):
        try:cursor=max(after,int(request.headers.get('last-event-id','0')))
        except ValueError:raise HTTPException(400,'非法事件序号')
        if cursor<0 or after<0:raise HTTPException(400,'非法事件序号')
        await asyncio.to_thread(manager.detail,run_id)
        async def stream():
            nonlocal cursor
            while not await request.is_disconnected():
                d=await asyncio.to_thread(manager.detail,run_id)
                batch=[e for e in d['events'] if e['seq']>cursor]
                try:enriched=await asyncio.to_thread(manager.enriched,run_id,batch)
                except HTTPException:
                    yield 'event: damaged\ndata: {"error":"运行产物损坏"}\n\n';return
                for item in enriched:
                    cursor=item['event']['seq']
                    yield f'id: {cursor}\nevent: harness\ndata: {json.dumps(item,ensure_ascii=False)}\n\n'
                if not d['active']:
                    yield 'event: complete\ndata: '+json.dumps(d['summary'],ensure_ascii=False)+'\n\n';return
                yield ': heartbeat\n\n'
                await asyncio.sleep(.25)
        return StreamingResponse(stream(),media_type='text/event-stream',headers={'X-Accel-Buffering':'no'})
    @app.get('/')
    def index():return FileResponse(ROOT/'web/index.html')
    @app.get('/assets/{name}')
    def assets(name):
        if name not in ('app.js','state.mjs','style.css'):raise HTTPException(404)
        return FileResponse(ROOT/'web'/name)
    return app


def main():
    import uvicorn
    parser=argparse.ArgumentParser(description='课程检索课堂网页')
    parser.add_argument('--port',type=int,default=8770)
    parser.add_argument('--root',type=Path,default=ROOT,help='教师可信项目目录，测试可指定临时目录')
    args=parser.parse_args()
    print(f'课堂网页：http://127.0.0.1:{args.port}；不提供运行取消。Ctrl+C 正常退出将等待当前运行结束。')
    uvicorn.run(create_app(args.root,args.port),host='127.0.0.1',port=args.port)


if __name__=='__main__':main()
