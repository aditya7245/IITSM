import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api, { cleanParams } from '../../api/client';
import { fetchTechnicians } from '../../api/lists';
import useLoad from '../../lib/useLoad';
import { label, PRIORITIES, SLA_STATUSES, STATUSES } from '../../lib/format';
import IncidentTable from '../../components/IncidentTable';
import { ErrorBox, Loading, PageHeader, Pagination } from '../../components/ui';

const FILTERS = ['incidentNumber', 'status', 'priority', 'categoryId', 'serviceId', 'technicianId', 'slaStatus',
  'createdFrom', 'createdTo'];
const SORTS = [
  { value: 'createdAt,desc', text: 'Newest first' },
  { value: 'createdAt,asc', text: 'Oldest first' },
  { value: 'resolutionDeadline,asc', text: 'Resolve-by date, soonest first' },
];

/** Every incident, with every filter the backend supports. Filters live in the address so Back keeps them. */
export default function IncidentSearchPage() {
  const [params, setParams] = useSearchParams();
  const filters = Object.fromEntries(FILTERS.map((f) => [f, params.get(f) || '']));
  const page = Number(params.get('page') || 0);
  const sort = params.get('sort') || SORTS[0].value;
  const [number, setNumber] = useState(filters.incidentNumber);

  const lookups = useLoad(async () => {
    const [services, categories, technicians] = await Promise.all([
      api.get('/api/admin/services'), api.get('/api/admin/categories'), fetchTechnicians(),
    ]);
    return { services: services.data, categories: categories.data, technicians };
  }, []);

  const key = params.toString();
  const list = useLoad(async () => (
    await api.get('/api/incidents', { params: cleanParams({ ...filters, page, size: 20, sort }) })
  ).data, [key]);

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null ? next.delete(k) : next.set(k, v)));
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  // Typing a number waits for a pause before searching.
  useEffect(() => {
    if (number === filters.incidentNumber) return undefined;
    const t = setTimeout(() => update({ incidentNumber: number.trim() }), 400);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [number]);

  const select = (name) => (e) => update({ [name]: e.target.value });
  const active = FILTERS.some((f) => filters[f]);
  const L = lookups.data;

  return (
    <>
      <PageHeader title="Incidents" subtitle="Search and manage every incident." />
      <ErrorBox error={lookups.error} />
      <div className="card filter-panel">
        <label className="field">
          <span className="field-label">Number</span>
          <input value={number} onChange={(e) => setNumber(e.target.value)} placeholder="INC10…" />
        </label>
        <label className="field">
          <span className="field-label">Status</span>
          <select value={filters.status} onChange={select('status')}>
            <option value="">Any</option>
            {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Priority</span>
          <select value={filters.priority} onChange={select('priority')}>
            <option value="">Any</option>
            {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">SLA</span>
          <select value={filters.slaStatus} onChange={select('slaStatus')}>
            <option value="">Any</option>
            {SLA_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Service</span>
          <select value={filters.serviceId} onChange={select('serviceId')}>
            <option value="">Any</option>
            {L?.services.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Category</span>
          <select value={filters.categoryId} onChange={select('categoryId')}>
            <option value="">Any</option>
            {L?.categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Technician</span>
          <select value={filters.technicianId} onChange={select('technicianId')}>
            <option value="">Anyone</option>
            {L?.technicians.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Created from</span>
          <input type="date" value={filters.createdFrom} onChange={select('createdFrom')} />
        </label>
        <label className="field">
          <span className="field-label">Created to</span>
          <input type="date" value={filters.createdTo} onChange={select('createdTo')} />
        </label>
        <label className="field">
          <span className="field-label">Sort</span>
          <select value={sort} onChange={(e) => update({ sort: e.target.value === SORTS[0].value ? '' : e.target.value })}>
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.text}</option>)}
          </select>
        </label>
        <div className="filter-actions">
          <button type="button" className="btn btn-small" disabled={!active}
            onClick={() => { setNumber(''); setParams(new URLSearchParams(), { replace: true }); }}>Clear filters</button>
        </div>
      </div>

      <ErrorBox error={list.error} />
      {list.loading && !list.data ? <Loading what="incidents" /> : list.data && (
        <div className={`card flush${list.loading ? ' is-loading' : ''}`}>
          <IncidentTable incidents={list.data.content} emptyText="No incident matches these filters." />
          <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements}
            onChange={(p) => update({ page: String(p) })} />
        </div>
      )}
    </>
  );
}
