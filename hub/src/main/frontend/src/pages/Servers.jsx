import { useState } from 'react'
import { api } from '../api'
import { Badge, CopyButton, ErrorNote, Icon, StatusBadge } from '../components'
import { formatDate, relativeTime, statusOf, whitelistSync } from '../format'
import { useApi } from '../hooks'

export default function Servers() {
  const { data: servers, error, reload } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers'] })
  const [registering, setRegistering] = useState(false)
  const [registered, setRegistered] = useState(null)

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Servers</h1>
          <p className="muted">Each server runs the WTC Lodestone mod with its own token.</p>
        </div>
        {!registering && (
          <button
            type="button"
            className="button button-primary"
            onClick={() => {
              setRegistering(true)
              setRegistered(null)
            }}
          >
            <Icon name="plus" />
            Register server
          </button>
        )}
      </header>

      {registered && <TokenNotice server={registered} onDone={() => setRegistered(null)} />}

      {registering && (
        <RegisterForm
          onCancel={() => setRegistering(false)}
          onRegistered={(server) => {
            setRegistering(false)
            setRegistered(server)
            reload()
          }}
        />
      )}

      <ErrorNote>{error}</ErrorNote>

      {servers?.length === 0 && !registering && (
        <div className="empty">
          <h2>No servers yet</h2>
          <p>Register your first server to get the token its mod needs.</p>
        </div>
      )}

      {servers?.length > 0 && (
        <div className="panel">
          <table className="table">
            <thead>
              <tr>
                <th>Server</th>
                <th>Status</th>
                <th className="hide-sm">Whitelist</th>
                <th className="hide-md">Mod</th>
                <th className="hide-md">Registered</th>
                <th>Last heartbeat</th>
              </tr>
            </thead>
            <tbody>
              {[...servers]
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((s) => (
                  <tr key={s.id} className="row-link">
                    <td>
                      <a href={`#/servers/${encodeURIComponent(s.id)}`} className="row-anchor">
                        <span className="cell-title">{s.name}</span>
                        <span className="cell-sub mono">{s.id}</span>
                      </a>
                    </td>
                    <td>
                      <StatusBadge status={statusOf(s)} />
                    </td>
                    <td className="hide-sm">
                      <WhitelistBadge server={s} />
                    </td>
                    <td className="hide-md muted mono">{s.mod.version ?? '–'}</td>
                    <td className="hide-md muted">{formatDate(s.createdAt)}</td>
                    <td className="muted">{relativeTime(s.lastSeen)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function WhitelistBadge({ server }) {
  const sync = whitelistSync(server)
  return (
    <Badge tone={sync.tone} title={sync.detail}>
      {sync.label}
    </Badge>
  )
}

function RegisterForm({ onCancel, onRegistered }) {
  const [id, setId] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onRegistered(await api('/api/admin/servers', { method: 'POST', body: { id, name: name.trim() } }))
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <form className="panel panel-pad form" onSubmit={submit}>
      <h2>Register a server</h2>
      <div className="form-row">
        <label className="field">
          <span>Display name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="WTC Genesis" maxLength={100} required autoFocus />
        </label>
        <label className="field">
          <span>Server ID</span>
          <input
            className="mono"
            value={id}
            onChange={(e) => setId(e.target.value.toLowerCase())}
            placeholder="genesis"
            pattern="[a-z0-9_\-]{1,64}"
            title="Lowercase letters, numbers, _ and - only"
            maxLength={64}
            required
          />
          <small>Goes in the mod config as serverId. Lowercase letters, numbers, _ and -.</small>
        </label>
      </div>
      <ErrorNote>{error}</ErrorNote>
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={busy}>
          {busy ? 'Registering…' : 'Register server'}
        </button>
        <button type="button" className="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function TokenNotice({ server, onDone }) {
  const config = `serverId = "${server.id}"\ntoken = "${server.token}"`
  return (
    <section className="panel panel-pad token-notice" aria-live="polite">
      <h2>{server.name} is registered</h2>
      <p>
        Copy its token now. The hub only stores a hash of it, so it can't be shown again. Put these lines in{' '}
        <code>config/wtc_lodestone-common.toml</code> on that server and restart it.
      </p>
      <pre className="codeblock">{config}</pre>
      <div className="form-actions">
        <CopyButton text={config} label="Copy config" />
        <button type="button" className="button" onClick={onDone}>
          Done
        </button>
      </div>
    </section>
  )
}
