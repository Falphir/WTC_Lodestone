import { PlayerHead } from './PlayerHead'

/**
 * Who is on, as of each server's last heartbeat. Renders nothing for an empty list on purpose: the
 * hub keeps presence in memory only (see ServerService.onlinePlayers), so "empty" means "not known"
 * -- a server running an older mod, or the first minute after a hub restart -- and never "nobody is
 * playing". The player count is what says how many are on.
 *
 * Each player is `{ uuid, name }`, optionally with a `note` shown underneath (the server they're on).
 */
export function OnlinePlayers({ players }) {
  if (!players?.length) return null
  return (
    <ul className="online-players">
      {players.map((p) => (
        <li key={`${p.note ?? ''}:${p.uuid}`}>
          <PlayerHead id={p.uuid} size={32} />
          <span>
            <span className="cell-title">{p.name}</span>
            {p.note && <span className="cell-sub">{p.note}</span>}
          </span>
        </li>
      ))}
    </ul>
  )
}
