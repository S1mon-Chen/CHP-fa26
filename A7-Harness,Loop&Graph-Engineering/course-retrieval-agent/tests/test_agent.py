import json
from pathlib import Path
import time
import pytest
from app.agent import Harness
from app.agent_types import AgentConfig
from app.corpus import load_corpus
from app.tools import ToolService
from app.scripted import ScriptedModel,SCENARIOS,tool
from app.ollama import TransportError,clean_response
from app.recording import replay,ReplayError

ROOT=Path(__file__).resolve().parent.parent


@pytest.fixture
def service(tmp_path):
    corpus=tmp_path/'corpus';corpus.mkdir()
    # 独立小型资料，标题/术语与 scenario 的查找条件一致。
    for name,title,body in [('hdfs','HDFS 文件块与副本','HDFS 副本保存相同块的拷贝。'),
                            ('spark','Spark Structured Streaming 的事件时间','Spark 水印与状态清理有关。'),
                            ('flink','Flink 事件时间与水印','Flink watermark 表示事件时间进度。')]:
        (corpus/(name+'.md')).write_text(f'# {title}\n主题：基础\n编写日期：2026-10-12\n适用版本：示例\n\n## 基础概念\n\n{body}\n')
    return ToolService(load_corpus(corpus))


def run(tmp_path,service,scenario='single',config=None,model=None,mode='scripted'):
    return Harness(config or AgentConfig(),lambda:service,model or ScriptedModel(scenario),tmp_path/'runs',mode,scenario).run('教学问题')


def events(result,kind):return [e for e in result['events'] if e['type']==kind]


@pytest.mark.parametrize('scenario,status,answer',[
 ('single','COMPLETED','answered'),('compare','COMPLETED','answered'),
 ('insufficient','COMPLETED','insufficient_evidence'),('unknown_tool','COMPLETED','insufficient_evidence'),
 ('unauthorized','COMPLETED','insufficient_evidence'),('stalled','STALLED',None),
 ('budget','BUDGET_EXHAUSTED',None),('bad_citation','COMPLETED','answered')])
def test_scenarios(tmp_path,service,scenario,status,answer):
    r=run(tmp_path,service,scenario)
    assert r['summary']['execution_status']==status
    assert r['summary']['answer_status']==answer
    assert len(events(r,'action_proposed'))==r['summary']['tool_attempts']
    assert len(events(r,'tool_observation'))==len(events(r,'action_proposed'))
    replayed=replay(tmp_path/'runs',r['summary']['run_id'])
    assert replayed['summary']==r['summary']
    if scenario=='compare':assert len(r['evidence'])==2 and r['summary']['tool_attempts']==4
    if scenario=='bad_citation':assert r['summary']['answer_repairs']==1
    if scenario in ('unknown_tool','unauthorized'):
        assert not events(r,'tool_started') and r['summary']['tool_attempts']==1


def test_injection(tmp_path):
    svc=ToolService(load_corpus(ROOT/'tests/fixtures/injection'))
    r=run(tmp_path,svc,'injection')
    assert any(e['error_code']=='INVALID_ARGUMENTS' for e in r['events'])
    assert r['summary']['tool_attempts']==3
    assert len(events(r,'tool_started'))==2
    assert any('FICTIONAL_SECRET' in e['text'] for e in r['evidence'].values())


class SequenceModel:
    def __init__(self,responses):self.responses=iter(responses);self.payloads=[]
    def request(self,payload,timeout):
        self.payloads.append(payload)
        value=next(self.responses)
        if isinstance(value,Exception):raise value
        return {'message':value,'done':True}


def action(*calls):return {'role':'assistant','content':'','tool_calls':list(calls)}
def final(ids=('E1',)):
    return {'role':'assistant','content':json.dumps({'answer_status':'answered','claims':[{'text':'有证据的结论','evidence_ids':list(ids)}],'missing_information':[]})}


def test_pairing_and_repeated_read(tmp_path,service):
    chunk=next(iter(service.snapshot.chunks.values()))
    read=tool('read_document',{'document_id':chunk.document_id,'chunk_ids':[chunk.chunk_id]})
    read['id']='original-id'
    model=SequenceModel([action(read),action(read),action(),final()])
    r=run(tmp_path,service,model=model)
    assert r['summary']['execution_status']=='COMPLETED' and len(r['evidence'])==1
    assert [e['new_evidence'] for e in events(r,'tool_observation')]==[1,0]
    result_message=model.payloads[1]['messages'][2]
    assert result_message['role']=='assistant'
    tool_messages=[m for m in model.payloads[-1]['messages'] if m['role']=='tool']
    assert len(tool_messages)==2
    assert all(m['tool_call_id']=='original-id' and m['tool_name']=='read_document' for m in tool_messages)


def test_repeated_read_stalls_after_two_no_progress(tmp_path,service):
    c=next(iter(service.snapshot.chunks.values()))
    call=tool('read_document',{'document_id':c.document_id,'chunk_ids':[c.chunk_id]})
    r=run(tmp_path,service,model=SequenceModel([action(call)]*3))
    assert r['summary']['execution_status']=='STALLED'
    assert r['summary']['tool_attempts']==3 and len(r['evidence'])==1


def test_multi_budget_all_observed(tmp_path,service):
    calls=[tool('search_documents',{'query':str(i)}) for i in range(4)]
    r=run(tmp_path,service,config=AgentConfig(max_tool_attempts=2,max_calls_per_round=2),model=SequenceModel([action(*calls)]))
    assert r['summary']['execution_status']=='BUDGET_EXHAUSTED'
    assert r['summary']['tool_attempts']==2 and r['summary']['tool_proposals']==4
    assert len(events(r,'tool_started'))==2 and len(events(r,'tool_observation'))==4


def test_round_limit_refuses_and_counts(tmp_path,service):
    calls=[tool('bad'+str(i),{}) for i in range(3)]
    model=SequenceModel([action(*calls),action(),{'content':json.dumps({'answer_status':'insufficient_evidence','claims':[],'missing_information':['无资料']})}])
    r=run(tmp_path,service,config=AgentConfig(max_calls_per_round=1),model=model)
    assert r['summary']['tool_attempts']==3
    assert len(events(r,'tool_observation'))==3
    assert any(e['error_code']=='ROUND_CALL_LIMIT' for e in r['events'])


def test_search_not_evidence_and_bounded_recovery(tmp_path,service):
    model=SequenceModel([action(tool('search_documents',{'query':'HDFS'})),action(),final(),
                         action(),final(),final()])
    r=run(tmp_path,service,model=model)
    assert r['summary']['execution_status']=='ANSWER_VALIDATION_FAILED'
    assert r['evidence']=={}
    assert r['summary']['answer_repairs']==1 and r['summary']['evidence_recoveries']==1


def test_invalid_final_only_one_repair(tmp_path,service):
    c=next(iter(service.snapshot.chunks.values()))
    model=SequenceModel([action(tool('read_document',{'document_id':c.document_id,'chunk_ids':[c.chunk_id]})),action(),final(('E99',)),final(('E99',))])
    r=run(tmp_path,service,model=model)
    assert r['summary']['execution_status']=='ANSWER_VALIDATION_FAILED'
    assert r['summary']['answer_repairs']==1


def test_transport_retries_finite(tmp_path,service):
    model=SequenceModel([TransportError('x')]*5)
    r=run(tmp_path,service,model=model)
    assert r['summary']['execution_status']=='MODEL_ERROR'
    assert r['summary']['model_requests']==2 and r['summary']['transport_retries']==1
    assert r['summary']['decision_rounds']==1


def test_deadline_covers_retry(tmp_path,service):
    class Slow:
        def request(self,payload,timeout):time.sleep(.035);raise TransportError('x')
    r=run(tmp_path,service,config=AgentConfig(deadline_seconds=.06,model_timeout=.05,transport_retries=3),model=Slow())
    assert r['summary']['execution_status']=='DEADLINE_EXCEEDED'
    assert r['summary']['elapsed_seconds']<.3
    assert r['summary']['transport_retries']>=1


@pytest.mark.parametrize('on_repair',[False,True])
def test_deadline_covers_final_and_repair(tmp_path,service,on_repair):
    class SlowFinal(ScriptedModel):
        def request(self,payload,timeout):
            if 'format' in payload and (not on_repair or self.finals==1):time.sleep(.2)
            return super().request(payload,timeout)
    r=run(tmp_path,service,config=AgentConfig(deadline_seconds=.08),model=SlowFinal('bad_citation'))
    assert r['summary']['execution_status']=='DEADLINE_EXCEEDED'
    assert r['summary']['elapsed_seconds']<.3
    if on_repair:assert r['summary']['answer_repairs']==1


def test_tool_timeout(tmp_path,service,monkeypatch):
    def slow(*_):time.sleep(.1)
    monkeypatch.setattr(service,'invoke_tool',slow)
    r=run(tmp_path,service,config=AgentConfig(tool_timeout=.01))
    assert r['summary']['execution_status']=='TOOL_ERROR'
    assert r['summary']['error_code']=='TOOL_TIMEOUT'


def test_replay_no_model_or_tools(tmp_path,service,monkeypatch):
    r=run(tmp_path,service)
    def forbidden(*_):raise AssertionError('replay 不可执行')
    monkeypatch.setattr(ToolService,'invoke_tool',forbidden)
    monkeypatch.setattr(ScriptedModel,'request',forbidden)
    assert replay(tmp_path/'runs',r['summary']['run_id'])['answer']==r['answer']


@pytest.mark.parametrize('damage',['missing','corrupt','version','incomplete'])
def test_replay_rejects_damage(tmp_path,service,damage):
    r=run(tmp_path,service);folder=tmp_path/'runs'/r['summary']['run_id']
    if damage=='missing':(folder/'evidence.json').unlink()
    elif damage=='corrupt':(folder/'events.jsonl').write_text('{}\n')
    elif damage=='incomplete':(folder/'manifest.json').unlink()
    else:
        m=json.loads((folder/'manifest.json').read_text());m['log_version']=99
        (folder/'manifest.json').write_text(json.dumps(m))
    with pytest.raises(ReplayError):replay(tmp_path/'runs',r['summary']['run_id'])


def test_no_thinking_persisted():
    value=clean_response({'message':{'content':'<think>secret thought</think>完成','thinking':'secret thought','reasoning':'secret'},'eval_count':3})
    assert 'secret' not in json.dumps(value)
    assert value['prompt_eval_count'] is None and value['eval_count']==3


def test_config_bounds():
    for kwargs in ({'max_decisions':0},{'answer_repairs':2},{'url':'https://remote.example'}, {'max_tool_attempts':True}):
        with pytest.raises(ValueError):AgentConfig(**kwargs)


def test_unauthorized_registered_document(tmp_path,service,monkeypatch):
    hidden=next(iter(service.snapshot.documents))
    limited=ToolService(service.snapshot,sorted(set(service.allowed)-{hidden}))
    model=ScriptedModel('unauthorized');model.unauthorized_document_id=hidden
    def forbidden(*_):raise AssertionError('不能执行越权调用')
    monkeypatch.setattr(limited,'invoke_tool',forbidden)
    r=run(tmp_path,limited,model=model)
    assert any(e['error_code']=='DOCUMENT_NOT_ALLOWED' for e in r['events'])
    assert not events(r,'tool_started')


def test_actual_read_then_recovery_succeeds(tmp_path,service):
    c=next(iter(service.snapshot.chunks.values()))
    model=SequenceModel([action(),final(),action(tool('read_document',{'document_id':c.document_id,'chunk_ids':[c.chunk_id]})),action(),final()])
    r=run(tmp_path,service,model=model)
    assert r['summary']['execution_status']=='COMPLETED'
    assert r['summary']['evidence_recoveries']==1


def test_strict_final_states():
    from app.agent_types import Answer
    for payload in [
        {'answer_status':'partial','claims':[{'text':'x','evidence_ids':['E1']}],'missing_information':[]},
        {'answer_status':'insufficient_evidence','claims':[{'text':'x','evidence_ids':['E1']}],'missing_information':['x']},
        {'answer_status':'answered','claims':[{'text':'x','evidence_ids':[]}],'missing_information':[]},
        {'answer_status':'insufficient_evidence','claims':[],'missing_information':['x'],'extra':1},
    ]:
        with pytest.raises(ValueError):Answer.model_validate(payload)


def test_request_timeout_then_retry_within_deadline(tmp_path,service):
    class TimesOut:
        def request(self,payload,timeout):time.sleep(.2)
    r=run(tmp_path,service,config=AgentConfig(model_timeout=.05,deadline_seconds=1.0),model=TimesOut())
    assert r['summary']['execution_status']=='MODEL_ERROR'
    assert r['summary']['model_requests']==2 and r['summary']['transport_retries']==1
    assert r['summary']['elapsed_seconds']<.3


def test_foreign_snapshot_not_evidence(tmp_path,service,monkeypatch):
    original=service.invoke_tool
    def corrupt(name,args):
        result=original(name,args)
        for c in result.get('chunks',[]):c['corpus_snapshot_id']='wrong'
        return result
    monkeypatch.setattr(service,'invoke_tool',corrupt)
    r=run(tmp_path,service)
    assert r['summary']['execution_status']=='TOOL_ERROR' and not r['evidence']


def test_live_unavailable_never_scripted(tmp_path,service):
    class Offline:
        def preflight(self,timeout):raise TransportError('OLLAMA_CONNECTION_ERROR')
        def request(self,*_):raise AssertionError('不应请求模型')
    r=run(tmp_path,service,model=Offline(),mode='live')
    assert r['summary']['execution_status']=='MODEL_ERROR'
    assert r['summary']['mode']=='live' and r['summary']['model_requests']==0


def test_missing_native_tool_capability(tmp_path,service):
    class Unsupported:
        def preflight(self,timeout):return {'model_installed':True}
        def request(self,*_):return {'message':{'content':'not a tool call'},'done':True}
    r=run(tmp_path,service,model=Unsupported(),mode='live')
    assert r['summary']['error_code']=='NATIVE_TOOL_PROBE_FAILED'
    assert r['summary']['tool_attempts']==0
