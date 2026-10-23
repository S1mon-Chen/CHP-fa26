"""CLI 和网页共用的适配器装配；两个编排引擎共用节点和执行器。"""
import json
from .core import run
from .executor import DockerExecutor
from .ollama import Ollama

SCENARIOS = {
    'success': '成功修复：先失败，再通过',
    'repeated': '重复错误：相同补丁触发 STALLED',
    'forbidden': '违规修改：请求修改测试文件',
    'budget': '预算耗尽：不同错误候选持续失败',
}


def adapters(root, config, mode, scenario='success', simulate=False, check=True):
    executor = DockerExecutor(root, config)
    model = Ollama(config)
    observation = 'historical_observation' if mode == 'replay' else 'simulated' if simulate else 'docker'
    if mode != 'replay':
        if check and not simulate and not executor.available():
            def unavailable(_):
                return {'passed': False, 'error': 'DOCKER_UNAVAILABLE',
                        'summary': 'live 代码执行需要 Docker；不会在宿主执行候选代码', 'output': ''}
            executor = unavailable
            print('Docker 不可用：不会在宿主执行候选代码。')
        if mode == 'scripted':
            fixture = json.loads((root / 'scenarios' / f'{scenario}.json').read_text())
            def scripted(request):
                response = fixture['responses'][min(request['candidate']-1, len(fixture['responses'])-1)]
                return {'raw': json.dumps(response), 'tokens': None}
            model = scripted
            if simulate:
                def simulated(request):
                    index = next((i for i, r in enumerate(fixture['responses']) if r['code'] == request['code']), 0)
                    return fixture['results'][index]
                executor = simulated
    return model, executor, observation


def execute(root, config, mode, scenario='success', simulate=False, recording=None,
            out=None, run_id=None, on_event=None):
    from .service import RunService
    return RunService(root, out).start(config, mode, scenario, simulate, recording,
                                       run_id=run_id, on_event=on_event)
