import argparse
import json
from pathlib import Path
from .runner import execute, SCENARIOS
from .executor import DockerExecutor
from .ollama import Ollama
from .service import RunService, ServiceError

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["live", "scripted", "replay", "status", "approve", "reject", "resume", "checkpoints", "fork"])
    parser.add_argument('run_id', nargs='?')
    parser.add_argument('--engine', choices=['python','langgraph'])
    parser.add_argument('--approval-id')
    parser.add_argument('--code-hash')
    parser.add_argument('--note', default='')
    parser.add_argument('--checkpoint')
    parser.add_argument('--reason', default='')
    parser.add_argument('--model')
    parser.add_argument('--max-candidates', type=int)
    parser.add_argument("--config", type=Path, default=ROOT / "config.json")
    parser.add_argument("--scenario", choices=list(SCENARIOS), default="success")
    parser.add_argument("--simulate", action="store_true", help="explicit simulated tool observations; scripted only")
    parser.add_argument("--recording", type=Path)
    parser.add_argument("--out", type=Path, default=ROOT / "runs")
    args = parser.parse_args()
    service = RunService(ROOT,args.out)
    if args.mode in ('status','approve','reject','resume','checkpoints','fork'):
        if not args.run_id:
            parser.error('此操作需要 run_id')
        try:
            if args.mode == 'status':
                result = service.inspect(args.run_id)
            elif args.mode == 'checkpoints':
                result = service.checkpoints(args.run_id)
            elif args.mode == 'resume':
                result = service.resume(args.run_id)
            elif args.mode == 'fork':
                if not args.checkpoint:
                    parser.error('--checkpoint 是必需的')
                result = service.fork(args.run_id,args.checkpoint,args.reason,args.model,args.max_candidates)
            else:
                if not args.approval_id or not args.code_hash:
                    parser.error('审批必须显式提供 --approval-id 与 --code-hash；从 status 读取')
                result = service.approve(args.run_id,{'run_id':args.run_id,'approval_id':args.approval_id,
                    'code_hash':args.code_hash,'approved':args.mode=='approve','note':args.note})
        except (ServiceError, ValueError) as exc:
            parser.error(str(exc))
        print(json.dumps(result,ensure_ascii=False,indent=2))
        return 0
    config = json.loads(args.config.read_text())
    if args.engine:
        config['engine']=args.engine
    if args.model:
        config['model']=args.model
    if args.max_candidates is not None:
        if not 1 <= args.max_candidates <= 5:
            parser.error('--max-candidates 必须为 1–5')
        config['max_candidates']=args.max_candidates
    for key in ["model_timeout", "max_candidates", "stall_threshold", "test_timeout", "output_limit", "code_limit"]:
        if not isinstance(config[key], (int, float)) or config[key] <= 0:
            parser.error(f"{key} must be positive")
    for key in ["max_candidates", "stall_threshold", "model_retries", "output_limit", "code_limit"]:
        if type(config[key]) is not int or config[key] < (0 if key == "model_retries" else 1):
            parser.error(f"invalid integer config: {key}")
    if args.simulate and args.mode != "scripted":
        parser.error("--simulate is only allowed in scripted mode")
    recording = None
    if args.mode == "replay":
        if not args.recording:
            parser.error("--recording is required")
        recording = json.loads(args.recording.read_text())
        print("历史观察回放：本次不调用模型，也不执行工具。")
    elif args.simulate:
        print("模拟执行：预设工具结果，不是本次真实测试。")
    summary = execute(ROOT, config, args.mode, args.scenario, args.simulate,
                      recording, args.out)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["status"] in ("SUCCESS", "WAITING_APPROVAL") else 1


if __name__ == "__main__":
    raise SystemExit(main())
