"""固定注册表；权限来自构造时可信配置，工具参数无法修改它。"""
import json
from types import MappingProxyType
from pydantic import ValidationError
from .schemas import ListArgs, SearchArgs, ReadArgs, ListResult, SearchResult, ReadResult, failure
from .search import search, DEFAULT_STOPS, DEFAULT_SYNONYMS

REGISTRY = MappingProxyType({'list_documents': ListArgs, 'search_documents': SearchArgs, 'read_document': ReadArgs})


def export_schemas():
    return [{'name':name, 'parameters':schema.model_json_schema()} for name,schema in REGISTRY.items()]


class ToolService:
    def __init__(self, snapshot, allowed_document_ids=None, output_limit=32768, stops=DEFAULT_STOPS, synonyms=None):
        if output_limit < 256:
            raise ValueError('输出上限至少 256 字节，以容纳固定错误信封')
        self.snapshot = snapshot
        self.allowed = frozenset(snapshot.documents if allowed_document_ids is None else allowed_document_ids)
        if not self.allowed <= snapshot.documents.keys():
            raise ValueError('可信配置包含未登记的文档 ID')
        self.output_limit = output_limit
        self.stops = tuple(stops)
        self.synonyms = {k:tuple(v) for k,v in (DEFAULT_SYNONYMS if synonyms is None else synonyms).items()}

    def validate_call(self, name, arguments):
        """供 Harness 审计使用；invoke_tool 仍会再次校验，不能绕过权限。"""
        if not isinstance(name, str) or name not in REGISTRY:
            return failure('UNKNOWN_TOOL', '工具名称未登记')
        try:
            args = REGISTRY[name].model_validate(arguments)
        except ValidationError:
            return failure('INVALID_ARGUMENTS', '参数格式、字段或范围不符合工具 Schema')
        ids = getattr(args, 'document_ids', None)
        if name == 'read_document':
            ids = [args.document_id]
        if ids is not None and not set(ids) <= self.allowed:
            return failure('DOCUMENT_NOT_ALLOWED', '文档未登记或不在当前允许访问集合')
        return None

    def invoke_tool(self, name, arguments):
        error = self.validate_call(name, arguments)
        if error:
            return error
        args = REGISTRY[name].model_validate(arguments)
        result = self._dispatch(name, args)
        if len(json.dumps(result, ensure_ascii=False).encode('utf-8')) > self.output_limit:
            return failure('OUTPUT_LIMIT_EXCEEDED', '结果超过输出上限，请缩小请求范围')
        return result

    def _dispatch(self, name, args):
        snapshot = self.snapshot
        common = {'corpus_snapshot_id':snapshot.corpus_snapshot_id}
        if name == 'list_documents':
            docs = [d for key,d in sorted(snapshot.documents.items()) if key in self.allowed
                    and (args.topic is None or args.topic.lower() in d.topic.lower())]
            return ListResult(**common, documents=docs[:args.limit]).model_dump()
        if name == 'search_documents':
            ids = self.allowed if args.document_ids is None else frozenset(args.document_ids)
            if not ids <= self.allowed:
                return failure('DOCUMENT_NOT_ALLOWED', '文档未登记或不在当前允许访问集合')
            terms,hits = search(snapshot,args.query,ids,args.top_k,self.stops,self.synonyms)
            return SearchResult(**common, query_terms=terms, hits=hits).model_dump()
        if args.document_id not in self.allowed:
            return failure('DOCUMENT_NOT_ALLOWED', '文档未登记或不在当前允许访问集合')
        chunks = []
        for chunk_id in args.chunk_ids:
            chunk = snapshot.chunks.get(chunk_id)
            # 未授权片段视为不存在，不泄露它属于哪个文档。
            if chunk is None or chunk.document_id not in self.allowed:
                return failure('CHUNK_NOT_FOUND', '当前可见资料中未找到该片段')
            if chunk.document_id != args.document_id:
                return failure('CHUNK_DOCUMENT_MISMATCH', '片段不属于指定文档')
            chunks.append(chunk)
        return ReadResult(**common, chunks=chunks).model_dump()
