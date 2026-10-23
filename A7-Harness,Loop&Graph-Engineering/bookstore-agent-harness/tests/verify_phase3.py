"""Opt-in REAL process restart + Docker verification (not part of host-only pytest).
Run: .venv/bin/python tests/verify_phase3.py
Artifacts: phase3-verification.json; isolated project under runs/phase3-process-project.
"""
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import time
import uuid
from pathlib import Path

import httpx

ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from harness.service import RunService


def wait(fn,timeout=60):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        value=fn()
        if value:return value
        time.sleep(.1)
    raise AssertionError('Timed out waiting for evidence')


def main():
    project=ROOT/'runs'/('phase3-process-'+uuid.uuid4().hex[:8]);project.mkdir()
    for name in ('starter','tests_business','scenarios','web'):shutil.copytree(ROOT/name,project/name)
    for name in ('TASK.md','config.json'):shutil.copy(ROOT/name,project/name)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    url=f'http://127.0.0.1:{port}'; process=None; client=None;log=(project/'backend.log').open('w')
    pids=[]; report={'project':str(project),'execution':'real Docker; independent uvicorn processes','process_ids':pids}
    def boot():
        nonlocal process,client
        process=subprocess.Popen([sys.executable,'-m','harness.web','--port',str(port),'--root',str(project)],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        pids.append(process.pid)
        def ready():
            try:return httpx.get(url+'/api/bootstrap',timeout=.5).status_code==200
            except httpx.HTTPError:return False
        wait(ready,15)
        client=httpx.Client(base_url=url,timeout=15)
        client.headers['X-Classroom-Token']=client.get('/api/bootstrap').json()['token']
    def shutdown(kill=False):
        nonlocal process
        if process:
            process.send_signal(signal.SIGKILL if kill else signal.SIGTERM)
            process.wait(timeout=30);process=None
        if client:client.close()
    def view(rid):
        response=client.get('/api/runs/'+rid);response.raise_for_status();return response.json()
    def done(rid):return wait(lambda: (v if not v['active'] else None) if (v:=view(rid)) else None)
    def start(scenario):
        r=client.post('/api/runs',json={'engine':'langgraph','mode':'scripted','scenario':scenario,'request_id':uuid.uuid4().hex})
        r.raise_for_status();return r.json()['run_id']
    def approval(rid):
        request=view(rid)['summary']['approval_request']
        return {k:request[k] for k in ('run_id','approval_id','code_hash')}|{'approved':True,'note':'真实进程重启后的批准'}
    try:
        boot(); rid=start('success'); result=done(rid)
        assert result['state']=='WAITING_APPROVAL',result
        assert not (project/'runs'/rid/'delivery').exists()
        stats=json.loads((project/'runs'/rid/'test-2.json').read_text())['counts']
        assert stats['passed']==29
        thread=result['summary']['thread_id']
        shutdown();boot()
        assert view(rid)['state']=='WAITING_APPROVAL'
        body=approval(rid)
        assert client.post(f'/api/runs/{rid}/approval',json=body|{'code_hash':'0'*64}).status_code==409
        assert client.post(f'/api/runs/{rid}/approval',json=body).status_code==202
        result=done(rid);assert result['state']=='SUCCESS',result
        assert result['summary']['thread_id']==thread
        assert client.post(f'/api/runs/{rid}/approval',json=body).status_code==409
        logevents=lambda run:[json.loads(x) for x in (project/'runs'/run/'events.jsonl').read_text().splitlines()]
        assert len([e for e in logevents(rid) if e['event_type']=='delivered'])==1
        report['approval_restart']={'run_id':rid,'status':result['state'],'counts':stats,'same_thread':True,'logical_deliveries':1}
        cp=next(c for c in client.get(f'/api/runs/{rid}/checkpoints').json() if c['safe_to_fork'])
        parent_bytes=(project/'runs'/rid/'events.jsonl').read_bytes()
        response=client.post(f'/api/runs/{rid}/fork',json={'checkpoint_id':cp['checkpoint_id'],'reason':'Docker 分支实测','max_candidates':3})
        response.raise_for_status();child=response.json()['run_id'];result=done(child)
        assert result['state']=='WAITING_APPROVAL'
        assert result['summary']['parent_run_id']==rid
        assert (project/'runs'/rid/'events.jsonl').read_bytes()==parent_bytes
        assert json.loads((project/'runs'/child/'test-2.json').read_text())['counts']['passed']==29
        assert client.post(f'/api/runs/{child}/approval',json=approval(child)|{'approved':False,'note':'教学拒绝'}).status_code==202
        assert done(child)['state']=='REJECTED'
        report['fork']={'run_id':child,'status':'REJECTED','retested':True,'parent_unchanged':True}
        # Kill during candidate 2 TEST after candidate 1 failure is checkpointed.
        fixture=project/'scenarios/repeated.json';data=json.loads(fixture.read_text())
        for item in data['responses']:
            item['code']+='\nimport time\ntime.sleep(5)\n'
        fixture.write_text(json.dumps(data))
        crash=start('repeated'); record=project/'runs'/crash/'execution-2.json'
        def running():
            if not record.exists():return False
            r=json.loads(record.read_text())
            if r['phase']!='running':return False
            d=subprocess.run(['docker','inspect','--format','{{.State.Running}}',r['name']],capture_output=True,text=True)
            return r if d.returncode==0 and d.stdout.strip()=='true' else False
        old=wait(running)
        checkpoint=RunService(project).inspect(crash,reconcile=False)['state']
        assert checkpoint['candidate']==2 and checkpoint['remaining_candidates']==1
        assert sum(checkpoint['fingerprints'].values())==1
        shutdown(kill=True);boot()
        recovered=view(crash);assert recovered['state']=='RECOVERABLE',recovered
        assert client.post(f'/api/runs/{crash}/resume').status_code==202
        result=done(crash);assert result['state']=='STALLED',result
        assert result['summary']['candidates']==2 and result['summary']['remaining_candidates']==1
        assert any(e['event_type']=='interrupted_execution_cleaned' for e in logevents(crash))
        assert sum(e['event_type']=='model_response' for e in logevents(crash))==2
        assert subprocess.run(['docker','inspect',old['name']],capture_output=True).returncode!=0
        report['crash_during_test']={'run_id':crash,'status':'STALLED','candidates':2,'remaining':1,'old_container_cleaned':True,'failure_fingerprints_preserved':True}
        print(json.dumps(report,ensure_ascii=False,indent=2))
        (ROOT/'phase3-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    finally:
        shutdown();log.close()

if __name__=='__main__':main()
