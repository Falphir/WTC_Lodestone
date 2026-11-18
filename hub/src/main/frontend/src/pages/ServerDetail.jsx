import { useState } from 'react'
import { api } from '../api'
import {
  Badge,
  ConfirmDialog,
  EmptyState,
  ErrorNote,
  Icon,
  ModpackSetupNote,
  OnlinePlayers,
  PageHead,
  Panel,
  RelativeTime,
  ServerEditDialog,
  ServerFigures,
  ServerHeartbeats,
  ServerListing,
  ServerMotd,
  ServerPower,
  ServerSetup,
  ServerTabBar,
  StatusBadge,
  TokenNotice,
} from '../components'
import { statusOf } from '../format'
import { useAction, useApi, useHubInfo } from '../hooks'

// Laid out like PufferPanel's server page: power in the header, one section at a time, and the
// sections in a bar along the bottom. A future Console tab slots in here.
const TABS = [
  { id: 'stats', label: 'Stats', icon: 'overview' },
  { id: 'details', label: 'Details', icon: 'servers' },
  { id: 'manage', label: 'Manage', icon: 'key' },
]

export default function ServerDetail({ id, tab: tabFromRoute }) {
  const tab = TABS.some((t) => t.id === tabFromRoute) ? tabFromRoute : 'stats'
  const info = useHubInfo()
  const { data: servers, error: serversError, reload: reloadServers } = useApi('/api/admin/servers', {
    refreshMs: 60_000,
    topics: ['servers'],
  })
  const server = servers?.find((s) => s.id === id)
  const { run, busy, error: removeError } = useAction()
  const { run: runEdit, busy: editBusy, error: editError } = useAction(reloadServers)
  const { run: runResetToken, busy: resetBusy, error: resetError } = useAction()
  const [editing, setEditing] = useState(false)
  const [confirmingRemove, setConfirmingRemove] = useState(false)
  const [removeFromPanel, setRemoveFromPanel] = useState(true)
  const [confirmingReset, setConfirmingReset] = useState(false)
  const [newToken, setNewToken] = useState(null)

  async function removeServer() {
    const ok = await run(async () => {
      const alsoPanel = server.pufferpanelId && removeFromPanel ? '?pufferpanel=true' : ''
      await api(`/api/admin/servers/${encodeURIComponent(id)}${alsoPanel}`, { method: 'DELETE' })
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

  async function saveEdit(fields) {
    const ok = await runEdit(async () => {
      const updated = await api(`/api/admin/servers/${encodeURIComponent(id)}`, { method: 'PATCH', body: fields })
      if (updated.id !== id) window.location.hash = `#/servers/${encodeURIComponent(updated.id)}/${tab}`
      return null
    })
    if (ok) setEditing(false)
  }

  if (servers && !server) {
    return (
      <div className="page">
        <BackLink />
        <EmptyState title={`No server called “${id}”`}>
          It may have been removed. <a href="#/servers">See all servers</a>
        </EmptyState>
      </div>
    )
  }

  const latest = server?.latest

  return (
    <div className="page page-with-tabs">
      <BackLink />

      <PageHead actions={server?.pufferpanelId && <ServerPower server={server} />}>
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
            {server && (
              <div className="head-status-info">
                <span className="muted mono">{id}</span>
                <StatusBadge status={statusOf(server)} />
                <small className="muted">
                  Last heartbeat <RelativeTime value={server.lastSeen} />
                </small>
              </div>
            )}
          </div>
        </div>
      </PageHead>

      <ErrorNote>{serversError}</ErrorNote>
      <ModpackSetupNote setup={server?.modpackSetup} />

      {server && tab === 'stats' && (
        <>
          {latest && <ServerFigures latest={latest} laggingBelowTps={info?.laggingBelowTps} />}
          {server.online?.length > 0 && (
            <section>
              <h2 className="section-label">Online now</h2>
              <Panel pad>
                <OnlinePlayers players={server.online} />
              </Panel>
            </section>
          )}
          <ServerHeartbeats id={id} latest={latest} />
        </>
      )}

      {server && tab === 'details' && (
        <>
          <div className="button-row">
            <button type="button" className="button" disabled={editBusy} onClick={() => setPublished(!server.listing.published)}>
              <Icon name={server.listing.published ? 'hide' : 'show'} />
              {server.listing.published ? 'Unpublish' : 'Publish'}
            </button>
            <button type="button" className="button" disabled={editBusy} onClick={() => setEditing(true)}>
              <Icon name="edit" />
              Edit
            </button>
          </div>
          <ErrorNote>{!editing && editError}</ErrorNote>
          <section>
            <h2 className="section-label">Server details</h2>
            <ServerSetup server={server} />
          </section>
          <section>
            <h2 className="section-label">What players see</h2>
            <ServerListing listing={server.listing} />
          </section>
          <section>
            <h2 className="section-label">Server list</h2>
            <ServerMotd server={server} onSaved={reloadServers} />
          </section>
        </>
      )}

      {/* the rare, risky actions: on their own tab, so neither is ever a slip away from Edit */}
      {server && tab === 'manage' && (
        <section>
          <Panel>
            <div className="manage-row">
              <div>
                <strong>Reset token</strong>
                <small className="muted">A new token for the mod config. The old one stops working at once.</small>
              </div>
              <button type="button" className="button" disabled={busy || resetBusy} onClick={() => setConfirmingReset(true)}>
                <Icon name="key" />
                Reset token
              </button>
            </div>
            <div className="manage-row">
              <div>
                <strong>Remove server</strong>
                <small className="muted">
                  Deletes it and its history from the hub{server.pufferpanelId && ', and if you choose, its PufferPanel server too'}.
                </small>
              </div>
              <button type="button" className="button button-danger" disabled={busy} onClick={() => setConfirmingRemove(true)}>
                <Icon name="trash" />
                Remove server
              </button>
            </div>
          </Panel>
          {newToken && <TokenNotice title={`New token for ${newToken.name}`} server={newToken} onDone={() => setNewToken(null)} />}
        </section>
      )}

      {server && <ServerTabBar serverId={id} tabs={TABS} active={tab} />}

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
          {server.pufferpanelId && (
            <label className="confirm-option">
              <input type="checkbox" checked={removeFromPanel} onChange={(e) => setRemoveFromPanel(e.target.checked)} />
              <span>
                Also delete it on PufferPanel. <strong>Its files and world are deleted too, and that can't be undone.</strong>
              </span>
            </label>
          )}
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
        <ServerEditDialog
          server={server}
          busy={editBusy}
          error={editError}
          onClose={() => setEditing(false)}
          onSave={saveEdit}
        />
      )}
    </div>
  )
}

function BackLink() {
  return (
    <a href="#/servers" className="back">
      <Icon name="back" />
      Servers
    </a>
  )
}
