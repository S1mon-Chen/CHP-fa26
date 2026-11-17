#!/usr/bin/env python3
"""Create the Word report from real recorded evidence. Requires python-docx."""
from datetime import datetime, timezone, timedelta
from pathlib import Path
import json
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH

ROOT = Path(__file__).resolve().parents[1]
DATA = json.loads((ROOT/'evidence/verification.json').read_text())
BROWSER = json.loads((ROOT/'evidence/browser-loadtest.json').read_text())
assert DATA['completed'] and DATA['passed'] == DATA['total'] == 28
LOAD = DATA['observations']['loadtest']
WHEN = datetime.fromisoformat(DATA['time']).astimezone(timezone(timedelta(hours=8))).strftime('%Y年%m月%d日 %H时%M分')
doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Inches(8.2677), Inches(11.6929)
sec.left_margin = sec.right_margin = Inches(.8)
sec.top_margin = sec.bottom_margin = Inches(.7)
sec.footer_distance = Inches(.3)
for name in ('Normal','Title','Subtitle','Heading 1','Heading 2','Caption'):
    s = doc.styles[name]
    s.font.name = 'Arial'
    s.font.color.rgb = RGBColor(0,0,0)
    s.element.get_or_add_rPr().get_or_add_rFonts().set(qn('w:eastAsia'), 'Songti SC' if name=='Normal' else 'Heiti SC')
    s.paragraph_format.line_spacing = Pt(18)
    s.paragraph_format.space_after = Pt(7)
    for border in s.element.findall('.//'+qn('w:pBdr')): border.getparent().remove(border)
doc.styles['Normal'].font.size = Pt(11)
doc.styles['Title'].font.size = Pt(23)
doc.styles['Title'].paragraph_format.line_spacing = Pt(31)
doc.styles['Title'].paragraph_format.space_after = Pt(12)
doc.styles['Heading 1'].font.size = Pt(16)
doc.styles['Heading 1'].paragraph_format.space_before = Pt(9)
doc.styles['Heading 1'].paragraph_format.line_spacing = Pt(23)
doc.styles['Heading 2'].font.size = Pt(12)
doc.styles['Heading 2'].paragraph_format.space_before = Pt(8)
doc.styles['Caption'].font.size = Pt(9)
doc.styles['Caption'].paragraph_format.line_spacing = Pt(13)
doc.core_properties.title = '作业7 缓存与限流实验报告'
doc.core_properties.author = '陈熹朗'
doc.core_properties.subject = '应用系统体系架构'
footer = sec.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
run=footer.add_run();run.font.size=Pt(9)
field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE');run._r.addnext(field)

def p(text, style=None): return doc.add_paragraph(text,style)
def h(text): return doc.add_heading(text,level=1)
def sub(text): return doc.add_heading(text,level=2)
def code(text):
    para=doc.add_paragraph()
    para.paragraph_format.line_spacing=Pt(13)
    para.paragraph_format.space_after=Pt(9)
    shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'F3F5F7');para._p.get_or_add_pPr().append(shade)
    for i,line in enumerate(text.splitlines()):
        if i: para.add_run().add_break()
        r=para.add_run(line);r.font.name='DejaVu Sans Mono';r.font.size=Pt(8.5)
    return para
def table(headers,rows,widths):
    t=doc.add_table(rows=1,cols=len(headers));t.alignment=WD_TABLE_ALIGNMENT.CENTER;t.autofit=False
    for col,width in zip(t.columns,widths): col.width=Inches(width)
    borders=OxmlElement('w:tblBorders')
    for edge in ('top','left','bottom','right','insideH','insideV'):
        e=OxmlElement('w:'+edge)
        for a,v in [('val','single'),('sz','4'),('color','D9D9D9')]: e.set(qn('w:'+a),v)
        borders.append(e)
    t._tbl.tblPr.append(borders)
    for i,values in enumerate([headers]+rows):
        row=t.rows[0] if i==0 else t.add_row()
        row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
        if i==0: row._tr.get_or_add_trPr().append(OxmlElement('w:tblHeader'))
        for cell,value,width in zip(row.cells,values,widths):
            cell.width=Inches(width);cell.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER;cell.text=str(value)
            mar=OxmlElement('w:tcMar')
            for edge in ('top','left','bottom','right'):
                e=OxmlElement('w:'+edge);e.set(qn('w:w'),'90');e.set(qn('w:type'),'dxa');mar.append(e)
            cell._tc.get_or_add_tcPr().append(mar)
            if i==0:
                sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'E5EBEF');cell._tc.get_or_add_tcPr().append(sh)
            for para in cell.paragraphs:
                para.paragraph_format.space_after=Pt(0);para.paragraph_format.line_spacing=Pt(14)
                for r in para.runs: r.font.size=Pt(10);r.bold=i==0
    spacer=p('');spacer.paragraph_format.space_after=Pt(0);spacer.paragraph_format.line_spacing=Pt(4)
    return t
def link(label,url):
    para=doc.add_paragraph()
    para.paragraph_format.space_after=Pt(4)
    relation=doc.part.relate_to(url,'http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink',is_external=True)
    a=OxmlElement('w:hyperlink');a.set(qn('r:id'),relation)
    r=OxmlElement('w:r');props=OxmlElement('w:rPr')
    size=OxmlElement('w:sz');size.set(qn('w:val'),'20');props.append(size);r.append(props)
    text=OxmlElement('w:t');text.text=label;r.append(text);a.append(r);para._p.append(a)

p('作业7 缓存与限流实验报告','Title')
p('应用系统体系架构　　524031910668　陈熹朗　　2026年11月13日')
p('本实验在书店图书详情接口中实现 Redis Cache-Aside、空对象缓存防穿透和 Sentinel 每秒 5 次限流。真实 MySQL 与 Redis 环境下，28 项验收全部通过；50 线程短突发放行 5 个请求，另外 45 个返回 HTTP 429，数据库查询增量为 0。提交包包含本次源码、脚本、报告和压测证据。')
h('1 作业要求与实现')
table(['评分项','对应实现'],[
    ['Redis 缓存','先读 Redis，未命中查 MySQL 并回填；写库提交后删缓存'],
    ['缓存治理','选择防穿透方案：缺失图书缓存 __NULL__，TTL 为 30 秒'],
    ['治理组件','引入 Sentinel Core 1.8.9，使用 SphU.entry 保护详情接口'],
    ['限流规则','全部图书 ID 共用一个资源，QPS 5，超额直接拒绝'],
    ['压测证据','50 线程脚本、浏览器并发工具、429 原始响应和实际截图'],
],[1.25,5.4])
sub('请求链路')
code('GET /api/books/{id}\n  -> Sentinel: GET:/api/books/{id}, QPS = 5\n     -> blocked: HTTP 429 + Retry-After: 1\n     -> allowed: Redis GET a7:book:{id}\n        -> HIT: return book / NULL_HIT: HTTP 404\n        -> MISS: MySQL SELECT -> Redis SET with TTL')
p('本模块沿用原书店图书详情路径，位于 BookStore_s4/assignment7。应用、数据库和缓存采用独立端口与 Compose 项目名，解压精简提交包即可运行，无需启动旧作业的 Kafka、Seata、React 或原书店后端。')
table(['组件','端口','环境'],[
    ['应用','18087','Spring Boot 3.5.16，Java 17 字节码，运行 JDK 20.0.2'],
    ['MySQL','13307','MySQL 8.4.13，专用 bookstore_cache.books 表'],
    ['Redis','16379','Redis 7.4.10，128 MB，allkeys-lru'],
],[1.05,.65,4.95])
p('应用与容器发布端口均绑定 127.0.0.1。运行脚本开启本地观测接口，记录缓存命中、数据库读、放行与拒绝次数。依赖版本及容器镜像摘要见 evidence/environment.json。')

doc.add_page_break()
h('2 缓存与限流实现')
sub('Cache Aside 读写流程')
p('BookCacheService.find 先读取 a7:book:{id}。命中 JSON 时反序列化返回，X-Cache 为 HIT；键不存在时调用 BookRepository.find 查询 MySQL，将结果写回 Redis，并返回 MISS。正常图书的 TTL 为 120 秒加 0～30 秒随机抖动，减少缓存集中到期。损坏 JSON 会被删除并从数据库重建。')
p('数据库查不到图书时写入实际字符串 __NULL__，TTL 30 秒，返回 404 和 NULL_MISS。再次请求相同 ID 命中标记后直接返回 404 和 NULL_HIT，不执行 SQL。短 TTL 避免不存在状态长期保留；新增图书时也会主动删除负缓存。')
code('String cached = redis.opsForValue().get(key);\nif (NULL_VALUE.equals(cached)) return new Lookup(null, "NULL_HIT");\n// On a miss: query MySQL, then SET a JSON value or __NULL__.\nredis.opsForValue().set(key, NULL_VALUE, Duration.ofSeconds(30));')
p('写接口先校验字段，再通过 TransactionTemplate 提交数据库事务。execute 返回之后执行 Redis DEL，不提前删除，也不直接把请求内容当成数据库已提交的数据写入缓存。缓存删除异常时返回 503，并明确 databaseCommitted=true。')
code('Book saved = transactions.execute(status -> repository.save(id, body));\nredis.delete(PREFIX + id);\nreturn saved;')
sub('Sentinel 规则与拦截位置')
p('BookFlowControl 在启动时调用 FlowRuleManager.loadRules 注册规则。资源名固定为 GET:/api/books/{id}，全部图书 ID 共用额度，避免通过更换 ID 绕过限流。控制规则为 QPS、阈值 5、直接关联当前资源、超额立即拒绝。')
code('FlowRule rule = new FlowRule("GET:/api/books/{id}");\nrule.setGrade(RuleConstant.FLOW_GRADE_QPS);\nrule.setCount(5);\nrule.setStrategy(RuleConstant.STRATEGY_DIRECT);\nrule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);\nFlowRuleManager.loadRules(List.of(rule));')
p('Controller 在访问 Redis 之前获取 Sentinel Entry，捕获 BlockException 后返回 HTTP 429、TOO_MANY_REQUESTS 和 Retry-After: 1。try-with-resources 保证所有放行路径都退出 Entry。仅准入检查短暂同步，避免本 JVM 并发检查产生额度超发，后续缓存与 SQL 操作仍可并发。')
sub('依赖故障处理')
p('Redis 不可用时返回 503，不绕过缓存直接冲击数据库。该行为是显式的依赖异常处理；本次实现的 Sentinel 治理能力为接口限流，未将异常处理描述为独立的熔断规则。')

doc.add_page_break()
h('3 真实压测与验证结果')
p(f'验收完成于北京时间 {WHEN}。28 项检查覆盖缓存命中、负缓存 TTL 与到期重建、新增及更新失效、损坏缓存恢复、参数校验、跨 ID 共享限额、Redis 故障与恢复。完整断言见 evidence/verification.json。')
table(['工具','请求数','200','429','SQL 增量','总耗时'],[
    ['Python 50 线程',50,5,45,0,f"{LOAD['durationMs']:.2f} ms"],
    ['浏览器并发工具',50,5,45,0,f"{BROWSER['elapsedMs']:.2f} ms"],
],[2.05,.65,.5,.5,.8,2.15])
p('Python 使用 Barrier 同步释放 50 个线程；浏览器同时调度 50 次 fetch。两次压测前均预热缓存并等待 1.3 秒。以下截图由真实浏览器直接捕获，显示实际响应与计数增量。')
pic=p('');pic.paragraph_format.space_after=Pt(3);pic.paragraph_format.line_spacing=1.0
pic.add_run().add_picture(str(ROOT/'evidence/01-http429-loadtest.png'),width=Inches(6.35))
inline=pic.runs[0]._r.xpath('.//wp:docPr')[0]
inline.set('descr','浏览器真实压测截图，50 请求返回 5 个 200 和 45 个 429，数据库查询增量为 0，并展示响应原文。')
p('图1 浏览器压测截图    原图及逐请求响应见 evidence 目录','Caption')

doc.add_page_break()
h('4 复现与提交说明')
sub('启动和验证')
p('准备 JDK 17+、Maven 3.9+、Docker Compose 和 Python 3。启动 Docker 后在解压目录运行以下命令；首次构建需要下载 Maven 依赖。')
code('cd BookStore_s4\nbash start.sh\n\n# In another terminal:\ncd BookStore_s4/assignment7\npython3 scripts/verify.py --outage\npython3 scripts/loadtest.py')
p('打开 http://127.0.0.1:18087/ 可运行浏览器压测。验证脚本和页面请分别运行，以免共同消耗 5 QPS 额度。--outage 会短暂停止本作业 Redis 并自动恢复；不使用该参数则跳过两项故障测试。Ctrl+C 停止应用，docker compose stop 停止本作业的容器并保留 MySQL 数据。')
p('可选工具包括 Playwright 截图脚本 capture.cjs 和 JMeter 计划 books-50-users.jmx。JMeter 中的 429 会显示为 error，是预期限流结果；本次实际运行与截图使用 Python 和浏览器工具。')
sub('提交内容')
p('524031910668-作业7.zip 采用允许清单打包：Java 源码、POM、配置、SQL、压测页面、运行与验证脚本、本报告、README 和原始证据。包内附 SHA256SUMS.txt；不包含旧工程、第三方 JAR、target、node_modules、模型、.env 或数据库数据。')
p('主要代码位置：BookCacheService.java 负责缓存读写与负缓存，BookFlowControl.java 负责规则加载，BookController.java 负责 Sentinel Entry 与 HTTP 429。源码都位于 src/main/java/com/bookstore/cache。详细操作说明与证据索引见模块 README。')
sub('一致性和适用边界')
p('本题缓存治理选择防穿透，未实现分布式锁。重复的缺失 ID 可由负缓存吸收，不同缺失 ID 仍可能回源，但受统一 5 QPS 约束。冷缓存并发未命中时可能重复查库，不能保证仅一个线程重建缓存。')
p('Cache-Aside 是最终一致性方案。并发读回填与写后删除存在竞态，提交后进程退出也可能漏删缓存；正常 TTL 将旧值保留限制在最多约 150 秒。绕过服务直接修改 SQL 不会自动失效。Sentinel 限额作用于单 JVM，多实例需另行配置集群限流或统一网关。')
sub('参考资料')
link('Redis 官方 Cache-Aside 文档','https://redis.io/docs/latest/develop/use-cases/cache-aside/')
link('Spring Data Redis 官方模板接口文档','https://docs.spring.io/spring-data/redis/reference/redis/template.html')
link('Sentinel 官方资源与规则文档','https://sentinelguard.io/en-us/docs/basic-api-resource-rule.html')
link('Sentinel 官方流量控制文档','https://sentinelguard.io/en-us/docs/flow-control.html')

target=ROOT.parents[1]/'作业7实验报告.docx'
doc.save(target)
print(target)
