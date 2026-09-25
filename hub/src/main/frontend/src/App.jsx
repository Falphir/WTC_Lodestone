import { useState } from 'react'
import './App.css'

function formatRelativeTime(iso) {
  const diffSec = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  if (diffSec < 60) return rtf.format(-diffSec, 'second')
  const diffMin = Math.round(diffSec / 60)
  if (diffMin < 60) return rtf.format(-diffMin, 'minute')
  return rtf.format(-Math.round(diffMin / 60), 'hour')
}

function App() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [servers, setServers] = useState(null)
  const [selectedServerId, setSelectedServerId] = useState('')
  const [heartbeats, setHeartbeats] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  function authHeader() {
    return `Basic ${btoa(`${username}:${password}`)}`
  }

  async function signIn(event) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const response = await fetch('/api/admin/servers', { headers: { Authorization: authHeader() } })
      if (!response.ok) {
        throw new Error(
          response.status === 401
            ? 'Wrong admin username or password.'
            : `Couldn't load servers (HTTP ${response.status})`,
        )
      }
      setServers(await response.json())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function selectServer(id) {
    setSelectedServerId(id)
    setHeartbeats(null)
    setError(null)
    if (!id) return

    setLoading(true)
    try {
      const response = await fetch(`/api/admin/servers/${encodeURIComponent(id)}/heartbeats`, {
        headers: { Authorization: authHeader() },
      })
      if (!response.ok) {
        throw new Error(`Couldn't load '${id}' (HTTP ${response.status})`)
      }
      setHeartbeats(await response.json())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const latest = heartbeats?.[0]

  return (
    <div id="dashboard">
      <header>
        <h1>Server status</h1>
        <p>Heartbeat data reported by WTC Lodestone servers.</p>
      </header>

      {!servers && (
        <form onSubmit={signIn}>
          <div className="row">
            <label htmlFor="username">Admin username</label>
            <input
              id="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="admin"
              autoComplete="username"
            />
            <label htmlFor="password">Admin password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="password"
              autoComplete="current-password"
            />
            <button type="submit" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </div>
        </form>
      )}

      {servers?.length === 0 && <p className="empty">No servers registered yet.</p>}

      {servers?.length > 0 && (
        <div className="row">
          <label htmlFor="serverId">Server</label>
          <select
            id="serverId"
            value={selectedServerId}
            onChange={(event) => selectServer(event.target.value)}
            disabled={loading}
          >
            <option value="">Select a server…</option>
            {servers.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.id})
              </option>
            ))}
          </select>
        </div>
      )}

      {error && <p className="error">{error}</p>}

      {latest && (
        <div className="stats">
          <div className="stat">
            <span className="stat-value">
              {latest.playerCount}/{latest.maxPlayers}
            </span>
            <span className="stat-label">players</span>
          </div>
          <div className="stat">
            <span className="stat-value">{latest.tps.toFixed(1)}</span>
            <span className="stat-label">tps</span>
          </div>
          <div className="stat">
            <span className="stat-value">
              {latest.memoryUsedMb}/{latest.memoryMaxMb} MB
            </span>
            <span className="stat-label">memory</span>
          </div>
          <div className="stat">
            <span className="stat-value">{formatRelativeTime(latest.recordedAt)}</span>
            <span className="stat-label">last seen</span>
          </div>
        </div>
      )}

      {heartbeats?.length === 0 && (
        <p className="empty">
          No heartbeats yet for '{selectedServerId}'. It reports one every 60 seconds once connected.
        </p>
      )}

      {heartbeats?.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Players</th>
                <th>TPS</th>
                <th>Memory</th>
              </tr>
            </thead>
            <tbody>
              {heartbeats.map((h) => (
                <tr key={h.recordedAt}>
                  <td>{formatRelativeTime(h.recordedAt)}</td>
                  <td>
                    {h.playerCount}/{h.maxPlayers}
                  </td>
                  <td>{h.tps.toFixed(1)}</td>
                  <td>
                    {h.memoryUsedMb}/{h.memoryMaxMb} MB
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default App
