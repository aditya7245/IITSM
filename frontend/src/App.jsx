import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Layout from './components/Layout';
import { HOME } from './lib/roles';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import NotFoundPage from './pages/NotFoundPage';
import IncidentDetailPage from './pages/IncidentDetailPage';
import MyIncidentsPage from './pages/employee/MyIncidentsPage';
import ReportIncidentPage from './pages/employee/ReportIncidentPage';
import TechnicianWorkPage from './pages/technician/TechnicianWorkPage';
import DashboardPage from './pages/admin/DashboardPage';
import IncidentSearchPage from './pages/admin/IncidentSearchPage';
import AuditLogPage from './pages/admin/AuditLogPage';
import UsersPage from './pages/admin/UsersPage';
import TeamsPage from './pages/admin/TeamsPage';
import TechniciansPage from './pages/admin/TechniciansPage';
import ServicesPage from './pages/admin/ServicesPage';
import CategoriesPage from './pages/admin/CategoriesPage';
import SlaPoliciesPage from './pages/admin/SlaPoliciesPage';
import SettingsPage from './pages/admin/SettingsPage';

function HomeRedirect() {
  const { user, group, loading } = useAuth();
  if (loading) return <div className="page-loading">Loading…</div>;
  return <Navigate to={user ? HOME[group] : '/login'} replace />;
}

const employee = (page) => <ProtectedRoute group="employee">{page}</ProtectedRoute>;
const technician = (page) => <ProtectedRoute group="technician">{page}</ProtectedRoute>;
const admin = (page) => <ProtectedRoute group="admin">{page}</ProtectedRoute>;

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/incidents/:id" element={<IncidentDetailPage />} />

        <Route path="/my-incidents" element={employee(<MyIncidentsPage />)} />
        <Route path="/report" element={employee(<ReportIncidentPage />)} />

        <Route path="/work" element={technician(<TechnicianWorkPage />)} />

        <Route path="/dashboard" element={admin(<DashboardPage />)} />
        <Route path="/incidents" element={admin(<IncidentSearchPage />)} />
        <Route path="/audit" element={admin(<AuditLogPage />)} />
        <Route path="/admin/users" element={admin(<UsersPage />)} />
        <Route path="/admin/teams" element={admin(<TeamsPage />)} />
        <Route path="/admin/technicians" element={admin(<TechniciansPage />)} />
        <Route path="/admin/services" element={admin(<ServicesPage />)} />
        <Route path="/admin/categories" element={admin(<CategoriesPage />)} />
        <Route path="/admin/sla" element={admin(<SlaPoliciesPage />)} />
        <Route path="/admin/settings" element={admin(<SettingsPage />)} />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
