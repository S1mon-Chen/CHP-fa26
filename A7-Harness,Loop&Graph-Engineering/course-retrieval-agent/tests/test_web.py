import concurrent.futures
import json
from pathlib import Path
import shutil
import time
import uuid
import pytest
from fastapi.testclient import TestClient
from app.web import create_app
from app.tools import ToolService
from app.ollama import OllamaClient

ROOT=Path(__file__).resolve().parent.parent


@pytest.fixture
def root(tmp_path):
    shutil.copytree(ROOT/'corpus',tmp_path/'corpus')
    for name in ('config.json','agent-config.json'):shutil.copy(ROOT/name,tmp_path/name)
    return tmp_path


@pytest.fixture
def client(root):
    with TestClient(create_app(root),base_url='http://127.0.0.1:8770') as c:yield c


def payload(**kwargs):
    return {'request_id':uuid.uuid4().hex,'question':'解释 HDFS','mode':'scripted',**kwargs}


def finish(client,run_id):
    for _ in range(100):
        d=client.get('/api/runs/'+run_id).json()
        if not d['active']:return d
        time.sleep(.03)
    raise AssertionError('worker not finished')


@pytest.mark.parametrize('change',[{'mode':'bogus'},{'max_decisions':0},{'max_tool_attempts':True},
                                 {'root':'/etc'},{'url':'http://evil.test'},{'question':' '},{'model':'qwen2.5:7b'}])
def test_invalid_start(client,change):assert client.post('/api/runs',json=payload(**change)).status_code==422


def test_origin_host_json(client):
    assert client.post('/api/runs',json=payload(),headers={'origin':'https://evil.test'}).status_code==403
    assert client.get('/api/status',headers={'host':'evil.test:8770'}).status_code==403
    assert client.post('/api/runs',content='x',headers={'content-type':'text/plain'}).status_code==415
    assert client.options('/api/runs',headers={'origin':'http://evil.test'}).status_code==403


def test_concurrent_duplicate(client,monkeypatch):
    import app.web as web
    children=[]
    class Pending:
        def __init__(self,*a,**kw):children.append(self)
        def poll(self):return None
        def wait(self):return 0
    monkeypatch.setattr(web.subprocess,'Popen',Pending)
    with concurrent.futures.ThreadPoolExecutor() as pool:
        responses=list(pool.map(lambda _:client.post('/api/runs',json=payload()),range(4)))
    assert sorted(r.status_code for r in responses)==[200,409,409,409]
    assert len(children)==1


def test_real_worker_sse_reconnect_refresh_and_dedupe(client,root):
    p=payload();started=client.post('/api/runs',json=p);assert started.status_code==200
    run_id=started.json()['run_id']
    assert client.post('/api/runs',json=p).json()['run_id']==run_id
    final=finish(client,run_id);assert final['summary']['execution_status']=='COMPLETED'
    raw=[json.loads(x) for x in (root/'runs'/run_id/'events.jsonl').read_text().splitlines()]
    text=client.get(f'/api/runs/{run_id}/events').text
    packets=[json.loads(line[6:]) for line in text.splitlines() if line.startswith('data: {"event"')]
    assert [x['event'] for x in packets]==raw
    tail=client.get(f'/api/runs/{run_id}/events?after=2',headers={'Last-Event-ID':'5'}).text
    ids=[int(line[4:]) for line in tail.splitlines() if line.startswith('id: ')]
    assert ids==list(range(6,len(raw)+1))
    again=client.get('/api/runs/'+run_id).json()
    assert again['summary']==final['summary']
    listed=next(r for r in client.get('/api/runs').json() if r['run_id']==run_id)
    assert listed['execution_status']==final['summary']['execution_status']


def test_historical_evidence_not_current_and_no_tools(client,root,monkeypatch):
    run_id=client.post('/api/runs',json=payload()).json()['run_id'];d=finish(client,run_id)
    saved=d['evidence']
    for file in (root/'corpus').glob('*'):file.write_text('NEW TEXT')
    def forbidden(*a,**kw):raise AssertionError('历史读取不应调用模型或工具')
    monkeypatch.setattr(ToolService,'invoke_tool',forbidden);monkeypatch.setattr(OllamaClient,'request',forbidden)
    assert client.get(f'/api/runs/{run_id}/evidence').json()['evidence']==saved
    assert client.get(f'/api/runs/{run_id}/replay').status_code==200
    assert client.get(f'/api/runs/{run_id}/evidence/E1').json()==saved['E1']
    assert client.get(f'/api/runs/{run_id}/evidence/E999').status_code==404
    assert client.get(f'/api/runs/{run_id}/evidence/not-id').status_code==400
    assert client.get('/api/runs/not-an-id').status_code==400


def test_rejected_is_not_executed(client):
    run_id=client.post('/api/runs',json=payload(scenario='unauthorized')).json()['run_id'];d=finish(client,run_id)
    assert any(e['error_code']=='DOCUMENT_NOT_ALLOWED' for e in d['events'])
    assert not any(e['type']=='tool_started' for e in d['events'])


def test_insufficient_separate_from_error(client):
    run_id=client.post('/api/runs',json=payload(scenario='insufficient')).json()['run_id'];d=finish(client,run_id)
    assert d['summary']['execution_status']=='COMPLETED' and d['summary']['answer_status']=='insufficient_evidence'


def test_missing_dependency(client,monkeypatch):
    def unavailable(*a,**kw):raise OSError()
    monkeypatch.setattr(OllamaClient,'preflight',unavailable)
    assert client.post('/api/runs',json=payload(mode='live')).status_code==503


def test_corrupt_and_interrupted(client,root):
    run_id=client.post('/api/runs',json=payload()).json()['run_id'];finish(client,run_id)
    (root/'runs'/run_id/'evidence.json').write_text('{}')
    assert client.get('/api/runs/'+run_id).json()['summary']['execution_status']=='CORRUPT'
    assert client.get(f'/api/runs/{run_id}/replay').status_code==409
    interrupted=uuid.uuid4().hex;(root/'runs'/interrupted).mkdir()
    assert client.get('/api/runs/'+interrupted).json()['summary']['execution_status']=='INTERRUPTED'


def test_safe_html_transport(client,root):
    path=root/'corpus/hdfs_basics.md';path.write_text(path.read_text()+'\n<script>window.PWNED=1</script>\n')
    run_id=client.post('/api/runs',json=payload(question='<img src=x onerror=alert(1)>')).json()['run_id'];d=finish(client,run_id)
    assert d['config']['question']=='<img src=x onerror=alert(1)>'
    assert 'innerHTML' not in (ROOT/'web/app.js').read_text()
    assert "script-src 'self'" in client.get('/').headers['content-security-policy']
