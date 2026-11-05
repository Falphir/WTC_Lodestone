import { useState } from 'react'
import { renderMotd } from '../../format'
import { Dialog } from '../ui/Dialog'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { MinecraftText } from '../ui/MinecraftText'

/** What the preview fills the variables with, since no one server is being looked at here. */
const SAMPLE = { modpack: 'Greg-E', version: '0.6.4a', name: 'WTC Genesis' }

/** Adds a network MOTD, or edits one (`motd` given), with a preview as the server list shows it. */
export function MotdEditDialog({ motd, busy, error, onClose, onSave }) {
  const [name, setName] = useState(motd?.name ?? '')
  const [text, setText] = useState(motd?.text ?? '')
  // a pasted server.properties line has \n for its line break
  const preview = renderMotd(text.replace(/\\n/g, '\n'), SAMPLE)

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
          <Field
            label="MOTD"
            hint="Two lines. {modpack}, {version} and {name} are filled in from each server's listing. § or & codes add colour (§6 gold, §e yellow, §7 gray) and style (§l bold, §m strikethrough, §r reset)."
          >
            <textarea className="mono" value={text} onChange={(e) => setText(e.target.value)} rows={3} maxLength={300} wrap="off" required />
          </Field>
          <div className="field">
            <span>Preview</span>
            <MinecraftText text={preview ?? text} />
            <small>
              Filled in with {SAMPLE.modpack} {SAMPLE.version}. Spacing is close to the game's, not exact.
            </small>
          </div>
          <ErrorNote>{error}</ErrorNote>
        </div>
        <footer className="wizard-foot">
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="button button-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </footer>
      </form>
    </Dialog>
  )
}
