import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useTheme } from '../theme/ThemeContext'
import './Header.css'

const nav = [
  { to: '/stocks', label: 'Фонд' },
  { to: '/crypto', label: 'Крипта' },
  { to: '/expenses', label: 'Траты' },
]

export function Header() {
  const { user, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const display = user?.displayName?.trim() || user?.email || '—'

  return (
    <header className="header">
      <div className="header-inner">
        <Link to="/" className="brand" aria-label="Fin Flow — на главную">
          <span className="brand-mark" aria-hidden>
            F
          </span>
          <span className="brand-name">
            Fin <em>Flow</em>
          </span>
        </Link>

        <nav className="header-nav" aria-label="Основное">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="header-user">
          <button
            type="button"
            className="icon-btn"
            aria-label={theme === 'light' ? 'Тёмная тема' : 'Светлая тема'}
            title={theme === 'light' ? 'Тёмная тема' : 'Светлая тема'}
            onClick={toggle}
          >
            {theme === 'light' ? <MoonIcon /> : <SunIcon />}
          </button>
          {user?.role === 'ADMIN' ? (
            <NavLink to="/admin" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Админ
            </NavLink>
          ) : null}
          <span className="user-name" title={user?.email}>
            {display}
          </span>
          <button type="button" className="btn btn-ghost" onClick={logout}>
            Выйти
          </button>
        </div>
      </div>
    </header>
  )
}

function MoonIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M19 13.5A7.5 7.5 0 1110.5 5 6 6 0 0019 13.5z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function SunIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.7" />
      <path
        d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M17 7l1.4-1.4M5.6 18.4l1.4-1.4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  )
}
