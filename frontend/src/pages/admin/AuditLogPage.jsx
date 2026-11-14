import { useState } from 'react';
import { Link } from 'react-router-dom';
import api, { cleanParams, errorMessage } from '../../api/client';
import { fetchAllUsers, findIncidentByNumber } from '../../api/lists';
import useLoad from '../../lib/useLoad';
import { formatDateTime, label } from '../../lib/format';
import { Empty, ErrorBox, Loading, PageHeader, Pagination } from '../../components/ui';

/** Everything that happened to incidents, newest first, filtered by incident, user and date. */
export default function AuditLogPage() {
  const [filters, setFilters] = useState({ incidentId: '', userId: '', from: '', to: '' });
  const [number, setNumber] = useState('');
  const [numberError, setNumberError] = useState(null);
  const [page, setPage] = useState(0);

  const users = useLoad(fetchAllUsers, []);
  const log = useLoad(async () => (
    await api.get('/api/audit', { params: cleanParams({ ...filters, page, size: 25, sort: 'createdAt,desc' }) })
  ).data, [filters, page]);

  const set = (changes) => { setFilters({ ...filters, ...changes }); setPage(0); };

  const applyNumber = async (e) => {
    e.preventDefault();
    setNumberError(null);
    if (!number.trim()) { set({ incidentId: '' }); return; }
    try {
      const found = await findIncidentByNumber(number);
      if (found) set({ incidentId: found.id });
      else setNumberError(`No incident ${number.trim().toUpperCase()}.`);
    } catch (err) {
      setNumberError(errorMessage(err));
    }
  };

  const clear = () => { setNumber(''); setNumberError(null); setFilters({ incidentId: '', userId: '', from: '', to: '' }); setPage(0); };

  return (
    <>
      <PageHeader title="Audit log" subtitle="Every change to every incident: who did what, and when." />
      <div className="card filter-panel">
        <form className="field" onSubmit={applyNumber}>
          <span className="field-label">Incident number</span>
          <div className="input-with-button">
            <input value={number} onChange={(e) => setNumber(e.target.value)} placeholder="INC1001" />
            <button type="submit" className="btn btn-small">Apply</button>
          </div>
        </form>
        <label className="field">
          <span className="field-label">User</span>
          <select value={filters.userId} onChange={(e) => set({ userId: e.target.value })}>
            <option value="">Anyone</option>
            {users.data?.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({label(u.role)})</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">From</span>
          <input type="date" value={filters.from} onChange={(e) => set({ from: e.target.value })} />
        </label>
        <label className="field">
          <span className="field-label">To</span>
          <input type="date" value={filters.to} onChange={(e) => set({ to: e.target.value })} />
        </label>
        <div className="filter-actions">
          <button type="button" className="btn btn-small" onClick={clear}>Clear filters</button>
        </div>
      </div>
      <ErrorBox error={numberError || users.error || log.error} />

      {log.loading && !log.data ? <Loading what="the audit log" /> : log.data && (
        <div className="card flush">
          {log.data.content.length === 0 ? <Empty>No entries match these filters.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>When</th><th>Incident</th><th>Action</th><th>Status change</th><th>Description</th><th>By</th></tr>
                </thead>
                <tbody>
                  {log.data.content.map((a) => (
                    <tr key={a.id}>
                      <td className="nowrap">{formatDateTime(a.createdAt)}</td>
                      <td>{a.incidentId ? <Link className="mono" to={`/incidents/${a.incidentId}`}>{a.incidentNumber}</Link> : '–'}</td>
                      <td>{label(a.action)}</td>
                      <td className="nowrap">
                        {a.previousState && a.newState && a.previousState !== a.newState
                          ? <>{label(a.previousState)} → {label(a.newState)}</>
                          : <span className="muted">{label(a.newState) || '–'}</span>}
                      </td>
                      <td>{a.description}</td>
                      <td>{a.user?.name || <span className="muted">System</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination page={log.data.page} totalPages={log.data.totalPages} totalElements={log.data.totalElements} onChange={setPage} />
        </div>
      )}
    </>
  );
}
