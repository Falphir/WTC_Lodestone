import { Badge } from '../ui/Badge'
import { Panel } from '../ui/Panel'
import { plural } from '../../format'

/**
 * Which servers have applied the current list; changes reach each one on its next poll. `syncOf` is
 * the format.js badge for the list in question: whitelistSync or banSync.
 */
export function SyncPanel({ servers, syncOf }) {
  const rows = servers.map((s) => ({ ...s, sync: syncOf(s) })).sort((a, b) => a.name.localeCompare(b.name))
  const current = rows.filter((s) => s.sync.tone === 'good').length
  return (
    <Panel as="section" aria-label="Sync status">
      <p className="panel-note">
        Up to date on {current} of {plural(rows.length, 'server')}.
      </p>
      <ul className="sync-list">
        {rows.map((s) => (
          <li key={s.id}>
            <a href={`#/servers/${encodeURIComponent(s.id)}`}>{s.name}</a>
            <Badge tone={s.sync.tone}>{s.sync.label}</Badge>
            <small className="muted">{s.sync.detail}</small>
          </li>
        ))}
      </ul>
    </Panel>
  )
}
