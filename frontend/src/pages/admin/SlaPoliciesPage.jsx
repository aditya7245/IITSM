import { useState } from 'react';
import api, { errorMessage } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { PRIORITIES } from '../../lib/format';
import { ErrorBox, Field, Loading, PageHeader, PriorityBadge, SuccessBox } from '../../components/ui';

function minutes(m) {
  if (m < 60) return `${m} min`;
  if (m % 60 === 0) return `${m / 60} h`;
  return `${Math.floor(m / 60)} h ${m % 60} min`;
}

/**
 * Response and resolution times per priority. A policy without a service is the default for that priority;
 * a policy for one service overrides it. Defaults can be changed but not deleted.
 */
export default function SlaPoliciesPage() {
  const [flash, setFlash] = useState(null);
  const [error, setError] = useState(null);
  const [editId, setEditId] = useState(null);
  const [deleteId, setDeleteId] = useState(null);
  const [creating, setCreating] = useState(false);

  const data = useLoad(async () => {
    const [policies, services] = await Promise.all([api.get('/api/admin/sla-policies'), api.get('/api/admin/services')]);
    return { policies: policies.data, services: services.data };
  }, []);

  const run = async (call, message) => {
    setError(null);
    setFlash(null);
    try {
      await call();
      setFlash(message);
      setEditId(null);
      setDeleteId(null);
      setCreating(false);
      data.reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const D = data.data;
  const sorted = D ? [...D.policies].sort((a, b) => a.priority.localeCompare(b.priority) || (a.service ? 1 : 0) - (b.service ? 1 : 0)) : [];

  return (
    <>
      <PageHeader title="SLA policies" subtitle="How fast incidents must be acknowledged and resolved, per priority.">
        <button type="button" className="btn btn-primary" onClick={() => { setCreating(true); setFlash(null); }}>New service override</button>
      </PageHeader>
      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={error || data.error} />

      {creating && D && <CreatePolicyForm services={D.services} onCancel={() => setCreating(false)}
        onSubmit={(body) => run(() => api.post('/api/admin/sla-policies', body), 'Policy created.')} />}

      {data.loading && !D ? <Loading what="policies" /> : D && (
        <div className="card flush">
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Priority</th><th>Applies to</th><th>Respond within</th><th>Resolve within</th><th /></tr></thead>
              <tbody>
                {sorted.map((p) => (editId === p.id
                  ? <EditRow key={p.id} policy={p} onCancel={() => setEditId(null)}
                      onSave={(body) => run(() => api.put(`/api/admin/sla-policies/${p.id}`, body), `${p.priority} times saved.`)} />
                  : (
                    <tr key={p.id}>
                      <td><PriorityBadge priority={p.priority} /></td>
                      <td>{p.service ? p.service.name : <strong>Default (all services)</strong>}</td>
                      <td>{minutes(p.responseMinutes)}</td>
                      <td>{minutes(p.resolutionMinutes)}</td>
                      <td className="right nowrap">
                        <button type="button" className="btn btn-small" onClick={() => { setEditId(p.id); setDeleteId(null); }}>Edit</button>
                        {p.service && (deleteId === p.id
                          ? <>
                              {' '}<button type="button" className="btn btn-small btn-danger" onClick={() => run(() => api.delete(`/api/admin/sla-policies/${p.id}`), 'Override deleted.')}>Confirm delete</button>
                              {' '}<button type="button" className="btn btn-small" onClick={() => setDeleteId(null)}>Keep</button>
                            </>
                          : <>{' '}<button type="button" className="btn btn-small btn-danger-outline" onClick={() => setDeleteId(p.id)}>Delete</button></>)}
                      </td>
                    </tr>
                  )))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}

function EditRow({ policy, onSave, onCancel }) {
  const [r, setR] = useState(policy.responseMinutes);
  const [s, setS] = useState(policy.resolutionMinutes);
  return (
    <tr className="row-editing">
      <td><PriorityBadge priority={policy.priority} /></td>
      <td>{policy.service ? policy.service.name : <strong>Default (all services)</strong>}</td>
      <td><input type="number" min={1} value={r} onChange={(e) => setR(e.target.value)} aria-label="Response minutes" className="input-small" /> min</td>
      <td><input type="number" min={1} value={s} onChange={(e) => setS(e.target.value)} aria-label="Resolution minutes" className="input-small" /> min</td>
      <td className="right nowrap">
        <button type="button" className="btn btn-small btn-primary" onClick={() => onSave({ responseMinutes: Number(r), resolutionMinutes: Number(s) })}>Save</button>{' '}
        <button type="button" className="btn btn-small" onClick={onCancel}>Cancel</button>
      </td>
    </tr>
  );
}

function CreatePolicyForm({ services, onSubmit, onCancel }) {
  const [form, setForm] = useState({ priority: 'P1', serviceId: '', responseMinutes: 15, resolutionMinutes: 60 });
  const set = (f) => (e) => setForm({ ...form, [f]: e.target.value });
  return (
    <form className="card form-grid" onSubmit={(e) => {
      e.preventDefault();
      onSubmit({ priority: form.priority, serviceId: Number(form.serviceId), responseMinutes: Number(form.responseMinutes), resolutionMinutes: Number(form.resolutionMinutes) });
    }}>
      <h2 className="field-wide">New service override</h2>
      <Field label="Priority">
        <select value={form.priority} onChange={set('priority')}>{PRIORITIES.map((p) => <option key={p}>{p}</option>)}</select>
      </Field>
      <Field label="Service">
        <select value={form.serviceId} onChange={set('serviceId')} required>
          <option value="">Choose a service</option>
          {services.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Respond within (minutes)"><input type="number" min={1} value={form.responseMinutes} onChange={set('responseMinutes')} required /></Field>
      <Field label="Resolve within (minutes)"><input type="number" min={1} value={form.resolutionMinutes} onChange={set('resolutionMinutes')} required /></Field>
      <div className="form-actions field-wide">
        <button type="submit" className="btn btn-primary">Create</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
