"""资料加载后只保留不可变内存对象。文件名不作为工具可读取路径。"""
from dataclasses import dataclass
from collections.abc import Mapping
from hashlib import sha256
import json
import os
from pathlib import Path
import re
from types import MappingProxyType
from .schemas import ChunkInfo, DocumentInfo


class CorpusError(ValueError):
    """message 只用固定诊断文字，不包含系统异常或宿主路径。"""


def digest(text):
    return sha256(text.encode('utf-8')).hexdigest()


@dataclass(frozen=True)
class Snapshot:
    corpus_snapshot_id: str
    documents: Mapping[str, DocumentInfo]
    chunks: Mapping[str, ChunkInfo]
    total_bytes: int


def split_text(text, markdown=True, maximum=600):
    """返回半开字符区间。标题不跨节；段落尽量整体保留；所有字符恰好出现一次。"""
    headings = list(re.finditer(r'^#{1,6} +(.+?)[ \t]*$', text, re.M)) if markdown else []
    boundaries = sorted({0, len(text), *(m.start() for m in headings)})
    names = {m.start(): m.group(1).strip() for m in headings}
    for left, right in zip(boundaries, boundaries[1:]):
        section = names.get(left, '正文')
        # 空行结束一个段落；分隔符归前段，不删除换行或 Markdown 标记。
        stops = [m.end() for m in re.finditer(r'\r?\n[ \t]*\r?\n', text[left:right])]
        stops = [left+p for p in stops] + [right]
        start = cursor = left
        for stop in sorted(set(stops)):
            if stop-start > maximum and cursor > start:
                yield start, cursor, section
                start = cursor
            while stop-start > maximum:
                window = text[start:start+maximum]
                choices = [m.end() for m in re.finditer(r'[。！？；\n]', window) if m.end() >= 300]
                end = start + (choices[-1] if choices else maximum)
                yield start, end, section
                start = end
            cursor = stop
        if start < right:
            yield start, right, section


def load_corpus(root, max_file_bytes=131072, max_total_bytes=1048576):
    try:
        return _load(Path(root), max_file_bytes, max_total_bytes)
    except CorpusError:
        raise
    except (OSError, UnicodeError, ValueError):
        raise CorpusError('资料读取失败：请检查文件权限、UTF-8 编码和元数据') from None


def _load(root, file_limit, total_limit):
    if root.is_symlink() or any(p.is_symlink() for p in root.absolute().parents):
        raise CorpusError('不允许符号链接作为资料根目录或其父目录')
    root = root.resolve(strict=True)
    if not root.is_dir():
        raise CorpusError('资料根目录必须是目录')
    files = []
    def walk_error(error):
        raise CorpusError('资料目录无法读取')

    for folder, dirs, names in os.walk(root, followlinks=False, onerror=walk_error):
        for name in dirs + names:
            path = Path(folder)/name
            if path.is_symlink():
                raise CorpusError('资料目录不允许符号链接')
            if not path.resolve().is_relative_to(root):
                raise CorpusError('资料路径越界')
        files.extend(Path(folder)/name for name in names if Path(name).suffix.lower() in ('.md', '.txt'))
    if not files:
        raise CorpusError('没有可加载的 Markdown 或 TXT 资料')
    loaded = []
    seen = set()
    total = 0
    for path in sorted(files, key=lambda p:p.relative_to(root).as_posix()):
        if not path.is_file():
            raise CorpusError('资料必须是普通文件')
        # 有界读取，不一次性读取超过限制的文件；目录应由可信教师管理。
        with path.open('rb') as stream:
            raw = stream.read(file_limit+1)
        if len(raw) > file_limit:
            raise CorpusError('单文件大小超过上限')
        total += len(raw)
        if total > total_limit:
            raise CorpusError('资料集总大小超过上限')
        text = raw.decode('utf-8', errors='strict')
        if not text.strip():
            raise CorpusError('资料文件为空')
        relative = path.relative_to(root).as_posix()
        # 同目录同名 md/txt 视为重复 ID，不受扫描顺序影响。
        document_id = 'doc_'+digest(str(Path(relative).with_suffix('')).casefold())[:16]
        if document_id in seen:
            raise CorpusError('重复文档 ID（检查同名 Markdown/TXT）')
        seen.add(document_id)
        lines = text.splitlines()
        title = lines[0].lstrip('# ').strip()
        meta = {}
        for line in lines[1:8]:
            for label, key in [('主题','topic'), ('编写日期','date'), ('适用版本','version'), ('简介','summary')]:
                if line.startswith(label+'：'):
                    meta[key] = line.split('：',1)[1].strip()
        if not title or any(not meta.get(k) for k in ('topic','date','version')):
            raise CorpusError('缺少标题、主题、编写日期或适用版本元数据')
        meta.setdefault('summary', text[:100])
        loaded.append((relative, document_id, title, meta, text, path.suffix.lower()=='.md'))
    # 与机器路径、mtime、扫描顺序无关；包含加载/切分版本，升级规则会改变 ID。
    material = {'format':1, 'chunk_max':600, 'files':[(r, d, t, m, x) for r,d,t,m,x,_ in loaded]}
    snapshot_id = 'snapshot_'+digest(json.dumps(material, ensure_ascii=False, sort_keys=True))
    documents, chunks = {}, {}
    for _, doc_id, title, meta, text, markdown in loaded:
        documents[doc_id] = DocumentInfo(document_id=doc_id, title=title, **meta, corpus_snapshot_id=snapshot_id)
        for index,(start,end,section) in enumerate(split_text(text, markdown)):
            body = text[start:end]
            chunk_id = 'chunk_'+digest(snapshot_id+doc_id+str(index))[:24]
            if chunk_id in chunks:
                raise CorpusError('重复片段 ID')
            chunks[chunk_id] = ChunkInfo(document_id=doc_id, chunk_id=chunk_id,title=title,section_title=section,
                line_start=text.count('\n',0,start)+1, line_end=text.count('\n',0,end-1)+1,
                char_start=start,char_end=end,text=body,content_hash=digest(body),corpus_snapshot_id=snapshot_id)
    return Snapshot(snapshot_id, MappingProxyType(documents), MappingProxyType(chunks), total)
