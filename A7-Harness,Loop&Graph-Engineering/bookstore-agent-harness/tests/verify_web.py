"""手动端到端 HTTP/SSE 验收。先运行 python -m harness.web。
此脚本会实际调用 Docker 和本地 Ollama；不是浏览器视觉测试。
"""
import json
import time
import urllib.request
import urllib.error
import uuid
from pathlib import Path

BASE = 'http://127.0.0.1:8765'


def call(path, body=None):
    headers = {'Content-Type': 'application/json'}
    if body is not None:
        headers['X-Classroom-Token'] = token
    req = urllib.request.Request(BASE + path,
                                 json.dumps(body).encode() if body is not None else None,
                                 headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(exc.read().decode()) from exc


def stream(rid, after=0):
    req = urllib.request.Request(f'{BASE}/api/runs/{rid}/events', headers={'Last-Event-ID':str(after)})
    with urllib.request.urlopen(req, timeout=180) as response:
        text = response.read().decode()
    return [json.loads(block.split('data: ',1)[1]) for block in text.split('\n\n') if 'event: harness\n' in block]


def experiment(body, expected):
    body['request_id'] = uuid.uuid4().hex
    result = call('/api/runs', body)
    rid = result['run_id']
    observed = stream(rid)
    detail = call('/api/runs/' + rid)
    assert detail['summary']['status'] == expected, detail
    assert observed == detail['events']
    assert stream(rid, 5) == observed[5:]
    print(body['mode'], body.get('scenario'), expected, rid, flush=True)
    return detail


token = call('/api/bootstrap')['token']
results = {}
results['success'] = experiment({'mode':'scripted','scenario':'success'}, 'SUCCESS')
rid = results['success']['run_id']
artifacts = call(f'/api/runs/{rid}/artifacts')
assert artifacts['tests']['1']['counts'] == {'passed':26,'failed':3,'errors':0,'skipped':0}
assert artifacts['tests']['2']['counts']['passed'] == 29
assert artifacts['tests']['2']['exit_code'] == 0
assert artifacts['tests']['2']['duration_ms'] > 0
assert artifacts['proposals']['2']['diff']
results['repeated'] = experiment({'mode':'scripted','scenario':'repeated'}, 'STALLED')
results['forbidden'] = experiment({'mode':'scripted','scenario':'forbidden'}, 'FAILED')
assert call(f"/api/runs/{results['forbidden']['run_id']}/artifacts")['proposals']['1']['decision'] == 'rejected'
results['budget'] = experiment({'mode':'scripted','scenario':'budget'}, 'FAILED')
results['simulated'] = experiment({'mode':'scripted','simulate':True}, 'SUCCESS')
results['replay'] = experiment({'mode':'replay','source':rid}, 'SUCCESS')
assert results['replay']['run_id'] == rid  # 回放仅查看，不创建另一次工具运行。
results['live'] = experiment({'mode':'live'}, 'SUCCESS')
root = Path(__file__).resolve().parent.parent
(root / 'web-verification-phase2.json').write_text(json.dumps({k:v['summary'] for k,v in results.items()}, indent=2))
