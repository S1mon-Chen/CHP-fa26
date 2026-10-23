import json
import sys
from pathlib import Path
import pytest
from harness.core import Tape, manifest, run, validate
from harness.executor import DockerExecutor, bounded, normalize

ROOT = Path(__file__).resolve().parent.parent
CONFIG = json.loads((ROOT / 'config.json').read_text())
BAD = (ROOT / 'starter/bookstore.py').read_text()
GOOD = BAD.replace('subtotal > Decimal("100")', 'subtotal >= Decimal("100")')
FAIL = {'passed': False, 'error': None, 'summary': 'FAILED boundary', 'output': ''}
PASS = {'passed': True, 'error': None, 'summary': '29 passed', 'output': ''}


def model(code=BAD, path='bookstore.py'):
    return lambda request: {'raw': json.dumps({'path': path, 'code': code}), 'tokens': None}


def execute(tmp_path, m=None, e=None, **config):
    return run(ROOT, CONFIG | config, m or model(), e or (lambda _: FAIL), tmp_path,
               observation='simulated')


def test_stalled(tmp_path):
    result = execute(tmp_path)
    assert (result['status'], result['candidates']) == ('STALLED', 2)


def test_budget(tmp_path):
    result = execute(tmp_path, stall_threshold=10)
    assert (result['status'], result['candidates']) == ('FAILED', 3)


def test_forbidden_never_executes(tmp_path):
    def forbidden(_):
        pytest.fail('executor must not be called')
    result = execute(tmp_path, model(path='tests/test_bookstore.py'), forbidden)
    assert result['status'] == 'FAILED'
    assert not (Path(result['directory']) / 'candidate.py').exists()


@pytest.mark.parametrize('raw', ['not json', '[]', '{}', '{"path":"../bookstore.py","code":"pass"}',
                                   '{"path":"bookstore.py","code":"def ?"}',
                                   '{"path":"bookstore.py","code":12}'])
def test_validation(raw):
    assert validate(raw, 100)[1]['type'] == 'VALIDATION_ERROR'


def test_length():
    assert validate(json.dumps({'path':'bookstore.py','code':'#'*101}), 100)[1]


def test_success_and_replay(tmp_path):
    result = execute(tmp_path, model(GOOD), lambda _: PASS)
    assert result['status'] == 'SUCCESS'
    recording = json.loads((Path(result['directory']) / 'recording.json').read_text())
    def forbidden(_):
        pytest.fail('replay must not invoke model or tool')
    replay = run(ROOT, CONFIG, forbidden, forbidden, tmp_path, recording)
    assert replay['status'] == 'SUCCESS'
    assert replay['observation'] == 'historical_observation'
    recording['calls'][0]['request']['candidate'] = 100
    mismatch = run(ROOT, CONFIG, forbidden, forbidden, tmp_path, recording)
    assert mismatch['status'] == 'EXECUTION_ERROR'


def test_manifest_mismatch():
    with pytest.raises(ValueError, match='manifest mismatch'):
        Tape({'a':1}, {'manifest': {'a':2}, 'calls':[]})


def test_model_retries_bounded(tmp_path):
    calls = []
    def broken(_):
        calls.append(1)
        raise TimeoutError('model timed out')
    result = execute(tmp_path, broken)
    assert result['status'] == 'MODEL_ERROR'
    assert len(calls) == 2


def test_timeout_terminal(tmp_path):
    result = execute(tmp_path, e=lambda _: FAIL | {'error':'TEST_TIMEOUT'})
    assert result['status'] == 'EXECUTION_ERROR'
    assert result['candidates'] == 1


def test_process_timeout_and_output_limit():
    # 固定测试辅助程序；不是模型生成代码。
    assert bounded([sys.executable, '-c', 'import time; time.sleep(10)'], .1, 100)[2] == 'TEST_TIMEOUT'
    assert bounded([sys.executable, '-c', 'print("x" * 100000)'], 5, 100)[2] == 'OUTPUT_LIMIT'


def test_normalize():
    assert normalize('FAILED boundary\n1 failed in 0.12s') == normalize('FAILED boundary\n1 failed in 3.45s')


def test_missing_docker_never_runs_host(monkeypatch, tmp_path, capsys):
    from harness import cli
    monkeypatch.setattr(DockerExecutor, 'available', lambda _: False)
    def forbidden(*args):
        pytest.fail('Docker executor must not run')
    monkeypatch.setattr(DockerExecutor, '__call__', forbidden)
    monkeypatch.setattr(sys, 'argv', ['harness', 'scripted', '--out', str(tmp_path)])
    assert cli.main() == 1
    summaries = list(tmp_path.glob('*/summary.json'))
    assert json.loads(summaries[0].read_text())['status'] == 'EXECUTION_ERROR'
    assert '不会在宿主执行' in capsys.readouterr().out


def test_manifest_mismatch_still_writes_artifacts(tmp_path):
    result = run(ROOT, CONFIG, model(), lambda _: PASS, tmp_path,
                 {'manifest': {}, 'calls': [], 'observation': 'simulated'})
    assert result['status'] == 'EXECUTION_ERROR'
    assert (Path(result['directory']) / 'events.jsonl').exists()


def test_different_code_does_not_trigger_false_stall(tmp_path):
    def changing(request):
        return model(BAD + '\n# attempt ' + str(request['candidate']))(request)
    result = execute(tmp_path, changing)
    assert result['status'] == 'FAILED'
    assert result['candidates'] == 3


def test_different_failure_does_not_trigger_false_stall(tmp_path):
    count = 0
    def changing(_):
        nonlocal count
        count += 1
        return FAIL | {'summary': 'FAILED case ' + str(count)}
    result = execute(tmp_path, e=changing)
    assert result['status'] == 'FAILED'
    assert result['candidates'] == 3
