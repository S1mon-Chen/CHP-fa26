"""从真实源码重新生成独立 HTML；不导入或执行候选代码。
项目根目录运行：python docs/code-map-src/build.py
"""
import ast
import hashlib
import html
import json
from pathlib import Path

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent.parent
OUTPUT=HERE.parent/'code-to-lecture.html'


def extract(spec):
    path=ROOT/spec['file']
    text=path.read_text()
    lines=text.splitlines()
    start,end=1,len(lines)
    if spec.get('symbol'):
        node=ast.parse(text)
        for part in spec['symbol'].split('.'):
            matches=[n for n in node.body if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef,ast.ClassDef)) and n.name==part]
            if len(matches)!=1:raise ValueError(f"symbol not found: {spec}")
            node=matches[0]
        start,end=node.lineno,node.end_lineno
    if spec.get('anchor'):
        matches=[i+1 for i,line in enumerate(lines) if start<=i+1<=end and line.startswith(spec['anchor'])]
        if len(matches)!=1:raise ValueError(f"anchor ambiguous or missing: {spec}: {matches}")
        start=matches[0]
    end=min(end,start+spec.get('count',end-start+1)-1)
    if end<start:raise ValueError(spec)
    code='\n'.join(lines[start-1:end])
    return dict(spec,start=start,end=end,code=code,sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    topics=json.loads((HERE/'topics.json').read_text())
    mapping_path=HERE/'lecture-pages.json'
    mapping=json.loads(mapping_path.read_text()) if mapping_path.exists() else {'source':None,'pages':{}}
    for number,topic in enumerate(topics,1):
        topic['page']=number
        topic['lecture']=mapping.get('pages',{}).get(topic['id'])
        topic['page_numbers']=mapping.get('page_numbers',{}).get(topic['id'],[])
        topic['alignment']=mapping.get('alignment_notes',{}).get(topic['id'],'')
        topic['sources']=[extract(s) for s in topic['sources']]
    # 可机器核对的侧车文件，也便于后续替换课件页码。
    data={'lecture_source':mapping.get('source'),'archive_sha256':mapping.get('archive_sha256'),'coverage':mapping.get('coverage',[]),'topics':topics}
    (HERE.parent/'code-to-lecture.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
    e=lambda value:html.escape(str(value),quote=True)
    sections=[];rows=[];nav=[]
    for t in topics:
        page=f"{t['page']:02d}"
        lecture=t['lecture'] or '待核对原课件'
        label=f"{page} · {t['title']}"
        nav.append(f'<a href="#{e(t["id"])}" data-topic="{e(t["id"])}"><span>{page}</span>{e(t["title"])}</a>')
        rows.append(f'<tr><td><a href="#{e(t["id"])}">{e(label)}</a><small>{e(t["stage"])}</small></td><td>{e(lecture)}</td><td>'+ '<br>'.join(e(s['file'])+f':{s["start"]}' for s in t['sources']) +f'</td><td>{e(t["ui"])}<small>观察：{e(t["expect"])}</small></td></tr>')
        sources=[]
        for i,s in enumerate(t['sources']):
            code='\n'.join(f'<span class="line"><span class="ln">{n}</span><span>{e(line)}</span></span>' for n,line in enumerate(s['code'].splitlines(),s['start']))
            sources.append(f'<details class="source" {"open" if i==0 else ""}><summary>{e(s["file"])} · L{s["start"]}–{s["end"]} <span>{e(s.get("symbol","源码摘录"))}</span></summary><pre class="code"><code>{code}</code></pre><small>真实源码摘录 · 文件 SHA-256：{s["sha256"][:16]}… · 完整指纹见 JSON 对照数据</small></details>')
        sections.append(f'''<article class="topic" id="{e(t['id'])}" data-stage="{e(t['stage'])}">
<div class="topic-head"><div><p class="eyebrow">{e(t['stage'])} / 讲解条目 {page}</p><h2>{e(t['title'])}</h2></div><span class="page">{page}<small>/ {len(topics)}</small></span></div>
<p class="concept">{e(t['concept'])}</p><p class="lecture"><b>原课件对应页</b>　{e(lecture)}</p><p class="alignment"><b>课件与实现差异</b>　{e(t['alignment'])}</p>
<div class="lesson-grid"><section><h3>01 · 读哪段代码</h3><p>{e(t['explain'])}</p>{''.join(sources)}</section>
<section><h3>02 · 怎样运行演示</h3><p class="requires">条件：{e(t['requires'])}</p><p>{e(t['ui'])}</p><div class="command"><button class="copy" type="button">复制命令</button><pre>{e(t['demo'])}</pre></div><h3>03 · 看什么证据</h3><p class="expected">{e(t['expect'])}</p><p><b>检查产物：</b>{e(t['evidence'])}</p><details><summary>对应验证命令</summary><pre>{e(t['test'])}</pre><p class="muted">pytest 控制逻辑测试可能使用明确标注的模拟观察。真实业务测试与进程重启以 Docker 集成程序为准。</p></details></section></div>
<footer class="lesson-foot"><p><b>向学生提问</b>　{e(t['question'])}</p><p><b>容易混淆</b>　{e(t['pitfall'])}</p></footer></article>''')
    coverage=[]
    for row in mapping.get('coverage',[]):
        related=' · '.join(f'<a href="#{e(key)}">{e(next(t["title"] for t in topics if t["id"]==key))}</a>' for key in row['topics']) or '—'
        coverage.append(f'<tr><td>第 {row["page"]} 页</td><td>{e(row["title"])}</td><td>{e(row["status"])}</td><td>{related}<small>{e(row["note"])}</small></td></tr>')
    template=(HERE/'template.html').read_text()
    for key,value in {'NAV':''.join(nav),'ROWS':''.join(rows),'SECTIONS':''.join(sections),'COUNT':str(len(topics)), 'COVERAGE':''.join(coverage),
                      'SOURCE':e(mapping.get('source') or '尚未收到原课件；准确页码待核对。LectureNote.md 只提供第 3–18、31–38 页的大范围线索。')}.items():
        template=template.replace('{{'+key+'}}',value)
    OUTPUT.write_text(template)
    print(f'Generated {OUTPUT}: {len(topics)} topics, {sum(len(t["sources"]) for t in topics)} verified source excerpts')

if __name__=='__main__':main()
