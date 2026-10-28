#!/usr/bin/env node
// Requires Playwright and Chromium; no network resources are loaded by the page.
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');

(async () => {
  const base = process.argv[2] || 'http://127.0.0.1:8080';
  const output = process.argv[3] || 'evidence-jwt';
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ headless: true,
    ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
    await page.goto(base + '/verify.html', { waitUntil: 'networkidle' });
    if (process.env.BOOKSTORE_USERNAME) await page.locator('#username').fill(process.env.BOOKSTORE_USERNAME);
    if (process.env.BOOKSTORE_PASSWORD) await page.locator('#password').fill(process.env.BOOKSTORE_PASSWORD);
    await page.locator('#run').click();
    await page.waitForFunction(() => window.verificationResult !== null && window.verificationResult !== undefined);
    const result = await page.evaluate(() => window.verificationResult);
    if (!result.passed) throw new Error('Browser verification failed: ' + JSON.stringify(result));
    await page.screenshot({ path: path.join(output, 'authentication-401-200.png'), fullPage: true });
    await fs.writeFile(path.join(output, 'browser-verification.json'), JSON.stringify(result, null, 2) + '\n');
    console.log('PASS: screenshot saved from live HTTP verification.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
