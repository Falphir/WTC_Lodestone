import { PlayerHead } from '../player/PlayerHead'
import { Badge } from '../ui/Badge'
import { formatDate, formatDateTime } from '../../format'

/** One banned player: who, why, by whom, since when and until when. */
export function BanRow({ ban: b, busy, onUnban }) {
  return (
    <tr>
      <td>
        <div className="cell-player">
          <PlayerHead id={b.uuid} />
          <div>
            <span className="cell-title">{b.name}</span>
            <span className="cell-sub mono">{b.uuid}</span>
          </div>
        </div>
      </td>
      <td data-label="Reason">{b.reason || <span className="muted">None given</span>}</td>
      <td data-label="Banned by" className="muted">{b.bannedBy}</td>
      <td data-label="Banned" className="muted">{formatDate(b.bannedAt)}</td>
      <td data-label="Expires">
        {b.expired ? (
          <Badge tone="none">Expired</Badge>
        ) : b.expiresAt ? (
          formatDateTime(b.expiresAt)
        ) : (
          <span className="muted">Never</span>
        )}
      </td>
      <td className="actions">
        <button type="button" className="button button-quiet button-danger" disabled={busy} onClick={() => onUnban(b)}>
          Unban
        </button>
      </td>
    </tr>
  )
}
