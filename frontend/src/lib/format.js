export const STATUSES = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'PENDING_INFO',
  'RESOLVED', 'VERIFIED', 'CLOSED', 'CANCELLED', 'DUPLICATE'];
export const PRIORITIES = ['P1', 'P2', 'P3', 'P4'];
export const SLA_STATUSES = ['ON_TRACK', 'AT_RISK', 'VIOLATED'];
export const LEVELS = [{ value: 1, label: 'Low' }, { value: 2, label: 'Medium' }, { value: 3, label: 'High' }];
export const TERMINAL = ['CLOSED', 'CANCELLED', 'DUPLICATE'];

/** IN_PROGRESS -> "In progress", TEAM_LEAD -> "Team lead". */
export function label(value) {
  if (value === null || value === undefined || value === '') return '';
  const text = String(value).replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function levelLabel(n) {
  return LEVELS.find((l) => l.value === Number(n))?.label ?? String(n);
}

export function formatDateTime(instant) {
  if (!instant) return '';
  return new Date(instant).toLocaleString(undefined, {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export function formatDate(instant) {
  if (!instant) return '';
  return new Date(instant).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

/** "in 12 min", "3 h ago": how far an instant is from now. */
export function relative(instant, now = Date.now()) {
  if (!instant) return '';
  const diff = new Date(instant).getTime() - now;
  const abs = Math.abs(diff);
  const min = Math.round(abs / 60000);
  let text;
  if (abs < 60000) text = `${Math.max(1, Math.round(abs / 1000))} s`;
  else if (min < 60) text = `${min} min`;
  else if (min < 60 * 48) text = `${Math.round(min / 6) / 10} h`;
  else text = `${Math.round(min / 1440)} days`;
  return diff >= 0 ? `in ${text}` : `${text} ago`;
}
