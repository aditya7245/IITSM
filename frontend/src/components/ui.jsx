import { label } from '../lib/format';

/** Status of the incident lifecycle. Colour groups: waiting, being worked, done, stopped. */
const STATUS_TONE = {
  NEW: 'neutral', CLASSIFIED: 'neutral', ASSIGNED: 'info', ACKNOWLEDGED: 'info', IN_PROGRESS: 'info',
  PENDING_INFO: 'warn', RESOLVED: 'good', VERIFIED: 'good', CLOSED: 'muted', CANCELLED: 'muted', DUPLICATE: 'muted',
};

export function StatusBadge({ status }) {
  if (!status) return null;
  return <span className={`badge tone-${STATUS_TONE[status] || 'neutral'}`}>{label(status)}</span>;
}

export function PriorityBadge({ priority }) {
  if (!priority) return <span className="muted">–</span>;
  return <span className={`badge priority priority-${priority.toLowerCase()}`}>{priority}</span>;
}

/** SLA state always carries an icon and a word, never colour alone. */
const SLA = {
  ON_TRACK: { icon: '✓', tone: 'good', text: 'On track' },
  AT_RISK: { icon: '!', tone: 'warn', text: 'At risk' },
  VIOLATED: { icon: '✕', tone: 'bad', text: 'Violated' },
};

export function SlaBadge({ sla }) {
  const s = SLA[sla];
  if (!s) return <span className="muted">–</span>;
  return <span className={`badge tone-${s.tone}`}><span aria-hidden="true">{s.icon}</span> {s.text}</span>;
}

export function ErrorBox({ error }) {
  if (!error) return null;
  return <div className="alert alert-error" role="alert">{error}</div>;
}

export function SuccessBox({ children }) {
  if (!children) return null;
  return <div className="alert alert-success" role="status">{children}</div>;
}

export function Pagination({ page, totalPages, totalElements, onChange }) {
  if (totalPages === undefined) return null;
  return (
    <div className="pagination">
      <span className="muted">{totalElements} result{totalElements === 1 ? '' : 's'}</span>
      <div className="pagination-buttons">
        <button type="button" className="btn btn-small" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</button>
        <span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
        <button type="button" className="btn btn-small" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
      </div>
    </div>
  );
}

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {children && <div className="page-header-actions">{children}</div>}
    </div>
  );
}

export function Loading({ what = '' }) {
  return <div className="loading">Loading{what ? ` ${what}` : ''}…</div>;
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

export function Field({ label: text, hint, children, wide }) {
  return (
    <label className={`field${wide ? ' field-wide' : ''}`}>
      <span className="field-label">{text}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}
