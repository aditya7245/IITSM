import { useCallback, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import NotificationBell from './NotificationBell';
import AssistantPanel, { AssistantButton } from './AssistantPanel';
import { label } from '../lib/format';

const NAV = {
  employee: [
    { to: '/my-incidents', text: 'My incidents' },
    { to: '/report', text: 'Report an incident' },
  ],
  technician: [
    { to: '/work', text: 'My work' },
  ],
  admin: [
    { section: 'Overview' },
    { to: '/dashboard', text: 'Dashboard' },
    { to: '/incidents', text: 'Incidents', end: true },
    { to: '/audit', text: 'Audit log' },
    { section: 'People' },
    { to: '/admin/users', text: 'Users' },
    { to: '/admin/teams', text: 'Teams' },
    { to: '/admin/technicians', text: 'Technicians' },
    { section: 'Configuration' },
    { to: '/admin/services', text: 'Services' },
    { to: '/admin/categories', text: 'Categories' },
    { to: '/admin/sla', text: 'SLA policies' },
    { to: '/admin/settings', text: 'Assignment settings' },
  ],
};

export default function Layout() {
  const { user, group, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);
  const closeGuide = useCallback(() => setGuideOpen(false), []);

  const signOut = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className={`shell${guideOpen ? ' guide-open' : ''}`}>
      <header className="topbar">
        <button type="button" className="menu-button" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu"
          aria-expanded={menuOpen}>☰</button>
        <span className="brand"><span className="brand-mark">IT</span> I-ITSM</span>
        <div className="topbar-right">
          <AssistantButton open={guideOpen} onToggle={() => setGuideOpen(!guideOpen)} />
          <NotificationBell />
          <div className="user-chip">
            <span className="user-name">{user.fullName}</span>
            <span className="user-role">{label(user.role)}</span>
          </div>
          <button type="button" className="btn btn-small" onClick={signOut}>Log out</button>
        </div>
      </header>
      <div className="body">
        <nav className={`sidebar${menuOpen ? ' open' : ''}`} onClick={() => setMenuOpen(false)}>
          {(NAV[group] || []).map((item) => (item.section
            ? <div key={item.section} className="nav-section">{item.section}</div>
            : (
              <NavLink key={item.to} to={item.to} end={item.end}
                className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
                {item.text}
              </NavLink>
            )))}
        </nav>
        <main className="content">
          <Outlet />
        </main>
      </div>
      <AssistantPanel open={guideOpen} onClose={closeGuide} />
    </div>
  );
}
