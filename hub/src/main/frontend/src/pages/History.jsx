import { useState } from 'react'
import { api } from '../api'
import { EmptyState, ErrorNote, PageHead, Panel, RelativeTime } from '../components'
import { auditAction } from '../format'
import { useApi } from '../hooks'

const PAGE = 50

/** Entries never change once written, so pages are merged by id and kept, newest first. */
function merge(kept, more) {
  const byId = new Map(kept.map((e) => [e.id, e]))
  for (const e of more) byId.set(e.id, e)
  return [...byId.values()].sort((a, b) => b.id - a.id)
}

export default function History() {
  const { data: newest, error: loadError } = useApi(`/api/admin/audit?limit=${PAGE}`, { topics: ['audit'] })
  // Older pages, plus the newest page as it was when they were asked for: a live refresh then only
  // adds on top, and nothing that was on screen falls into a gap between the two.
  const [older, setOlder] = useState([])
  const [more, setMore] = useState({ busy: false, done: false, error: null })
  const entries = merge(older, newest ?? [])
  const done = more.done || (newest && newest.length < PAGE)

  async function loadMore() {
    setMore({ busy: true, done: false, error: null })
    try {
      const page = await api(`/api/admin/audit?limit=${PAGE}&before=${entries[entries.length - 1].id}`)
      setOlder((kept) => merge(kept, [...entries, ...page]))
      setMore({ busy: false, done: page.length < PAGE, error: null })
    } catch (error) {
      setMore({ busy: false, done: false, error: error.message })
    }
  }

  return (
    <div className="page">
      <PageHead>
        <h1>History</h1>
        <p className="muted">Who changed what, from the dashboard or Discord. Kept for 180 days.</p>
      </PageHead>

      <ErrorNote>{loadError || more.error}</ErrorNote>

      {newest && entries.length === 0 ? (
        <EmptyState title="Nothing yet">Changes made from the dashboard or the Discord bot show up here.</EmptyState>
      ) : (
        <Panel>
          <table className="table table-cards">
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>What</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((e) => (
                <tr key={e.id}>
                  <td data-label="When" className="muted">
                    <RelativeTime value={e.at} />
                  </td>
                  <td data-label="Who">
                    {/* for the bot's calls, the staff member it acted for is who actually did it */}
                    {e.via ? (
                      <span>
                        {e.via} <span className="muted">via {e.actor}</span>
                      </span>
                    ) : (
                      e.actor
                    )}
                  </td>
                  <td data-label="What">
                    {/* one span, so a card keeps the action and its target together on the right */}
                    <span>
                      {auditAction(e.action)} <strong>{e.target}</strong>
                    </span>
                  </td>
                  <td data-label="Details" className="muted" data-empty={e.detail ? undefined : ''}>
                    {e.detail}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}

      {entries.length > 0 && !done && (
        <div className="button-row">
          <button type="button" className="button" disabled={more.busy} onClick={loadMore}>
            {more.busy ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </div>
  )
}
