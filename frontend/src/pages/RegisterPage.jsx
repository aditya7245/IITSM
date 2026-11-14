import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorMessage } from '../api/client';
import { HOME } from '../lib/roles';
import { ErrorBox } from '../components/ui';

/** Self-registration always creates an employee account; other roles are created by an administrator. */
export default function RegisterPage() {
  const { user, group, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', department: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={HOME[group]} replace />;

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register({ ...form, email: form.email.trim(), department: form.department.trim() || null });
      navigate(HOME.employee, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-brand"><span className="brand-mark">IT</span> I-ITSM</div>
        <h1>Create an account</h1>
        <p className="muted">Employees can register here to report IT problems.</p>
        <ErrorBox error={error} />
        <label className="field">
          <span className="field-label">Full name</span>
          <input value={form.fullName} onChange={set('fullName')} required maxLength={120} autoFocus />
        </label>
        <label className="field">
          <span className="field-label">Email</span>
          <input type="email" value={form.email} onChange={set('email')} required maxLength={150} autoComplete="username" />
        </label>
        <label className="field">
          <span className="field-label">Password</span>
          <input type="password" value={form.password} onChange={set('password')} required minLength={8} maxLength={72} autoComplete="new-password" />
          <span className="field-hint">At least 8 characters.</span>
        </label>
        <label className="field">
          <span className="field-label">Department (optional)</span>
          <input value={form.department} onChange={set('department')} maxLength={100} />
        </label>
        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="auth-switch">Already registered? <Link to="/login">Log in</Link></p>
      </form>
    </div>
  );
}
