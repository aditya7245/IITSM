import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api, { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import useLoad from '../lib/useLoad';
import { findIncidentByNumber } from '../api/lists';
import { formatDateTime, label, levelLabel, PRIORITIES, relative, TERMINAL } from '../lib/format';
import { ErrorBox, Loading, PriorityBadge, SlaBadge, StatusBadge, SuccessBox } from '../components/ui';

const REFRESH_MS = 20000;   // keeps the SLA state and status fresh while the page is open

/**
 * One incident, for every role. Which actions appear depends on who is looking (reporter, assignee, admin)
 * and on the status, following the backend's state machine, so a button is only shown when it can work.
 */
export default function IncidentDetailPage() {
  const { id } = useParams();
  const { user, group } = useAuth();
  const [flash, setFlash] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState(null);   // which inline form is open

  const { data, error, loading, reload } = useLoad(async () => {
    const [incident, comments, history] = await Promise.all([
      api.get(`/api/incidents/${id}`),
      api.get(`/api/incidents/${id}/comments`),
      api.get(`/api/incidents/${id}/history`),
    ]);
    let feedback = null;
    if (incident.data.status === 'CLOSED') {
      try { feedback = (await api.get(`/api/incidents/${id}/feedback`)).data; } catch { /* closed without feedback */ }
    }
    return { incident: incident.data, comments: comments.data, history: history.data, feedback };
  }, [id]);

  useEffect(() => {
    const timer = setInterval(() => { if (!busy && !mode) reload(); }, REFRESH_MS);
    return () => clearInterval(timer);
  }, [reload, busy, mode]);

  useEffect(() => { setFlash(null); setActionError(null); setMode(null); }, [id]);

  if (loading && !data) return <Loading what="the incident" />;
  if (error && !data) return <><ErrorBox error={error} /><Link to="/">Back</Link></>;
  if (!data) return null;

  const { incident: inc, comments, history, feedback } = data;
  const s = inc.status;
  const isReporter = user.id === inc.reporter?.id;
  const isAssignee = user.id === inc.assignee?.id;
  const isAdmin = group === 'admin';
  const open = !TERMINAL.includes(s);

  /** Runs one action, then shows its result and reloads everything. */
  const act = async (call, message) => {
    setBusy(true);
    setActionError(null);
    setFlash(null);
    try {
      await call();
      await reload();
      setMode(null);
      setFlash(message);
      return true;
    } catch (e) {
      setActionError(errorMessage(e));
      return false;
    } finally {
      setBusy(false);
    }
  };
  const post = (path, body) => () => api.post(`/api/incidents/${inc.id}/${path}`, body);

  const buttons = [];
  const toggle = (name) => () => { setActionError(null); setMode(mode === name ? null : name); };
  if (isAssignee && s === 'ASSIGNED') buttons.push(<button key="ack" className="btn btn-primary" disabled={busy} onClick={() => act(post('acknowledge'), 'Incident acknowledged.')}>Acknowledge</button>);
  if (isAssignee && s === 'ACKNOWLEDGED') buttons.push(<button key="start" className="btn btn-primary" disabled={busy} onClick={() => act(post('start'), 'Work started.')}>Start work</button>);
  if (isAssignee && s === 'IN_PROGRESS') {
    buttons.push(<button key="resolve" className="btn btn-primary" onClick={toggle('resolve')}>Resolve</button>);
    buttons.push(<button key="info" className="btn" onClick={toggle('info')}>Request information</button>);
  }
  if (isReporter && s === 'RESOLVED') {
    buttons.push(<button key="verify" className="btn btn-primary" disabled={busy} onClick={() => act(post('verify'), 'Thank you. The fix is confirmed; please rate the service below.')}>Confirm it is fixed</button>);
    buttons.push(<button key="reopen" className="btn" onClick={toggle('reopen')}>Still broken: reopen</button>);
  }
  if (isAdmin && ['CLASSIFIED', 'ASSIGNED'].includes(s)) buttons.push(<button key="reassign" className="btn" onClick={toggle('reassign')}>{inc.assignee ? 'Reassign' : 'Assign'}</button>);
  if (isAdmin && open && !['RESOLVED', 'VERIFIED'].includes(s)) buttons.push(<button key="priority" className="btn" onClick={toggle('priority')}>Change priority</button>);
  if (isAdmin && ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED'].includes(s)) buttons.push(<button key="dup" className="btn" onClick={toggle('duplicate')}>Mark duplicate</button>);
  if (isAdmin && s === 'VERIFIED') buttons.push(<button key="close" className="btn" disabled={busy} onClick={() => act(post('close'), 'Incident closed.')}>Close without feedback</button>);
  if ((isReporter || isAdmin) && ['NEW', 'CLASSIFIED', 'ASSIGNED'].includes(s)) buttons.push(<button key="cancel" className="btn btn-danger-outline" onClick={toggle('cancel')}>Cancel incident</button>);

  return (
    <>
      <div className="detail-head">
        <div className="detail-title">
          <span className="mono muted">{inc.incidentNumber}</span>
          <h1>{inc.title}</h1>
          <div className="badge-row">
            <PriorityBadge priority={inc.priority} />
            <StatusBadge status={s} />
            {open && <SlaBadge sla={inc.slaStatus} />}
            {inc.escalationLevel > 0 && <span className="badge tone-bad">Escalated · level {inc.escalationLevel}</span>}
            {inc.slaPausedAt && <span className="badge tone-warn">SLA paused</span>}
          </div>
        </div>
      </div>

      <SuccessBox>{flash}</SuccessBox>
      <ErrorBox error={actionError} />
      {error && <ErrorBox error={`Could not refresh: ${error}`} />}

      {s === 'PENDING_INFO' && isReporter && (
        <div className="alert alert-warn">The technician needs more information from you. Please answer in the conversation below.</div>
      )}

      {buttons.length > 0 && <div className="action-bar">{buttons}</div>}

      {mode === 'resolve' && (
        <TextForm title="Resolve the incident" fieldLabel="What was done to fix it" submitText="Mark resolved" busy={busy}
          onCancel={() => setMode(null)}
          onSubmit={(text) => act(post('resolve', { resolution: text }), 'Incident resolved. The reporter has been asked to confirm.')} />
      )}
      {mode === 'info' && (
        <TextForm title="Ask the reporter for information" fieldLabel="Your question" submitText="Send request" busy={busy}
          hint="The SLA clock pauses until the reporter answers."
          onCancel={() => setMode(null)}
          onSubmit={(text) => act(() => api.post(`/api/incidents/${inc.id}/comments`, { type: 'INFO_REQUEST', body: text }), 'Information requested. The SLA clock is paused.')} />
      )}
      {mode === 'reopen' && (
        <TextForm title="Reopen the incident" fieldLabel="What is still wrong" submitText="Reopen" busy={busy}
          onCancel={() => setMode(null)}
          onSubmit={(text) => act(post('reopen', { reason: text }), 'Incident reopened and sent back to the technician.')} />
      )}
      {mode === 'cancel' && (
        <div className="card action-form">
          <h3>Cancel this incident?</h3>
          <p className="muted">Use this if the problem went away or was reported by mistake. It cannot be undone.</p>
          <div className="button-row">
            <button type="button" className="btn btn-danger" disabled={busy} onClick={() => act(post('cancel'), 'Incident cancelled.')}>Yes, cancel it</button>
            <button type="button" className="btn" onClick={() => setMode(null)}>Keep it</button>
          </div>
        </div>
      )}
      {mode === 'reassign' && <ReassignForm incident={inc} busy={busy} onCancel={() => setMode(null)}
        onSubmit={(techId, name) => act(post('reassign', { technicianId: techId }), `Assigned to ${name}.`)} />}
      {mode === 'priority' && <PriorityForm current={inc.priority} busy={busy} onCancel={() => setMode(null)}
        onSubmit={(p) => act(post('priority', { priority: p }), `Priority changed to ${p}. SLA deadlines were recalculated.`)} />}
      {mode === 'duplicate' && <DuplicateForm incident={inc} busy={busy} onCancel={() => setMode(null)}
        onSubmit={(original) => act(post('duplicate', { originalId: original.id }), `Marked as a duplicate of ${original.incidentNumber}.`)} />}

      {isReporter && s === 'VERIFIED' && (
        <FeedbackForm busy={busy}
          onSubmit={(rating, comments) => act(post('feedback', { rating, comments }), 'Thank you for your feedback. The incident is now closed.')} />
      )}

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card">
            <h2>Description</h2>
            <p className="prewrap">{inc.description}</p>
            {inc.resolution && (
              <>
                <h3>Resolution</h3>
                <p className="prewrap">{inc.resolution}</p>
              </>
            )}
            {inc.duplicateOfNumber && <p>Duplicate of <span className="mono">{inc.duplicateOfNumber}</span>.</p>}
            {feedback && (
              <>
                <h3>Feedback</h3>
                <p><Stars value={feedback.rating} /> {feedback.comments && <span className="prewrap">“{feedback.comments}”</span>}</p>
              </>
            )}
          </section>

          <Conversation incident={inc} comments={comments} isReporter={isReporter} canNote={open && (isAssignee || isAdmin)}
            canReply={open && isReporter} busy={busy}
            onSend={(type, text) => act(() => api.post(`/api/incidents/${inc.id}/comments`, { type, body: text }),
              type === 'INFO_RESPONSE' ? 'Your answer was sent.' : 'Note added.')} />

          <section className="card">
            <h2>History</h2>
            {history.length === 0 ? <p className="muted">No history yet.</p> : (
              <ol className="timeline">
                {history.map((h) => (
                  <li key={h.id}>
                    <div className="timeline-top">
                      <strong>{label(h.action)}</strong>
                      {h.previousState && h.newState && h.previousState !== h.newState && (
                        <span className="muted"> · {label(h.previousState)} → {label(h.newState)}</span>
                      )}
                    </div>
                    <div>{h.description}</div>
                    <div className="muted small">{h.user?.name || 'System'} · {formatDateTime(h.createdAt)}</div>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>

        <aside className="detail-side card">
          <h2>Details</h2>
          <dl className="facts">
            <dt>Reporter</dt><dd>{inc.reporter?.name}</dd>
            <dt>Assignee</dt><dd>{inc.assignee?.name || <span className="muted">Not assigned</span>}</dd>
            <dt>Service</dt><dd>{inc.service?.name}</dd>
            <dt>Category</dt><dd>{inc.category?.name}</dd>
            <dt>Impact</dt><dd>{levelLabel(inc.impact)}</dd>
            <dt>Urgency</dt><dd>{levelLabel(inc.urgency)}</dd>
            <dt>Affected users</dt><dd>{inc.affectedUsers}</dd>
            {inc.priorityScore !== null && <><dt>Priority score</dt><dd>{inc.priorityScore}</dd></>}
          </dl>
          <h3>SLA</h3>
          <dl className="facts">
            <Deadline name="Respond by" at={inc.responseDeadline} done={inc.respondedAt} open={open} />
            <Deadline name="Resolve by" at={inc.resolutionDeadline} done={inc.resolvedAt} open={open} />
            {inc.slaPausedAt && <><dt>Paused since</dt><dd>{formatDateTime(inc.slaPausedAt)}</dd></>}
          </dl>
          <h3>Dates</h3>
          <dl className="facts">
            <dt>Reported</dt><dd>{formatDateTime(inc.createdAt)}</dd>
            {inc.respondedAt && <><dt>Acknowledged</dt><dd>{formatDateTime(inc.respondedAt)}</dd></>}
            {inc.resolvedAt && <><dt>Resolved</dt><dd>{formatDateTime(inc.resolvedAt)}</dd></>}
            {inc.closedAt && <><dt>Closed</dt><dd>{formatDateTime(inc.closedAt)}</dd></>}
            <dt>Last change</dt><dd>{formatDateTime(inc.updatedAt)}</dd>
          </dl>
        </aside>
      </div>
    </>
  );
}

function Deadline({ name, at, done, open }) {
  if (!at) return null;
  const late = done ? new Date(done) > new Date(at) : open && new Date(at) < new Date();
  return (
    <>
      <dt>{name}</dt>
      <dd>
        {formatDateTime(at)}
        <div className={`small ${late ? 'text-bad' : 'muted'}`}>
          {done ? (late ? 'Met late' : 'Met on time') : open ? relative(at) : ''}
        </div>
      </dd>
    </>
  );
}

function TextForm({ title, fieldLabel, hint, submitText, busy, onSubmit, onCancel }) {
  const [text, setText] = useState('');
  return (
    <form className="card action-form" onSubmit={(e) => { e.preventDefault(); onSubmit(text.trim()); }}>
      <h3>{title}</h3>
      <label className="field">
        <span className="field-label">{fieldLabel}</span>
        <textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} required autoFocus />
        {hint && <span className="field-hint">{hint}</span>}
      </label>
      <div className="button-row">
        <button type="submit" className="btn btn-primary" disabled={busy || !text.trim()}>{submitText}</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function ReassignForm({ incident, busy, onSubmit, onCancel }) {
  const [choice, setChoice] = useState('');
  const recs = useLoad(async () => (await api.get(`/api/incidents/${incident.id}/recommendations`)).data, [incident.id]);
  const list = recs.data || [];
  return (
    <form className="card action-form" onSubmit={(e) => {
      e.preventDefault();
      const tech = list.find((t) => String(t.id) === choice);
      if (tech) onSubmit(tech.id, tech.fullName);
    }}>
      <h3>{incident.assignee ? 'Reassign' : 'Assign'} the incident</h3>
      <p className="muted">Best matches for this incident, best first.</p>
      <ErrorBox error={recs.error} />
      {recs.loading && !recs.data && <Loading what="recommendations" />}
      {recs.data && list.length === 0 && <p className="muted">No technician is available with the needed skill and free capacity.</p>}
      <div className="choice-list">
        {list.map((t, i) => (
          <label key={t.id} className={`choice${choice === String(t.id) ? ' selected' : ''}`}>
            <input type="radio" name="tech" value={t.id} checked={choice === String(t.id)} onChange={(e) => setChoice(e.target.value)} />
            <span>
              <strong>{t.fullName}</strong>{i === 0 && <span className="badge tone-good small-badge">Best match</span>}
              {incident.assignee?.id === t.id && <span className="badge tone-neutral small-badge">Current</span>}
              <span className="muted small block">{t.teamName || 'No team'} · {t.experienceYears} years · up to {t.maxWorkload} incidents{t.available ? '' : ' · unavailable'}</span>
            </span>
          </label>
        ))}
      </div>
      <div className="button-row">
        <button type="submit" className="btn btn-primary" disabled={busy || !choice}>Assign</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function PriorityForm({ current, busy, onSubmit, onCancel }) {
  const [p, setP] = useState(current || 'P3');
  return (
    <form className="card action-form" onSubmit={(e) => { e.preventDefault(); onSubmit(p); }}>
      <h3>Change priority</h3>
      <div className="segmented">
        {PRIORITIES.map((x) => (
          <label key={x} className={p === x ? 'selected' : ''}>
            <input type="radio" name="priority" value={x} checked={p === x} onChange={() => setP(x)} />{x}
          </label>
        ))}
      </div>
      <p className="muted small">Deadlines are recalculated from when the incident was reported.</p>
      <div className="button-row">
        <button type="submit" className="btn btn-primary" disabled={busy || p === current}>Save priority</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function DuplicateForm({ incident, busy, onSubmit, onCancel }) {
  const [number, setNumber] = useState('');
  const [error, setError] = useState(null);
  const [looking, setLooking] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setLooking(true);
    try {
      const original = await findIncidentByNumber(number);
      if (!original) setError(`No incident ${number.trim().toUpperCase()} was found.`);
      else if (original.id === incident.id) setError('An incident cannot be a duplicate of itself.');
      else onSubmit(original);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLooking(false);
    }
  };
  return (
    <form className="card action-form" onSubmit={submit}>
      <h3>Mark as a duplicate</h3>
      <label className="field">
        <span className="field-label">Number of the original incident</span>
        <input value={number} onChange={(e) => setNumber(e.target.value)} placeholder="INC1001" required autoFocus />
      </label>
      <ErrorBox error={error} />
      <div className="button-row">
        <button type="submit" className="btn btn-primary" disabled={busy || looking || !number.trim()}>Mark duplicate</button>
        <button type="button" className="btn" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function FeedbackForm({ busy, onSubmit }) {
  const [rating, setRating] = useState(0);
  const [comments, setComments] = useState('');
  return (
    <form className="card action-form highlight" onSubmit={(e) => { e.preventDefault(); onSubmit(rating, comments.trim() || null); }}>
      <h3>How was the service?</h3>
      <div className="star-input" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={rating === n} aria-label={`${n} of 5`}
            className={`star${n <= rating ? ' on' : ''}`} onClick={() => setRating(n)}>★</button>
        ))}
        <span className="muted">{rating ? `${rating} of 5` : 'Choose a rating'}</span>
      </div>
      <label className="field">
        <span className="field-label">Comments (optional)</span>
        <textarea rows={3} value={comments} onChange={(e) => setComments(e.target.value)} />
      </label>
      <button type="submit" className="btn btn-primary" disabled={busy || !rating}>Send feedback and close</button>
    </form>
  );
}

function Stars({ value }) {
  return <span className="stars" aria-label={`${value} of 5`}>{'★'.repeat(value)}<span className="stars-off">{'★'.repeat(5 - value)}</span></span>;
}

const COMMENT_LABEL = { NOTE: 'Internal note', INFO_REQUEST: 'Question for the reporter', INFO_RESPONSE: 'Reporter’s answer' };

function Conversation({ incident, comments, isReporter, canNote, canReply, busy, onSend }) {
  const [text, setText] = useState('');
  const type = canReply ? 'INFO_RESPONSE' : 'NOTE';
  const send = async (e) => {
    e.preventDefault();
    const ok = await onSend(type, text.trim());
    if (ok) setText('');
  };
  return (
    <section className="card">
      <h2>Conversation</h2>
      {!isReporter && <p className="muted small">Internal notes are hidden from the reporter.</p>}
      {comments.length === 0 && <p className="muted">No messages yet.</p>}
      <ul className="thread">
        {comments.map((c) => (
          <li key={c.id} className={`message message-${c.type.toLowerCase()}`}>
            <div className="message-top">
              <strong>{c.author?.name}</strong>
              <span className="muted small">{COMMENT_LABEL[c.type]} · {formatDateTime(c.createdAt)}</span>
            </div>
            <div className="prewrap">{c.body}</div>
          </li>
        ))}
      </ul>
      {(canReply || canNote) && (
        <form onSubmit={send} className="reply-form">
          <label className="field">
            <span className="field-label">
              {canReply ? (incident.status === 'PENDING_INFO' ? 'Your answer' : 'Add information for the technician') : 'Add an internal note'}
            </span>
            <textarea rows={3} value={text} onChange={(e) => setText(e.target.value)} />
          </label>
          <button type="submit" className="btn btn-primary" disabled={busy || !text.trim()}>
            {canReply ? 'Send' : 'Add note'}
          </button>
        </form>
      )}
    </section>
  );
}
