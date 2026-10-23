"""仅编排层：共享节点 + SQLite checkpoint + 动态人工中断。"""
import copy
import uuid
from contextlib import contextmanager
from typing import TypedDict

from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import StateGraph, START, END
from langgraph.types import interrupt

from .core import digest
from .runtime import TERMINAL, RecoveryRequired, atomic, read_json


class HarnessState(TypedDict, total=False):
    run_id: str
    thread_id: str
    engine: str
    mode: str
    scenario: str
    config: dict
    task_version: dict
    initial_code_hash: str
    task_hash: str
    candidate: int
    code_hash: str | None
    code_ref: str | None
    response_ref: str | None
    test_ref: str | None
    test_result_hash: str | None
    test_status: str | None
    failure_summary: str | None
    fingerprints: dict
    remaining_candidates: int
    feedback: dict | None
    approval_request: dict | None
    approval_result: dict | None
    status: str
    next: str
    observation: str
    parent_run_id: str | None
    source_checkpoint: str | None
    branch_reason: str | None


def validate_approval(runtime, state, answer):
    request = state.get('approval_request')
    if not request or any(answer.get(k) != request[k] for k in ('run_id','approval_id','code_hash')):
        raise RecoveryRequired('审批请求过期，或 run_id / 审批 ID / 候选哈希不匹配')
    if type(answer.get('approved')) is not bool:
        raise RecoveryRequired('审批结果必须为布尔值')
    runtime.verified_code(state)
    result = read_json(runtime.folder/state['test_ref'])
    if not result.get('passed') or result.get('error') or digest(result) != state['test_result_hash']:
        raise RecoveryRequired('测试产物被改变或未通过，不能审批交付')


def build(runtime, checkpointer):
    builder = StateGraph(HarnessState)

    def common(node, state):
        try:
            result = runtime.step(node, state)
        except RecoveryRequired:
            raise
        except Exception as exc:
            result = dict(state, status='EXECUTION_ERROR')
            runtime.emit(result,node,'controller_error',error=type(exc).__name__,summary=str(exc))
        if result['status'] == 'TEST_PASSED':
            result['status'] = 'WAITING_APPROVAL'
            result['next'] = 'APPROVAL'
            result['approval_request'] = {'run_id':result['run_id'], 'code_hash':result['code_hash'],
                'approval_id':uuid.uuid5(uuid.NAMESPACE_URL,result['run_id']+result['code_hash']).hex,
                'candidate':result['candidate'], 'test_ref':result['test_ref']}
        return result

    for name in ('LOAD','GENERATE','VALIDATE','APPLY','TEST'):
        def node(state: HarnessState, name=name):
            return common(name,state)
        builder.add_node(name,node)
        builder.add_conditional_edges(name,lambda state: state['status'] if state['status'] in TERMINAL else state['next'])

    def approval(state: HarnessState):
        # interrupt 恢复会从函数开头重新执行。这里只有可去重的审计事件，无交付副作用。
        runtime.emit(state,'APPROVAL','approval_requested',key='approval-request:'+state['approval_request']['approval_id'],
                     details=state['approval_request'])
        answer = interrupt(state['approval_request'])
        validate_approval(runtime,state,answer)
        result = dict(state,approval_result=answer,status='RUNNING',next='DELIVER' if answer['approved'] else 'REJECTED')
        runtime.emit(result,'APPROVAL','approval_decided',key='approval-decision:'+answer['approval_id'],
                     details=answer,summary=answer.get('note'))
        return result

    def deliver(state: HarnessState):
        validate_approval(runtime,state,state['approval_result'])
        if state['approval_result']['approved'] is not True:
            raise RecoveryRequired('没有批准结果，禁止交付')
        directory = runtime.folder/'delivery'
        content = {'run_id':state['run_id'],'thread_id':state['thread_id'],'code_hash':state['code_hash'],
                   'approval_id':state['approval_request']['approval_id'],'observation':state['observation']}
        previous = read_json(directory/'manifest.json')
        if previous is not None and previous != content:
            raise RecoveryRequired('已有交付产物与当前批准不一致')
        code = runtime.verified_code(state)
        existing = directory/'bookstore.py'
        if existing.exists() and digest(existing.read_text()) != state['code_hash']:
            raise RecoveryRequired('交付文件已被改变，需要人工检查')
        runtime.write_diff(code)
        atomic(existing,code)
        atomic(directory/'changes.diff',(runtime.folder/'changes.diff').read_text())
        atomic(directory/'manifest.json',content)
        runtime.emit(state,'DELIVER','delivered',key='delivery:'+state['run_id'],details=content)
        return dict(state,next='SUCCESS')

    builder.add_node('APPROVAL',approval)
    builder.add_conditional_edges('APPROVAL',lambda state:state['next'])
    builder.add_node('DELIVER',deliver)
    builder.add_edge('DELIVER','SUCCESS')
    for name in sorted(TERMINAL):
        def terminal(state: HarnessState, name=name):
            state = dict(state,status=name,next='END')
            runtime.finish(state)
            return state
        builder.add_node(name,terminal)
        builder.add_edge(name,END)
    builder.add_edge(START,'LOAD')
    return builder.compile(checkpointer=checkpointer)


@contextmanager
def graph_session(database, runtime):
    database.parent.mkdir(parents=True,exist_ok=True)
    with SqliteSaver.from_conn_string(str(database)) as saver:
        saver.conn.execute('PRAGMA busy_timeout=10000')
        saver.conn.execute('PRAGMA journal_mode=WAL')
        saver.setup()
        yield build(runtime,saver)
