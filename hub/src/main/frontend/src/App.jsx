import { useEffect, useRef, useState } from 'react'
import './App.css'
import { getToken, setSignedOutHandler } from './api'
import { Icon, Mark, SidebarNav } from './components'
import { useLiveStatus, useRoute, useTheme } from './hooks'
import Admins from './pages/Admins'
import Overview from './pages/Overview'
import ServerDetail from './pages/ServerDetail'
import Servers from './pages/Servers'
import SignIn from './pages/SignIn'
import Whitelist from './pages/Whitelist'

function App() {
  const [signedIn, setSignedIn] = useState(() => getToken() !== null)
  const [theme, setTheme] = useTheme()
  const [menuOpen, setMenuOpen] = useState(false)
  const route = useRoute()
  const live = useLiveStatus()
  const drawer = useRef(null)

  useEffect(() => setSignedOutHandler(() => setSignedIn(false)), [])

  // Close the mobile drawer on navigation (link click, or back/forward).
  useEffect(() => {
    const onHashChange = () => setMenuOpen(false)
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  // The dialog stays mounted at all times (never conditionally rendered) so its CSS open/close
  // transition -- see .nav-drawer -- actually gets to play instead of the node being yanked out
  // mid-animation; this effect just keeps it in sync with menuOpen via the imperative DOM API.
  useEffect(() => {
    const dialog = drawer.current
    if (!dialog) return // not yet rendered, e.g. still on the sign-in screen
    if (menuOpen && !dialog.open) dialog.showModal()
    if (!menuOpen && dialog.open) dialog.close()
  }, [menuOpen])

  if (!signedIn) return <SignIn onSignedIn={() => setSignedIn(true)} />

  const [section = '', id] = route
  let page
  if (section === 'servers' && id) page = <ServerDetail key={id} id={id} />
  else if (section === 'servers') page = <Servers />
  else if (section === 'whitelist') page = <Whitelist />
  else if (section === 'admins') page = <Admins />
  else page = <Overview />

  return (
    <div className="shell">
      <aside className="sidebar">
        <SidebarNav section={section} live={live} theme={theme} setTheme={setTheme} />
      </aside>

      <header className="mobile-bar">
        <a href="#/" className="brand">
          <Mark size={24} />
          WTC Lodestone
        </a>
        <button type="button" className="icon-button" onClick={() => setMenuOpen(true)} aria-label="Open menu" aria-haspopup="true">
          <Icon name="menu" />
        </button>
      </header>

      <dialog
        ref={drawer}
        className="nav-drawer"
        onClose={() => setMenuOpen(false)}
        onClick={(e) => {
          if (e.target === e.currentTarget) drawer.current.close()
        }}
      >
        <SidebarNav section={section} live={live} theme={theme} setTheme={setTheme} onClose={() => setMenuOpen(false)} />
      </dialog>

      <main className="main">{page}</main>
    </div>
  )
}

export default App
