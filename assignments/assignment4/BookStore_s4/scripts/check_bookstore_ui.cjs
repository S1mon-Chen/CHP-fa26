#!/usr/bin/env node
// Exercise the existing React bookstore against its real integrated backend.
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const assert = require('node:assert/strict');

(async () => {
  const base = process.argv[2] || 'http://127.0.0.1:5173';
  const output = process.argv[3] || 'evidence-jwt';
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ headless: true,
    ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}) });
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    const failures = [];
    const requests = [];
    const checks = [];
    page.on('pageerror', error => failures.push(error.message));
    page.on('request', request => {
      if (!request.url().includes('/api/')) return;
      requests.push({ path: new URL(request.url()).pathname, method: request.method(),
        bearer: request.headers().authorization?.startsWith('Bearer ') || false,
        cookie: Boolean(request.headers().cookie) });
    });
    async function login(username, password) {
      await page.getByPlaceholder('请输入用户名').fill(username);
      await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
      await page.getByRole('dialog').getByRole('button', { name: /登\s*录/ }).click();
      await page.locator('.username').filter({ hasText: username }).waitFor();
      await page.locator('.book-card').first().waitFor();
    }
    await page.goto(base, { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: '登录浏览图书' }).waitFor();
    assert.equal(await page.locator('.book-card').count(), 0);
    checks.push('Guest is prompted to log in before loading protected books');
    await page.getByRole('button', { name: '登录浏览图书' }).click();
    await login('student', 'bookstore-demo-2026');
    assert.equal(await page.locator('.book-card').count(), 3);
    checks.push('Database customer logs in and immediately sees three books');
    await page.screenshot({ path: `${output}/bookstore-jwt-login.png`, fullPage: true });
    await page.reload({ waitUntil: 'networkidle' });
    await page.locator('.username').filter({ hasText: 'student' }).waitFor();
    await page.locator('.book-card').first().waitFor();
    checks.push('Refresh restores user identity through the saved JWT');
    const orderResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/orders' && response.request().method() === 'POST');
    await page.getByRole('button', { name: '加入购物车' }).first().click();
    await page.locator('.cart-btn').click();
    await page.getByRole('button', { name: '一键购买' }).click();
    const order = await orderResponse;
    assert.equal(order.status(), 201);
    const orderBody = await order.json();
    assert.equal(orderBody.order.orderItems.length, 1);
    checks.push('Checkout creates a real database order with Bearer authentication');
    await page.getByText('订单已生成', { exact: true }).waitFor();
    await page.getByRole('button', { name: /^确\s*定$/ }).click();
    await page.getByRole('button', { name: '继续购物' }).click();
    await page.locator('.nav-desktop').getByRole('menuitem', { name: '订单列表' }).click();
    await page.getByText('待支付', { exact: true }).first().waitFor();
    await page.screenshot({ path: `${output}/bookstore-jwt-order.png`, fullPage: true });
    checks.push('The order list displays the newly created order');
    await page.getByRole('button', { name: '退出登录' }).click();
    await page.getByRole('button', { name: '登录浏览图书' }).waitFor();
    assert.equal(await page.locator('.book-card').count(), 0);
    assert.equal(await page.evaluate(() => sessionStorage.getItem('bookstore.accessToken')), null);
    checks.push('Logout clears the credential, book list and user-specific state');
    await page.getByRole('button', { name: '登录浏览图书' }).click();
    await login('admin', 'admin-demo-2026');
    await page.locator('.nav-desktop').getByRole('menuitem', { name: '管理员Dashboard' }).waitFor();
    checks.push('Database administrator retains the admin navigation');
    await page.evaluate(() => sessionStorage.setItem('bookstore.accessToken', 'deliberately-invalid-token'));
    await page.reload({ waitUntil: 'networkidle' });
    await page.getByRole('dialog').waitFor();
    assert.equal(await page.evaluate(() => sessionStorage.getItem('bookstore.accessToken')), null);
    checks.push('Invalid JWT on refresh is cleared and opens the login dialog');
    assert.deepEqual(await context.cookies(), []);
    assert.ok(requests.filter(request => request.path === '/api/books').every(request => request.bearer));
    assert.ok(requests.every(request => !request.cookie && request.path !== '/api/auth/csrf'));
    assert.deepEqual(failures, []);
    checks.push('API requests use Bearer without cookies or CSRF bootstrap; no page exceptions');
    await fs.writeFile(`${output}/bookstore-ui-verification.json`, JSON.stringify({
      timestamp: new Date().toISOString(), baseUrl: base, passed: checks.length,
      checks, orderId: orderBody.order.id, requests,
    }, null, 2) + '\n');
    console.log(`PASS ${checks.length} browser checks; order ${orderBody.order.id}; no cookies or page exceptions.`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
