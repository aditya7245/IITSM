// Reproduces the stale edit form on the Users page without saving anything.
const path = require('path');
const { chromium } = require(process.argv[2]);
(async () => {
  const browser = await chromium.launch({ executablePath: process.argv[3] });
  const login = await (await fetch('http://localhost:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'admin@iitsm.local', password: 'Password@123' }) })).json();
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  await ctx.addInitScript((t) => localStorage.setItem('iitsm_token', t), login.token);
  const page = await ctx.newPage();
  await page.goto('http://localhost:5173/admin/users'); await page.waitForLoadState('networkidle');
  const rows = page.locator('tbody tr');
  const nameOf = async (i) => (await rows.nth(i).locator('td').first().innerText()).trim();
  const a = await nameOf(0), b = await nameOf(1);
  await rows.nth(0).getByRole('button', { name: 'Edit' }).click();
  await page.waitForTimeout(300);
  await rows.nth(1).getByRole('button', { name: 'Edit' }).click();
  await page.waitForTimeout(300);
  const heading = await page.locator('form h2, form h3, .card h2').first().innerText().catch(() => '?');
  const nameField = await page.getByLabel(/full name/i).first().inputValue().catch(() => '?');
  console.log(`clicked Edit on "${a.split('\n')[0]}", then on "${b.split('\n')[0]}"`);
  console.log(`form heading: "${heading}"   name field holds: "${nameField}"`);
  await page.screenshot({ path: path.join(__dirname, 'shots', 'stale-edit.png'), clip: { x: 220, y: 56, width: 1180, height: 420 } });
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
