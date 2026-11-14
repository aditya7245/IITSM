import { useState } from 'react';
import api, { errorMessage } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { Empty, ErrorBox, Field, Loading, PageHeader, SuccessBox } from '../../components/ui';

const EMPTY = { name: '', description: '', keywords: '', requiredSkillId: '', active: true };

/** Incident categories. Keywords drive the category suggestion; the required skill drives skill-based assignment. */
export default function CategoriesPage() {
  const [editing, setEditing] = useState(null);
  const [flash, setFlash] = useState(null);
  const data = useLoad(async () => {
    const [categories, skills] = await Promise.all([api.get('/api/admin/categories'), api.get('/api/skills')]);
    return { categories: categories.data, skills: skills.data };
  }, []);

  return (
    <>
      <PageHeader title="Categories" subtitle="Keywords power the “Suggest” button on the report form. The required skill decides who can be assigned.">
        <button type="button" className="btn btn-primary" onClick={() => { setEditing('new'); setFlash(null); }}>New category</button>
      </PageHeader>
      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={data.error} />

      {editing && data.data && (
        <CategoryForm key={editing === 'new' ? 'new' : editing.id} category={editing === 'new' ? null : editing} skills={data.data.skills}
          onCancel={() => setEditing(null)}
          onDone={(msg) => { setFlash(msg); setEditing(null); data.reload(); }} />
      )}

      {data.loading && !data.data ? <Loading what="categories" /> : data.data && (
        <div className="card flush">
          {data.data.categories.length === 0 ? <Empty>No categories.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Name</th><th>Keywords</th><th>Required skill</th><th>Status</th><th /></tr></thead>
                <tbody>
                  {data.data.categories.map((c) => (
                    <tr key={c.id} className={c.active ? '' : 'row-muted'}>
                      <td><strong>{c.name}</strong>{c.description && <div className="muted small">{c.description}</div>}</td>
                      <td className="keywords">{c.keywords ? c.keywords.split(',').map((k) => k.trim()).filter(Boolean).map((k) => <span key={k} className="tag">{k}</span>) : <span className="muted">–</span>}</td>
                      <td>{c.requiredSkill?.name || <span className="muted">Any</span>}</td>
                      <td>{c.active ? <span className="badge tone-good">Active</span> : <span className="badge tone-muted">Inactive</span>}</td>
                      <td className="right"><button type="button" className="btn btn-small" onClick={() => { setEditing(c); setFlash(null); }}>Edit</button></td>
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

function CategoryForm({ category, skills, onCancel, onDone }) {
  const [form, setForm] = useState(category ? {
    name: category.name, description: category.description || '', keywords: category.keywords || '',
    requiredSkillId: category.requiredSkill?.id || '', active: category.active,
  } : EMPTY);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (f) => (e) => setForm({ ...form, [f]: e.target.value });

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = {
      name: form.name.trim(), description: form.description.trim() || null, keywords: form.keywords.trim() || null,
      requiredSkillId: form.requiredSkillId ? Number(form.requiredSkillId) : null, active: form.active,
    };
    try {
      if (category) await api.put(`/api/admin/categories/${category.id}`, body);
      else await api.post('/api/admin/categories', body);
      onDone(`${category ? 'Saved' : 'Created'} ${body.name}.`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form className="card form-grid" onSubmit={save}>
      <h2 className="field-wide">{category ? `Edit ${category.name}` : 'New category'}</h2>
      <ErrorBox error={error} />
      <Field label="Name"><input value={form.name} onChange={set('name')} required maxLength={80} /></Field>
      <Field label="Required skill" hint="Skill-based assignment only picks technicians with this skill.">
        <select value={form.requiredSkillId} onChange={set('requiredSkillId')}>
          <option value="">Any skill</option>
          {skills.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Description" wide><input value={form.description} onChange={set('description')} maxLength={255} /></Field>
      <Field label="Keywords" wide hint="Comma separated, for example: printer, toner, paper jam">
        <input value={form.keywords} onChange={set('keywords')} />
      </Field>
      <Field label="Status">
        <label className="switch">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span className="switch-track" aria-hidden="true" />
          <span>{form.active ? 'Active' : 'Inactive'}</span>
        </label>
      </Field>
      <div className="form-actions field-wide">
        <button type="submit" className="btn btn-primary" disabled={busy}>{category ? 'Save' : 'Create category'}</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
