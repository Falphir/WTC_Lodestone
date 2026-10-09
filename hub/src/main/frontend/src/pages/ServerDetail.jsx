import { useMemo, useState } from 'react'
import { api } from '../api'
import {
  Badge,
  ConfirmDialog,
  Dialog,
  EmptyState,
  ErrorNote,
  Field,
  Icon,
  LineChart,
  ListingFields,
  PageHead,
  Panel,
  StatusBadge,
  TokenNotice,
} from '../components'
import { formatDateTime, formatMemory, formatTps, relativeTime, statusOf, whitelistSync } from '../format'
import { useAction, useApi, useHubInfo } from '../hooks'

const RANGES = [
  { hours: 1, label: 'Last hour' },
  { hours: 24, label: 'Last 24 hours' },
  { hours: 168, label: 'Last 7 days' },
]

export default function ServerDetail({ id }) {
  const [hours, setHours] = useState(24)
  const [showTable, setShowTable] = useState(false)
  const info = useHubInfo()
  const { data: servers, error: serversError, reload: reloadServers } = useApi('/api/admin/servers', {
    refreshMs: 60_000,
    topics: ['servers'],
  })
  const { data: heartbeats, error, stale } = useApi(`/api/admin/servers/${encodeURIComponent(id)}/heartbeats?hours=${hours}`, {
    refreshMs: 120_000,
    topics: ['servers'],
    serverId: id,
  })
  const server = servers?.find((s) => s.id === id)
  const { run, busy, error: removeError } = useAction()
  const { run: runEdit, busy: editBusy, error: editError } = useAction(reloadServers)
  const { run: runResetToken, busy: resetBusy, error: resetError } = useAction()
  const [editing, setEditing] = useState(false)
  const [confirmingRemove, setConfirmingRemove] = useState(false)
  const [confirmingReset, setConfirmingReset] = useState(false)
  const [newToken, setNewToken] = useState(null)

  async function removeServer() {
    const ok = await run(async () => {
      await api(`/api/admin/servers/${encodeURIComponent(id)}`, { method: 'DELETE' })
      return null
    })
    if (ok) window.location.hash = '#/servers'
  }

  async function resetToken() {
    const ok = await runResetToken(async () => {
      setNewToken(await api(`/api/admin/servers/${encodeURIComponent(id)}/reset-token`, { method: 'POST' }))
      return null
    })
    if (ok) setConfirmingReset(false)
  }

  // `published` on its own: the hub leaves every field the patch doesn't mention alone
  function setPublished(published) {
    runEdit(async () => {
      await api(`/api/admin/servers/${encodeURIComponent(id)}`, { method: 'PATCH', body: { published } })
      return null
    })
  }

  const series = useMemo(() => {
    const ascending = [...(heartbeats ?? [])].reverse()
    const at = (h) => new Date(h.recordedAt).getTime()
    return {
      players: ascending.map((h) => ({ t: at(h), v: h.playerCount })),
      tps: ascending.map((h) => ({ t: at(h), v: h.tps })),
      memory: ascending.map((h) => ({ t: at(h), v: h.memoryUsedMb })),
    }
  }, [heartbeats])

  if (servers && !server) {
    return (
      <div className="page">
        <a href="#/servers" className="back">
          <Icon name="back" />
          Servers
        </a>
        <EmptyState title={`No server called “${id}”`}>
          It may have been removed. <a href="#/servers">See all servers</a>
        </EmptyState>
      </div>
    )
  }

  // the range is anchored at "now" on every render so the axis keeps moving with the auto-refresh
  // eslint-disable-next-line react-hooks/purity
  const to = Date.now()
  const from = to - hours * 3600_000
  const latest = server?.latest
  // the hub's offline threshold; until /api/info loads, assume the default 3 missed minutes
  const gapMs = (info?.offlineAfterSeconds ?? 180) * 1000
  const describe = `${heartbeats?.length ?? 0} heartbeats, ${RANGES.find((r) => r.hours === hours).label.toLowerCase()}`

  return (
    <div className="page">
      <a href="#/servers" className="back">
        <Icon name="back" />
        Servers
      </a>

      <PageHead
        actions={
          server && (
            <div className="head-status">
              <div className="head-status-info">
                <StatusBadge status={statusOf(server)} />
                <small className="muted">Last heartbeat {relativeTime(server.lastSeen)}</small>
              </div>
              <div className="button-row">
                <button
                  type="button"
                  className="button button-quiet"
                  disabled={busy || editBusy}
                  onClick={() => setPublished(!server.listing.published)}
                >
                  <Icon name={server.listing.published ? 'hide' : 'show'} />
                  {server.listing.published ? 'Unpublish' : 'Publish'}
                </button>
                <button type="button" className="button button-quiet" disabled={busy || resetBusy} onClick={() => setEditing(true)}>
                  <Icon name="edit" />
                  Edit
                </button>
                <button type="button" className="button button-quiet" disabled={busy || resetBusy} onClick={() => setConfirmingReset(true)}>
                  <Icon name="key" />
                  Reset token
                </button>
                <button
                  type="button"
                  className="button button-quiet button-danger"
                  disabled={busy}
                  onClick={() => setConfirmingRemove(true)}
                >
                  <Icon name="trash" />
                  Remove server
                </button>
              </div>
            </div>
          )
        }
      >
        <div className="cell-server">
          {server?.listing.iconUrl && <img src={server.listing.iconUrl} alt="" className="server-icon" />}
          <div>
            <div className="page-title">
              <h1>{server?.name ?? id}</h1>
              {server && (
                <Badge tone={server.listing.published ? 'good' : 'none'}>
                  {server.listing.published ? 'Published' : 'Not published'}
                </Badge>
              )}
            </div>
            <p className="muted mono">{id}</p>
          </div>
        </div>
      </PageHead>

      <ErrorNote>{serversError || error}</ErrorNote>

      {confirmingRemove && server && (
        <ConfirmDialog
          title="Remove server?"
          confirmLabel="Remove server"
          danger
          busy={busy}
          error={removeError}
          onCancel={() => setConfirmingRemove(false)}
          onConfirm={removeServer}
        >
          Remove {server.name}? It'll need to be registered again to reconnect.
        </ConfirmDialog>
      )}

      {confirmingReset && server && (
        <ConfirmDialog
          title="Reset token?"
          confirmLabel="Reset token"
          danger
          busy={resetBusy}
          error={resetError}
          onCancel={() => setConfirmingReset(false)}
          onConfirm={resetToken}
        >
          Reset the token for {server.name}? The old token stops working immediately.
        </ConfirmDialog>
      )}

      {editing && server && (
        <EditDialog
          server={server}
          busy={editBusy}
          error={editError}
          onClose={() => setEditing(false)}
          onSave={async (fields) => {
            const ok = await runEdit(async () => {
              const updated = await api(`/api/admin/servers/${encodeURIComponent(id)}`, { method: 'PATCH', body: fields })
              if (updated.id !== id) window.location.hash = `#/servers/${encodeURIComponent(updated.id)}`
              return null
            })
            if (ok) setEditing(false)
          }}
        />
      )}

      {newToken && <TokenNotice title={`New token for ${newToken.name}`} server={newToken} onDone={() => setNewToken(null)} />}

      {latest && (
        <dl className="figures">
          <div>
            <dt>Players</dt>
            <dd>
              {latest.playerCount}
              <span className="muted"> / {latest.maxPlayers}</span>
            </dd>
          </div>
          <div>
            <dt>TPS</dt>
            <dd>{formatTps(latest.tps)}</dd>
          </div>
          <div>
            <dt>Memory</dt>
            <dd>
              {formatMemory(latest.memoryUsedMb)}
              <span className="muted"> / {formatMemory(latest.memoryMaxMb)}</span>
            </dd>
          </div>
        </dl>
      )}

      {server && (
        <section>
          <h2 className="section-label">Server details</h2>
          <Setup server={server} />
        </section>
      )}
      {server && (
        <section>
          <h2 className="section-label">What players see</h2>
          <Listing listing={server.listing} />
        </section>
      )}

      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Time range">
          {RANGES.map((r) => (
            <button key={r.hours} type="button" aria-pressed={hours === r.hours} onClick={() => setHours(r.hours)}>
              {r.label}
            </button>
          ))}
        </div>
        <button type="button" className="button button-quiet" aria-pressed={showTable} onClick={() => setShowTable((v) => !v)}>
          {showTable ? 'Show charts' : 'Show as table'}
        </button>
      </div>

      {!heartbeats ? (
        !error && <p className="muted">Loading heartbeats…</p>
      ) : showTable ? (
        <HeartbeatTable heartbeats={heartbeats} />
      ) : (
        <div className={`charts${stale ? ' is-stale' : ''}`}>
          <LineChart
            title="Players online"
            points={series.players}
            from={from}
            to={to}
            yMax={latest?.maxPlayers}
            format={(v) => Math.round(v).toLocaleString()}
            gapMs={gapMs}
            describe={describe}
          />
          <LineChart
            title="Ticks per second"
            points={series.tps}
            from={from}
            to={to}
            yMax={20}
            format={formatTps}
            gapMs={gapMs}
            describe={describe}
          />
          <LineChart
            title="Memory used"
            points={series.memory}
            from={from}
            to={to}
            yMax={latest?.memoryMaxMb}
            format={formatMemory}
            gapMs={gapMs}
            describe={describe}
          />
        </div>
      )}
    </div>
  )
}

function EditDialog({ server, busy, error, onClose, onSave }) {
  // `published` is deliberately left out: it has its own button, and carrying a stale copy of it
  // through this form would quietly undo a publish made while the dialog sat open.
  const [fields, setFields] = useState(() => {
    const listing = { ...server.listing, id: server.id, name: server.name }
    delete listing.published
    return listing
  })
  const change = (key, value) => setFields((f) => ({ ...f, [key]: value }))

  return (
    <Dialog onClose={onClose}>
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault()
          onSave({ ...fields, name: fields.name.trim() })
        }}
      >
        <h2>Edit {server.name}</h2>
        <div className="form-row">
          <Field label="Display name">
            <input value={fields.name} onChange={(e) => change('name', e.target.value)} maxLength={100} required autoFocus />
          </Field>
          <Field label="Server ID" hint="Goes in the mod config as serverId. Changing it needs a matching update there.">
            <input
              className="mono"
              value={fields.id}
              onChange={(e) => change('id', e.target.value.toLowerCase())}
              pattern="[a-z0-9_\-]{1,64}"
              title="Lowercase letters, numbers, _ and - only"
              maxLength={64}
              required
            />
          </Field>
        </div>

        <h3>What players see</h3>
        <ListingFields values={fields} onChange={change} />

        <ErrorNote>{error}</ErrorNote>
        <div className="form-actions">
          <button type="submit" className="button button-primary" disabled={busy}>
            Save
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  )
}

const TABLE_LIMIT = 500

/** What the mod reported about itself on check-in. */
function Setup({ server }) {
  const sync = whitelistSync(server)
  const { version, minecraftVersion, loaderVersion } = server.mod
  return (
    <dl className="setup">
      <div>
        <dt>Mod</dt>
        <dd className="mono">{version ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>Minecraft</dt>
        <dd className="mono">{minecraftVersion ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>NeoForge</dt>
        <dd className="mono">{loaderVersion ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>Whitelist</dt>
        <dd>
          <Badge tone={sync.tone}>{sync.label}</Badge>
          <small className="muted">{sync.detail}</small>
        </dd>
      </div>
    </dl>
  )
}

/** What players are shown in Discord's /serverinfo. Whether they see it at all is the badge up top. */
function Listing({ listing }) {
  const { publicAddress, modpack, modpackUrl, modpackVersion, launcher } = listing
  return (
    <dl className="setup">
      <div>
        <dt>Address</dt>
        <dd className="mono">{publicAddress || 'Not set'}</dd>
      </div>
      <div>
        <dt>Modpack</dt>
        <dd>
          {modpack || 'Not set'}
          {modpackVersion && <span className="muted mono"> {modpackVersion}</span>}
        </dd>
      </div>
      <div>
        <dt>Modpack link</dt>
        <dd>
          {modpackUrl ? (
              <a href={modpackUrl} title={modpackUrl} className="ellipsis" target="_blank" rel="noreferrer noopener">
                {modpackUrl}
              </a>
          ) : (
              'Not set'
          )}
        </dd>
      </div>
      <div>
        <dt>Launcher</dt>
        <dd>{launcher || 'Not set'}</dd>
      </div>

    </dl>
  )
}

function HeartbeatTable({ heartbeats }) {
  if (heartbeats.length === 0) return <p className="muted">No heartbeats in this range.</p>
  return (
    <Panel>
      {heartbeats.length > TABLE_LIMIT && (
        <p className="panel-note muted">
          Showing the latest {TABLE_LIMIT} of {heartbeats.length.toLocaleString()} heartbeats. Pick a shorter range to see
          them all.
        </p>
      )}
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>Time</th>
              <th className="num">Players</th>
              <th className="num">TPS</th>
              <th className="num">Memory</th>
            </tr>
          </thead>
          <tbody>
            {heartbeats.slice(0, TABLE_LIMIT).map((h) => (
              <tr key={h.recordedAt}>
                <td className="muted">{formatDateTime(h.recordedAt)}</td>
                <td className="num">
                  {h.playerCount} / {h.maxPlayers}
                </td>
                <td className="num">{formatTps(h.tps)}</td>
                <td className="num">
                  {formatMemory(h.memoryUsedMb)} / {formatMemory(h.memoryMaxMb)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}
