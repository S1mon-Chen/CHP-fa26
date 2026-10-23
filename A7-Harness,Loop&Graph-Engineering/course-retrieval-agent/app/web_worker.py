"""固定后台进程入口；监督管道关闭表示服务进程消失，不伪装续跑。"""
import json
import os
from pathlib import Path
import sys
import threading
from .agent_types import AgentConfig
from .cli import Settings
from .runner import execute


def main():
    job=json.loads(Path(sys.argv[1]).read_text())
    guard=int(sys.argv[2])
    def parent_watch():
        os.read(guard,1)
        os._exit(75)  # 非正常服务退出；不生成虚假的正常 summary。
    threading.Thread(target=parent_watch,daemon=True).start()
    execute(job['question'],AgentConfig.model_validate(job['agent']),Path(job['root'])/'config.json',
            Path(job['root'])/'runs',job['mode'],job['scenario'],job['run_id'],Settings.model_validate(job['settings']),execution_fd=int(sys.argv[3]))


if __name__=='__main__':main()
