"""透明词频评分：正文次数 + 3×标题次数 + 2×小节标题次数。"""
import re
from .schemas import Hit

DEFAULT_STOPS = ('的', '了', '是', '如何', '什么', '以及', '一个')
DEFAULT_SYNONYMS = {'水位线': ('水印', 'watermark'), '迟到消息': ('迟到数据', 'late data'), '行键': ('rowkey',)}


def tokens(query, stops=DEFAULT_STOPS, synonyms=None):
    lower = query.lower()
    terms = set()
    for piece in re.findall(r'[a-z0-9_]+|[\u4e00-\u9fff]+', lower):
        if piece in stops:
            continue
        if piece.isascii() or len(piece) == 1:
            terms.add(piece)
        else:
            terms.update(piece[i:i+2] for i in range(len(piece)-1))
    for source, targets in (DEFAULT_SYNONYMS if synonyms is None else synonyms).items():
        if source.lower() in lower:
            terms.update(t.lower() for t in targets)
    return sorted(t for t in terms if t and t not in stops)


def count(term, text):
    text = text.lower()
    if re.fullmatch(r'[a-z0-9_ ]+', term):
        return len(re.findall(r'(?<![a-z0-9_])'+re.escape(term)+r'(?![a-z0-9_])', text))
    return text.count(term)


def search(snapshot, query, allowed, top_k=3, stops=DEFAULT_STOPS, synonyms=None):
    terms = tokens(query, stops, synonyms)
    hits = []
    for chunk in snapshot.chunks.values():
        if chunk.document_id not in allowed:
            continue
        weights = {t:float(count(t, chunk.text)+3*count(t, chunk.title)+2*count(t, chunk.section_title)) for t in terms}
        weights = {t:n for t,n in weights.items() if n}
        if not weights:
            continue
        # 优先围绕正文中的第一个命中位置摘录；只命中标题时截取片段开头。
        positions = [chunk.text.lower().find(t) for t in weights if t in chunk.text.lower()]
        start = max(0, min(positions)-35) if positions else 0
        snippet = chunk.text[start:start+180]
        hits.append(Hit(**{k:getattr(chunk,k) for k in ('document_id','chunk_id','title','section_title',
                    'line_start','line_end','content_hash','corpus_snapshot_id')},
                    score=sum(weights.values()), matched_terms=sorted(weights), term_scores=weights, snippet=snippet))
    return terms, sorted(hits, key=lambda h:(-h.score,h.document_id,h.line_start,h.chunk_id))[:top_k]
