import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import api, { errorMessage } from '../api/client';
import { label, relative } from '../lib/format';

const POLL_MS = 15000;

/** The bell in the top bar: polls the unread count every 15 seconds and lists recent notifications. */
export default function NotificationBell() {
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [error, setError] = useState(null);
  const ref = useRef(null);
  const navigate = useNavigate();
  const location = useLocation();

  const refreshCount = useCallback(async () => {
    try {
      const { data } = await api.get('/api/notifications/unread-count');
      setCount(data.count);
    } catch { /* the next poll tries again */ }
  }, []);

  const loadItems = useCallback(async () => {
    try {
      setError(null);
      const { data } = await api.get('/api/notifications', { params: { size: 15 } });
      setItems(data.content);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    refreshCount();
    const timer = setInterval(refreshCount, POLL_MS);
    return () => clearInterval(timer);
  }, [refreshCount]);

  // A page change often follows an action, so check again straight away.
  useEffect(() => { refreshCount(); }, [location.pathname, refreshCount]);

  useEffect(() => {
    if (!open) return undefined;
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
  }, [open]);

  const toggle = () => {
    if (!open) loadItems();
    setOpen(!open);
  };

  const openItem = async (n) => {
    if (!n.read) {
      try { await api.post(`/api/notifications/${n.id}/read`); } catch { /* still navigate */ }
    }
    setOpen(false);
    refreshCount();
    if (n.incidentId) navigate(`/incidents/${n.incidentId}`);
  };

  const markAll = async () => {
    try {
      await api.post('/api/notifications/read-all');
      setItems((list) => list.map((n) => ({ ...n, read: true })));
      setCount(0);
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <div className="bell" ref={ref}>
      <button type="button" className="bell-button" onClick={toggle} aria-expanded={open}
        aria-label={`Notifications, ${count} unread`}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {count > 0 && <span className="bell-badge">{count > 99 ? '99+' : count}</span>}
      </button>
      {open && (
        <div className="bell-panel" role="dialog" aria-label="Notifications">
          <div className="bell-head">
            <strong>Notifications</strong>
            <button type="button" className="link-button" onClick={markAll} disabled={count === 0}>Mark all read</button>
          </div>
          {error && <div className="alert alert-error">{error}</div>}
          {!items.length && !error && <div className="empty">No notifications yet.</div>}
          <ul className="bell-list">
            {items.map((n) => (
              <li key={n.id}>
                <button type="button" className={`bell-item${n.read ? '' : ' unread'}`} onClick={() => openItem(n)}>
                  <span className="bell-item-top">
                    <span className="mono">{n.incidentNumber}</span>
                    <span className="muted">{label(n.eventType)} · {relative(n.createdAt)}</span>
                  </span>
                  <span>{n.message}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
