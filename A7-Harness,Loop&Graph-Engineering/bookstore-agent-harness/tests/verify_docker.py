"""手动 Docker 集成验收：python tests/verify_docker.py（从项目根目录执行）。"""
import json
from pathlib import Path
from harness.executor import DockerExecutor

root = Path(__file__).resolve().parent.parent
config = json.loads((root / 'config.json').read_text())
executor = DockerExecutor(root, config)
assert executor.available(), 'Docker is required'
bad = (root / 'starter/bookstore.py').read_text()
good = bad.replace('subtotal > Decimal("100")', 'subtotal >= Decimal("100")')
results = {}
for name, code in [('initial', bad), ('correct', good), ('timeout', 'while True: pass\n'),
                   ('output_limit', 'while True: print("x" * 10000, flush=True)\n')]:
    if name in ('timeout', 'output_limit'):
        executor.config = config | {'test_timeout': 3, 'output_limit': 4096}
    result = executor({'code': code})
    results[name] = result
    print(name, json.dumps(result, ensure_ascii=False))
assert not results['initial']['passed'] and results['initial']['error'] is None
assert results['correct']['passed']
assert results['timeout']['error'] == 'TEST_TIMEOUT'
assert results['output_limit']['error'] == 'OUTPUT_LIMIT'
(root / 'docker-verification.json').write_text(json.dumps(results, ensure_ascii=False, indent=2))
