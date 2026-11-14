import { useState } from 'react';
import api, { cleanParams, errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import useLoad from '../../lib/useLoad';
import { label, STATUSES } from '../../lib/format';
import IncidentTable from '../../components/IncidentTable';
import { ErrorBox, Loading, PageHeader, Pagination } from '../../components/ui';

/** A technician's incidents (a team lead also sees their team's), workload and availability switch. */
export default function TechnicianWorkPage() {
  const { user, refreshUser } = useAuth();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [toggling, setToggling] = useState(false);
  const [toggleError, setToggleError] = useState(null);

  const workload = useLoad(async () => (await api.get('/api/technicians/me/workload')).data, []);
  const list = useLoad(async () => (
    await api.get('/api/incidents', { params: cleanParams({ status, page, size: 15 }) })
  ).data, [status, page]);

  const toggleAvailability = async () => {
    setToggling(true);
    setToggleError(null);
    try {
      await api.put('/api/technicians/me/availability', { available: !user.available });
      await refreshUser();
    } catch (e) {
      setToggleError(errorMessage(e));
    } finally {
      setToggling(false);
    }
  };

  const w = workload.data;
  const ratio = w && w.max ? Math.min(1, w.active / w.max) : 0;
  const isLead = user.role === 'TEAM_LEAD';

  return (
    <>
      <PageHeader title="My work"
        subtitle={isLead ? 'Incidents assigned to you and to the technicians in your team.' : 'Incidents assigned to you.'} />

      <div className="tiles">
        <div className="card tile">
          <div className="tile-label">Active workload</div>
          {w ? (
            <>
              <div className="tile-value">{w.active} <span className="tile-of">/ {w.max}</span></div>
              <div className="meter" role="meter" aria-valuemin={0} aria-valuemax={w.max} aria-valuenow={w.active}
                aria-label="Active incidents out of maximum">
                <div className={`meter-fill${ratio >= 1 ? ' full' : ''}`} style={{ width: `${ratio * 100}%` }} />
              </div>
              <div className="muted small">{w.active >= w.max ? 'At your limit: no new automatic assignments.' : `${w.max - w.active} more can be assigned to you.`}</div>
            </>
          ) : <ErrorBox error={workload.error} />}
        </div>
        <div className="card tile">
          <div className="tile-label">Availability</div>
          <div className="tile-value">{user.available ? 'Available' : 'Away'}</div>
          <label className="switch">
            <input type="checkbox" checked={!!user.available} onChange={toggleAvailability} disabled={toggling} />
            <span className="switch-track" aria-hidden="true" />
            <span>{user.available ? 'Receiving new incidents' : 'Not receiving new incidents'}</span>
          </label>
          <ErrorBox error={toggleError} />
        </div>
      </div>

      <div className="filters">
        <label className="field field-inline">
          <span className="field-label">Status</span>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </label>
        <button type="button" className="btn btn-small" onClick={() => { list.reload(); workload.reload(); }}>Refresh</button>
      </div>

      <ErrorBox error={list.error} />
      {list.loading && !list.data ? <Loading what="incidents" /> : list.data && (
        <div className="card flush">
          <IncidentTable incidents={list.data.content} hide={isLead ? [] : ['assignee']}
            emptyText="Nothing assigned right now." />
          <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onChange={setPage} />
        </div>
      )}
    </>
  );
}
