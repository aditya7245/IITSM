import { useState } from 'react';
import api, { cleanParams, errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import useLoad from '../../lib/useLoad';
import { label } from '../../lib/format';
import { isTechnicianRole, ROLES } from '../../lib/roles';
import { Empty, ErrorBox, Field, Loading, PageHeader, Pagination, SuccessBox } from '../../components/ui';

const NEW_USER = { email: '', password: '', fullName: '', role: 'EMPLOYEE', department: '', teamId: '', maxWorkload: 5, experienceYears: 0 };

export default function UsersPage() {
  const { user: me } = useAuth();
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);
  const [flash, setFlash] = useState(null);

  const users = useLoad(async () => (
    await api.get('/api/admin/users', { params: cleanParams({ role, page, size: 20 }) })
  ).data, [role, page]);
  const teams = useLoad(async () => (await api.get('/api/admin/teams')).data, []);

  const done = (message) => { setFlash(message); setCreating(false); setEditing(null); users.reload(); };

  return (
    <>
      <PageHeader title="Users" subtitle="Create accounts for staff, change details and deactivate people who left.">
        <button type="button" className="btn btn-primary" onClick={() => { setCreating(true); setEditing(null); setFlash(null); }}>New user</button>
      </PageHeader>
      <SuccessBox>{flash}</SuccessBox>

      {creating && <CreateUserForm teams={teams.data || []} onCancel={() => setCreating(false)}
        onDone={(u) => done(`Created ${u.fullName} (${label(u.role)}).`)} />}
      {editing && <EditUserForm key={editing.id} user={editing} isMe={editing.id === me.id} onCancel={() => setEditing(null)}
        onDone={(msg) => done(msg)} />}

      <div className="filters">
        <label className="field field-inline">
          <span className="field-label">Role</span>
          <select value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
            <option value="">All roles</option>
            {ROLES.map((r) => <option key={r} value={r}>{label(r)}</option>)}
          </select>
        </label>
      </div>

      <ErrorBox error={users.error} />
      {users.loading && !users.data ? <Loading what="users" /> : users.data && (
        <div className="card flush">
          {users.data.content.length === 0 ? <Empty>No users.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Department</th><th>Team</th><th>Status</th><th /></tr></thead>
                <tbody>
                  {users.data.content.map((u) => (
                    <tr key={u.id} className={u.active ? '' : 'row-muted'}>
                      <td>{u.fullName}</td>
                      <td>{u.email}</td>
                      <td>{label(u.role)}</td>
                      <td>{u.department || <span className="muted">–</span>}</td>
                      <td>{u.team?.name || <span className="muted">–</span>}</td>
                      <td>{u.active ? <span className="badge tone-good">Active</span> : <span className="badge tone-muted">Deactivated</span>}</td>
                      <td className="right"><button type="button" className="btn btn-small" onClick={() => { setEditing(u); setCreating(false); setFlash(null); }}>Edit</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={users.data.page} totalPages={users.data.totalPages} totalElements={users.data.totalElements} onChange={setPage} />
        </div>
      )}
    </>
  );
}

function CreateUserForm({ teams, onCancel, onDone }) {
  const [form, setForm] = useState(NEW_USER);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (f) => (e) => setForm({ ...form, [f]: e.target.value });
  const tech = isTechnicianRole(form.role);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const body = {
        email: form.email.trim(), password: form.password, fullName: form.fullName.trim(), role: form.role,
        department: form.department.trim() || null,
        teamId: tech && form.teamId ? Number(form.teamId) : null,
        maxWorkload: tech ? Number(form.maxWorkload) : null,
        experienceYears: tech ? Number(form.experienceYears) : null,
      };
      const { data } = await api.post('/api/admin/users', body);
      onDone(data);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form className="card form-grid" onSubmit={submit}>
      <h2 className="field-wide">New user</h2>
      <ErrorBox error={error} />
      <Field label="Full name"><input value={form.fullName} onChange={set('fullName')} required maxLength={120} /></Field>
      <Field label="Email"><input type="email" value={form.email} onChange={set('email')} required maxLength={150} /></Field>
      <Field label="Password" hint="At least 8 characters."><input type="password" value={form.password} onChange={set('password')} required minLength={8} maxLength={72} autoComplete="new-password" /></Field>
      <Field label="Role">
        <select value={form.role} onChange={set('role')}>
          {ROLES.map((r) => <option key={r} value={r}>{label(r)}</option>)}
        </select>
      </Field>
      <Field label="Department"><input value={form.department} onChange={set('department')} maxLength={100} /></Field>
      {tech && (
        <>
          <Field label="Team">
            <select value={form.teamId} onChange={set('teamId')}>
              <option value="">No team</option>
              {teams.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </Field>
          <Field label="Maximum workload" hint="Active incidents at once."><input type="number" min={1} value={form.maxWorkload} onChange={set('maxWorkload')} required /></Field>
          <Field label="Experience (years)"><input type="number" min={0} value={form.experienceYears} onChange={set('experienceYears')} required /></Field>
        </>
      )}
      <div className="form-actions field-wide">
        <button type="submit" className="btn btn-primary" disabled={busy}>Create user</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function EditUserForm({ user, isMe, onCancel, onDone }) {
  const [form, setForm] = useState({ fullName: user.fullName, department: user.department || '', active: user.active });
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.put(`/api/admin/users/${user.id}`, { fullName: form.fullName.trim(), active: form.active, department: form.department.trim() || null });
      if (password) await api.put(`/api/admin/users/${user.id}/password`, { password });
      onDone(`Saved ${form.fullName}${password ? ' and set a new password' : ''}.`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form className="card form-grid" onSubmit={save}>
      <h2 className="field-wide">Edit {user.fullName} <span className="muted small">{user.email} · {label(user.role)}</span></h2>
      <ErrorBox error={error} />
      <Field label="Full name"><input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required maxLength={120} /></Field>
      <Field label="Department"><input value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} maxLength={100} /></Field>
      <Field label="New password" hint="Leave empty to keep the current one.">
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="new-password" />
      </Field>
      <Field label="Account">
        <label className="switch">
          <input type="checkbox" checked={form.active} disabled={isMe} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span className="switch-track" aria-hidden="true" />
          <span>{form.active ? 'Active' : 'Deactivated'}{isMe ? ' (you cannot deactivate yourself)' : ''}</span>
        </label>
      </Field>
      {isTechnicianRole(user.role) && <p className="field-wide muted small">Workload, team, availability and skills are on the Technicians page.</p>}
      <div className="form-actions field-wide">
        <button type="submit" className="btn btn-primary" disabled={busy}>Save</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
