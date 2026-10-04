import { currentAdmin, signOut } from '../api'
import { Icon } from './Icon'
import { Mark } from './Mark'

const NAV = [
  { path: '', label: 'Overview', icon: 'overview' },
  { path: 'servers', label: 'Servers', icon: 'servers' },
  { path: 'whitelist', label: 'Whitelist', icon: 'whitelist' },
  { path: 'admins', label: 'Admins', icon: 'admins' },
]

const THEMES = [
  { value: 'system', label: 'Match system', icon: 'system' },
  { value: 'light', label: 'Light', icon: 'sun' },
  { value: 'dark', label: 'Dark', icon: 'moon' },
]

/** The sidebar's content -- rendered once as App's static desktop column, and again (with a close
 * button) inside its mobile nav drawer, since a <dialog> can't just be the same DOM node moved. */
export function SidebarNav({ section, live, theme, setTheme, onClose }) {
  const themeNow = THEMES.find((t) => t.value === theme) ?? THEMES[0]
  const nextTheme = THEMES[(THEMES.indexOf(themeNow) + 1) % THEMES.length]
  const themeLabel = `Theme: ${themeNow.label}. Switch to ${nextTheme.label.toLowerCase()}.`

  return (
    <>
      <div className="sidebar-head">
        <a href="#/" className="brand">
          <Mark size={26} />
          <span className="brand-text">
            WTC Lodestone
            <small>Network console</small>
          </span>
        </a>
        {onClose && (
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close menu">
            <Icon name="close" />
          </button>
        )}
      </div>

      <nav className="nav" aria-label="Main">
        {NAV.map((item) => (
          <a
            key={item.label}
            href={`#/${item.path}`}
            className="nav-link"
            aria-current={section === item.path ? 'page' : undefined}
          >
            <Icon name={item.icon} />
            {item.label}
          </a>
        ))}
      </nav>

      <p className={`live${live ? ' is-live' : ''}`} title={live ? 'Changes appear as they happen' : 'Pages refresh every minute until the connection is back'}>
        <span className="live-dot" aria-hidden="true" />
        {live ? 'Live updates' : 'Reconnecting…'}
      </p>

      <div className="sidebar-foot">
        <span className="whoami">{currentAdmin()}</span>
        <button type="button" className="icon-button" onClick={() => setTheme(nextTheme.value)} title={themeLabel} aria-label={themeLabel}>
          <Icon name={themeNow.icon} />
        </button>
        <button type="button" className="icon-button" onClick={signOut} title="Sign out" aria-label="Sign out">
          <Icon name="signOut" />
        </button>
      </div>
    </>
  )
}
