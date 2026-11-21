import { useState } from 'react';
import { Link } from 'react-router-dom';
import api, { errorMessage } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { LEVELS } from '../../lib/format';
import { ErrorBox, Field, Loading, PageHeader, PriorityBadge, StatusBadge } from '../../components/ui';

const EMPTY = { title: '', description: '', serviceId: '', categoryId: '', impact: 2, urgency: 2, affectedUsers: 1 };

export default function ReportIncidentPage() {
  const [form, setForm] = useState(EMPTY);
  const [suggestions, setSuggestions] = useState(null);
  const [suggesting, setSuggesting] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState(null);

  const lists = useLoad(async () => {
    const [services, categories] = await Promise.all([api.get('/api/services'), api.get('/api/categories')]);
    return { services: services.data, categories: categories.data };
  }, []);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const suggest = async () => {
    setSuggesting(true);
    setError(null);
    try {
      const { data } = await api.post('/api/incidents/suggest-category', { title: form.title, description: form.description });
      setSuggestions(data);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSuggesting(false);
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post('/api/incidents', {
        title: form.title.trim(),
        description: form.description.trim(),
        serviceId: Number(form.serviceId),
        categoryId: Number(form.categoryId),
        impact: Number(form.impact),
        urgency: Number(form.urgency),
        affectedUsers: Number(form.affectedUsers),
      });
      setCreated(data);
      window.scrollTo(0, 0);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const reset = () => {
    setForm(EMPTY);
    setSuggestions(null);
    setCreated(null);
  };

  if (created) {
    const { incident, possibleDuplicates } = created;
    return (
      <>
        <PageHeader title="Incident reported" />
        <div className="card">
          <div className="created-head">
            <span className="mono big">{incident.incidentNumber}</span>
            <PriorityBadge priority={incident.priority} />
            <StatusBadge status={incident.status} />
          </div>
          <p><strong>{incident.title}</strong></p>
          <p>
            {incident.assignee
              ? <>Assigned to <strong>{incident.assignee.name}</strong>. You will be notified as it progresses.</>
              : <>It is waiting for a technician. An administrator has been told.</>}
          </p>
          {possibleDuplicates?.length > 0 && (
            <div className="alert alert-warn">
              <strong>Similar incidents are already open.</strong> Yours was still recorded; staff may link them.
              <ul className="dup-list">
                {possibleDuplicates.map((d) => (
                  <li key={d.number}>
                    <span className="mono">{d.number}</span>{' '}
                    {d.title ?? <span className="muted">(reported by someone else, details hidden)</span>}{' '}
                    <span className="muted">({Math.round(d.score * 100)}% similar)</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="button-row">
            <Link className="btn btn-primary" to={`/incidents/${incident.id}`}>View incident</Link>
            <button type="button" className="btn" onClick={reset}>Report another</button>
          </div>
        </div>
      </>
    );
  }

  if (lists.loading && !lists.data) return <Loading what="the form" />;

  return (
    <>
      <PageHeader title="Report an incident" subtitle="Describe the problem. The system sets the priority and assigns a technician." />
      <ErrorBox error={lists.error || error} />
      {lists.data && (
        <form className="card form-grid" onSubmit={submit}>
          <Field label="Title" wide>
            <input value={form.title} onChange={set('title')} required maxLength={200} placeholder="For example: Payment application outage" />
          </Field>
          <Field label="Description" wide>
            <textarea value={form.description} onChange={set('description')} required rows={5} maxLength={5000}
              placeholder="What happened, when it started, what you already tried." />
          </Field>
          <Field label="Service">
            <select value={form.serviceId} onChange={set('serviceId')} required>
              <option value="">Choose a service</option>
              {lists.data.services.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Category">
            <div className="input-with-button">
              <select value={form.categoryId} onChange={set('categoryId')} required>
                <option value="">Choose a category</option>
                {lists.data.categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
              <button type="button" className="btn" onClick={suggest}
                disabled={suggesting || (!form.title.trim() && !form.description.trim())}>
                {suggesting ? 'Thinking…' : 'Suggest'}
              </button>
            </div>
            {suggestions && (
              suggestions.length === 0
                ? <span className="field-hint">No suggestion; add more detail or choose yourself.</span>
                : (
                  <span className="suggestions">
                    Suggested:
                    {suggestions.map((s) => (
                      <button key={s.categoryId} type="button"
                        className={`chip${String(s.categoryId) === String(form.categoryId) ? ' chip-active' : ''}`}
                        onClick={() => setForm({ ...form, categoryId: String(s.categoryId) })}>
                        {s.name}
                      </button>
                    ))}
                  </span>
                )
            )}
          </Field>
          <Field label="Impact" hint="How badly the work is affected.">
            <select value={form.impact} onChange={set('impact')}>
              {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
            </select>
          </Field>
          <Field label="Urgency" hint="How soon it must be fixed.">
            <select value={form.urgency} onChange={set('urgency')}>
              {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
            </select>
          </Field>
          <Field label="Affected users">
            <input type="number" min={1} value={form.affectedUsers} onChange={set('affectedUsers')} required />
          </Field>
          <div className="form-actions field-wide">
            <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Submitting…' : 'Submit incident'}</button>
          </div>
        </form>
      )}
    </>
  );
}
