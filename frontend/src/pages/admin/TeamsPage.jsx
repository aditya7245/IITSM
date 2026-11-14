import { useState } from 'react';
import api, { errorMessage } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { Empty, ErrorBox, Loading, PageHeader, SuccessBox } from '../../components/ui';

/** Teams of technicians and who leads each one. A lead must be a team lead who belongs to that team. */
export default function TeamsPage() {
  const [name, setName] = useState('');
  const [error, setError] = useState(null);
  const [flash, setFlash] = useState(null);
  const [busy, setBusy] = useState(false);

  const data = useLoad(async () => {
    const [teams, leads] = await Promise.all([
      api.get('/api/admin/teams'),
      api.get('/api/admin/users', { params: { role: 'TEAM_LEAD', size: 100 } }),
    ]);
    return { teams: teams.data, leads: leads.data.content };
  }, []);

  const run = async (call, message) => {
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      await call();
      setFlash(message);
      await data.reload();
      return true;
    } catch (e) {
      setError(errorMessage(e));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const create = async (e) => {
    e.preventDefault();
    const clean = name.trim();
    if (await run(() => api.post('/api/admin/teams', { name: clean }), `Team ${clean} created.`)) setName('');
  };

  return (
    <>
      <PageHeader title="Teams" subtitle="Technicians are grouped in teams; a team lead receives escalations for their team." />
      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={error || data.error} />

      <form className="card inline-form" onSubmit={create}>
        <label className="field">
          <span className="field-label">New team name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy || !name.trim()}>Create team</button>
      </form>

      {data.loading && !data.data ? <Loading what="teams" /> : data.data && (
        <div className="card flush">
          {data.data.teams.length === 0 ? <Empty>No teams yet.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Team</th><th>Lead</th><th>Change lead</th></tr></thead>
                <tbody>
                  {data.data.teams.map((t) => {
                    const candidates = data.data.leads.filter((l) => l.team?.id === t.id && l.active);
                    return (
                      <tr key={t.id}>
                        <td><strong>{t.name}</strong></td>
                        <td>{t.lead?.name || <span className="muted">No lead</span>}</td>
                        <td>
                          {candidates.length === 0
                            ? <span className="muted small">No active team lead in this team. Add one on the Users page.</span>
                            : (
                              <select value={t.lead?.id || ''} disabled={busy}
                                onChange={(e) => run(() => api.put(`/api/admin/teams/${t.id}/lead`, { leadId: Number(e.target.value) }), `Lead of ${t.name} changed.`)}>
                                {!t.lead && <option value="">Choose a lead</option>}
                                {candidates.map((l) => <option key={l.id} value={l.id}>{l.fullName}</option>)}
                              </select>
                            )}
                        </td>
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
