import { useState } from 'react';
import api, { errorMessage } from '../../api/client';
import useLoad from '../../lib/useLoad';
import { ErrorBox, Loading, PageHeader, SuccessBox } from '../../components/ui';

const STRATEGY_TEXT = {
  SKILL_BASED: 'Picks the technician with the best proficiency in the category’s required skill, then the least busy. Nobody with the skill means the incident waits for an admin.',
  LOAD_BASED: 'Picks the least busy available technician, then the most experienced. Ignores skills.',
  ROUND_ROBIN: 'Takes turns through the available technicians, skipping anyone at their limit.',
};

/** How new incidents are assigned automatically. */
export default function SettingsPage() {
  const [flash, setFlash] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const data = useLoad(async () => {
    const [strategy, leads] = await Promise.all([
      api.get('/api/admin/config/assignment-strategy'),
      api.get('/api/admin/config/team-lead-assignment'),
    ]);
    return { strategy: strategy.data, leads: leads.data };
  }, []);

  const run = async (call, message) => {
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      await call();
      setFlash(message);
      await data.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const D = data.data;

  return (
    <>
      <PageHeader title="Assignment settings" subtitle="How the system chooses a technician for each new incident." />
      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={error || data.error} />
      {data.loading && !D ? <Loading what="settings" /> : D && (
        <>
          <section className="card">
            <h2>Assignment strategy</h2>
            <div className="choice-list">
              {D.strategy.available.map((name) => (
                <label key={name} className={`choice${D.strategy.strategy === name ? ' selected' : ''}`}>
                  <input type="radio" name="strategy" checked={D.strategy.strategy === name} disabled={busy}
                    onChange={() => run(() => api.put('/api/admin/config/assignment-strategy', { strategy: name }), `Strategy switched to ${name.replace(/_/g, ' ').toLowerCase()}.`)} />
                  <span>
                    <strong>{name.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</strong>
                    <span className="muted small block">{STRATEGY_TEXT[name] || ''}</span>
                  </span>
                </label>
              ))}
            </div>
          </section>
          <section className="card">
            <h2>Team leads</h2>
            <label className="switch">
              <input type="checkbox" checked={D.leads.enabled} disabled={busy}
                onChange={(e) => run(() => api.put('/api/admin/config/team-lead-assignment', { enabled: e.target.checked }),
                  e.target.checked ? 'Team leads now take part in automatic assignment.' : 'Team leads are no longer assigned automatically.')} />
              <span className="switch-track" aria-hidden="true" />
              <span>Assign new incidents to team leads automatically</span>
            </label>
            <p className="muted small">When off, team leads are kept free for escalations. They are still offered for escalated incidents, and an admin can always assign one by hand.</p>
          </section>
        </>
      )}
    </>
  );
}
