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
  const [serverId, setServerId] = useState('')
  const [heartbeats, setHeartbeats] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function loadHeartbeats(event) {
    event.preventDefault()
    const id = serverId.trim()
    if (!id) return

    setLoading(true)
    setError(null)
    try {
      const response = await fetch(`/api/admin/servers/${encodeURIComponent(id)}/heartbeats`, {
        headers: { Authorization: `Basic ${btoa(`${username}:${password}`)}` },
      })
      if (!response.ok) {
        throw new Error(
          response.status === 401
            ? 'Wrong admin username or password.'
            : `Couldn't load '${id}' (HTTP ${response.status})`,
        )
      }
      setHeartbeats(await response.json())
    } catch (err) {
      setError(err.message)
      setHeartbeats(null)
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

      <form onSubmit={loadHeartbeats}>
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
        </div>
        <div className="row">
          <label htmlFor="serverId">Server ID</label>
          <input
            id="serverId"
            value={serverId}
            onChange={(event) => setServerId(event.target.value)}
            placeholder="genesis"
            autoComplete="off"
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Loading…' : 'Load'}
          </button>
        </div>
      </form>

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
          No heartbeats yet for '{serverId}'. It reports one every 60 seconds once connected.
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
