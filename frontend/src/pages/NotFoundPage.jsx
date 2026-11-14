import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { HOME } from '../lib/roles';

export default function NotFoundPage() {
  const { group } = useAuth();
  return (
    <div className="card narrow">
      <h1>Page not found</h1>
      <p className="muted">There is nothing at this address.</p>
      <Link className="btn" to={HOME[group] || '/'}>Go to your home page</Link>
    </div>
  );
}
