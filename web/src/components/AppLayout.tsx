import { NavLink, Outlet } from 'react-router-dom';
import { syncEngine } from '@/sync/instance';

const links = [
  { to: '/intakes', label: 'נטילות', icon: '🧾' },
  { to: '/medications', label: 'תרופות', icon: '💊' },
  { to: '/history', label: 'היסטוריה', icon: '📋' },
  { to: '/settings', label: 'הגדרות', icon: '⚙️' },
];

export function AppLayout() {
  function onNavClick(path: string) {
    if (path !== '/history') {
      return;
    }
    void syncEngine.syncNow('history_nav_click');
  }

  return (
    <>
      <header className="app-header">
        <div className="app-header-inner">
          <div className="app-logo" aria-hidden="true">💊</div>
          <div className="app-header-text">
            <p className="app-brand-title">MedMinder</p>
            <span className="app-subtitle">ניהול תרופות חכם</span>
          </div>
        </div>
      </header>

      <div className="app-shell">
        <main className="app-main">
          <Outlet />
        </main>
      </div>

      <nav className="app-bottom-nav" aria-label="ניווט ראשי">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            onClick={() => onNavClick(link.to)}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            <span className="nav-icon">{link.icon}</span>
            <span>{link.label}</span>
          </NavLink>
        ))}
      </nav>
    </>
  );
}
