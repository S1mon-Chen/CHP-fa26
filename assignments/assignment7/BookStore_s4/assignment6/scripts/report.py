#!/usr/bin/env python3
"""Create the Word experiment report from the recorded real test evidence.

Requires python-docx. Service execution and verification use only Java/Python stdlib.
"""
from pathlib import Path
import json
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH

ROOT = Path(__file__).resolve().parents[1]
DATA = json.loads((ROOT / 'evidence/verification.json').read_text())
SUCCESS = DATA['scenarios']['success']
FAILURE = DATA['scenarios']['rollback']
doc = Document()
section = doc.sections[0]
section.page_width, section.page_height = Inches(8.5), Inches(11)
section.top_margin = section.bottom_margin = Inches(.7)
section.left_margin = section.right_margin = Inches(.8)

for name in ('Normal','Title','Subtitle','Heading 1','Heading 2'):
    style = doc.styles[name]
    style.font.name = 'Noto Sans'
    style.font.color.rgb = RGBColor(0,0,0)
    rpr = style.element.get_or_add_rPr()
    fonts = rpr.find(qn('w:rFonts'))
    if fonts is None:
        fonts = OxmlElement('w:rFonts'); rpr.append(fonts)
    fonts.set(qn('w:eastAsia'), 'Songti SC' if name == 'Normal' else 'Heiti SC')
    style.paragraph_format.space_after = Pt(7)
    style.paragraph_format.line_spacing = Pt(17)
    for border in style.element.findall('.//' + qn('w:pBdr')):
        border.getparent().remove(border)
    snap = OxmlElement('w:snapToGrid'); snap.set(qn('w:val'), '0')
    style.element.get_or_add_pPr().append(snap)
doc.styles['Normal'].font.size = Pt(11.5)
doc.styles['Title'].font.size = Pt(23)
doc.styles['Title'].paragraph_format.line_spacing = Pt(29)
doc.styles['Heading 1'].font.size = Pt(16)
doc.styles['Heading 1'].paragraph_format.space_before = Pt(11)
doc.styles['Heading 1'].paragraph_format.line_spacing = Pt(21)
doc.styles['Heading 2'].font.size = Pt(12.5)
doc.core_properties.title = '作业6 Seata AT 分布式下单实验报告'
doc.core_properties.author = '陈熹朗'
doc.core_properties.subject = '应用系统体系架构'


def p(text, style=None):
    return doc.add_paragraph(text, style)


def h(text):
    return doc.add_heading(text, level=1)


def code(text):
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.space_before = Pt(3)
    paragraph.paragraph_format.space_after = Pt(8)
    paragraph.paragraph_format.line_spacing = 1.0
    for index, line in enumerate(text.splitlines()):
        if index:
            paragraph.add_run().add_break()
        run = paragraph.add_run(line)
        run.font.name = 'DejaVu Sans Mono'
        run.font.size = Pt(9)
    return paragraph


def table(headers, rows, widths):
    t = doc.add_table(rows=1, cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for col, width in zip(t.columns,widths): col.width = Inches(width)
    properties = t._tbl.tblPr
    borders = OxmlElement('w:tblBorders')
    for edge in ('top','left','bottom','right','insideH','insideV'):
        element = OxmlElement('w:'+edge)
        for attr,value in [('val','single'),('sz','4'),('color','D9D9D9')]: element.set(qn('w:'+attr),value)
        borders.append(element)
    properties.append(borders)
    for index, values in enumerate([headers]+rows):
        cells = t.rows[0].cells if index == 0 else t.add_row().cells
        for cell, text, width in zip(cells,values,widths):
            cell.width = Inches(width)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            cell.text = str(text)
            tcpr = cell._tc.get_or_add_tcPr()
            margins = OxmlElement('w:tcMar')
            for edge in ('top','left','bottom','right'):
                margin = OxmlElement('w:'+edge); margin.set(qn('w:w'),'100'); margin.set(qn('w:type'),'dxa'); margins.append(margin)
            tcpr.append(margins)
            if index == 0:
                shade = OxmlElement('w:shd');shade.set(qn('w:fill'),'DCE6EB');tcpr.append(shade)
            for paragraph in cell.paragraphs:
                paragraph.paragraph_format.space_after=Pt(0)
                paragraph.paragraph_format.line_spacing=Pt(14)
                if str(text).isdigit(): paragraph.alignment=WD_ALIGN_PARAGRAPH.CENTER
                for run in paragraph.runs:
                    run.font.size=Pt(10.5);run.bold=index==0
        trpr=t.rows[index]._tr.get_or_add_trPr()
        trpr.append(OxmlElement('w:cantSplit'))
        if index==0: trpr.append(OxmlElement('w:tblHeader'))
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after=Pt(0)
    spacer.paragraph_format.line_spacing=Pt(5)
    spacer.add_run().font.size=Pt(5)
    return t


doc.add_heading('作业6 分布式下单实验报告',0)
p('应用系统体系架构　　524031910668　陈熹朗　　2026年11月7日')
p('本实验将订单与库存拆分为两个 MySQL 逻辑库，以三个独立 Java 服务完成下单，通过 OpenFeign 传播请求与 XID，并接入 Apache Seata AT。真实验收的 28 项检查全部通过。订单写入异常时，已经提交的库存从 98 扣到 95 后自动恢复到 98，失败订单没有保留。')
h('1 架构与事务边界')
table(['组件','端口','职责与数据'],[
    ['Checkout','18080','统一入口与全局事务，不连接业务数据库'],
    ['Inventory','18081','库存本地事务；inventory_db 与 inventory_app'],
    ['Orders','18082','订单本地事务；order_db 与 order_app'],
    ['Seata TC','18091','协调全局提交与回滚，文件存储持久化'],
    ['MySQL','13306','两个独立逻辑库，分别授予业务账号权限'],
],[1.05,.65,5.2])
p('调用顺序为 Checkout → Inventory → Orders。库存与订单分别运行在独立 JVM，使用各自的连接池和本地事务；共享一个 MySQL 实例仅用于模拟，业务账号不能访问对方的库。两个库均创建 Seata undo_log 表。')
h('2 核心实现')
p('入口的 CheckoutService.place 使用 @GlobalTransactional，超时 30 秒，异常回滚规则为 Exception。Feign 拦截器将当前 XID 放入 TX_XID 请求头；下游 XidFilter 绑定到 RootContext，在 finally 中清理，防止线程复用导致事务串线。')
p('库存操作先 SELECT FOR UPDATE，再执行带库存下界条件的 UPDATE。Seata 自动代理数据源，使库存更新和 Undo 镜像一起提交。库存 Controller 在本地事务代理返回后才记录 INVENTORY_BRANCH_COMMITTED，能够证明远程库存分支确实已经提交。')
p('订单服务先执行 INSERT，再按 failOrder 开关抛出 BusinessException。该异常直接继承 RuntimeException。订单本地事务回滚后，Feign 将 HTTP 500 传播到入口，入口重新抛出异常，触发 Seata 全局回滚。恢复库存由 RM 使用 Undo 镜像完成，业务代码没有手写反向更新库存。')

doc.add_page_break()
h('3 正常与异常流程实测')
p('验收脚本调用真实 HTTP 服务，并通过 MySQL 客户端直接读取数据库；异常请求在库存 RPC 返回后停留 3 秒，捕获已提交库存的中间状态。以下为北京时间 2026年11月7日 21时27分的实测，原始证据文件时间采用 UTC。')
table(['观察阶段','库存','当前请求订单数','库存 Undo 数'],[
    ['正常请求前',100,0,0],['正常请求返回',98,1,'1，随后清理为 0'],
    ['异常请求前',98,0,0],['异常请求中 库存已提交',95,0,1],['异常请求回滚完成',98,0,0],
],[2.55,.6,1.5,2.25])
p('正常场景返回 HTTP 200，生成订单 1，库存扣减 2。成功响应返回时 Undo 日志可暂时存在，脚本随后确认库存库和订单库中的 Undo 最终清理为 0。')
p('异常场景返回 HTTP 500，错误码为 SIMULATED_ORDER_FAILURE；库存恢复至 98，当前请求的订单数为 0。中间状态的库存 Undo XID 与异常响应 XID 一致：')
code(FAILURE['response']['xid'])
doc.add_heading('异常链路的运行日志',level=2)
code('INVENTORY_BRANCH_COMMITTED ... before=98 after=95\n'
     'SIMULATED_RUNTIME_EXCEPTION after INSERT ...\n'
     'GLOBAL_ROLLBACK_TRIGGER ... SIMULATED_ORDER_FAILURE\n'
     'branch rollback success, xid: ...\n'
     'Branch Rollbacked result: PhaseTwo_Rollbacked\n'
     'rollback status: Rollbacked')
p('上述日志摘取自同一事务，完整请求号、XID、时间及分支号保存在 evidence/checkout-transactions.log、inventory-transactions.log、orders-transactions.log 与 seata-server.log。数据库快照及 28 项断言保存在 verification.json 和 verification.txt。')
doc.add_heading('补充验证',level=2)
p('验证还覆盖已完成请求重试不重复扣库存、同一请求号内容冲突、非法数量、缺失图书、库存不足、无 XID 写入拒绝、Undo 清理。最后一本图书的两个并发请求分别返回 200 和 409，最终库存为 0、订单数为 1，没有超卖。')
p('真实浏览器单独完成正常和异常场景：正常库存 98→96，异常库存 96→94→96，失败订单未生成，页面无 JavaScript 异常。三张截图和 browser-verification.json 保存在 evidence 目录，浏览器测试的库存基数与前述脚本测试不同。')

doc.add_page_break()
h('4 启动与复现')
p('需要 JDK 21、Maven 3.9+、Docker Compose v2、Python 3。首次运行需要下载依赖和镜像。本次使用 Spring Boot 3.5.16、Spring Cloud 2025.0.0、Seata 2.5.0；实测数据库为 MySQL 8.4.13。')
code('cd BookStore_s4\nexport JAVA_HOME=/path/to/jdk21\nbash start.sh')
p('打开 http://127.0.0.1:18080/，依次点击“正常下单”和“模拟订单入库异常”。另开终端，从 assignment6 目录运行验收：')
code('cd BookStore_s4/assignment6\npython3 scripts/verify.py')
p('统一下单接口为 POST /api/orders，最小请求体如下。模拟异常时加入 failOrder:true；pauseAfterInventoryMs 可设为 3000 观察中间状态。')
code('{"requestId":"demo-001","bookId":1,"quantity":2}')
p('按 requestId 查询使用 GET /api/orders/{requestId}，库存查询使用 GET /api/books。相同请求号和购买内容的已完成重试返回原订单；改变购买内容返回 409。发生超时等不确定结果，应先查询原请求。')
p('Ctrl+C 停止本次启动的三个 Java 进程；在 assignment6 目录运行 docker compose stop 停止本作业容器。数据存放在专用命名卷中，再次启动不会重置订单或库存。故障注入由演示脚本显式开启，可设置 DEMO_FAILURE_ENABLED=false 关闭。')
h('5 提交与适用范围')
p('提交包仅含本次源码、Maven 配置、SQL、页面、脚本、报告、说明和运行证据，并提供 SHA-256 清单。解压后可独立构建，不包含第三方 JAR、target、node_modules、模型文件或整个旧工程。完整复现步骤和评分项定位见 BookStore_s4/assignment6/README.md。')
p('本实验以单本图书的购买作为最小业务单元，Seata TC 为单节点文件存储。普通库存查询可能看到全局完成前的中间值，这是 AT 一阶段已本地提交的表现。所有库存写入应遵守同一代理与全局锁协议，绕过代理的 SQL 可能导致镜像冲突。本次未测试断电、长时间网络分区或 TC 重启恢复。')
h('参考资料')
for text in [
    'Apache Seata 部署指南：seata.apache.org/docs/ops/deploy-guide-beginner/',
    'Seata v2.5.0：script/client/at/db/mysql.sql 与 server/src/main/resources/application.yml，位于 github.com/apache/incubator-seata。',
    'Spring Cloud OpenFeign 官方文档：docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html',
]:
    paragraph=p(text)
    for run in paragraph.runs: run.font.size=Pt(10)

output=ROOT.parent.parent/'作业6实验报告.docx'
doc.save(output)
print(output)
