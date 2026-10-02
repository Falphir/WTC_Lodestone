import { ErrorNote, Sparkline, StatusBadge } from '../components'
import { formatMemory, formatTps, isUp, plural, relativeTime, statusOf } from '../format'
import { useApi } from '../hooks'

const SPARK_HOURS = 6

export default function Overview() {
  const { data: servers, error, stale } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers'] })

  if (!servers) {
    return (
      <div className="page">
        <h1>Overview</h1>
        {error ? <ErrorNote>{error}</ErrorNote> : <p className="muted">Loading servers…</p>}
      </div>
    )
  }

  const rows = servers
    .map((s) => ({ ...s, status: statusOf(s) }))
    .sort((a, b) => Number(isUp(b.status)) - Number(isUp(a.status)) || a.name.localeCompare(b.name))
  const up = rows.filter((s) => isUp(s.status))
  const players = up.reduce((sum, s) => sum + (s.latest?.playerCount ?? 0), 0)
  const down = rows.filter((s) => s.status === 'offline')

  return (
    <div className={`page${stale ? ' is-stale' : ''}`}>
      <h1 className="visually-hidden">Overview</h1>

      <section className="hero" aria-label="Network">
        <p className="hero-figure">{players.toLocaleString()}</p>
        <p className="hero-label">
          {players === 1 ? 'player' : 'players'} online
          {rows.length > 0 && (
            <span className="muted">
              {' '}
              {up.length === rows.length
                ? rows.length === 1
                  ? 'on the only server'
                  : rows.length === 2
                    ? 'across both servers'
                    : `across all ${rows.length} servers`
                : `across ${up.length} of ${plural(rows.length, 'server')}`}
            </span>
          )}
        </p>
        {down.length > 0 && (
          <p className="hero-alert">
            {down.length === 1
              ? `${down[0].name} has stopped reporting. Last heartbeat ${relativeTime(down[0].lastSeen)}.`
              : `${new Intl.ListFormat('en').format(down.map((s) => s.name))} have stopped reporting.`}
          </p>
        )}
      </section>

      <ErrorNote>{error}</ErrorNote>

      {rows.length === 0 ? (
        <div className="empty">
          <h2>No servers yet</h2>
          <p>
            Register a server to get its token, then put the token in the mod config.{' '}
            <a href="#/servers">Register a server</a>
          </p>
        </div>
      ) : (
        <div className="panel">
          <table className="table">
            <thead>
              <tr>
                <th>Server</th>
                <th>Status</th>
                <th className="num">Players</th>
                <th className="num">TPS</th>
                <th className="num hide-sm">Memory</th>
                <th className="hide-sm">Players, last 6 hours</th>
                <th className="hide-md">Last heartbeat</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <ServerRow key={s.id} server={s} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function ServerRow({ server }) {
  const { data: recent } = useApi(`/api/admin/servers/${encodeURIComponent(server.id)}/heartbeats?hours=${SPARK_HOURS}`, {
    refreshMs: 120_000,
    topics: ['servers'],
    serverId: server.id,
  })
  const latest = isUp(server.status) ? server.latest : null
  const points = recent?.map((h) => ({ t: new Date(h.recordedAt).getTime(), v: h.playerCount })).reverse()
  // eslint-disable-next-line react-hooks/purity -- the 6h window ends at "now" each refresh
  const to = Date.now()

  return (
    <tr className="row-link">
      <td>
        <a href={`#/servers/${encodeURIComponent(server.id)}`} className="row-anchor">
          <span className="cell-title">{server.name}</span>
          <span className="cell-sub mono">{server.id}</span>
        </a>
      </td>
      <td>
        <StatusBadge status={server.status} />
      </td>
      <td className="num">{latest ? `${latest.playerCount} / ${latest.maxPlayers}` : '–'}</td>
      <td className="num">{latest ? formatTps(latest.tps) : '–'}</td>
      <td className="num hide-sm">{latest ? formatMemory(latest.memoryUsedMb) : '–'}</td>
      <td className="hide-sm">
        <Sparkline points={points} from={to - SPARK_HOURS * 3600_000} to={to} />
      </td>
      <td className="hide-md muted">{relativeTime(server.lastSeen)}</td>
    </tr>
  )
}
