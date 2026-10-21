import { useRef, useState } from 'react'
import { api } from '../api'
import {
  EmptyState,
  ErrorNote,
  Field,
  Icon,
  KickMessagePanel,
  Note,
  PageHead,
  SyncPanel,
  WhitelistTable,
} from '../components'
import { plural, whitelistSync } from '../format'
import { useAction, useApi } from '../hooks'

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
      return `Added ${player.name}. Servers pick it up within seconds.`
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

  // Vanilla's whitelist.json shape -- the same one Import reads, so an export round-trips.
  function exportFile() {
    const list = players.map(({ uuid, name }) => ({ uuid, name })).sort((a, b) => a.name.localeCompare(b.name))
    const url = URL.createObjectURL(new Blob([JSON.stringify(list, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a')
    link.href = url
    link.download = 'whitelist.json'
    link.click()
    URL.revokeObjectURL(url)
  }

  const needle = query.trim().toLowerCase()
  const shown = players?.filter((p) => !needle || p.name.toLowerCase().includes(needle) || p.uuid.includes(needle))

  return (
    <div className="page">
      <PageHead
        actions={
          <div className="button-row">
            {players?.length > 0 && (
              <button type="button" className="button" onClick={exportFile}>
                <Icon name="download" />
                Export whitelist.json
              </button>
            )}
            <button type="button" className="button" disabled={busy} onClick={() => fileInput.current.click()}>
              <Icon name="upload" />
              Import whitelist.json
            </button>
          </div>
        }
      >
        <h1>Whitelist</h1>
        <p className="muted">
          Shared by every server. Changes reach the servers within seconds.
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

      {players?.length > 0 && servers?.length > 0 && <SyncPanel servers={servers} syncOf={whitelistSync} />}
      <KickMessagePanel />

      <Note>{message}</Note>
      <ErrorNote>{error || loadError}</ErrorNote>

      {players?.length === 0 && (
        <EmptyState title="The whitelist is empty">
          Import an existing server's <code>whitelist.json</code> to start, or add players one by one. Until the list has at
          least one player, servers keep their own whitelist untouched.
        </EmptyState>
      )}

      {shown?.length === 0 && players.length > 0 && <p className="muted">No players match “{query}”.</p>}

      {shown?.length > 0 && <WhitelistTable players={shown} busy={busy} onRemove={removePlayer} />}
    </div>
  )
}
