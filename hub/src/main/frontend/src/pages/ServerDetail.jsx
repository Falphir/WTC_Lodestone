import { useState } from 'react'
import { api } from '../api'
import {
  Badge,
  ConfirmDialog,
  EmptyState,
  ErrorNote,
  Icon,
  OnlinePlayers,
  PageHead,
  Panel,
  RelativeTime,
  ServerEditDialog,
  ServerFigures,
  ServerHeartbeats,
  ServerListing,
  ServerSetup,
  StatusBadge,
  TokenNotice,
} from '../components'
import { statusOf } from '../format'
import { useAction, useApi, useHubInfo } from '../hooks'

export default function ServerDetail({ id }) {
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

  async function saveEdit(fields) {
    const ok = await runEdit(async () => {
      const updated = await api(`/api/admin/servers/${encodeURIComponent(id)}`, { method: 'PATCH', body: fields })
      if (updated.id !== id) window.location.hash = `#/servers/${encodeURIComponent(updated.id)}`
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
    <div className="page">
      <BackLink />

      <PageHead
        actions={
          server && (
            <div className="head-status">
              <div className="head-status-info">
                <StatusBadge status={statusOf(server)} />
                <small className="muted">Last heartbeat <RelativeTime value={server.lastSeen} /></small>
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

      <ErrorNote>{serversError}</ErrorNote>

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
        <ServerEditDialog
          server={server}
          busy={editBusy}
          error={editError}
          onClose={() => setEditing(false)}
          onSave={saveEdit}
        />
      )}

      {newToken && <TokenNotice title={`New token for ${newToken.name}`} server={newToken} onDone={() => setNewToken(null)} />}

      {latest && <ServerFigures latest={latest} laggingBelowTps={info?.laggingBelowTps} />}

      {server?.online?.length > 0 && (
        <section>
          <h2 className="section-label">Online now</h2>
          <Panel pad>
            <OnlinePlayers players={server.online} />
          </Panel>
        </section>
      )}

      {server && (
        <section>
          <h2 className="section-label">Server details</h2>
          <ServerSetup server={server} />
        </section>
      )}

      {server && (
        <section>
          <h2 className="section-label">What players see</h2>
          <ServerListing listing={server.listing} />
        </section>
      )}

      <ServerHeartbeats id={id} latest={latest} />
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
