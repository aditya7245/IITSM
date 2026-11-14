import { useState } from 'react';
import api, { errorMessage } from '../../api/client';
import { fetchTechnicians } from '../../api/lists';
import useLoad from '../../lib/useLoad';
import { label } from '../../lib/format';
import { Empty, ErrorBox, Field, Loading, PageHeader, SuccessBox } from '../../components/ui';

/** Technicians and team leads: workload, availability, experience, team and skills. */
export default function TechniciansPage() {
  const [editing, setEditing] = useState(null);
  const [flash, setFlash] = useState(null);

  const data = useLoad(async () => {
    const [technicians, workload, teams, skills] = await Promise.all([
      fetchTechnicians(),
      api.get('/api/reports/technician-workload'),
      api.get('/api/admin/teams'),
      api.get('/api/skills'),
    ]);
    const active = Object.fromEntries(workload.data.map((w) => [w.technicianId, w.active]));
    return { technicians, active, teams: teams.data, skills: skills.data };
  }, []);

  const D = data.data;

  return (
    <>
      <PageHeader title="Technicians" subtitle="Who can take incidents, how many, and with which skills." />
      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={data.error} />

      {editing && D && (
        <TechnicianEditor technician={editing} teams={D.teams} skills={D.skills}
          onCancel={() => setEditing(null)}
          onDone={(msg) => { setFlash(msg); setEditing(null); data.reload(); }} />
      )}

      {data.loading && !D ? <Loading what="technicians" /> : D && (
        <div className="card flush">
          {D.technicians.length === 0 ? <Empty>No technicians.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Name</th><th>Role</th><th>Team</th><th>Workload</th><th>Experience</th><th>Availability</th><th /></tr></thead>
                <tbody>
                  {D.technicians.map((t) => {
                    const active = D.active[t.id] ?? 0;
                    const ratio = t.maxWorkload ? Math.min(1, active / t.maxWorkload) : 0;
                    return (
                      <tr key={t.id} className={t.active ? '' : 'row-muted'}>
                        <td>{t.fullName}{!t.active && <span className="muted small"> (deactivated)</span>}</td>
                        <td>{label(t.role)}</td>
                        <td>{t.team?.name || <span className="muted">–</span>}</td>
                        <td>
                          <div className="workload-cell">
                            <span className="nowrap">{active} / {t.maxWorkload}</span>
                            <div className="meter small-meter" aria-hidden="true"><div className={`meter-fill${ratio >= 1 ? ' full' : ''}`} style={{ width: `${ratio * 100}%` }} /></div>
                          </div>
                        </td>
                        <td>{t.experienceYears} years</td>
                        <td>{t.available ? <span className="badge tone-good">Available</span> : <span className="badge tone-muted">Away</span>}</td>
                        <td className="right"><button type="button" className="btn btn-small" onClick={() => { setEditing(t); setFlash(null); }}>Edit</button></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  );
}

function TechnicianEditor({ technician: t, teams, skills, onCancel, onDone }) {
  const [form, setForm] = useState({
    maxWorkload: t.maxWorkload ?? 5, available: !!t.available, experienceYears: t.experienceYears ?? 0, teamId: t.team?.id || '',
  });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const current = useLoad(async () => (await api.get(`/api/admin/technicians/${t.id}/skills`)).data, [t.id]);
  const [levels, setLevels] = useState(null);   // skillId -> proficiency, filled once the current skills arrive

  const shown = levels ?? Object.fromEntries((current.data || []).map((s) => [s.skillId, s.proficiency]));

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.put(`/api/admin/technicians/${t.id}`, {
        maxWorkload: Number(form.maxWorkload), available: form.available,
        experienceYears: Number(form.experienceYears), teamId: form.teamId ? Number(form.teamId) : null,
      });
      if (levels) {
        const list = Object.entries(levels).filter(([, p]) => p).map(([skillId, p]) => ({ skillId: Number(skillId), proficiency: Number(p) }));
        await api.put(`/api/admin/technicians/${t.id}/skills`, { skills: list });
      }
      onDone(`Saved ${t.fullName}.`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form className="card form-grid" onSubmit={save}>
      <h2 className="field-wide">Edit {t.fullName}</h2>
      <ErrorBox error={error || current.error} />
      <Field label="Maximum workload"><input type="number" min={1} value={form.maxWorkload} onChange={(e) => setForm({ ...form, maxWorkload: e.target.value })} required /></Field>
      <Field label="Experience (years)"><input type="number" min={0} value={form.experienceYears} onChange={(e) => setForm({ ...form, experienceYears: e.target.value })} required /></Field>
      <Field label="Team">
        <select value={form.teamId} onChange={(e) => setForm({ ...form, teamId: e.target.value })}>
          <option value="">No team</option>
          {teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
        </select>
      </Field>
      <Field label="Availability">
        <label className="switch">
          <input type="checkbox" checked={form.available} onChange={(e) => setForm({ ...form, available: e.target.checked })} />
          <span className="switch-track" aria-hidden="true" />
          <span>{form.available ? 'Available' : 'Away'}</span>
        </label>
      </Field>
      <div className="field-wide">
        <span className="field-label">Skills and proficiency (1 = basic, 5 = expert)</span>
        {current.loading && !current.data ? <Loading what="skills" /> : (
          <div className="skill-grid">
            {skills.map((s) => (
              <label key={s.id} className="skill-row">
                <span>{s.name}</span>
                <select value={shown[s.id] || ''} onChange={(e) => setLevels({ ...shown, [s.id]: e.target.value ? Number(e.target.value) : 0 })}>
                  <option value="">None</option>
                  {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
                </select>
              </label>
            ))}
          </div>
        )}
      </div>
      <div className="form-actions field-wide">
        <button type="submit" className="btn btn-primary" disabled={busy}>Save</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
