import { useState } from 'react'
import { api } from '../../api'
import { plural, renderMotd } from '../../format'
import { useAction, useApi } from '../../hooks'
import { Badge } from '../ui/Badge'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { EmptyState } from '../ui/EmptyState'
import { ErrorNote } from '../ui/ErrorNote'
import { Icon } from '../ui/Icon'
import { MinecraftText } from '../ui/MinecraftText'
import { Panel } from '../ui/Panel'
import { MotdEditDialog } from './MotdEditDialog'

const SAMPLE = { modpack: 'Greg-E', version: '0.6.4a', name: 'WTC Genesis' }

/**
 * The network's MOTDs: each server picks one on its page (or when it's created), and the hub fills in
 * that server's pack and version and keeps it on the server. The default is the one a new server starts with.
 */
export function MotdPanel() {
  const { data: motds, error: loadError, reload } = useApi('/api/admin/motds', { topics: ['servers'] })
  // null: closed; {}: adding; a MOTD: editing it
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const { run: runSave, busy: saving, error: saveError } = useAction(reload)
  const { run, busy, error } = useAction(reload)

  async function save(fields) {
    const ok = await runSave(async () => {
      if (editing.id) await api(`/api/admin/motds/${editing.id}`, { method: 'PUT', body: fields })
      else await api('/api/admin/motds', { method: 'POST', body: fields })
      return null
    })
    if (ok) setEditing(null)
  }

  async function remove() {
    const ok = await run(async () => {
      await api(`/api/admin/motds/${deleting.id}`, { method: 'DELETE' })
      return null
    })
    if (ok) setDeleting(null)
  }

  return (
    <section>
      <div className="section-head">
        <h2 className="section-label">Network MOTDs</h2>
        <button type="button" className="button" onClick={() => setEditing({})}>
          <Icon name="plus" />
          New MOTD
        </button>
      </div>
      <ErrorNote>{loadError || (!deleting && error)}</ErrorNote>

      {motds?.length === 0 && (
        <EmptyState title="No MOTDs yet">
          Add one with {'{modpack}'} and {'{version}'} in it, and every server that picks it shows its own pack.
        </EmptyState>
      )}

      {motds?.length > 0 && (
        <Panel>
          {motds.map((motd) => (
            <div key={motd.id} className="manage-row motd-row">
              <div>
                <span className="motd-title">
                  <strong>{motd.name}</strong>
                  {motd.isDefault && <Badge tone="good">Default</Badge>}
                  <small className="muted">
                    {motd.usedBy.length > 0 ? `Used by ${plural(motd.usedBy.length, 'server')}` : 'Not used yet'}
                  </small>
                </span>
                <MinecraftText text={renderMotd(motd.text, SAMPLE) ?? motd.text} />
              </div>
              <div className="button-row">
                {!motd.isDefault && (
                  <button
                    type="button"
                    className="button"
                    disabled={busy}
                    onClick={() => run(async () => (await api(`/api/admin/motds/${motd.id}/default`, { method: 'PUT' }), null))}
                  >
                    Make default
                  </button>
                )}
                <button type="button" className="button" onClick={() => setEditing(motd)}>
                  <Icon name="edit" />
                  Edit
                </button>
                <button type="button" className="button button-danger" disabled={busy} onClick={() => setDeleting(motd)}>
                  <Icon name="trash" />
                  <span className="visually-hidden">Delete {motd.name}</span>
                </button>
              </div>
            </div>
          ))}
        </Panel>
      )}

      {editing && <MotdEditDialog motd={editing.id ? editing : null} busy={saving} error={saveError} onClose={() => setEditing(null)} onSave={save} />}

      {deleting && (
        <ConfirmDialog
          title="Delete MOTD?"
          confirmLabel="Delete"
          danger
          busy={busy}
          error={error}
          onCancel={() => setDeleting(null)}
          onConfirm={remove}
        >
          Delete {deleting.name}?{' '}
          {deleting.usedBy.length > 0 && `It's used by ${deleting.usedBy.join(', ')}, so pick another MOTD for them first.`}
        </ConfirmDialog>
      )}
    </section>
  )
}
