"""可选浏览器端到端脚本。先启动本项目 8770 服务，且确保没有活动任务。
安装可选测试工具：pip install playwright；python -m playwright install chromium
运行：python tests/browser_e2e.py。不是运行时依赖，不进入默认 pytest。
"""
import re
import uuid
from playwright.sync_api import sync_playwright,expect


def main():
    question='E2E 单资料 '+uuid.uuid4().hex[:8]
    with sync_playwright() as p:
        browser=p.chromium.launch()
        page=browser.new_page(viewport={'width':1440,'height':1000})
        page.goto('http://127.0.0.1:8770/')
        page.get_by_label('课堂问题',exact=True).fill(question)
        page.get_by_role('button',name='开始运行',exact=True).click()
        expect(page.locator('#metrics')).to_contain_text('执行：COMPLETED',timeout=15000)
        expect(page.locator('#answer')).to_contain_text('HDFS')
        identity=page.locator('#identity').inner_text()
        run_id=re.search(r'run_id：([a-f0-9]{32})',identity)[1]
        page.get_by_role('button',name='E1',exact=True).click()
        expect(page.locator('#evidence-E1')).to_have_class('highlight')
        page.reload()
        expect(page.locator('#identity')).to_contain_text(run_id,timeout=10000)
        expect(page.locator('#modeBadge')).to_have_text('历史轨迹回放')
        row=page.get_by_role('row').filter(has_text=question)
        row.get_by_role('button',name='逐步回放',exact=True).click()
        expect(page.locator('#replayPosition')).to_contain_text('历史事件 0/')
        page.get_by_role('button',name='单步',exact=True).click()
        expect(page.locator('#replayPosition')).to_contain_text('历史事件 1/')
        page.get_by_role('button',name='自动播放',exact=True).click()
        page.get_by_role('button',name='暂停回放',exact=True).click()
        paused=page.locator('#replayPosition').inner_text()
        page.wait_for_timeout(300)
        expect(page.locator('#replayPosition')).to_have_text(paused)
        page.get_by_label('预设场景',exact=True).select_option('unauthorized')
        page.get_by_role('button',name='开始运行',exact=True).click()
        expect(page.locator('#actions')).to_contain_text('DOCUMENT_NOT_ALLOWED',timeout=15000)
        expect(page.locator('#actions')).to_contain_text('未执行')
        expect(page.locator('#metrics')).to_contain_text('答案：insufficient_evidence')
        browser.close()
    print('PASS: browser run, citation, refresh, replay, pause, refusal')


if __name__=='__main__':main()
