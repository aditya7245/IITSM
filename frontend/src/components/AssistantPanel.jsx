import { Fragment, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import api, { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const MAX_LENGTH = 4000;

/** Starter questions, by role group, and extra ones while an incident is open on screen. */
const STARTERS = {
  employee: ['How do I report an incident?', 'What do the incident statuses mean?', 'How is priority decided?'],
  technician: ['What should I work on first?', 'How do I ask the reporter for more information?', 'What happens if I miss an SLA deadline?'],
  admin: ['How does automatic assignment work?', 'When does an incident escalate?', 'How do I change an SLA policy?'],
};
const INCIDENT_STARTERS = ['What can I do next on this incident?', 'Explain this incident’s SLA status.'];

/** The incident id when the current page is an incident's detail page. */
function incidentIdFrom(pathname) {
  const match = /^\/incidents\/(\d+)/.exec(pathname);
  return match ? Number(match[1]) : null;
}

/** The chat icon button in the top bar that opens and closes the guide. */
export function AssistantButton({ open, onToggle }) {
  return (
    <button type="button" className={`bell-button${open ? ' is-active' : ''}`} onClick={onToggle}
      aria-expanded={open} aria-controls="assistant-panel" aria-label={open ? 'Close the AI guide' : 'Open the AI guide'}
      title="AI guide">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
        strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        <path d="M8 9h8M8 13h5" />
      </svg>
    </button>
  );
}

/**
 * The AI guide side panel. It stays mounted while closed, so the conversation survives closing the panel and
 * moving between pages. Each question is sent with the current page and, on an incident page, the incident id;
 * the server adds the incident's details only if this user may see it.
 */
export default function AssistantPanel({ open, onClose }) {
  const { group } = useAuth();
  const location = useLocation();
  const [enabled, setEnabled] = useState(null);       // null while checking
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const listRef = useRef(null);
  const inputRef = useRef(null);

  const incidentId = incidentIdFrom(location.pathname);

  useEffect(() => {
    if (!open || enabled !== null) return;
    api.get('/api/assistant/status')
      .then(({ data }) => setEnabled(Boolean(data.enabled)))
      .catch(() => setEnabled(false));
  }, [open, enabled]);

  useEffect(() => {
    if (!open) return undefined;
    inputRef.current?.focus();
    const esc = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', esc);
    return () => document.removeEventListener('keydown', esc);
  }, [open, onClose]);

  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages, busy, error]);

  const ask = async (conversation) => {
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post('/api/assistant/chat', {
        messages: conversation,
        page: location.pathname,
        incidentId,
      });
      setMessages([...conversation, { role: 'assistant', content: data.reply }]);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  };

  const send = (text) => {
    const question = text.trim();
    if (!question || busy) return;
    const conversation = [...messages, { role: 'user', content: question.slice(0, MAX_LENGTH) }];
    setMessages(conversation);
    setDraft('');
    ask(conversation);
  };

  // After a failure the question is still the last message, so it can be sent again as it is.
  const retry = () => ask(messages);
  const lastIsQuestion = messages.length > 0 && messages[messages.length - 1].role === 'user';

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send(draft);
    }
  };

  const startNew = () => {
    setMessages([]);
    setError(null);
    setDraft('');
    inputRef.current?.focus();
  };

  const starters = [...(incidentId ? INCIDENT_STARTERS : []), ...(STARTERS[group] || [])];

  return (
    <aside id="assistant-panel" className={`assistant${open ? ' open' : ''}`} aria-label="AI guide" aria-hidden={!open}
      inert={open ? undefined : ''}>
      <div className="assistant-head">
        <div>
          <strong>I-ITSM Guide</strong>
          <span className="assistant-badge">AI</span>
        </div>
        <div className="assistant-head-actions">
          <button type="button" className="link-button" onClick={startNew} disabled={busy || messages.length === 0}>New chat</button>
          <button type="button" className="assistant-close" onClick={onClose} aria-label="Close the AI guide">×</button>
        </div>
      </div>

      <div className="assistant-body" ref={listRef} aria-live="polite">
        {enabled === false && (
          <div className="assistant-empty">
            <p><strong>The AI guide is not set up on this server.</strong></p>
            <p className="muted small">
              An administrator can turn it on by setting <span className="mono">ASSISTANT_BASE_URL</span> and{' '}
              <span className="mono">ASSISTANT_MODEL</span> (and usually <span className="mono">ASSISTANT_API_KEY</span>)
              for the backend.
            </p>
          </div>
        )}

        {enabled && messages.length === 0 && (
          <div className="assistant-empty">
            <p>Hi! I can explain how I-ITSM works and help you with {incidentId ? 'the incident on your screen' : 'what you are doing'}.</p>
            <div className="assistant-starters">
              {starters.map((s) => (
                <button key={s} type="button" className="assistant-starter" onClick={() => send(s)} disabled={busy}>{s}</button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m, i) => (
          // eslint-disable-next-line react/no-array-index-key
          <div key={i} className={`assistant-msg from-${m.role}`}>
            {m.role === 'assistant' ? <Formatted text={m.content} /> : <span className="prewrap">{m.content}</span>}
          </div>
        ))}

        {busy && <div className="assistant-msg from-assistant typing" aria-label="The guide is writing">
          <span /><span /><span />
        </div>}

        {error && (
          <div className="alert alert-error assistant-error">
            {error}
            {lastIsQuestion && <> <button type="button" className="link-button" onClick={retry}>Try again</button></>}
          </div>
        )}
      </div>

      {enabled && (
        <form className="assistant-input" onSubmit={(e) => { e.preventDefault(); send(draft); }}>
          <textarea ref={inputRef} rows={2} value={draft} maxLength={MAX_LENGTH} placeholder="Ask about I-ITSM…"
            onChange={(e) => setDraft(e.target.value)} onKeyDown={onKeyDown} aria-label="Your question" />
          <button type="submit" className="btn btn-primary" disabled={busy || !draft.trim()}>Send</button>
        </form>
      )}
      {enabled && <p className="assistant-note">AI answers can be wrong. It cannot change anything for you.</p>}
    </aside>
  );
}

/**
 * Shows the small part of Markdown models usually write (paragraphs, "-" and "1." lists, **bold**, `code`) as
 * React elements, never as raw HTML.
 */
function Formatted({ text }) {
  const blocks = [];
  let list = null;
  text.split('\n').forEach((raw) => {
    const line = raw.trimEnd();
    const bullet = /^\s*[-*•]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      const kind = bullet ? 'ul' : 'ol';
      if (!list || list.kind !== kind) {
        list = { kind, items: [] };
        blocks.push(list);
      }
      list.items.push((bullet || numbered)[1]);
      return;
    }
    list = null;
    if (line.trim()) blocks.push({ kind: 'p', text: line.replace(/^#{1,6}\s+/, '') });
  });

  return blocks.map((b, i) => {
    if (b.kind === 'p') return <p key={i}>{inline(b.text)}</p>;
    const Tag = b.kind;
    return <Tag key={i}>{b.items.map((item, j) => <li key={j}>{inline(item)}</li>)}</Tag>;
  });
}

function inline(text) {
  return text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return <strong key={i}>{part.slice(2, -2)}</strong>;
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) return <code key={i}>{part.slice(1, -1)}</code>;
    return <Fragment key={i}>{part}</Fragment>;
  });
}
