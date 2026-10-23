import json
import shutil
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from harness.core import run
from harness.executor import pytest_counts
from harness.web import create_app

ROOT = Path(__file__).resolve().parent.parent
READY = {'docker': {'available': True, 'message': 'ready'}, 'ollama': {'available': True, 'message': 'ready'}}


@pytest.fixture
def web(tmp_path, monkeypatch):
    for folder in ('starter', 'tests_business', 'scenarios', 'web'):
        shutil.copytree(ROOT / folder, tmp_path / folder)
    for name in ('config.json', 'TASK.md'):
        shutil.copy(ROOT / name, tmp_path / name)
    monkeypatch.setattr('harness.web.dependency_status', lambda *args: READY)
    app = create_app(tmp_path)
    with TestClient(app, base_url='http://127.0.0.1') as client:
        client.headers['X-Classroom-Token'] = client.get('/api/bootstrap').json()['token']
        yield client, app.state.classroom


def payload(**kwargs):
    return {'mode': 'scripted', 'simulate': True, 'request_id': uuid.uuid4().hex} | kwargs


def finished(client, run_id):
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        detail = client.get('/api/runs/' + run_id).json()
        if not detail['active']:
            return detail
        time.sleep(.01)
    pytest.fail('run did not finish')


def sse_events(text):
    return [json.loads(block.split('data: ', 1)[1]) for block in text.split('\n\n')
            if 'event: harness\n' in block]


@pytest.mark.parametrize('extra', [
    {'mode':'shell'}, {'scenario':'../tests'}, {'mode':'live','simulate':True},
    {'max_candidates':0}, {'max_candidates':6}, {'max_candidates':True},
    {'max_candidates':'3'}, {'command':'ls'}, {'url':'http://evil'},
    {'mode':'replay','source':'../config.json','simulate':False},
    {'mode':'live','model':'bad\nname','simulate':False},
])
def test_invalid_start(web, extra):
    client, manager = web
    assert client.post('/api/runs', json=payload(**extra)).status_code == 422
    assert manager.active_id is None
    assert list(manager.store.base.iterdir()) == []


def test_concurrent_and_duplicate_start(web, monkeypatch):
    client, manager = web
    gate = threading.Event()
    from harness.runner import execute
    def blocked(*args, **kwargs):
        assert gate.wait(3)
        return execute(*args, **kwargs)
    monkeypatch.setattr('harness.web.execute', blocked)
    request = payload()
    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(lambda _: client.post('/api/runs', json=request), range(2)))
        assert {r.status_code for r in responses} == {202}
        ids = {r.json()['run_id'] for r in responses}
        assert len(ids) == 1
        assert client.post('/api/runs', json=payload()).status_code == 409
        assert client.post('/api/runs', json=request | {'max_candidates':1}).status_code == 409
        assert len(list(manager.store.base.glob('*/launch.json'))) == 1
        # 新页面可从服务端 active_run_id 恢复，不依赖旧浏览器内存。
        assert client.get('/api/bootstrap').json()['active_run_id'] in ids
    finally:
        gate.set()
    result = finished(client, next(iter(ids)))
    assert result['summary']['status'] == 'SUCCESS'


def test_events_reconnect_summary_and_artifacts(web):
    client, manager = web
    run_id = client.post('/api/runs', json=payload()).json()['run_id']
    detail = finished(client, run_id)
    folder = manager.store.folder(run_id)
    persisted = [json.loads(line) for line in (folder / 'events.jsonl').read_text().splitlines()]
    stream = client.get(f'/api/runs/{run_id}/events').text
    assert sse_events(stream) == persisted == detail['events']
    assert [e['seq'] for e in persisted] == list(range(1, len(persisted)+1))
    replay = client.get(f'/api/runs/{run_id}/events?after=2', headers={'Last-Event-ID':'5'}).text
    assert sse_events(replay) == persisted[5:]
    assert sse_events(client.get(f'/api/runs/{run_id}/events?after={len(persisted)}').text) == []
    assert client.get(f'/api/runs/{run_id}/events', headers={'Last-Event-ID':'no'}).status_code == 400
    listed = next(row for row in client.get('/api/runs').json() if row['run_id'] == run_id)
    assert listed['summary'] == json.loads((folder / 'summary.json').read_text())
    artifact = client.get(f'/api/runs/{run_id}/artifacts').json()
    assert artifact['proposals']['2']['decision'] == 'applied'
    assert '>=' in artifact['proposals']['2']['diff']
    assert artifact['tests']['1']['passed'] is False
    assert artifact['tests']['2']['passed'] is True
    assert artifact['tests']['2'].get('counts') is None  # 模拟观察无真实计数。


def test_replay_reads_only(web, monkeypatch):
    client, manager = web
    rid = client.post('/api/runs', json=payload()).json()['run_id']
    finished(client, rid)
    def forbidden(*args, **kwargs):
        pytest.fail('replay called model/tool/preflight')
    monkeypatch.setattr('harness.web.execute', forbidden)
    monkeypatch.setattr('harness.web.dependency_status', forbidden)
    count = len(list(manager.store.base.iterdir()))
    response = client.post('/api/runs', json=payload(mode='replay', source=rid, simulate=False))
    assert response.json() == {'run_id': rid, 'replay': True}
    assert client.get(f'/api/runs/{rid}/artifacts').status_code == 200
    assert sse_events(client.get(f'/api/runs/{rid}/events').text)
    assert len(list(manager.store.base.iterdir())) == count


def test_dependency_failure_no_run(web, monkeypatch):
    client, manager = web
    monkeypatch.setattr('harness.web.dependency_status', lambda *args: {
        'docker': {'available':False, 'message':'Docker 未运行'},
        'ollama': {'available':False, 'message':'模型不存在'}})
    response = client.post('/api/runs', json=payload(mode='live', simulate=False))
    assert response.status_code == 409
    assert 'Docker 未运行' in response.json()['detail']
    assert not list(manager.store.base.iterdir())


def test_paths_and_cross_site(web, tmp_path):
    client, manager = web
    assert client.get('/api/runs/not-a-run').status_code == 400
    assert client.get('/api/runs/'+'a'*32).status_code == 404
    outside = tmp_path / 'outside'
    outside.mkdir()
    (manager.store.base / ('f'*32)).symlink_to(outside, target_is_directory=True)
    assert client.get('/api/runs/'+'f'*32).status_code == 400
    assert client.get('/assets/config.json').status_code == 404
    assert client.get('/api/bootstrap', headers={'Host':'attacker.example'}).status_code == 403
    assert client.post('/api/runs', json=payload(), headers={'X-Classroom-Token':'wrong'}).status_code == 403


def test_html_is_data_and_frontend_uses_text(web):
    client, manager = web
    rid = client.post('/api/runs', json=payload()).json()['run_id']
    finished(client, rid)
    path = manager.store.folder(rid) / 'proposal-1.json'
    value = json.loads(path.read_text())
    value['code'] = '<img src=x onerror=alert(1)>'
    path.write_text(json.dumps(value))
    response = client.get(f'/api/runs/{rid}/artifacts')
    assert response.headers['content-type'] == 'application/json'
    assert response.json()['proposals']['1']['code'] == value['code']
    source = client.get('/assets/app.js').text
    assert 'innerHTML' not in source and 'insertAdjacentHTML' not in source
    assert "$('code').textContent=" in source and 'raw.textContent=JSON.stringify(event' in source


def test_callback_is_durable_and_observer_cannot_change_execution(tmp_path):
    config = json.loads((ROOT / 'config.json').read_text())
    scene = json.loads((ROOT / 'scenarios/success.json').read_text())
    seen = []
    def callback(event):
        logs = [json.loads(line) for line in (tmp_path / event['run_id'] / 'events.jsonl').read_text().splitlines()]
        assert logs[-1] == event
        seen.append(event)
        raise RuntimeError('broken observer')
    result = run(ROOT, config, lambda _: {'raw':json.dumps(scene['responses'][1])},
                 lambda _: scene['results'][1], tmp_path, on_event=callback, observation='simulated')
    assert result['status'] == 'SUCCESS'
    assert seen[-1]['event_type'] == 'finished'
    assert [e['node'] for e in seen if e['event_type']=='entered'] == ['LOAD','GENERATE','VALIDATE','APPLY','TEST']


def test_counts_only_when_summary_exists():
    assert pytest_counts('3 failed, 26 passed in 0.03s') == {'passed':26,'failed':3,'errors':0,'skipped':0}
    assert pytest_counts('29 passed in 0.01s') == {'passed':29,'failed':0,'errors':0,'skipped':0}
    assert pytest_counts('partial output') is None


def test_real_dependency_probe_replay_is_noop(monkeypatch):
    from harness.web import dependency_status
    def forbidden(*args, **kwargs):
        pytest.fail('unneeded probe')
    monkeypatch.setattr('harness.web.subprocess.run', forbidden)
    monkeypatch.setattr('harness.web.urllib.request.urlopen', forbidden)
    assert dependency_status(ROOT, 'replay')['docker']['available'] is None


def test_duplicate_request_survives_service_restart(web):
    client, manager = web
    request = payload()
    rid = client.post('/api/runs', json=request).json()['run_id']
    finished(client, rid)
    restarted = create_app(manager.root)
    with TestClient(restarted, base_url='http://127.0.0.1') as fresh:
        fresh.headers['X-Classroom-Token'] = fresh.get('/api/bootstrap').json()['token']
        response = fresh.post('/api/runs', json=request)
        assert response.json()['run_id'] == rid
        assert response.json()['duplicate'] is True
        assert fresh.get('/api/runs/' + rid).json()['summary']['status'] == 'SUCCESS'
    assert len(list(manager.store.base.glob('*/launch.json'))) == 1


def test_negative_sse_cursor_rejected(web):
    client, _ = web
    rid = client.post('/api/runs', json=payload()).json()['run_id']
    finished(client, rid)
    assert client.get(f'/api/runs/{rid}/events?after=-1').status_code == 400


def test_graph_approval_and_fork_api(web):
    client,manager=web
    rid=client.post('/api/runs',json=payload(engine='langgraph')).json()['run_id']
    view=finished(client,rid)
    assert view['state']=='WAITING_APPROVAL' and view['graph']['approval_pending']
    req=view['summary']['approval_request']
    body={key:req[key] for key in ('run_id','approval_id','code_hash')} | {'approved':True,'note':'课堂批准'}
    assert client.post(f'/api/runs/{rid}/approval',json=body|{'code_hash':'0'*64}).status_code==409
    assert client.post(f'/api/runs/{rid}/approval',json=body).status_code==202
    assert finished(client,rid)['state']=='SUCCESS'
    assert client.post(f'/api/runs/{rid}/approval',json=body).status_code==409
    rows=client.get(f'/api/runs/{rid}/checkpoints').json()
    safe=next(row for row in rows if row['safe_to_fork'])
    response=client.post(f'/api/runs/{rid}/fork',json={'checkpoint_id':safe['checkpoint_id'],'reason':'新的实验','max_candidates':3})
    assert response.status_code==202
    child=finished(client,response.json()['run_id'])
    assert child['state']=='WAITING_APPROVAL'
    assert child['summary']['parent_run_id']==rid
    assert client.get(f'/api/runs/{rid}').json()['state']=='SUCCESS'


def test_graph_history_read_is_not_resume_or_reconcile(web,monkeypatch):
    client,manager=web
    rid=client.post('/api/runs',json=payload(engine='langgraph')).json()['run_id']
    finished(client,rid)
    before=(manager.store.base/rid/'events.jsonl').read_bytes()
    monkeypatch.setattr('harness.service.RunService.inspect',lambda *a,**kw: pytest.fail('historical view must only read artifacts'))
    assert client.get(f'/api/runs/{rid}?historical=true').status_code==200
    assert client.post('/api/runs',json=payload(mode='replay',simulate=False,source=rid)).status_code==202
    assert (manager.store.base/rid/'events.jsonl').read_bytes()==before
