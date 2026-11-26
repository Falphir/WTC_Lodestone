import { useCallback, useState } from 'react'
import { api } from '../api'
import { ApplicationDetail, ApplicationTable, EmptyState, ErrorNote, Note, PageHead } from '../components'
import { plural } from '../format'
import { useAction, useApi } from '../hooks'

const FILTERS = [
  { status: 'pending', label: 'Pending', empty: 'Nobody is waiting for a decision.' },
  { status: 'approved', label: 'Approved', empty: 'No approved applications. The hub keeps them for a day.' },
  { status: 'denied', label: 'Denied', empty: 'No denied applications. The hub keeps them until the re-apply wait ends.' },
  { status: '', label: 'All', empty: 'No applications.' },
]

const PATH = '/api/admin/discord/applications'

// No export or download here on purpose: the page only reads what the hub still holds, so
// applications disappear from it on the same schedule they disappear from the hub.
export default function Applications({ id }) {
  const [status, setStatus] = useState('pending')
  const filter = FILTERS.find((f) => f.status === status)
  const list = useApi(`${PATH}${status ? `?status=${status}` : ''}`, { topics: ['applications'] })
  const selected = useApi(id ? `${PATH}/${encodeURIComponent(id)}` : null, { topics: ['applications'] })
  const { reload: reloadList } = list
  const { reload: reloadSelected } = selected
  const reloadBoth = useCallback(() => {
    reloadList()
    reloadSelected()
  }, [reloadList, reloadSelected])
  const { run, busy, message, error } = useAction(reloadBoth)

  const app = selected.data?.discordId === id ? selected.data : null
  const name = app?.discordName || id

  function decide(decision, reason) {
    run(async () => {
      await api(`${PATH}/${encodeURIComponent(id)}/decision`, {
        method: 'PUT',
        body: { status: decision, reason: reason || null },
      })
      return decision === 'approved'
        ? `Approved ${name}. The bot gives them the member role within a minute.`
        : `Denied ${name}. The bot tells them in a DM within a minute${reason ? ', with your reason' : ''}.`
    })
  }

  function approve() {
    if (!window.confirm(`Approve ${name}? The bot gives them the member role and welcomes them in Discord.`)) return
    decide('approved')
  }

  const applications = list.data
  return (
    <div className="page">
      <PageHead>
        <h1>Applications</h1>
        <p className="muted">
          Sent with /apply in Discord. A decision here reaches Discord through the bot.
          {applications && ` ${plural(applications.length, filter.status ? `${filter.label.toLowerCase()} application` : 'application')}.`}
        </p>
      </PageHead>

      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Status">
          {FILTERS.map((f) => (
            <button key={f.label} type="button" aria-pressed={status === f.status} onClick={() => setStatus(f.status)}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      <Note>{message}</Note>
      <ErrorNote>{error || list.error || (id && selected.error)}</ErrorNote>

      <div className={app ? 'review' : undefined}>
        {applications?.length === 0 && <EmptyState title="Nothing here">{filter.empty}</EmptyState>}
        {applications?.length > 0 && <ApplicationTable applications={applications} selectedId={id} />}
        {app && (
          <ApplicationDetail
            key={app.discordId}
            application={app}
            busy={busy}
            onApprove={approve}
            onDeny={(reason) => decide('denied', reason)}
          />
        )}
      </div>
    </div>
  )
}
