"""第二轮 CLI 子命令；replay/inspect 在加载资料和创建模型之前分流。"""
import argparse
import json
from pathlib import Path
from .agent_types import AgentConfig
from .recording import replay,ReplayError

ROOT=Path(__file__).resolve().parent.parent


def run_cli(argv):
    parser=argparse.ArgumentParser(description='受限课程检索 Agent Harness')
    parser.add_argument('--json',action='store_true')
    parser.add_argument('--agent-config',type=Path,default=ROOT/'agent-config.json')
    parser.add_argument('--config',type=Path,default=ROOT/'config.json',help='第一轮可信资料配置')
    parser.add_argument('--runs',type=Path,default=ROOT/'runs')
    sub=parser.add_subparsers(dest='command',required=True)
    ask=sub.add_parser('ask');ask.add_argument('question');ask.add_argument('--mode',choices=['live','scripted'],default='live')
    ask.add_argument('--scenario',default='single')
    sub.add_parser('scenarios')
    for name in ('inspect','replay'):
        p=sub.add_parser(name);p.add_argument('run_id')
    args=parser.parse_args(argv)
    try:
        if args.command in ('inspect','replay'):
            result=replay(args.runs,args.run_id)
            if args.command=='inspect':result.pop('events')
        elif args.command=='scenarios':
            from .scripted import SCENARIOS
            result={'ok':True,'scenarios':SCENARIOS}
        else:
            from .cli import Settings
            from .corpus import load_corpus
            from .tools import ToolService
            from .agent import Harness
            from .ollama import OllamaClient
            from .scripted import ScriptedModel,SCENARIOS
            if not args.question.strip() or len(args.question)>2000:raise ValueError('问题应为 1–2000 字符')
            config=AgentConfig.model_validate_json(args.agent_config.read_text())
            if args.mode=='scripted' and args.scenario not in SCENARIOS:raise ValueError('未知场景')
            from .runner import execute
            result=execute(args.question,config,args.config,args.runs,args.mode,args.scenario if args.mode=='scripted' else None)
    except ReplayError as exc:
        result={'ok':False,'error':{'code':'REPLAY_INVALID','message':str(exc)}}
    except (OSError,ValueError):
        result={'ok':False,'error':{'code':'INVALID_CONFIGURATION','message':'配置、问题或场景无效，请核对文件和参数'}}
    if args.json:print(json.dumps(result,ensure_ascii=False))
    else:render(result)
    return 0 if result['ok'] else 1


def render(result):
    if 'error' in result:
        print(result['error']);return
    if 'scenarios' in result:
        for name,label in result['scenarios'].items():print(f'{name}: {label}')
        return
    if 'observation' in result:print(result['observation'])
    summary=result['summary']
    print(f"{summary['mode_label']} | run_id={summary['run_id']}\nexecution_status={summary['execution_status']} | answer_status={summary['answer_status']}")
    print(f"决策轮={summary['decision_rounds']} 工具尝试={summary['tool_attempts']} 模型请求={summary['model_requests']} 传输重试={summary['transport_retries']}")
    if summary['error_code']:print('错误：'+summary['error_code'])
    for event in result.get('events',[]):
        print(f"{event['seq']:03} {event['phase']} {event['type']} call={event['call_id']} {event['error_code'] or ''}")
    answer=result.get('answer')
    if answer:
        for claim in answer['claims']:print(claim['text']+' ['+', '.join(claim['evidence_ids'])+']')
        for missing in answer['missing_information']:print('资料缺少：'+missing)
    for eid,item in result['evidence'].items():
        print(f"{eid}: {item['title']}，行 {item['line_start']}–{item['line_end']}，{item['chunk_id']}")
    print(summary['validation_scope'])
