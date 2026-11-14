import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorMessage } from '../api/client';
import { groupOf, HOME } from '../lib/roles';
import { ErrorBox } from '../components/ui';

// The users seeded by the dev profile (migration V3). Shown only in development builds.
const DEMO_USERS = [
  { role: 'Employees', users: [
    { email: 'emp1@iitsm.local', name: 'Asha' },
    { email: 'emp2@iitsm.local', name: 'Ravi' },
  ] },
  { role: 'Technicians', users: [
    { email: 'tech1@iitsm.local', name: 'Kiran' },
    { email: 'tech2@iitsm.local', name: 'Sana' },
    { email: 'tech3@iitsm.local', name: 'Dev' },
    { email: 'lead.app@iitsm.local', name: 'Meera (team lead)' },
  ] },
  { role: 'Admins', users: [
    { email: 'admin@iitsm.local', name: 'System Admin' },
    { email: 'manager@iitsm.local', name: 'IT Manager' },
  ] },
];

export default function LoginPage() {
  const { user, group, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={HOME[group]} replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const me = await login(email.trim(), password);
      const from = location.state?.from;
      navigate(from && from !== '/login' ? from : HOME[groupOf(me.role)], { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const fill = (demoEmail) => {
    setEmail(demoEmail);
    setPassword('Password@123');
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-brand"><span className="brand-mark">IT</span> I-ITSM</div>
        <h1>Log in</h1>
        <p className="muted">Intelligent IT Service Management</p>
        <ErrorBox error={error} />
        <label className="field">
          <span className="field-label">Email</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" autoFocus />
        </label>
        <label className="field">
          <span className="field-label">Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
        </label>
        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
        <p className="auth-switch">No account? <Link to="/register">Register as an employee</Link></p>

        {import.meta.env.DEV && (
          <div className="demo-users">
            <div className="field-label">Demo users (password Password@123). Click one, then Log in.</div>
            {DEMO_USERS.map((group) => (
              <div key={group.role} className="demo-group">
                <span className="demo-role">{group.role}</span>
                <div className="demo-list">
                  {group.users.map((u) => (
                    <button key={u.email} type="button" title={u.email}
                      className={`chip${email === u.email ? ' chip-active' : ''}`} onClick={() => fill(u.email)}>
                      {u.name}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </form>
    </div>
  );
}
