import { useState } from 'react'
import { api } from '../api'
import { Badge, EmptyState, ErrorNote, Field, Icon, PageHead, Panel, StatusBadge, TokenNotice } from '../components'
import { formatDate, relativeTime, statusOf, whitelistSync } from '../format'
import { useAction, useApi } from '../hooks'

export default function Servers() {
  const { data: servers, error, reload } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers'] })
  const [registering, setRegistering] = useState(false)
  const [registered, setRegistered] = useState(null)

  return (
    <div className="page">
      <PageHead
        actions={
          !registering && (
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
          )
        }
      >
        <h1>Servers</h1>
        <p className="muted">Each server runs the WTC Lodestone mod with its own token.</p>
      </PageHead>

      {registered && (
        <TokenNotice title={`${registered.name} is registered`} server={registered} onDone={() => setRegistered(null)} />
      )}

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
        <EmptyState title="No servers yet">Register your first server to get the token its mod needs.</EmptyState>
      )}

      {servers?.length > 0 && (
        <Panel>
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
        </Panel>
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
  const { run, busy, error } = useAction()

  function submit(event) {
    event.preventDefault()
    run(async () => {
      onRegistered(await api('/api/admin/servers', { method: 'POST', body: { id, name: name.trim() } }))
      return null
    })
  }

  return (
    <Panel as="form" pad className="form" onSubmit={submit}>
      <h2>Register a server</h2>
      <div className="form-row">
        <Field label="Display name">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="WTC Genesis" maxLength={100} required autoFocus />
        </Field>
        <Field label="Server ID" hint="Goes in the mod config as serverId. Lowercase letters, numbers, _ and -.">
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
        </Field>
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
    </Panel>
  )
}
