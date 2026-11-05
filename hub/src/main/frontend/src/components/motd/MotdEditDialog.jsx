import { useState } from 'react'
import { Dialog } from '../ui/Dialog'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { MotdEditor } from './MotdEditor'

/** Adds a network MOTD, or edits one (`motd` given). The editor shows it as it looks, so it needs no preview. */
export function MotdEditDialog({ motd, busy, error, onClose, onSave }) {
  const [name, setName] = useState(motd?.name ?? '')
  const [text, setText] = useState(motd?.text ?? '')

  return (
    <Dialog onClose={onClose} className="dialog-wizard">
      <form
        className="wizard-form"
        onSubmit={(e) => {
          e.preventDefault()
          onSave({ name: name.trim(), text })
        }}
      >
        <header className="wizard-head">
          <h2>{motd ? `Edit ${motd.name}` : 'New MOTD'}</h2>
        </header>
        <div className="wizard-body">
          <Field label="Name" hint="Only for picking it on the dashboard.">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="WTC" maxLength={60} required autoFocus />
          </Field>
          {/* not a Field: a <label> around the toolbar would hand a click on "MOTD" to its first swatch */}
          <div className="field">
            <span id="motd-text-label">MOTD</span>
            <MotdEditor value={text} onChange={setText} labelledBy="motd-text-label" />
            <small>
              Two lines. Select text to colour or style it; alignment is for the line the caret is on. Each server gets its own
              pack and version in the tags, lined up to match.
            </small>
          </div>
          <ErrorNote>{error}</ErrorNote>
        </div>
        <footer className="wizard-foot">
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="button button-primary" disabled={busy || !text.trim()}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </footer>
      </form>
    </Dialog>
  )
}
