import { Panel } from '../ui/Panel'
import { Badge } from '../ui/Badge'
import { RelativeTime } from '../ui/RelativeTime'
import { applicationStatus } from '../../format'

/** The applications in one status, each row opening that application beside the list. The
 * Minecraft name sits under the Discord one: it's the name staff know players by in game. */
export function ApplicationTable({ applications, selectedId }) {
  return (
    <Panel>
      <table className="table table-cards">
        <thead>
          <tr>
            <th>Applicant</th>
            <th>Applied</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {applications.map((a) => {
            const status = applicationStatus(a.status)
            return (
              <tr key={a.discordId} className="row-link" aria-current={a.discordId === selectedId || undefined}>
                <td>
                  <a href={`#/applications/${encodeURIComponent(a.discordId)}`} className="row-anchor">
                    <span className="cell-title">{a.discordName || a.discordId}</span>
                    <span className="cell-sub">{a.answers?.mc ?? 'No answers recorded'}</span>
                  </a>
                </td>
                <td data-label="Applied" className="muted">
                  <RelativeTime value={new Date(a.createdAt).toISOString()} />
                </td>
                <td data-label="Status">
                  <span>
                    <Badge tone={status.tone}>{status.label}</Badge>
                    {a.botPending && <span className="cell-sub">Waiting for the bot</span>}
                  </span>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </Panel>
  )
}
