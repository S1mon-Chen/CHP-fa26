"""工具契约：输入、输出都禁止未知字段，不做字符串到数字的隐式转换。"""
from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field

DocID = Annotated[str, Field(pattern=r'^doc_[0-9a-f]{16}$')]
ChunkID = Annotated[str, Field(pattern=r'^chunk_[0-9a-f]{24}$')]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True, frozen=True)


class ListArgs(StrictModel):
    topic: str | None = Field(default=None, min_length=1, max_length=80)
    limit: int = Field(default=20, ge=1, le=50)


class SearchArgs(StrictModel):
    query: str = Field(min_length=1, max_length=200)
    document_ids: list[DocID] | None = Field(default=None, min_length=1, max_length=20)
    top_k: int = Field(default=3, ge=1, le=5)


class ReadArgs(StrictModel):
    document_id: DocID
    chunk_ids: list[ChunkID] = Field(min_length=1, max_length=2)


class DocumentInfo(StrictModel):
    document_id: DocID
    title: str
    topic: str
    summary: str
    date: str
    version: str
    corpus_snapshot_id: str


class ChunkInfo(StrictModel):
    document_id: DocID
    chunk_id: ChunkID
    title: str
    section_title: str
    line_start: int
    line_end: int
    char_start: int
    char_end: int
    text: str
    content_hash: str
    corpus_snapshot_id: str


class Hit(StrictModel):
    document_id: DocID
    chunk_id: ChunkID
    title: str
    section_title: str
    score: float
    matched_terms: list[str]
    term_scores: dict[str, float]
    snippet: str
    line_start: int
    line_end: int
    content_hash: str
    corpus_snapshot_id: str


class ListResult(StrictModel):
    ok: Literal[True] = True
    corpus_snapshot_id: str
    documents: list[DocumentInfo]


class SearchResult(StrictModel):
    ok: Literal[True] = True
    corpus_snapshot_id: str
    query_terms: list[str]
    hits: list[Hit]


class ReadResult(StrictModel):
    ok: Literal[True] = True
    corpus_snapshot_id: str
    chunks: list[ChunkInfo]


class Error(StrictModel):
    code: Literal['UNKNOWN_TOOL', 'INVALID_ARGUMENTS', 'DOCUMENT_NOT_ALLOWED',
                  'CHUNK_NOT_FOUND', 'CHUNK_DOCUMENT_MISMATCH', 'CORPUS_LOAD_ERROR',
                  'OUTPUT_LIMIT_EXCEEDED']
    message: str


class Failure(StrictModel):
    ok: Literal[False] = False
    error: Error


def failure(code, message):
    return Failure(error=Error(code=code, message=message)).model_dump()
