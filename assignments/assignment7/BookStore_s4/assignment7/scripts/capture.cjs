// Capture the actual running browser load tester, without mocked responses.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
(async () => {
  const output = path.join(__dirname, '..', 'evidence');
  fs.mkdirSync(output, {recursive:true});
  const browser = await chromium.launch({ headless:true, executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });
  try {
    const page = await browser.newPage({viewport:{width:1280,height:1100},deviceScaleFactor:1});
    const errors=[];
    page.on('pageerror', e=>errors.push(e.message));
    await page.goto(process.env.BASE_URL || 'http://127.0.0.1:18087/', {waitUntil:'networkidle'});
    await page.getByRole('button',{name:'发起 50 个并发请求'}).click();
    await page.waitForFunction(()=>Boolean(window.lastRun),{},{timeout:30000});
    const result=await page.evaluate(()=>window.lastRun);
    if(result.statusCounts['429']<=0 || result.statusCounts['200']<=0 || result.metricDelta.dbReads!==0 || errors.length) throw new Error(JSON.stringify({result,errors}));
    await page.screenshot({path:path.join(output,'01-http429-loadtest.png'),fullPage:true});
    fs.writeFileSync(path.join(output,'browser-loadtest.json'),JSON.stringify({...result,errors},null,2)+'\n');
    console.log(JSON.stringify({statusCounts:result.statusCounts,elapsedMs:result.elapsedMs,metricDelta:result.metricDelta,errors}));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
