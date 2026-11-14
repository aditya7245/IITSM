import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { HOME } from '../lib/roles';

/**
 * Shows its children only to a logged-in user of the required role group ("employee", "technician", "admin",
 * or a list of them). Anyone else goes to the login page, or to their own home page if they lack the role.
 */
export default function ProtectedRoute({ group, children }) {
  const { user, group: mine, loading } = useAuth();
  const location = useLocation();

  if (loading) return <div className="page-loading">Loading…</div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;

  const allowed = !group || (Array.isArray(group) ? group.includes(mine) : group === mine);
  if (!allowed) return <Navigate to={HOME[mine] || '/login'} replace />;
  return children;
}
