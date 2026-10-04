import { useRef, useState } from 'react'
import { api } from '../api'
import { Badge, EmptyState, ErrorNote, Field, Icon, PageHead, Panel, PlayerHead } from '../components'
import { formatDate, plural, whitelistSync } from '../format'
import { useAction, useApi } from '../hooks'

/** Which servers have applied the current list; changes reach each one within a heartbeat. */
function SyncPanel({ servers }) {
  const rows = servers.map((s) => ({ ...s, sync: whitelistSync(s) })).sort((a, b) => a.name.localeCompare(b.name))
  const current = rows.filter((s) => s.sync.tone === 'good').length
  return (
    <Panel as="section" aria-label="Sync status">
      <p className="panel-note">
        Up to date on {current} of {plural(rows.length, 'server')}.
      </p>
      <ul className="sync-list">
        {rows.map((s) => (
          <li key={s.id}>
            <a href={`#/servers/${encodeURIComponent(s.id)}`}>{s.name}</a>
            <Badge tone={s.sync.tone}>{s.sync.label}</Badge>
            <small className="muted">{s.sync.detail}</small>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

export default function Whitelist() {
  const { data: players, error: loadError, reload } = useApi('/api/admin/whitelist', { topics: ['whitelist'] })
  const { data: servers } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers', 'whitelist'] })
  const [name, setName] = useState('')
  const [query, setQuery] = useState('')
  const { run, busy, message, error } = useAction(reload)
  const fileInput = useRef(null)

  function addPlayer(event) {
    event.preventDefault()
    run(async () => {
      const player = await api('/api/admin/whitelist', { method: 'POST', body: { name: name.trim() } })
      setName('')
      return `Added ${player.name}. Servers pick it up within a minute.`
    })
  }

  function removePlayer(player) {
    if (!window.confirm(`Remove ${player.name} from the whitelist on every server?`)) return
    run(async () => {
      await api(`/api/admin/whitelist/${player.uuid}`, { method: 'DELETE' })
      return `Removed ${player.name}.`
    })
  }

  function importFile(event) {
    const file = event.target.files[0]
    event.target.value = ''
    if (!file) return
    run(async () => {
      let entries
      try {
        entries = JSON.parse(await file.text())
      } catch {
        throw new Error(`${file.name} isn't valid JSON. Pick a server's whitelist.json.`)
      }
      const { added } = await api('/api/admin/whitelist/import', { method: 'POST', body: { players: entries } })
      return `Imported ${plural(added, 'new player')} from ${file.name}.`
    })
  }

  const needle = query.trim().toLowerCase()
  const shown = players?.filter((p) => !needle || p.name.toLowerCase().includes(needle) || p.uuid.includes(needle))

  return (
    <div className="page">
      <PageHead
        actions={
          <button type="button" className="button" disabled={busy} onClick={() => fileInput.current.click()}>
            <Icon name="upload" />
            Import whitelist.json
          </button>
        }
      >
        <h1>Whitelist</h1>
        <p className="muted">
          Shared by every server. Changes reach the servers within a minute.
          {players && ` ${plural(players.length, 'player')}.`}
        </p>
      </PageHead>
      <input ref={fileInput} type="file" accept=".json,application/json" onChange={importFile} hidden />

      <form className="toolbar" onSubmit={addPlayer}>
        <Field label="Minecraft username" hidden inline>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Minecraft username"
            maxLength={16}
            pattern="[A-Za-z0-9_]{1,16}"
            title="Letters, numbers and _ only, up to 16 characters"
          />
        </Field>
        <button type="submit" className="button button-primary" disabled={busy || !name.trim()}>
          <Icon name="plus" />
          Add player
        </button>

        {players?.length > 0 && (
          <Field label="Search players" hidden inline search icon="search">
            <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search" />
          </Field>
        )}
      </form>

      {players?.length > 0 && servers?.length > 0 && <SyncPanel servers={servers} />}

      {message && (
        <p className="note" role="status">
          {message}
        </p>
      )}
      <ErrorNote>{error || loadError}</ErrorNote>

      {players?.length === 0 && (
        <EmptyState title="The whitelist is empty">
          Import an existing server's <code>whitelist.json</code> to start, or add players one by one. Until the list has at
          least one player, servers keep their own whitelist untouched.
        </EmptyState>
      )}

      {shown?.length === 0 && players.length > 0 && <p className="muted">No players match “{query}”.</p>}

      {shown?.length > 0 && (
        <Panel>
          <table className="table table-cards">
            <thead>
              <tr>
                <th>Player</th>
                <th>Discord</th>
                <th>Added by</th>
                <th>Added</th>
                <th>
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {shown.map((p) => (
                <tr key={p.uuid}>
                  <td>
                    <div className="cell-player">
                      <PlayerHead id={p.uuid} />
                      <div>
                        <span className="cell-title">{p.name}</span>
                        <span className="cell-sub mono">{p.uuid}</span>
                      </div>
                    </div>
                  </td>
                  <td data-label="Discord">
                    {p.linkedDiscordId ? (
                      <a className="mono" href={`https://discord.com/users/${p.linkedDiscordId}`} target="_blank" rel="noreferrer">
                        {p.linkedDiscordId}
                      </a>
                    ) : (
                      <span className="muted">Not linked</span>
                    )}
                  </td>
                  <td data-label="Added by" className="muted">{p.addedBy}</td>
                  <td data-label="Added" className="muted">{formatDate(p.addedAt)}</td>
                  <td className="actions">
                    <button type="button" className="button button-quiet button-danger" disabled={busy} onClick={() => removePlayer(p)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}
    </div>
  )
}
