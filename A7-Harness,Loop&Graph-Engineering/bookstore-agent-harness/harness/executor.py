"""Docker 是本项目执行边界；绝不回退到宿主 Python。"""
import re
import json
import subprocess
import tempfile
import threading
import time
import uuid
from pathlib import Path


def bounded(command, timeout, limit):
    """持续排空管道；超过上限或时间即终止客户端，调用者清理容器。"""
    start = time.monotonic()
    p = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    chunks = bytearray()
    overflow = threading.Event()

    def read():
        while data := p.stdout.read(1024):
            remaining = max(0, limit - len(chunks))
            chunks.extend(data[:remaining])
            if len(data) > remaining:
                overflow.set()
                break

    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    error = None
    while p.poll() is None:
        if overflow.is_set():
            error = "OUTPUT_LIMIT"
            break
        if time.monotonic() - start >= timeout:
            error = "TEST_TIMEOUT"
            break
        time.sleep(0.02)
    if error:
        p.kill()
    p.wait()
    reader.join(timeout=1)
    if overflow.is_set():
        error = "OUTPUT_LIMIT"
    p.stdout.close()
    return p.returncode, chunks.decode(errors="replace"), error


def normalize(output):
    output = re.sub(r'\x1b\[[0-9;]*m', '', output)
    output = re.sub(r'\b\d+(?:\.\d+)?s\b', '<time>', output)
    output = re.sub(r'0x[0-9a-fA-F]+', '<address>', output)
    # 只保留测试身份及断言摘要，避免随机临时路径/耗时干扰指纹。
    lines = [line.strip() for line in output.splitlines()
             if line.startswith(('FAILED ', 'ERROR ', 'E ', 'E\t'))]
    return '\n'.join(lines)[:8000] or output.strip()[-2000:]


def pytest_counts(output):
    """只读取完整 pytest 汇总行；缺失或截断时返回 None，不推测数量。"""
    lines = [line for line in output.splitlines()
             if re.search(r"\b\d+ (?:passed|failed|errors?|skipped).* in \d", line)]
    if not lines:
        return None
    counts = {"passed": 0, "failed": 0, "errors": 0, "skipped": 0}
    for count, kind in re.findall(r"(\d+) (passed|failed|errors?|skipped)", lines[-1]):
        counts["errors" if kind in ("error", "errors") else kind] = int(count)
    return counts


class DockerExecutor:
    def __init__(self, root, config):
        self.root, self.config = root, config

    def available(self):
        try:
            return subprocess.run(["docker", "info"], capture_output=True,
                                  timeout=10).returncode == 0
        except (OSError, subprocess.TimeoutExpired):
            return False

    def __call__(self, request):
        c = self.config
        name = request.get("execution_id") or "bookstore-" + uuid.uuid4().hex
        record = Path(request['execution_record']) if request.get('execution_record') else None
        from .runtime import atomic
        identity = {'name':name, 'run_id':request.get('run_id')}
        if record and not re.fullmatch(r'bookstore-[a-f0-9]{32}-[1-9][0-9]*',name):
            raise ValueError('invalid execution identity')
        with tempfile.TemporaryDirectory(prefix="bookstore-") as directory:
            work = Path(directory)
            work.chmod(0o755)
            (work / "tests").mkdir(mode=0o755)
            (work / "bookstore.py").write_text(request["code"])
            for source in (self.root / "tests_business").glob("*.py"):
                (work / "tests" / source.name).write_bytes(source.read_bytes())
            for path in work.rglob("*.py"):
                path.chmod(0o444)
            command = ["docker", "create" if record else "run", "--pull=never", "--name", name, "--network=none",
                       "--user=10001:10001", "--read-only", "--cap-drop=ALL",
                       "--security-opt=no-new-privileges", "--memory=256m",
                       "--memory-swap=256m", "--cpus=1", "--pids-limit=64",
                       "--tmpfs=/tmp:rw,noexec,nosuid,size=32m",
                       "--mount", f"type=bind,src={work},dst=/work,readonly",
                       "--label=bookstore.harness=1", "--label=bookstore.run_id="+str(request.get('run_id','legacy')),
                       c["image"], "python", "-m", "pytest", "-q", "-s",
                       "-p", "no:cacheprovider", "tests"]
            try:
                start = time.monotonic()
                if record:
                    atomic(record, identity | {'phase':'allocating', 'workdir':str(work)})
                    created = subprocess.run(command,capture_output=True,text=True,timeout=15)
                    if created.returncode:
                        raise RuntimeError('Docker create failed: '+created.stderr[-2000:])
                    atomic(record, identity | {'phase':'created','container_id':created.stdout.strip()})
                    # Persist before start: recovery inspects the named container even if start is interrupted.
                    atomic(record, identity | {'phase':'running','container_id':created.stdout.strip()})
                    command = ['docker','start','-a',name]
                rc, output, error = bounded(command, c["test_timeout"], c["output_limit"])
                if rc not in (0, 1) and not error:
                    error = "DOCKER_OR_PYTEST_ERROR"
                return {"passed": rc == 0 and error is None, "error": error,
                        "summary": normalize(output), "output": output,
                        "execution": "docker", "exit_code": rc,
                        "duration_ms": round((time.monotonic() - start) * 1000, 3),
                        "truncated": error == "OUTPUT_LIMIT",
                        "counts": pytest_counts(output) if not error else None}
            finally:
                removed = subprocess.run(["docker", "rm", "-f", name], capture_output=True, timeout=10)
                if record and removed.returncode == 0:
                    atomic(record,identity | {'phase':'cleaned'})

    def recover(self, path, run_id):
        """核对自己创建的容器；不确定 create 是否完成时要求人工检查。"""
        from .runtime import RecoveryRequired, read_json, atomic
        saved = read_json(path)
        if saved is None:
            return '执行器尚未记录创建意图；没有启动容器'
        name = saved.get('name','')
        if saved.get('run_id') != run_id or not re.fullmatch('bookstore-'+run_id+r'-[1-9][0-9]*',name):
            raise RecoveryRequired('遗留执行身份不匹配')
        if not self.available():
            raise RecoveryRequired('Docker 不可用，无法核对或清理遗留容器')
        inspect = subprocess.run(['docker','container','inspect',name],capture_output=True,text=True,timeout=10)
        if inspect.returncode == 0:
            container = json.loads(inspect.stdout)[0]
            labels = container['Config'].get('Labels') or {}
            if labels.get('bookstore.harness')!='1' or labels.get('bookstore.run_id')!=run_id:
                raise RecoveryRequired('同名容器不属于本运行，禁止自动删除')
            removed = subprocess.run(['docker','rm','-f',container['Id']],capture_output=True,timeout=10)
            if removed.returncode:
                raise RecoveryRequired('未能终止遗留容器，禁止并行重试')
        elif saved['phase']=='allocating':
            raise RecoveryRequired('Docker create 提交窗口中断且找不到容器；需要人工确认旧客户端已结束后检查执行记录')
        elif 'No such container' not in inspect.stderr and 'No such object' not in inspect.stderr:
            raise RecoveryRequired('无法确认旧容器是否存在：'+inspect.stderr[-500:])
        atomic(path,saved | {'phase':'recovered'})
        return '已核对并清理旧执行；重试使用新的独立项目副本'
