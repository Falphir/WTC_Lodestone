import { useState } from 'react'
import { Dialog } from '../ui/Dialog'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { ListingFields } from './ListingFields'

/** Renames a server, changes its id, and edits everything ListingFields covers, in one form. */
export function ServerEditDialog({ server, busy, error, onClose, onSave }) {
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
