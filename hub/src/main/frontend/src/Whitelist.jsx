import { useEffect, useRef, useState } from 'react'

async function api(authHeader, path, options = {}) {
  const response = await fetch(`/api/admin/whitelist${path}`, {
    ...options,
    headers: { Authorization: authHeader, 'Content-Type': 'application/json' },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `Request failed (HTTP ${response.status})`)
  }
  return response.status === 204 ? null : response.json()
}

const byName = (a, b) => a.name.localeCompare(b.name)

function Whitelist({ authHeader }) {
  const [players, setPlayers] = useState(null)
  const [name, setName] = useState('')
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const fileInput = useRef(null)

  useEffect(() => {
    api(authHeader, '').then(setPlayers, (err) => setError(err.message))
  }, [authHeader])

  async function run(action) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      setMessage(await action())
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function addPlayer(event) {
    event.preventDefault()
    run(async () => {
      const player = await api(authHeader, '', { method: 'POST', body: JSON.stringify({ name: name.trim() }) })
      setPlayers((list) => [...list, player].sort(byName))
      setName('')
      return `Added ${player.name}. Servers pick it up within a minute.`
    })
  }

  function removePlayer(player) {
    if (!window.confirm(`Remove ${player.name} from the whitelist on every server?`)) return
    run(async () => {
      await api(authHeader, `/${player.uuid}`, { method: 'DELETE' })
      setPlayers((list) => list.filter((p) => p.uuid !== player.uuid))
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
        throw new Error(`${file.name} isn't valid JSON.`)
      }
      const { added } = await api(authHeader, '/import', {
        method: 'POST',
        body: JSON.stringify({ players: entries }),
      })
      setPlayers(await api(authHeader, ''))
      return `Imported ${added} new player${added === 1 ? '' : 's'} from ${file.name}.`
    })
  }

  return (
    <section className="whitelist">
      <h2>Whitelist</h2>
      <p>Shared by every server. Changes reach the servers within a minute.</p>

      <form onSubmit={addPlayer}>
        <div className="row">
          <label htmlFor="playerName">Minecraft username</label>
          <input
            id="playerName"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Minecraft username"
            maxLength={16}
          />
          <button type="submit" disabled={busy || !name.trim()}>
            Add
          </button>
          <button type="button" disabled={busy} onClick={() => fileInput.current.click()}>
            Import whitelist.json
          </button>
          <input ref={fileInput} type="file" accept=".json,application/json" onChange={importFile} hidden />
        </div>
      </form>

      {message && <p className="empty">{message}</p>}
      {error && <p className="error">{error}</p>}

      {players?.length === 0 && (
        <p className="empty">
          Nothing here yet. Import a server's whitelist.json to start. Until then, servers keep their own whitelist.
        </p>
      )}

      {players?.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Player</th>
                <th>Added by</th>
                <th>Added</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {players.map((p) => (
                <tr key={p.uuid}>
                  <td title={p.uuid}>{p.name}</td>
                  <td>{p.addedBy}</td>
                  <td>{new Date(p.addedAt).toLocaleDateString()}</td>
                  <td>
                    <button type="button" disabled={busy} onClick={() => removePlayer(p)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default Whitelist
