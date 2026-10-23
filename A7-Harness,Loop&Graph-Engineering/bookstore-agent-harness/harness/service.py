"""CLI/Web 共用的运行服务：文件锁互斥、恢复核对、审批绑定、独立分支。"""
import copy
import fcntl
import json
import re
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from langgraph.errors import GraphRecursionError
from langgraph.types import Command

from .core import digest, manifest, run
from .graph_engine import graph_session, validate_approval
from .runtime import Runtime, RecoveryRequired, TERMINAL, atomic, read_json

ID = re.compile(r'^[a-f0-9]{32}$')
CHECKPOINT = re.compile(r'^[0-9a-f-]{36}$')


class ServiceError(RuntimeError):
    pass


class RunService:
    def __init__(self, root, out=None):
        self.root = Path(root).resolve()
        self.base = Path(out or self.root/'runs').resolve()
        self.control = self.root/'.harness'
        self.database = self.control/'checkpoints.sqlite'

    def folder(self, run_id):
        if not ID.fullmatch(run_id):
            raise ServiceError('非法 run_id')
        folder = (self.base/run_id).resolve()
        if not folder.is_relative_to(self.base) or not folder.is_dir():
            raise ServiceError('运行不存在或路径越界')
        return folder

    @contextmanager
    def slot(self, run_id):
        self.control.mkdir(exist_ok=True)
        with (self.control/'execution.lock').open('a') as stream:
            try:
                fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:
                raise ServiceError('已有活动执行；CLI 与网页共用同一执行槽位')
            try:
                atomic(self.control/'active.json',{'run_id':run_id})
                yield
            finally:
                atomic(self.control/'active.json',{'run_id':None})
                fcntl.flock(stream,fcntl.LOCK_UN)

    def active(self):
        path = self.control/'execution.lock'
        if not path.exists():
            return None
        with path.open('a') as stream:
            try:
                fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
                fcntl.flock(stream,fcntl.LOCK_UN)
                return None
            except BlockingIOError:
                return read_json(self.control/'active.json',{}).get('run_id') or 'starting'

    def config(self, state):
        return {'configurable':{'thread_id':state['thread_id']},
                'recursion_limit':state['config'].get('recursion_limit',64)}

    def runtime(self, state, on_event=None, tools=False):
        model=executor=None
        if tools:
            from .runner import adapters
            model,executor,_ = adapters(self.root,state['config'],state['mode'],state['scenario'],
                                        state['observation']=='simulated',check=False)
        return Runtime(self.root,state['config'],model,executor,self.base,run_id=state['run_id'],
                       observation=state['observation'],on_event=on_event)

    def metadata(self, run_id):
        metadata = read_json(self.folder(run_id)/'metadata.json',{})
        if metadata.get('engine') != 'langgraph':
            raise ServiceError('此操作只支持 LangGraph 运行；Python 引擎保留原有流程')
        return metadata

    def check_version(self, runtime, state):
        if state['task_version'] != manifest(self.root,state['config'])['files']:
            raise RecoveryRequired('任务、固定测试或初始代码版本已变化；请恢复文件版本后再恢复')
        if digest((runtime.folder/'initial.py').read_text()) != state['initial_code_hash'] or digest((runtime.folder/'task.md').read_text()) != state['task_hash']:
            raise RecoveryRequired('运行的任务/初始代码快照已被改变')
        if state.get('code_ref'):
            runtime.verified_code(state)

    def start(self, config, mode, scenario='success', simulate=False, recording=None, *, run_id=None, on_event=None):
        from .runner import adapters
        run_id = run_id or uuid.uuid4().hex
        with self.slot(run_id):
            model,executor,origin = adapters(self.root,config,mode,scenario,simulate)
            engine = config.get('engine','python')
            if engine not in ('python','langgraph'):
                raise ServiceError('engine 必须为 python 或 langgraph')
            # 兼容旧 CLI 的录制校验；Tape 保证 replay 不调用模型/工具。
            if engine=='python' or mode=='replay':
                return run(self.root,config,model,executor,self.base,recording,origin,run_id=run_id,
                           on_event=on_event,mode=mode,scenario=scenario)
            runtime = Runtime(self.root,config,model,executor,self.base,run_id=run_id,observation=origin,on_event=on_event)
            state = runtime.initialize(mode,scenario,'langgraph')
            return self.drive(runtime,state,initial=state)

    def drive(self,runtime,state,initial=None,answer=None):
        try:
            self.check_version(runtime,state)
            (runtime.folder/'operation-error.json').unlink(missing_ok=True)
            (runtime.folder/'recovery-required.json').unlink(missing_ok=True)
            with graph_session(self.database,runtime) as graph:
                graph.invoke(Command(resume=answer) if answer is not None else initial,self.config(state))
                snapshot = graph.get_state(self.config(state))
                return self.reconcile(runtime,snapshot)
        except RecoveryRequired as exc:
            state = self.latest(runtime,state)
            atomic(runtime.folder/'recovery-required.json',{'reason':str(exc)})
            runtime.emit(state,state.get('next','RECOVERY'),'recovery_required',error='RECOVERY_REQUIRED',summary=str(exc))
            return runtime.report(state,'RECOVERY_REQUIRED',str(exc))
        except Exception as exc:
            state = self.latest(runtime,state)
            name = 'GRAPH_RECURSION_LIMIT' if isinstance(exc,GraphRecursionError) else type(exc).__name__
            state = dict(state,status='EXECUTION_ERROR')
            atomic(runtime.folder/'framework-error.json',{'error':name,'reason':str(exc)})
            runtime.emit(state,'FRAMEWORK','framework_error',error=name,summary=str(exc))
            return runtime.finish(state)

    def latest(self,runtime,fallback):
        with graph_session(self.database,runtime) as graph:
            return dict(graph.get_state(self.config(fallback)).values) or fallback

    def reconcile(self,runtime,snapshot):
        state = dict(snapshot.values)
        if not state:
            return runtime.report(read_json(runtime.folder/'metadata.json'),'RECOVERY_REQUIRED','缺少可恢复 checkpoint，不能伪装从头续跑')
        error = read_json(runtime.folder/'framework-error.json')
        if error:
            return runtime.report(state,'EXECUTION_ERROR',error['reason'])
        blocked = read_json(runtime.folder/'recovery-required.json')
        if blocked:
            return runtime.report(state,'RECOVERY_REQUIRED',blocked['reason'])
        pending = any(task.interrupts for task in snapshot.tasks)
        if not snapshot.next and state['status'] in TERMINAL:
            # 图结束但文件投影可能没完成；重新生成汇总，并按事件 ID 去重终止事件。
            return runtime.finish(state)
        if pending and not (runtime.folder/'approval-decision.json').exists():
            return runtime.report(state,'WAITING_APPROVAL')
        return runtime.report(state,'RECOVERABLE','从 checkpoint 继续；未完成动作会先核对收据和遗留执行')

    def inspect(self,run_id,reconcile=True):
        state = self.metadata(run_id)
        runtime = self.runtime(state)
        with graph_session(self.database,runtime) as graph:
            snapshot = graph.get_state(self.config(state))
            values = dict(snapshot.values) or state
            pending = any(task.interrupts for task in snapshot.tasks)
            if reconcile and self.active()!=run_id:
                try:
                    self.check_version(runtime,values)
                    summary = self.reconcile(runtime,snapshot)
                except RecoveryRequired as exc:
                    summary = runtime.report(values,'RECOVERY_REQUIRED',str(exc))
            else:
                summary = read_json(runtime.folder/'summary.json')
            return {'summary':summary,'state':values,'next':list(snapshot.next),'interrupted':pending,
                    'checkpoint_id':snapshot.config.get('configurable',{}).get('checkpoint_id') if snapshot.config else None,
                    'approval_pending':pending and not (runtime.folder/'approval-decision.json').exists()}

    def checkpoints(self,run_id):
        state = self.metadata(run_id)
        with graph_session(self.database,self.runtime(state)) as graph:
            return [{'checkpoint_id':s.config['configurable']['checkpoint_id'],'time':s.created_at,
                     'next':list(s.next),'candidate':s.values.get('candidate'), 'test_status':s.values.get('test_status'),
                     'status':s.values.get('status'), 'safe_to_fork':s.next==('GENERATE',) and s.values.get('candidate')==0,
                     'label':'LOAD 完成 · 首次生成前' if s.next==('GENERATE',) and s.values.get('candidate')==0 else '下一节点：'+(', '.join(s.next) or 'END')}
                    for s in graph.get_state_history(self.config(state))]

    def approval_check(self,run_id,answer):
        view = self.inspect(run_id,reconcile=False)
        state = view['state']
        if not view['approval_pending'] or state.get('status')!='WAITING_APPROVAL' or view['next']!=['APPROVAL']:
            raise ServiceError('当前不是可审批状态；已完成、已拒绝或审批已提交的运行不能再次审批')
        runtime = self.runtime(state)
        try:
            self.check_version(runtime,state)
            blocked = read_json(runtime.folder/'recovery-required.json')
            if blocked:
                raise RecoveryRequired(blocked['reason'])
            validate_approval(runtime,state,answer)
        except RecoveryRequired as exc:
            raise ServiceError(str(exc))
        return state

    def approve(self,run_id,answer,on_event=None):
        with self.slot(run_id):
            # 文件锁使同一运行恢复、两次审批、CLI 与 Web 互斥。
            state = self.approval_check(run_id,answer)
            runtime = self.runtime(state,on_event)
            if not isinstance(answer.get('note',''),str) or len(answer.get('note',''))>1000:
                raise ServiceError('审批备注最多 1000 字')
            answer = dict(answer,time=datetime.now(timezone.utc).isoformat())
            atomic(runtime.folder/'approval-decision.json',answer)
            runtime.emit(state,'APPROVAL','approval_submitted',key='approval-submitted:'+answer['approval_id'],details=answer)
            return self.drive(runtime,state,answer=answer)

    def resume(self,run_id,on_event=None):
        with self.slot(run_id):
            view = self.inspect(run_id,reconcile=False)
            state = view['state']
            if not view['next'] or (self.folder(run_id)/'framework-error.json').exists():
                raise ServiceError('运行已结束，不能恢复；可从安全 checkpoint 创建新分支')
            runtime = self.runtime(state,on_event,tools=any(n in ('LOAD','GENERATE','VALIDATE','APPLY','TEST') for n in view['next']))
            answer = read_json(runtime.folder/'approval-decision.json')
            if view['interrupted'] and answer is None:
                return runtime.report(state,'WAITING_APPROVAL')
            runtime.emit(state,'RECOVERY','resume_requested',summary='继续原 thread；不重置预算或失败指纹')
            return self.drive(runtime,state,answer=answer if view['interrupted'] else None)

    def fork(self,run_id,checkpoint_id,reason,model=None,budget=None,on_event=None,child_id=None):
        if not CHECKPOINT.fullmatch(checkpoint_id) or not reason.strip() or len(reason)>1000:
            raise ServiceError('必须提供合法 checkpoint 和非空分支原因（最多 1000 字）')
        if budget is not None and (type(budget) is not int or not 1<=budget<=5):
            raise ServiceError('分支预算必须为 1–5')
        if model is not None and not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}',model):
            raise ServiceError('非法模型名称')
        child_id = child_id or uuid.uuid4().hex
        with self.slot(child_id):
            parent = self.metadata(run_id)
            parent_runtime = self.runtime(parent)
            with graph_session(self.database,parent_runtime) as graph:
                selected = graph.get_state({'configurable':{'thread_id':parent['thread_id'],'checkpoint_id':checkpoint_id}})
            if not selected.values or selected.next!=('GENERATE',) or selected.values.get('candidate')!=0:
                raise ServiceError('只允许从 LOAD 完成、首次 GENERATE 之前的 checkpoint 分支')
            source = dict(selected.values)
            self.check_version(parent_runtime,source)
            config = dict(source['config'])
            if model is not None:
                config['model']=model
            if budget is not None:
                config['max_candidates']=budget
            from .runner import adapters
            adapters_ = adapters(self.root,config,source['mode'],source['scenario'],source['observation']=='simulated',check=False)
            runtime = Runtime(self.root,config,adapters_[0],adapters_[1],self.base,run_id=child_id,
                              observation=source['observation'],on_event=on_event)
            child = runtime.initialize(source['mode'],source['scenario'],'langgraph',parent=run_id,
                    checkpoint=checkpoint_id,reason=reason,initial=(parent_runtime.folder/'initial.py').read_text(),
                    task=(parent_runtime.folder/'task.md').read_text())
            # 明确复制所选状态，再替换新线程身份和产物引用，不把父 checkpoint_id 用作子 config。
            copied = copy.deepcopy(source)
            copied.update({k:child[k] for k in ('run_id','thread_id','config','parent_run_id','source_checkpoint',
                                               'branch_reason','remaining_candidates')})
            copied.update(approval_request=None,approval_result=None,status='RUNNING',next='GENERATE')
            runtime.metadata(copied)
            with graph_session(self.database,runtime) as graph:
                graph.update_state(self.config(copied),copied,as_node='LOAD')
            runtime.emit(copied,'LOAD','fork_created',details={'parent_run_id':run_id,'checkpoint_id':checkpoint_id,'reason':reason})
            return self.drive(runtime,copied)
