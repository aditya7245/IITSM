import { useState } from 'react';
import { Link } from 'react-router-dom';
import api, { cleanParams } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { label, STATUSES } from '../../lib/format';
import IncidentTable from '../../components/IncidentTable';
import { ErrorBox, Loading, PageHeader, Pagination } from '../../components/ui';

/** The incidents the employee reported. The backend only returns their own, whatever the filter. */
export default function MyIncidentsPage() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

  const { data, error, loading } = useLoad(async () => {
    const res = await api.get('/api/incidents', { params: cleanParams({ status, page, size: 10 }) });
    return res.data;
  }, [status, page]);

  return (
    <>
      <PageHeader title="My incidents" subtitle="Problems you have reported and where they stand.">
        <Link to="/report" className="btn btn-primary">Report an incident</Link>
      </PageHeader>

      <div className="filters">
        <label className="field field-inline">
          <span className="field-label">Status</span>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </label>
      </div>

      <ErrorBox error={error} />
      {loading && !data ? <Loading what="incidents" /> : data && (
        <div className="card flush">
          <IncidentTable incidents={data.content} hide={['reporter', 'service']}
            emptyText={status ? 'No incidents with this status.' : 'You have not reported any incidents yet.'} />
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
        </div>
      )}
    </>
  );
}
