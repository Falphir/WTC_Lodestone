import { useState } from 'react'
import { api } from '../../api'
import { useAction, useApi } from '../../hooks'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { Note } from '../ui/Note'
import { Panel } from '../ui/Panel'

/** The network-wide kick message; blank hands each server back to its own mod config's whitelistMessage. */
export function KickMessagePanel() {
  const { data, reload } = useApi('/api/admin/whitelist/kick-message', { topics: ['whitelist'] })
  const [draft, setDraft] = useState(null)
  const { run, busy, message, error } = useAction(reload)
  const value = draft ?? data?.message ?? ''

  function save(event) {
    event.preventDefault()
    run(async () => {
      await api('/api/admin/whitelist/kick-message', { method: 'PUT', body: { message: value } })
      return value.trim() ? 'Saved. Servers pick it up on their next heartbeat.' : "Cleared. Servers use their own config's message again."
    })
  }

  return (
    <Panel as="form" pad className="form" onSubmit={save}>
      <Field
        label="Message for players who aren't whitelisted"
        hint="Shown on every server instead of Minecraft's own. &c-style codes add color (&c red, &a green, &7 gray, &l bold, &r reset). Leave blank to use each server's own whitelistMessage."
      >
        <textarea
          value={value}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="&cYou are not whitelisted on the WTC Network. &7Apply on our Discord."
          maxLength={500}
          rows={2}
        />
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
