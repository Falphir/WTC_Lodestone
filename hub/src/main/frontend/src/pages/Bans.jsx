import { useState } from 'react'
import { api } from '../api'
import { BanMessagePanel, BanTable, EmptyState, ErrorNote, Field, Icon, Note, PageHead, SyncPanel } from '../components'
import { banSync, plural } from '../format'
import { useAction, useApi } from '../hooks'

// Days, or null for permanent. The server lifts a ban itself once its expiry passes.
const DURATIONS = [
  { label: 'Permanent', days: null },
  { label: '1 day', days: 1 },
  { label: '7 days', days: 7 },
  { label: '30 days', days: 30 },
]

export default function Bans() {
  const { data: bans, error: loadError, reload } = useApi('/api/admin/bans', { topics: ['bans'] })
  const { data: servers } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers', 'bans'] })
  const [name, setName] = useState('')
  const [reason, setReason] = useState('')
  const [days, setDays] = useState('')
  const { run, busy, message, error } = useAction(reload)

  function ban(event) {
    event.preventDefault()
    run(async () => {
      const expiresAt = days ? new Date(Date.now() + Number(days) * 86_400_000).toISOString() : null
      const banned = await api('/api/admin/bans', {
        method: 'POST',
        body: { name: name.trim(), reason: reason.trim() || null, expiresAt },
      })
      setName('')
      setReason('')
      return `Banned ${banned.name} on every server. Anyone online is kicked within seconds.`
    })
  }

  function unban(b) {
    if (!window.confirm(`Lift ${b.name}'s ban on every server?`)) return
    run(async () => {
      await api(`/api/admin/bans/${b.uuid}`, { method: 'DELETE' })
      return `Unbanned ${b.name}.`
    })
  }

  return (
    <div className="page">
      <PageHead>
        <h1>Bans</h1>
        <p className="muted">
          Applied by every server. A server's own /ban stays local and isn't listed here.
          {bans && ` ${plural(bans.length, 'ban')}.`}
        </p>
      </PageHead>

      <form className="toolbar" onSubmit={ban}>
        <Field label="Minecraft username" hidden inline>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Minecraft username"
            maxLength={16}
            pattern="[A-Za-z0-9_]{1,16}"
            title="Letters, numbers and _ only, up to 16 characters"
          />
        </Field>
        <Field label="Reason" hidden inline>
          <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason (staff only)" maxLength={255} />
        </Field>
        <Field label="Duration" hidden inline>
          <select value={days} onChange={(e) => setDays(e.target.value)}>
            {DURATIONS.map((d) => (
              <option key={d.label} value={d.days ?? ''}>
                {d.label}
              </option>
            ))}
          </select>
        </Field>
        <button type="submit" className="button button-primary" disabled={busy || !name.trim()}>
          <Icon name="bans" />
          Ban
        </button>
      </form>

      {bans?.length > 0 && servers?.length > 0 && <SyncPanel servers={servers} syncOf={banSync} />}
      <BanMessagePanel />

      <Note>{message}</Note>
      <ErrorNote>{error || loadError}</ErrorNote>

      {bans?.length === 0 && (
        <EmptyState title="Nobody is banned">
          A ban here reaches every server within seconds and kicks the player if they're on.
        </EmptyState>
      )}

      {bans?.length > 0 && <BanTable bans={bans} busy={busy} onUnban={unban} />}
    </div>
  )
}
