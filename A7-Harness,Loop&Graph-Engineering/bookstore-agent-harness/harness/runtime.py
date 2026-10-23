"""共享节点、业务结束规则和文件产物。两个引擎只负责决定下一个节点。"""
import copy
import difflib
import json
import os
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

from .core import Tape, digest, manifest, validate

TERMINAL = {'SUCCESS', 'FAILED', 'STALLED', 'MODEL_ERROR', 'EXECUTION_ERROR', 'REJECTED'}


class RecoveryRequired(RuntimeError):
    pass


def atomic(path, value):
    """单个文件原子替换并 fsync；不声称能与 SQLite/其他文件构成原子事务。"""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    with temp.open('w') as stream:
        stream.write(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2))
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temp, path)
    fd = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def read_json(path, default=None):
    return json.loads(path.read_text()) if path.exists() else default


def failed_status(candidate, config, fingerprints, code_hash, summary):
    """重复补丁 + 同失败优先 STALLED；业务预算与框架递归限制彼此独立。"""
    key = digest({'code': code_hash, 'failure': summary})
    counts = dict(fingerprints)
    counts[key] = counts.get(key, 0) + 1
    status = 'STALLED' if counts[key] >= config['stall_threshold'] else 'FAILED' if candidate >= config['max_candidates'] else 'RUNNING'
    return status, counts


class Runtime:
    def __init__(self, root, config, model, executor, out, *, run_id=None,
                 recording=None, observation='docker', on_event=None):
        self.root, self.config = Path(root), config
        self.model, self.executor = model, executor
        self.run_id = run_id or uuid.uuid4().hex
        if len(self.run_id) != 32 or any(x not in '0123456789abcdef' for x in self.run_id):
            raise ValueError('invalid run_id')
        self.folder = Path(out) / self.run_id
        self.folder.mkdir(parents=True, exist_ok=True)
        self.recording = recording
        self.origin = 'historical_observation' if recording else observation
        self.on_event = on_event
        self.tape = Tape(manifest(self.root, config))
        self.events = []
        log = self.folder / 'events.jsonl'
        if log.exists():
            for line in log.read_text().splitlines():
                try:
                    self.events.append(json.loads(line))
                except ValueError:
                    break
        self.started = time.monotonic()

    def check_recording(self):
        if self.recording is not None:
            self.tape = Tape(manifest(self.root, self.config), self.recording)

    def initialize(self, mode, scenario, engine, *, parent=None, checkpoint=None, reason=None,
                   initial=None, task=None):
        initial = initial if initial is not None else (self.root / 'starter/bookstore.py').read_text()
        task = task if task is not None else (self.root / 'TASK.md').read_text()
        state = {'run_id': self.run_id, 'thread_id': uuid.uuid4().hex, 'engine': engine,
                 'mode': mode, 'scenario': scenario, 'config': self.config,
                 'task_version': manifest(self.root, self.config)['files'],
                 'initial_code_hash': digest(initial), 'task_hash': digest(task),
                 'candidate': 0, 'code_hash': None, 'code_ref': None, 'response_ref': None,
                 'test_ref': None, 'test_status': None, 'failure_summary': None,
                 'fingerprints': {}, 'remaining_candidates': self.config['max_candidates'],
                 'feedback': None, 'approval_request': None, 'approval_result': None,
                 'status': 'RUNNING', 'next': 'LOAD', 'observation': self.origin,
                 'parent_run_id': parent, 'source_checkpoint': checkpoint, 'branch_reason': reason}
        atomic(self.folder / 'initial.py', initial)
        atomic(self.folder / 'task.md', task)
        self.metadata(state)
        return state

    def metadata(self, state):
        atomic(self.folder / 'metadata.json', {**state, 'max_candidates': self.config['max_candidates'],
                                              'model': self.config['model']})

    def emit(self, state, node, kind, *, key=None, error=None, summary=None, tokens=None, elapsed=0, details=None):
        event_id = key or uuid.uuid4().hex
        if any(e.get('event_id') == event_id for e in self.events):
            return
        event = {'run_id': self.run_id, 'thread_id': state.get('thread_id'), 'engine': state.get('engine'),
                 'seq': len(self.events)+1, 'event_id': event_id, 'node': node, 'event_type': kind,
                 'candidate': state['candidate'], 'time': datetime.now(timezone.utc).isoformat(),
                 'elapsed_ms': round(elapsed*1000, 3), 'status': state['status'],
                 'error_type': error, 'code_hash': state.get('code_hash'), 'test_summary': summary,
                 'tokens': tokens, 'observation': self.origin, 'details': details,
                 'remaining_candidates': state['remaining_candidates']}
        self.events.append(event)
        # 每次以完整有效前缀重写，小型教学日志可承受；同时修复崩溃留下的半行。
        atomic(self.folder / 'events.jsonl', ''.join(json.dumps(e, ensure_ascii=False)+'\n' for e in self.events))
        if self.on_event:
            try:
                self.on_event(event)
            except Exception:
                pass

    def _call(self, kind, request, fn, state, retry=0):
        key = f'{kind}-{state["candidate"]}-{retry}'
        path = self.folder / 'calls' / (key+'.json')
        saved = read_json(path)
        if saved and not self.tape.replay:
            entry = saved['entry']
            if entry['request'] != request:
                raise RecoveryRequired('动作请求与已保存结果不匹配')
            if entry['error']:
                raise RuntimeError(entry['error']['message'])
            return entry['response']
        pending = path.with_suffix('.pending')
        if pending.exists() and not self.tape.replay:
            if read_json(pending)['request_hash'] != digest(request):
                raise RecoveryRequired('未完成调用的请求哈希不匹配')
            self.emit(state, 'GENERATE' if kind == 'model' else 'TEST', 'resumed_call_retry',
                      summary='崩溃前调用结果未落盘，将重试；模型调用不保证 exactly-once 或同样输出。')
        atomic(pending, {'request_hash': digest(request)})
        caught = None
        try:
            result = self.tape.call(kind, request, fn)
        except Exception as exc:
            caught = exc
            result = None
        if self.tape.replay:
            if caught:
                raise caught
            return result
        entry = self.tape.data['calls'][-1]
        atomic(path, {'order': state['candidate']*100 + (retry if kind=='model' else 99), 'entry': entry})
        self.save_recording()
        if caught:
            raise caught
        return result

    def save_recording(self):
        if self.tape.replay:
            data = self.tape.data
        else:
            receipts = [read_json(p) for p in (self.folder/'calls').glob('*.json')]
            data = {'manifest': manifest(self.root, self.config),
                    'calls': [r['entry'] for r in sorted(receipts, key=lambda r:r['order'])],
                    'observation': self.origin}
        atomic(self.folder / 'recording.json', data)

    def step(self, node, incoming):
        state = copy.deepcopy(incoming)
        number = state['candidate'] + (node == 'GENERATE')
        action = f'{node}-{number}'
        directory = self.folder / 'actions' / action
        receipt = read_json(directory / 'result.json')
        if receipt:
            if receipt['input_hash'] != digest(incoming):
                raise RecoveryRequired('节点输入与动作收据不一致：'+action)
            return receipt['state']
        previous = read_json(directory / 'intent.json')
        if previous:
            if previous['input_hash'] != digest(incoming):
                raise RecoveryRequired('未完成节点输入不匹配：'+action)
            attempts = previous.get('recoveries', 0)+1
            if attempts > 3:
                raise RecoveryRequired('同一动作恢复重试超过 3 次，需要人工检查')
            if node == 'TEST' and not (self.folder/'calls'/f'test-{number}-0.json').exists() and self.origin == 'docker':
                if not hasattr(self.executor, 'recover'):
                    raise RecoveryRequired('执行器不能核对遗留执行，请人工恢复')
                info = self.executor.recover(self.folder / f'execution-{number}.json', self.run_id)
                self.emit(state, node, 'interrupted_execution_cleaned', summary=str(info))
            self.emit(state, node, 'node_recovered', summary='使用同一动作 ID 恢复；不额外扣除候选预算。')
        else:
            attempts = 0
        atomic(directory / 'intent.json', {'action_id':action, 'input_hash':digest(incoming), 'recoveries':attempts})
        if node == 'GENERATE':
            state['candidate'] = number
            state['remaining_candidates'] = max(0, self.config['max_candidates']-number)
        self.emit(state, node, 'entered', key=action+':entered')
        tick = time.monotonic()
        if node == 'LOAD':
            state['next'] = 'GENERATE'
            self.emit(state, node, 'loaded', key=action+':loaded')
        elif node == 'GENERATE':
            previous_code = (self.folder/state['code_ref']).read_text() if state['code_ref'] else None
            request = {'task': (self.folder/'task.md').read_text(), 'initial_code': (self.folder/'initial.py').read_text(),
                       'previous_code':previous_code, 'feedback':state['feedback'], 'candidate':number}
            for retry in range(self.config['model_retries']+1):
                try:
                    response = self._call('model',request,self.model,state,retry)
                    state['response_ref'] = f'model-response-{number}.json'
                    atomic(self.folder/state['response_ref'], response)
                    try:
                        value = json.loads(response['raw'])
                    except (TypeError,ValueError):
                        value = {}
                    value = value if isinstance(value,dict) else {}
                    code = value.get('code')
                    proposal = {'path':value.get('path'), 'code':code[:self.config['code_limit']] if isinstance(code,str) else None,
                                'code_hash':digest(code) if isinstance(code,str) else None, 'decision':'proposed',
                                'reason':None, 'truncated':isinstance(code,str) and len(code)>self.config['code_limit']}
                    atomic(self.folder/f'proposal-{number}.json',proposal)
                    self.emit(state | {'code_hash':proposal['code_hash']},node,'model_response',key=action+':response',
                              tokens=response.get('tokens'),elapsed=time.monotonic()-tick)
                    state['next'] = 'VALIDATE'
                    break
                except RecoveryRequired:
                    raise
                except Exception as exc:
                    self.emit(state,node,'model_error',key=action+f':error-{retry}',error=type(exc).__name__,summary=str(exc))
                    if retry == self.config['model_retries']:
                        state['status'] = 'MODEL_ERROR'
        elif node == 'VALIDATE':
            response = read_json(self.folder/state['response_ref'])
            code,error = validate(response['raw'],self.config['code_limit'])
            proposal = read_json(self.folder/f'proposal-{number}.json')
            if error:
                proposal.update(decision='rejected',reason=error['message'])
                state['feedback'] = error
                state['status'] = 'FAILED' if state['remaining_candidates']==0 else 'RUNNING'
                state['next'] = 'GENERATE'
                self.emit(state | {'code_hash':proposal['code_hash']},node,'rejected',key=action+':rejected',
                          error=error['type'],summary=error['message'])
            else:
                proposal.update(decision='accepted',reason='目标路径、代码长度与 Python 语法校验通过')
                state['code_hash'] = digest(code)
                state['code_ref'] = None  # 新候选尚未 APPLY；不能拿上一候选文件核对新哈希。
                state['next'] = 'APPLY'
                self.emit(state,node,'accepted',key=action+':accepted',summary=proposal['reason'])
            atomic(self.folder/f'proposal-{number}.json',proposal)
        elif node == 'APPLY':
            code = read_json(self.folder/state['response_ref'])['raw']
            code = json.loads(code)['code']
            if digest(code) != state['code_hash']:
                raise RecoveryRequired('模型响应产物哈希不匹配')
            state['code_ref'] = f'candidate-{number}.py'
            atomic(self.folder/state['code_ref'],code)
            atomic(self.folder/'candidate.py',code)
            proposal = read_json(self.folder/f'proposal-{number}.json')
            proposal['decision'] = 'applied'
            atomic(self.folder/f'proposal-{number}.json',proposal)
            self.write_diff(code)
            state['next'] = 'TEST'
            self.emit(state,node,'candidate_staged',key=action+':applied')
        elif node == 'TEST':
            code = self.verified_code(state)
            def execute(request):
                if state['engine']=='langgraph' and self.origin=='docker':
                    request = request | {'execution_id':f'bookstore-{self.run_id}-{number}', 'run_id':self.run_id,
                                         'execution_record':str(self.folder/f'execution-{number}.json')}
                return self.executor(request)
            result = self._call('test',{'code':code,'code_hash':state['code_hash']},execute,state)
            state['test_ref'] = f'test-{number}.json'
            state['test_result_hash'] = digest(result)
            atomic(self.folder/state['test_ref'],result)
            atomic(self.folder/f'test-{number}.txt',result.get('output',''))
            self.emit(state,node,'test_result',key=action+':result',error=result['error'],summary=result['summary'],
                      elapsed=time.monotonic()-tick,details={k:v for k,v in result.items() if k!='output'})
            state['test_status'] = 'ERROR' if result['error'] else 'PASSED' if result['passed'] else 'FAILED'
            if result['error']:
                state['status'] = 'EXECUTION_ERROR'
            elif result['passed']:
                state['status'] = 'TEST_PASSED'
            else:
                state['failure_summary'] = result['summary']
                state['feedback'] = {'type':'TEST_FAILURE','summary':result['summary']}
                state['status'],state['fingerprints'] = failed_status(number,self.config,state['fingerprints'],state['code_hash'],result['summary'])
                state['next'] = 'GENERATE'
        atomic(directory/'result.json',{'input_hash':digest(incoming),'state':state})
        return state

    def verified_code(self,state):
        path = (self.folder/state['code_ref']).resolve()
        if not path.is_relative_to(self.folder.resolve()) or digest(path.read_text()) != state['code_hash']:
            raise RecoveryRequired('候选产物被改变或路径越界，不能执行/审批/交付')
        return path.read_text()

    def write_diff(self,code):
        initial = (self.folder/'initial.py').read_text()
        atomic(self.folder/'changes.diff',''.join(difflib.unified_diff(initial.splitlines(True),code.splitlines(True),
               fromfile='initial/bookstore.py',tofile='candidate/bookstore.py')))

    def report(self,state,status=None,reason=None):
        summary = {key:state.get(key) for key in ('run_id','thread_id','engine','mode','scenario','observation',
                   'parent_run_id','source_checkpoint','branch_reason','code_hash','approval_request','approval_result','test_status')}
        summary.update(status=status or state['status'], candidates=state['candidate'],
                       remaining_candidates=state['remaining_candidates'],max_candidates=self.config['max_candidates'],
                       source_observation=self.recording.get('observation') if self.recording else None,
                       directory=str(self.folder.resolve()), recovery_reason=reason)
        atomic(self.folder/'summary.json',summary)
        return summary

    def finish(self,state):
        self.write_diff((self.folder/state['code_ref']).read_text() if state.get('code_ref') else (self.folder/'initial.py').read_text())
        self.save_recording()
        summary = self.report(state)
        self.emit(state,state['status'],'finished',key='terminal:'+state['status'],elapsed=time.monotonic()-self.started)
        return summary
