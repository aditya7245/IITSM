// Checks the frontend against the fixed backend (backend :8080, frontend :5174) in headless Chrome.
const path = require('path');
const { chromium } = require(process.argv[2]);
const exe = process.argv[3];
const API = 'http://localhost:8080';
const WEB = 'http://localhost:5174';
const shots = path.join(__dirname, 'shots');
const tag = Date.now().toString(36);

const call = async (method, p, token, body) => {
  const r = await fetch(API + p, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text(); let d; try { d = JSON.parse(t); } catch { d = t; } return { status: r.status, data: d };
};
const login = async (who) => (await call('POST', '/api/auth/login', null, { email: `${who}@iitsm.local`, password: 'Password@123' })).data.token;
const results = [];
const check = (ok, what, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'} ${what}${detail ? ' :: ' + detail : ''}`); };

(async () => {
  const T = {}; for (const u of ['admin', 'emp1', 'emp2', 'tech1', 'tech2', 'lead.app']) T[u] = await login(u);
  const cats = (await call('GET', '/api/categories', T.emp1)).data, svcs = (await call('GET', '/api/services', T.emp1)).data;
  const cat = (n) => cats.find((c) => c.name === n).id, svc = (n) => svcs.find((s) => s.name === n).id;
  const report = (who, title) => call('POST', '/api/incidents', T[who], { title, description: 'Users cannot complete payment transactions', categoryId: cat('Application'), serviceId: svc('Payment Application'), impact: 2, urgency: 2, affectedUsers: 5 });

  // 1. duplicate privacy through the API the report page uses
  const a = (await report('emp1', `Payment app down ${tag}`)).data.incident;
  const b = await report('emp2', `Payment application unavailable ${tag}`);
  const leaked = b.data.possibleDuplicates.filter((d) => d.id === a.id);
  check(leaked.length === 0, "emp2's report does not list emp1's incident", JSON.stringify(b.data.possibleDuplicates.map((d) => d.number)));

  const browser = await chromium.launch({ executablePath: exe });
  const errors = [];
  const pageAs = async (who, w = 1400) => {
    const ctx = await browser.newContext({ viewport: { width: w, height: 900 } });
    await ctx.addInitScript((t) => localStorage.setItem('iitsm_token', t), T[who]);
    const page = await ctx.newPage();
    page.on('console', (m) => { if (m.type() === 'error') errors.push(`${who}: ${m.text().slice(0, 150)}`); });
    page.on('pageerror', (e) => errors.push(`${who}: ${String(e).slice(0, 150)}`));
    return page;
  };

  // 2. admin sees "Possible duplicates" on emp2's incident and links it with one click
  let page = await pageAs('admin');
  await page.goto(`${WEB}/incidents/${b.data.incident.id}`); await page.waitForLoadState('networkidle');
  const card = page.locator('section.card', { hasText: 'Possible duplicates' });
  check(await card.count() === 1, 'admin sees the "Possible duplicates" card');
  check(await card.getByRole('link', { name: a.incidentNumber }).count() === 1, `card lists emp1's ${a.incidentNumber}`);
  await page.screenshot({ path: path.join(shots, 'fix-similar.png'), clip: { x: 220, y: 56, width: 1180, height: 600 } });
  await card.getByRole('button', { name: `Mark duplicate of ${a.incidentNumber}` }).click();
  await page.getByRole('status').filter({ hasText: `Marked as a duplicate of ${a.incidentNumber}` }).waitFor({ timeout: 10000 });
  const after = (await call('GET', `/api/incidents/${b.data.incident.id}`, T.admin)).data;
  check(after.status === 'DUPLICATE' && after.duplicateOfNumber === a.incidentNumber, 'one click marks it a duplicate', `${after.status} of ${after.duplicateOfNumber}`);
  await page.context().close();

  // 3. hand-over: admin sees Reassign on an incident that is already in progress
  const work = (await call('GET', '/api/incidents?status=ASSIGNED&size=100', T.admin)).data.content;
  const tech2Users = (await call('GET', '/api/admin/users?size=200', T.admin)).data.content;
  const tech2Id = tech2Users.find((u) => u.email === 'tech2@iitsm.local').id;
  const mine = work.find((i) => i.assignee?.id === tech2Id);
  if (mine) {
    await call('POST', `/api/incidents/${mine.id}/acknowledge`, T.tech2);
    await call('POST', `/api/incidents/${mine.id}/start`, T.tech2);
    page = await pageAs('admin');
    await page.goto(`${WEB}/incidents/${mine.id}`); await page.waitForLoadState('networkidle');
    const reassign = page.getByRole('button', { name: 'Reassign' });
    check(await reassign.count() === 1, `admin sees "Reassign" on IN_PROGRESS ${mine.incidentNumber}`);
    await reassign.click();
    check(await page.getByText('Work has already started').count() === 1, 'reassign form explains the hand-over');
    check(await page.getByRole('button', { name: 'Change priority' }).count() === 1, '"Change priority" still offered while in progress');
    await page.screenshot({ path: path.join(shots, 'fix-handover.png'), clip: { x: 220, y: 56, width: 1180, height: 640 } });
    await page.context().close();
  } else check(false, 'found an ASSIGNED incident of tech2 for the hand-over check');

  // 4. team lead can add a note on their team's incident
  const tech1Id = tech2Users.find((u) => u.email === 'tech1@iitsm.local').id;
  const teamIncident = (await call('GET', `/api/incidents?technicianId=${tech1Id}&size=100`, T.admin)).data.content
    .find((i) => !['CLOSED', 'CANCELLED', 'DUPLICATE'].includes(i.status));
  if (teamIncident) {
    page = await pageAs('lead.app');
    await page.goto(`${WEB}/incidents/${teamIncident.id}`); await page.waitForLoadState('networkidle');
    check(await page.getByText('Add an internal note').count() === 1, `team lead gets the note box on ${teamIncident.incidentNumber}`);
    await page.getByRole('textbox').last().fill(`Lead note ${tag}`);
    await page.getByRole('button', { name: 'Add note' }).click();
    await page.getByRole('status').filter({ hasText: 'Note added.' }).waitFor({ timeout: 10000 });
    check(true, 'team lead note saved');
    await page.context().close();
  } else check(false, 'found an open incident of tech1');

  // 5. the edit form follows the user that was clicked
  page = await pageAs('admin');
  await page.goto(`${WEB}/admin/users`); await page.waitForLoadState('networkidle');
  const rows = page.locator('tbody tr');
  const second = (await rows.nth(1).locator('td').first().innerText()).split('\n')[0].trim();
  await rows.nth(0).getByRole('button', { name: 'Edit' }).click();
  await rows.nth(1).getByRole('button', { name: 'Edit' }).click();
  const nameField = await page.getByLabel(/full name/i).first().inputValue();
  check(nameField === second, 'Users edit form shows the user that was clicked last', `field="${nameField}" expected="${second}"`);
  await page.context().close();

  // 6. text limits are enforced in the browser too
  page = await pageAs('emp1');
  await page.goto(`${WEB}/report`); await page.waitForLoadState('networkidle');
  check(await page.locator('textarea[maxlength="5000"]').count() === 1, 'report description is limited to 5000 characters');
  await page.context().close();

  check(errors.length === 0, 'no console errors', errors.join(' | '));
  await browser.close();
  console.log(`\nSUMMARY ${results.filter(Boolean).length}/${results.length}`);
})().catch((e) => { console.error(e); process.exit(1); });
