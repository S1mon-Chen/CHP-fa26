"""SQLite + shared-node integration. Tool results here are explicitly simulated."""
import json
import shutil
from pathlib import Path

import pytest
from harness.service import RunService, ServiceError
from harness.runtime import Runtime, RecoveryRequired
from harness.graph_engine import graph_session
from harness.runner import adapters

ROOT=Path(__file__).resolve().parent.parent

@pytest.fixture
def project(tmp_path):
    for directory in ('starter','tests_business','scenarios','web'):
        shutil.copytree(ROOT/directory,tmp_path/directory)
    for name in ('TASK.md','config.json'):
        shutil.copy(ROOT/name,tmp_path/name)
    return tmp_path


def config(root):
    return json.loads((root/'config.json').read_text()) | {'engine':'langgraph'}


def start(root,scenario='success',**overrides):
    return RunService(root).start(config(root)|overrides,'scripted',scenario,True)


def answer(summary,approved=True):
    return summary['approval_request'] | {'approved':approved,'note':'教师检查 <script>text</script>'}


def events(root,run_id):
    return [json.loads(line) for line in (root/'runs'/run_id/'events.jsonl').read_text().splitlines()]


@pytest.mark.parametrize('scenario,expected', [('success','WAITING_APPROVAL'),('repeated','STALLED'),('forbidden','FAILED'),('budget','FAILED')])
def test_python_graph_business_parity(project,scenario,expected):
    service=RunService(project)
    py=service.start(config(project)|{'engine':'python'},'scripted',scenario,True)
    graph=start(project,scenario)
    assert graph['status']==expected
    assert py['status']==('SUCCESS' if expected=='WAITING_APPROVAL' else expected)
    assert (py['candidates'],py['remaining_candidates'],py['test_status']) == (graph['candidates'],graph['remaining_candidates'],graph['test_status'])
    assert not (project/'runs'/graph['run_id']/'delivery').exists()


def test_approval_binding_and_idempotent_projection(project):
    summary=start(project); rid=summary['run_id']; service=RunService(project)
    assert service.active() is None
    with pytest.raises(ServiceError,match='不匹配'):
        service.approve(rid,answer(summary)|{'code_hash':'0'*64})
    done=service.approve(rid,answer(summary))
    assert done['status']=='SUCCESS'
    service.inspect(rid); service.inspect(rid)
    with pytest.raises(ServiceError): service.approve(rid,answer(summary))
    assert len([e for e in events(project,rid) if e['event_type']=='delivered'])==1
    assert len([e for e in events(project,rid) if e['event_type']=='finished'])==1
    assert (project/'runs'/rid/'delivery/bookstore.py').exists()


def test_rejection_and_thread_isolation(project):
    a=start(project); b=start(project)
    assert a['thread_id']!=b['thread_id']
    service=RunService(project)
    assert service.approve(a['run_id'],answer(a,False))['status']=='REJECTED'
    assert service.inspect(b['run_id'])['summary']['status']=='WAITING_APPROVAL'
    with pytest.raises(ServiceError): service.approve(a['run_id'],answer(a))
    assert not (project/'runs'/a['run_id']/'delivery').exists()


def test_fork_copies_safe_state_to_new_thread(project):
    a=start(project);service=RunService(project);rid=a['run_id']
    before=(project/'runs'/rid/'events.jsonl').read_bytes()
    checkpoints=service.checkpoints(rid)
    safe=next(c for c in checkpoints if c['safe_to_fork'])
    with pytest.raises(ServiceError):
        service.fork(rid,checkpoints[0]['checkpoint_id'],'unsafe')
    b=service.fork(rid,safe['checkpoint_id'],'独立实验',budget=3)
    assert b['parent_run_id']==rid and b['thread_id']!=a['thread_id']
    assert b['source_checkpoint']==safe['checkpoint_id']
    assert b['approval_request']['approval_id']!=a['approval_request']['approval_id']
    assert b['status']=='WAITING_APPROVAL' and b['approval_result'] is None
    assert (project/'runs'/rid/'events.jsonl').read_bytes()==before
    assert len([e for e in events(project,b['run_id']) if e['event_type']=='test_result'])==2


def test_action_receipt_no_double_budget_or_model_call(project):
    c=config(project);model,execute,origin=adapters(project,c,'scripted',simulate=True)
    rt=Runtime(project,c,model,execute,project/'runs',observation=origin)
    state=rt.initialize('scripted','success','langgraph');state=rt.step('LOAD',state)
    after=rt.step('GENERATE',state)
    rt.model=lambda _: pytest.fail('must reuse persisted result')
    assert rt.step('GENERATE',state)==after
    assert after['candidate']==1 and after['remaining_candidates']==2


def test_artifact_tamper_requires_manual_recovery(project):
    a=start(project);service=RunService(project);rid=a['run_id']
    (project/'runs'/rid/'candidate-2.py').write_text('changed')
    assert service.inspect(rid)['summary']['status']=='RECOVERY_REQUIRED'
    with pytest.raises(ServiceError):service.approve(rid,answer(a))


def test_graph_recursion_limit_is_reported(project):
    result=start(project,recursion_limit=2)
    assert result['status']=='EXECUTION_ERROR'
    assert result['candidates']==1  # report uses latest checkpoint, not initial state
    assert any(e['error_type']=='GRAPH_RECURSION_LIMIT' for e in events(project,result['run_id']))


def test_cli_and_web_share_lock(project):
    service=RunService(project)
    with service.slot('a'*32):
        assert service.active()=='a'*32
        with pytest.raises(ServiceError,match='活动执行'): start(project)


def test_replay_no_adapters_called(project,monkeypatch):
    a=start(project); service=RunService(project)
    recording=json.loads((project/'runs'/a['run_id']/'recording.json').read_text())
    def forbidden(*args,**kwargs):pytest.fail('replay must not execute adapters')
    monkeypatch.setattr('harness.ollama.Ollama.__call__',forbidden)
    monkeypatch.setattr('harness.executor.DockerExecutor.__call__',forbidden)
    replay=service.start(config(project),'replay',recording=recording)
    assert replay['status']=='SUCCESS' and replay['observation']=='historical_observation'
    assert not (project/'runs'/replay['run_id']/'delivery').exists()


def test_delivery_crash_window_recovers_once(project,monkeypatch):
    import harness.graph_engine as engine
    summary=start(project);service=RunService(project);rid=summary['run_id']
    real=engine.atomic
    def crash_after_write(path,value):
        real(path,value)
        if path.name=='manifest.json':
            raise RecoveryRequired('injected delivery interruption')
    monkeypatch.setattr(engine,'atomic',crash_after_write)
    result=service.approve(rid,answer(summary))
    assert result['status']=='RECOVERY_REQUIRED'
    assert service.inspect(rid)['summary']['status']=='RECOVERY_REQUIRED'
    assert not any(e['event_type']=='delivered' for e in events(project,rid))
    monkeypatch.setattr(engine,'atomic',real)
    assert service.resume(rid)['status']=='SUCCESS'
    assert len([e for e in events(project,rid) if e['event_type']=='delivered'])==1


def test_validated_next_candidate_has_no_stale_code_reference(project):
    c=config(project);model,executor,origin=adapters(project,c,'scripted',simulate=True)
    rt=Runtime(project,c,model,executor,project/'runs',observation=origin)
    state=rt.initialize('scripted','success','langgraph')
    for node in ('LOAD','GENERATE','VALIDATE','APPLY','TEST','GENERATE','VALIDATE'):
        state=rt.step(node,state)
    assert state['candidate']==2 and state['next']=='APPLY' and state['code_ref'] is None
    RunService(project).check_version(rt,state)


def test_approval_uses_checkpoint_even_if_report_missing(project):
    summary=start(project);service=RunService(project);rid=summary['run_id']
    # Simulate crash after interrupt checkpoint but before the report projection.
    (project/'runs'/rid/'summary.json').unlink()
    assert service.approve(rid,answer(summary))['status']=='SUCCESS'
