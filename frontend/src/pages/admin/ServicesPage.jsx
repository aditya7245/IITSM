import { useState } from 'react';
import api, { errorMessage } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { Empty, ErrorBox, Field, Loading, PageHeader, SuccessBox } from '../../components/ui';

const EMPTY = { name: '', description: '', businessCriticality: 3, active: true };

/** The IT services incidents are reported against. Criticality feeds into the priority calculation. */
export default function ServicesPage() {
  const [editing, setEditing] = useState(null);   // null, 'new', or a service
  const [flash, setFlash] = useState(null);
  const services = useLoad(async () => (await api.get('/api/admin/services')).data, []);

  return (
    <>
      <PageHeader title="IT services" subtitle="Business criticality (1 to 5) raises the priority of incidents on that service.">
        <button type="button" className="btn btn-primary" onClick={() => { setEditing('new'); setFlash(null); }}>New service</button>
      </PageHeader>
      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={services.error} />

      {editing && (
        <ServiceForm key={editing === 'new' ? 'new' : editing.id} service={editing === 'new' ? null : editing}
          onCancel={() => setEditing(null)}
          onDone={(msg) => { setFlash(msg); setEditing(null); services.reload(); }} />
      )}

      {services.loading && !services.data ? <Loading what="services" /> : services.data && (
        <div className="card flush">
          {services.data.length === 0 ? <Empty>No services.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Name</th><th>Description</th><th>Criticality</th><th>Status</th><th /></tr></thead>
                <tbody>
                  {services.data.map((s) => (
                    <tr key={s.id} className={s.active ? '' : 'row-muted'}>
                      <td><strong>{s.name}</strong></td>
                      <td>{s.description || <span className="muted">–</span>}</td>
                      <td><span className="dots" aria-label={`${s.businessCriticality} of 5`}>{'●'.repeat(s.businessCriticality)}<span className="dots-off">{'●'.repeat(5 - s.businessCriticality)}</span></span> {s.businessCriticality}</td>
                      <td>{s.active ? <span className="badge tone-good">Active</span> : <span className="badge tone-muted">Inactive</span>}</td>
                      <td className="right"><button type="button" className="btn btn-small" onClick={() => { setEditing(s); setFlash(null); }}>Edit</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  );
}

function ServiceForm({ service, onCancel, onDone }) {
  const [form, setForm] = useState(service ? { ...EMPTY, ...service, description: service.description || '' } : EMPTY);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = { name: form.name.trim(), description: form.description.trim() || null, businessCriticality: Number(form.businessCriticality), active: form.active };
    try {
      if (service) await api.put(`/api/admin/services/${service.id}`, body);
      else await api.post('/api/admin/services', body);
      onDone(`${service ? 'Saved' : 'Created'} ${body.name}.`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form className="card form-grid" onSubmit={save}>
      <h2 className="field-wide">{service ? `Edit ${service.name}` : 'New service'}</h2>
      <ErrorBox error={error} />
      <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required maxLength={120} /></Field>
      <Field label="Business criticality" hint="1 = low, 5 = business critical.">
        <select value={form.businessCriticality} onChange={(e) => setForm({ ...form, businessCriticality: e.target.value })}>
          {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
      </Field>
      <Field label="Description" wide><input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} maxLength={255} /></Field>
      <Field label="Status">
        <label className="switch">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span className="switch-track" aria-hidden="true" />
          <span>{form.active ? 'Active: shown in the report form' : 'Inactive: hidden from the report form'}</span>
        </label>
      </Field>
      <div className="form-actions field-wide">
        <button type="submit" className="btn btn-primary" disabled={busy}>{service ? 'Save' : 'Create service'}</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
