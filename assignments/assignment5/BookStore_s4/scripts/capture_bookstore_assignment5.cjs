#!/usr/bin/env node
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_EXECUTABLE?{executablePath:process.env.CHROMIUM_EXECUTABLE}:{})});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const checks=[];
  await page.goto('http://127.0.0.1:5173');
  await page.locator('.auth-buttons').getByRole('button',{name:/登\s*录/}).click();
  await page.getByPlaceholder('请输入用户名').fill('student');
  await page.getByPlaceholder('请输入密码').fill('bookstore-demo-2026');
  await page.getByRole('dialog',{name:'用户登录'}).getByRole('button',{name:/登\s*录/}).click();
  await page.getByRole('button',{name:'退出登录'}).waitFor();checks.push('JWT login');
  await page.getByRole('button',{name:'加入购物车'}).first().click();
  await page.locator('.cart-btn').click();
  const responsePromise=page.waitForResponse(r=>r.url().endsWith('/api/orders')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'一键购买'}).click();
  const response=await responsePromise;if(response.status()!==202)throw Error('Checkout did not return 202');
  const accepted=await response.json();checks.push('Cart POST returns 202');
  await page.getByText('排队号：'+accepted.queueId).waitFor();
  await page.screenshot({path:'evidence-kafka/04-bookstore-queued.png',fullPage:true});
  await page.getByRole('heading',{name:'订单已生成'}).waitFor();checks.push('Success shown only after SSE result');
  await page.screenshot({path:'evidence-kafka/05-bookstore-success.png',fullPage:true});
  if(!await page.getByText('购物车为空').isVisible())throw Error('Cart not cleared after success');
  checks.push('Cart cleared after confirmed success');
  await fs.writeFile('evidence-kafka/bookstore-browser-verification.json',JSON.stringify({passed:true,queueId:accepted.queueId,checks},null,2)+'\n');
  console.log('PASS: integrated React checkout and screenshots');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
