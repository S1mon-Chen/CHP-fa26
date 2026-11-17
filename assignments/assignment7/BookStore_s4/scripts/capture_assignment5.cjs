#!/usr/bin/env node
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
(async () => {
  const base=process.argv[2]||'http://127.0.0.1:8080', output=process.argv[3]||'evidence-kafka';
  await fs.mkdir(output,{recursive:true});
  const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_EXECUTABLE?{executablePath:process.env.CHROMIUM_EXECUTABLE}:{})});
  try {
    const page=await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
    await page.goto(base+'/orders-demo.html');
    await page.locator('#run-success').click();
    await page.waitForSelector('#success .status.QUEUED');
    await page.locator('#interact').click();
    if(await page.locator('#clicks').textContent()!=='1')throw Error('Page stopped responding while queued');
    await page.screenshot({path:path.join(output,'01-accepted-and-responsive.png'),fullPage:true});
    await page.waitForFunction(()=>window.demoRuns.success!==null&&window.demoRuns.success!==undefined);
    await page.screenshot({path:path.join(output,'02-success-push.png'),fullPage:true});
    await page.locator('#run-failure').click();
    await page.waitForFunction(()=>window.demoRuns.failure!==null&&window.demoRuns.failure!==undefined);
    const runs=await page.evaluate(()=>window.demoRuns);
    for(const [name,expected] of [['success','SUCCEEDED'],['failure','FAILED']]){
      if(runs[name].error||runs[name].httpStatus!==202||runs[name].events.at(-1).status!==expected)throw Error(JSON.stringify(runs));
      if(runs[name].acceptedMs>=2000||runs[name].finishedMs<2800)throw Error('Nonblocking timing check failed');
    }
    await page.screenshot({path:path.join(output,'03-success-and-failure.png'),fullPage:true});
    await fs.writeFile(path.join(output,'browser-verification.json'),JSON.stringify({passed:true,responsiveClicks:1,runs},null,2)+'\n');
    console.log('PASS: real 202 / Kafka / SSE screenshots saved.');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
