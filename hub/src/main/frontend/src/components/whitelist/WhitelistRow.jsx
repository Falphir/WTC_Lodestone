import { PlayerHead } from '../player/PlayerHead'
import { formatDate } from '../../format'

/** One whitelisted player: who they are, their Discord link, who added them and when. */
export function WhitelistRow({ player: p, busy, onRemove }) {
  return (
    <tr>
      <td>
        <div className="cell-player">
          <PlayerHead id={p.uuid} />
          <div>
            <span className="cell-title">{p.name}</span>
            <span className="cell-sub mono">{p.uuid}</span>
          </div>
        </div>
      </td>
      <td data-label="Discord">
        {p.linkedDiscordId ? (
          <a className="mono" href={`https://discord.com/users/${p.linkedDiscordId}`} target="_blank" rel="noreferrer">
            {p.linkedDiscordId}
          </a>
        ) : (
          <span className="muted">Not linked</span>
        )}
      </td>
      <td data-label="Added by" className="muted">{p.addedBy}</td>
      <td data-label="Added" className="muted">{formatDate(p.addedAt)}</td>
      <td className="actions">
        <button type="button" className="button button-quiet button-danger" disabled={busy} onClick={() => onRemove(p)}>
          Remove
        </button>
      </td>
    </tr>
  )
}
