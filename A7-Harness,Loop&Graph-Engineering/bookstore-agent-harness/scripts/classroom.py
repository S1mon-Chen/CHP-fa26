"""本地服务启停：独立进程、文件锁、进程身份核对；不管理 Docker/Ollama。"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
CONTROL = ROOT / '.classroom'
STATE = CONTROL / 'server.json'


def identity(pid):
    # macOS Python 启动器可能在启动时替换可执行路径；按启动时间与参数核对。
    started = subprocess.run(['ps', '-p', str(pid), '-o', 'lstart='], capture_output=True, text=True)
    command = subprocess.run(['ps', '-p', str(pid), '-o', 'command='], capture_output=True, text=True)
    marker = ' -m harness.web --root ' + str(ROOT) + ' --port '
    if started.returncode or command.returncode or marker not in command.stdout:
        return ''
    return started.stdout.strip() + marker + command.stdout.split(marker, 1)[1].strip()


def owned(state):
    return bool(state and identity(state['pid']) == state['identity'])


def main():
    parser = argparse.ArgumentParser(description='启动/关闭本项目课堂网页')
    parser.add_argument('action', choices=['start', 'stop', 'status'])
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--wait', type=int, default=30, help='启动/关闭等待秒数；超时不强杀')
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535 or not 1 <= args.wait <= 300:
        parser.error('port 必须为 1024–65535，wait 必须为 1–300')
    CONTROL.mkdir(exist_ok=True)
    with (CONTROL/'control.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        state = json.loads(STATE.read_text()) if STATE.exists() else None
        if args.action == 'status':
            print(f"服务进程存在：http://127.0.0.1:{state['port']} (PID {state['pid']})" if owned(state) else '脚本管理的服务未运行')
            return 0
        if args.action == 'stop':
            if not owned(state):
                print('脚本管理的服务未运行；不会关闭其他进程。')
                STATE.unlink(missing_ok=True)
                return 0
            print('正在优雅关闭，等待当前任务完成；运行记录和 checkpoint 会保留。', flush=True)
            os.kill(state['pid'], signal.SIGTERM)
            deadline = time.monotonic() + args.wait
            while owned(state) and time.monotonic() < deadline:
                time.sleep(.2)
            if owned(state):
                print('任务尚未结束，服务仍在关闭中。未强制终止；稍后重新运行关闭脚本。')
                return 1
            STATE.unlink(missing_ok=True)
            print('课堂服务已关闭。Docker Desktop 和 Ollama 未关闭。')
            return 0
        if owned(state):
            print(f"已有脚本管理的服务：http://127.0.0.1:{state['port']}；不会重复启动。")
            return 0
        # 仅做明确诊断，后续 bind 失败仍由真实服务启动结果判断。
        try:
            with socket.socket() as sock:
                # 与 Uvicorn 一致，允许刚关闭连接的 TIME_WAIT；仍拒绝活动监听。
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                sock.bind(('127.0.0.1', args.port))
        except OSError:
            print(f'端口 {args.port} 不可用。不会接管其他服务；请用 --port 8766 等指定空闲端口。')
            return 1
        log_path = CONTROL/'server.log'
        with log_path.open('ab') as log:
            child = subprocess.Popen([sys.executable, '-m', 'harness.web', '--root', str(ROOT),
                                      '--port', str(args.port)], cwd=ROOT, stdin=subprocess.DEVNULL,
                                     stdout=log, stderr=log, start_new_session=True)
        stamp = identity(child.pid)
        if not stamp:
            print(f'服务启动失败，查看 {log_path}')
            return 1
        state = {'pid':child.pid, 'port':args.port, 'identity':stamp}
        STATE.write_text(json.dumps(state))
        deadline = time.monotonic() + args.wait
        while time.monotonic() < deadline:
            if child.poll() is not None:
                STATE.unlink(missing_ok=True)
                print(f'服务启动失败，查看 {log_path}')
                return 1
            try:
                with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
                        f'http://127.0.0.1:{args.port}/', timeout=1) as response:
                    if response.status == 200 and child.poll() is None:
                        print(f'课堂网页：http://127.0.0.1:{args.port}\n日志：{log_path}\n关闭：./stop.sh')
                        return 0
            except OSError:
                pass
            time.sleep(.2)
        print(f'启动尚未就绪，保留进程供排查。查看 {log_path}；可用 ./stop.sh 关闭。')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
