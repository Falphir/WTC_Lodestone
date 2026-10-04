import { useState } from 'react'
import { api, currentAdmin } from '../api'
import { Dialog, ErrorNote, Field, Icon, PageHead, Panel } from '../components'
import { formatDate } from '../format'
import { useAction, useApi } from '../hooks'

export default function Admins() {
  const { data: admins, error: loadError, reload } = useApi('/api/admin/users', { topics: ['admins'] })
  const [creating, setCreating] = useState(false)
  const [passwordFor, setPasswordFor] = useState(null)
  const { run, busy, message, error } = useAction(reload)
  const me = currentAdmin()

  function toggle(admin) {
    const verb = admin.enabled ? 'Disable' : 'Enable'
    if (admin.enabled && !window.confirm(`Disable ${admin.username}? They can't sign in again until re-enabled.`)) return
    run(async () => {
      await api(`/api/admin/users/${admin.id}`, { method: 'PATCH', body: { enabled: !admin.enabled } })
      return `${verb}d ${admin.username}.`
    })
  }

  function remove(admin) {
    if (!window.confirm(`Delete ${admin.username}? This can't be undone.`)) return
    run(async () => {
      await api(`/api/admin/users/${admin.id}`, { method: 'DELETE' })
      return `Deleted ${admin.username}.`
    })
  }

  return (
    <div className="page">
      <PageHead
        actions={
          !creating && (
            <button type="button" className="button button-primary" onClick={() => setCreating(true)}>
              <Icon name="plus" />
              Add admin
            </button>
          )
        }
      >
        <h1>Admins</h1>
        <p className="muted">Everyone here can manage servers, the whitelist and other admins.</p>
      </PageHead>

      {creating && (
        <CreateForm
          onCancel={() => setCreating(false)}
          onCreate={async (username, password) => {
            const ok = await run(async () => {
              await api('/api/admin/users', { method: 'POST', body: { username, password } })
              return `Added ${username}. They can sign in now.`
            })
            if (ok) setCreating(false)
          }}
          busy={busy}
        />
      )}

      {message && (
        <p className="note" role="status">
          {message}
        </p>
      )}
      <ErrorNote>{error || loadError}</ErrorNote>

      {admins && (
        <Panel>
          <table className="table">
            <thead>
              <tr>
                <th>Admin</th>
                <th>Access</th>
                <th className="hide-sm">Added</th>
                <th>
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {admins.map((a) => (
                <tr key={a.id}>
                  <td>
                    <span className="cell-title">
                      {a.username}
                      {a.username === me && <span className="tag">You</span>}
                    </span>
                  </td>
                  <td className={a.enabled ? '' : 'muted'}>{a.enabled ? 'Can sign in' : 'Disabled'}</td>
                  <td className="hide-sm muted">{formatDate(a.createdAt)}</td>
                  <td className="actions">
                    <button type="button" className="button button-quiet" disabled={busy} onClick={() => setPasswordFor(a)}>
                      Change password
                    </button>
                    {a.username !== me && (
                      <>
                        <button type="button" className="button button-quiet" disabled={busy} onClick={() => toggle(a)}>
                          {a.enabled ? 'Disable' : 'Enable'}
                        </button>
                        <button type="button" className="button button-quiet button-danger" disabled={busy} onClick={() => remove(a)}>
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}

      {passwordFor && (
        <PasswordDialog
          admin={passwordFor}
          isMe={passwordFor.username === me}
          onClose={() => setPasswordFor(null)}
          onSave={async (password) => {
            const ok = await run(async () => {
              await api(`/api/admin/users/${passwordFor.id}`, { method: 'PATCH', body: { password } })
              return `Changed the password for ${passwordFor.username}.`
            })
            if (ok) setPasswordFor(null)
          }}
          busy={busy}
        />
      )}
    </div>
  )
}

function CreateForm({ onCreate, onCancel, busy }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  return (
    <Panel
      as="form"
      pad
      className="form"
      onSubmit={(e) => {
        e.preventDefault()
        onCreate(username, password)
      }}
    >
      <h2>Add an admin</h2>
      <div className="form-row">
        <Field label="Username">
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            pattern="[a-zA-Z0-9_\-]{3,64}"
            title="3 to 64 letters, numbers, _ and -"
            autoComplete="off"
            required
            autoFocus
          />
        </Field>
        <Field label="Password" hint="At least 8 characters. Share it with them privately.">
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} autoComplete="new-password" required />
        </Field>
      </div>
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={busy}>
          Add admin
        </button>
        <button type="button" className="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </Panel>
  )
}

function PasswordDialog({ admin, isMe, onSave, onClose, busy }) {
  const [password, setPassword] = useState('')

  return (
    <Dialog onClose={onClose}>
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault()
          onSave(password)
        }}
      >
        <h2>{isMe ? 'Change your password' : `Change password for ${admin.username}`}</h2>
        <Field label="New password" hint={`At least 8 characters.${isMe ? ' You stay signed in on this browser.' : ''}`}>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} autoComplete="new-password" required autoFocus />
        </Field>
        <div className="form-actions">
          <button type="submit" className="button button-primary" disabled={busy}>
            Change password
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  )
}
