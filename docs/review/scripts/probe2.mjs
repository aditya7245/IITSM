// Follow-up checks for the two failures: workload race and the spec's duplicate example.
import crypto from 'node:crypto';
const API = 'http://localhost:8080';
async function call(method, path, token, body) {
  const res = await fetch(API + path, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await res.text(); let d; try { d = JSON.parse(t); } catch { d = t; } return { status: res.status, data: d };
}
const login = async (w) => (await call('POST', '/api/auth/login', null, { email: `${w}@iitsm.local`, password: 'Password@123' })).data.token;
const admin = await login('admin'); const emp1 = await login('emp1'); const emp2 = await login('emp2');
const cats = (await call('GET', '/api/categories', emp1)).data; const svcs = (await call('GET', '/api/services', emp1)).data;
const cat = (n) => cats.find((c) => c.name === n).id; const svc = (n) => svcs.find((s) => s.name === n).id;
const wl = async () => (await call('GET', '/api/reports/technician-workload', admin)).data.map((t) => `${t.name} ${t.active}/${t.max}`).join(', ');
const strategy = (await call('GET', '/api/admin/config/assignment-strategy', admin)).data;
console.log('strategy:', JSON.stringify(strategy));

// 1) Close out existing open incidents' load? No: just measure. Sequential reports must never exceed the limit.
console.log('workload before sequential:', await wl());
const seq = [];
for (let i = 0; i < 12; i++) {
  const r = await call('POST', '/api/incidents', emp1, { title: `Keyboard keys stuck seq ${i}`, description: `Keys stuck on keyboard ${crypto.randomBytes(4).toString('hex')}`, categoryId: cat('Hardware'), serviceId: svc('Corporate Email'), impact: 1, urgency: 1, affectedUsers: 1 });
  seq.push(`${r.data.incident.status}${r.data.incident.assignee ? ':' + r.data.incident.assignee.name : ''}`);
}
console.log('sequential results:', seq.join(' | '));
console.log('workload after sequential:', await wl());

// 2) The spec's duplicate example, word for word, on the same service.
const rnd = crypto.randomBytes(2).toString('hex');
for (const [title, desc] of [['Payment application unavailable', 'Payment application unavailable'], ['Payment transaction failing', 'Payment transaction failing'], ['Unable to make payment', 'Unable to make payment']]) {
  const r = await call('POST', '/api/incidents', emp2, { title: `${title}`, description: desc, categoryId: cat('Application'), serviceId: svc('Payment Application'), impact: 2, urgency: 2, affectedUsers: 5 });
  console.log(`"${title}" -> duplicates: ${JSON.stringify(r.data.possibleDuplicates.map((d) => `${d.number} ${d.title} ${d.score.toFixed(2)}`))}`);
}
