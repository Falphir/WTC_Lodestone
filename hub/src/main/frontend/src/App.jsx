import { useEffect, useState } from 'react'
import './App.css'
import { currentAdmin, getToken, setSignedOutHandler, signOut } from './api'
import { Icon, Mark } from './components'
import { useLiveStatus, useRoute, useTheme } from './hooks'
import Admins from './pages/Admins'
import Overview from './pages/Overview'
import ServerDetail from './pages/ServerDetail'
import Servers from './pages/Servers'
import SignIn from './pages/SignIn'
import Whitelist from './pages/Whitelist'

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

function App() {
  const [signedIn, setSignedIn] = useState(() => getToken() !== null)
  const [theme, setTheme] = useTheme()
  const route = useRoute()
  const live = useLiveStatus()

  useEffect(() => setSignedOutHandler(() => setSignedIn(false)), [])

  if (!signedIn) return <SignIn onSignedIn={() => setSignedIn(true)} />

  const [section = '', id] = route
  let page
  if (section === 'servers' && id) page = <ServerDetail key={id} id={id} />
  else if (section === 'servers') page = <Servers />
  else if (section === 'whitelist') page = <Whitelist />
  else if (section === 'admins') page = <Admins />
  else page = <Overview />

  const themeNow = THEMES.find((t) => t.value === theme) ?? THEMES[0]
  const nextTheme = THEMES[(THEMES.indexOf(themeNow) + 1) % THEMES.length]
  const themeLabel = `Theme: ${themeNow.label}. Switch to ${nextTheme.label.toLowerCase()}.`

  return (
    <div className="shell">
      <aside className="sidebar">
        <a href="#/" className="brand">
          <Mark size={26} />
          <span className="brand-text">
            WTC Lodestone
            <small>Network console</small>
          </span>
        </a>

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
      </aside>

      <main className="main">{page}</main>
    </div>
  )
}

export default App
