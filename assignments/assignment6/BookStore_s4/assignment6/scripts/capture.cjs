// Requires Playwright + its Chromium browser; screenshots show the real running services.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const output = path.join(__dirname, '..', 'evidence');
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });
  const page = await browser.newPage({ viewport: { width: 1280, height: 980 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  try {
    await page.goto('http://127.0.0.1:18080/', { waitUntil: 'networkidle' });
    await page.locator('#stock').filter({ hasText: /^\d+$/ }).waitFor();
    const before = Number(await page.locator('#stock').innerText());
    await page.getByRole('button', { name: '正常下单', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('#status').textContent.includes('下单成功'));
    const successStock = Number(await page.locator('#stock').innerText());
    await page.screenshot({ path: path.join(output, '01-success.png'), fullPage: true });
    await page.getByRole('button', { name: '模拟订单入库异常' }).click();
    await page.waitForFunction(() => document.querySelector('#phase').textContent.includes('库存已提交扣减'));
    const intermediate = Number(await page.locator('#stock').innerText());
    await page.screenshot({ path: path.join(output, '02-deducted-before-failure.png'), fullPage: true });
    await page.waitForFunction(() => document.querySelector('#status').textContent.includes('库存已回滚'));
    const finalStock = Number(await page.locator('#stock').innerText());
    await page.screenshot({ path: path.join(output, '03-automatic-rollback.png'), fullPage: true });
    if (successStock !== before - 2 || intermediate !== successStock - 2 || finalStock !== successStock || errors.length) {
      throw new Error(JSON.stringify({ before, successStock, intermediate, finalStock, errors }));
    }
    const evidence = { before, successStock, intermediate, finalStock, errors,
      finalStatus: await page.locator('#status').innerText(), history: await page.locator('#history').innerText() };
    fs.writeFileSync(path.join(output, 'browser-verification.json'), JSON.stringify(evidence, null, 2) + '\n');
    console.log(JSON.stringify({ before, successStock, intermediate, finalStock, errors }));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
