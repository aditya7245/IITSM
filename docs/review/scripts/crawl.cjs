// Visits every page per role in headless Chrome: console errors, failed requests, horizontal overflow on a phone,
// and role-guard behaviour when a role opens another role's page. Screenshots go to ./shots.
const path = require('path'); const fs = require('fs');
const { chromium } = require(process.argv[2]);
const exe = process.argv[3]; const shots = path.join(__dirname, 'shots'); fs.mkdirSync(shots, { recursive: true });
const PAGES = {
  emp1: ['/my-incidents', '/report'],
  tech2: ['/work'],
  admin: ['/dashboard', '/incidents', '/audit', '/admin/users', '/admin/teams', '/admin/technicians', '/admin/services', '/admin/categories', '/admin/sla', '/admin/settings'],
};
const FOREIGN = { emp1: ['/dashboard', '/admin/users', '/work'], tech2: ['/dashboard', '/report'], admin: ['/report'] };
(async () => {
  const browser = await chromium.launch({ executablePath: exe });
  for (const who of Object.keys(PAGES)) {
    const login = await (await fetch('http://localhost:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: `${who}@iitsm.local`, password: 'Password@123' }) })).json();
    for (const [vw, w] of [['desk', 1400], ['phone', 390]]) {
      const ctx = await browser.newContext({ viewport: { width: w, height: 900 } });
      await ctx.addInitScript((t) => localStorage.setItem('iitsm_token', t), login.token);
      const page = await ctx.newPage();
      const errs = [];
      page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)); });
      page.on('pageerror', (e) => errs.push('pageerror ' + String(e).slice(0, 160)));
      page.on('response', (r) => { if (r.status() >= 400 && r.url().includes('/api/')) errs.push(`HTTP ${r.status()} ${r.url().replace('http://localhost:8080', '')}`); });
      for (const p of PAGES[who]) {
        errs.length = 0;
        await page.goto('http://localhost:5173' + p); await page.waitForLoadState('networkidle'); await page.waitForTimeout(500);
        const m = await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, iw: innerWidth, h1: document.querySelector('h1')?.textContent, wide: [...document.querySelectorAll('.content *')].filter((e) => e.getBoundingClientRect().right > innerWidth + 2).slice(0, 3).map((e) => e.tagName + '.' + (e.className || '').toString().slice(0, 30)) }));
        const over = m.sw > m.iw + 1;
        console.log(`${who} ${vw} ${p} h1="${m.h1}" ${over ? `OVERFLOW ${m.sw}>${m.iw} ${m.wide.join(' ')}` : 'fits'} ${errs.length ? 'ERRORS ' + JSON.stringify(errs) : ''}`);
        await page.screenshot({ path: path.join(shots, `${who}-${vw}-${p.replace(/\//g, '_')}.png`), fullPage: vw === 'desk' });
      }
      if (vw === 'desk') for (const p of FOREIGN[who]) {
        await page.goto('http://localhost:5173' + p); await page.waitForLoadState('networkidle');
        console.log(`${who} foreign ${p} -> ${new URL(page.url()).pathname} h1="${await page.evaluate(() => document.querySelector('h1')?.textContent)}"`);
      }
      await ctx.close();
    }
  }
  // an incident page per role
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
