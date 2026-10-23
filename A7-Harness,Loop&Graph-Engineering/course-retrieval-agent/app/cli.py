"""CLI 配置是教师可信输入，模型只能看到 tools.py 的固定工具参数。"""
import argparse
import sys
import json
from pathlib import Path
from pydantic import Field, ValidationError
from .corpus import load_corpus, CorpusError
from .schemas import StrictModel, DocID, failure
from .tools import ToolService, export_schemas
from .search import DEFAULT_STOPS, DEFAULT_SYNONYMS

ROOT = Path(__file__).resolve().parent.parent


class Settings(StrictModel):
    corpus: str = 'corpus'
    allowed_document_ids: list[DocID] | None = None
    max_file_bytes: int = Field(default=131072, ge=1, le=10485760)
    max_total_bytes: int = Field(default=1048576, ge=1, le=52428800)
    output_limit: int = Field(default=32768, ge=256, le=1048576)
    stopwords: list[str] = list(DEFAULT_STOPS)
    synonyms: dict[str,list[str]] = {k:list(v) for k,v in DEFAULT_SYNONYMS.items()}


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    prefix = 0
    while prefix < len(argv):
        if argv[prefix] == '--json' or argv[prefix].startswith('--config='):
            prefix += 1
        elif argv[prefix] == '--config' and prefix + 1 < len(argv):
            prefix += 2
        else:
            break
    if prefix < len(argv) and argv[prefix] == 'agent':
        from .agent_cli import run_cli
        return run_cli(argv[:prefix] + argv[prefix+1:])
    p = argparse.ArgumentParser(description='课程资料快照与受限检索工具；agent 子命令提供第二轮模型闭环')
    p.add_argument('--json', action='store_true', help='全局参数放在子命令前')
    p.add_argument('--config', type=Path, default=ROOT/'config.json', help='教师控制的配置文件')
    sub = p.add_subparsers(dest='command', required=True)
    sub.add_parser('agent', help='第二轮 Harness；详细帮助：agent --help')
    for name in ('check','snapshot','schema'):sub.add_parser(name)
    ls=sub.add_parser('list');ls.add_argument('--topic');ls.add_argument('--limit',type=int,default=20)
    se=sub.add_parser('search');se.add_argument('query');se.add_argument('--top-k',type=int,default=3);se.add_argument('--document-id',action='append')
    rd=sub.add_parser('read');rd.add_argument('document_id');rd.add_argument('chunk_ids',nargs='+')
    inv=sub.add_parser('invoke');inv.add_argument('name');inv.add_argument('arguments',help='JSON 对象')
    a=p.parse_args(argv)
    try:
        if a.command == 'schema':
            result={'ok':True,'tools':export_schemas()}
        else:
            config=Settings.model_validate(json.loads(a.config.read_text(encoding='utf-8')))
            corpus=Path(config.corpus)
            if not corpus.is_absolute():corpus=a.config.absolute().parent/corpus
            snap=load_corpus(corpus,config.max_file_bytes,config.max_total_bytes)
            service=ToolService(snap,config.allowed_document_ids,config.output_limit,config.stopwords,config.synonyms)
            if a.command in ('check','snapshot'):
                visible=[c for c in snap.chunks.values() if c.document_id in service.allowed]
                result={'ok':True,'corpus_snapshot_id':snap.corpus_snapshot_id,
                        'visible_documents':len(service.allowed),'visible_chunks':len(visible),
                        'chunk_lengths':[len(c.text) for c in visible], 'note':'仅统计当前可见资料；源文件变化需显式重新加载'}
            elif a.command=='list':result=service.invoke_tool('list_documents',{'topic':a.topic,'limit':a.limit})
            elif a.command=='search':result=service.invoke_tool('search_documents',{'query':a.query,'top_k':a.top_k,'document_ids':a.document_id})
            elif a.command=='read':result=service.invoke_tool('read_document',{'document_id':a.document_id,'chunk_ids':a.chunk_ids})
            else:
                try:arguments=json.loads(a.arguments)
                except json.JSONDecodeError:arguments=None
                result=service.invoke_tool(a.name,arguments)
    except CorpusError as exc:
        result=failure('CORPUS_LOAD_ERROR',str(exc))
    except (OSError, UnicodeError, ValueError, ValidationError):
        result=failure('CORPUS_LOAD_ERROR','可信配置或资料无法加载，请检查配置与权限')
    if a.json:
        print(json.dumps(result,ensure_ascii=False))
    else:
        render(result)
    return 0 if result['ok'] else 1


def render(result):
    if not result['ok']:
        print(f"错误 {result['error']['code']}：{result['error']['message']}");return
    if 'corpus_snapshot_id' in result:print('快照：'+result['corpus_snapshot_id'])
    if 'query_terms' in result:print('检索词：'+', '.join(result['query_terms']))
    rows=result.get('documents',result.get('hits',result.get('chunks')))
    if rows is None:
        print(json.dumps(result,ensure_ascii=False,indent=2));return
    if not rows:print('没有结果。')
    for row in rows:
        print(f"\n{row['title']} | {row['document_id']}")
        if 'chunk_id' in row:print(f"{row['chunk_id']} | 行 {row['line_start']}–{row['line_end']} | {row['section_title']}")
        if 'score' in row:print(f"得分 {row['score']}（不是正确概率）；贡献：{row['term_scores']}")
        print(row.get('text',row.get('snippet',row.get('summary',''))))
        if 'version' in row:print('适用版本：'+row['version'])


if __name__=='__main__':
    raise SystemExit(main())
