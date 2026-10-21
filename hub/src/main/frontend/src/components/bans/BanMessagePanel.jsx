import { useState } from 'react'
import { api } from '../../api'
import { useAction, useApi } from '../../hooks'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { Note } from '../ui/Note'
import { Panel } from '../ui/Panel'

/** What a network-banned player sees; blank goes back to the hub's built-in default. */
export function BanMessagePanel() {
  const { data, reload } = useApi('/api/admin/bans/message', { topics: ['bans'] })
  const [draft, setDraft] = useState(null)
  const { run, busy, message, error } = useAction(reload)
  const value = draft ?? data?.message ?? ''

  function save(event) {
    event.preventDefault()
    run(async () => {
      const saved = await api('/api/admin/bans/message', { method: 'PUT', body: { message: value } })
      setDraft(saved.message)
      return 'Saved. Servers pick it up on their next heartbeat.'
    })
  }

  return (
    <Panel as="form" pad className="form" onSubmit={save}>
      <Field
        label="Message for banned players"
        hint="Shown when a network-banned player tries to join or is kicked. {reason} and {expires} are filled in; &c-style codes add color (&c red, &7 gray, &f white, &l bold). Bans made in-game with /ban keep Minecraft's message. Leave blank for the default."
      >
        <textarea value={value} onChange={(e) => setDraft(e.target.value)} maxLength={500} rows={4} />
      </Field>
      <Note>{message}</Note>
      <ErrorNote>{error}</ErrorNote>
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={busy || !data || value === data.message}>
          {busy ? 'Saving…' : 'Save message'}
        </button>
      </div>
    </Panel>
  )
}
