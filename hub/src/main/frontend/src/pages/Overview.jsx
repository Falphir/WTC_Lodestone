import { useCallback, useEffect, useMemo, useState } from 'react'
import { EmptyState, ErrorNote, LineChart, Panel, Sparkline, StatusBadge } from '../components'
import { formatMemory, formatMspt, formatTps, isUp, msptTone, plural, relativeTime, statusOf } from '../format'
import { useApi, useHubInfo } from '../hooks'

const WINDOW_HOURS = 6
// Heartbeats land a minute apart and each server's land on its own seconds, so the network line is
// summed per 5-minute bucket: fine enough to show a rush starting, coarse enough that two servers
// reporting 20 seconds apart count as the same moment rather than as two half-height steps.
const BUCKET_MS = 5 * 60_000

export default function Overview() {
  const info = useHubInfo()
  const { data: servers, error, stale } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers'] })

  // Each row fetches its own 6h of heartbeats for its sparkline and hands the series up here, so
  // the network chart is the sum of what is already on the page rather than a second round of
  // requests. Rows drop their entry when they unmount, so a removed server leaves nothing behind.
  const [history, setHistory] = useState({})
  const report = useCallback((id, points) => {
    setHistory((current) => (current[id] === points ? current : { ...current, [id]: points }))
  }, [])
  const forget = useCallback((id) => {
    setHistory((current) => {
      if (!(id in current)) return current
      const rest = { ...current }
      delete rest[id]
      return rest
    })
  }, [])
  const network = useMemo(
    () => ({ players: sumByBucket(history, 'players'), memory: sumByBucket(history, 'memory') }),
    [history],
  )

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
  const slots = up.reduce((sum, s) => sum + (s.latest?.maxPlayers ?? 0), 0)
  // Heap across the servers that are reporting: what the network is costing the box right now.
  const memoryUsed = up.reduce((sum, s) => sum + (s.latest?.memoryUsedMb ?? 0), 0)
  const memoryMax = up.reduce((sum, s) => sum + (s.latest?.memoryMaxMb ?? 0), 0)
  // Only servers that have reported before have "stopped": a never-connected one isn't news.
  const down = rows.filter((s) => s.status === 'offline')
  const busiest = network.players.length ? Math.max(...network.players.map((p) => p.v)) : null

  // the window ends at "now" on every render, so it keeps moving with the auto-refresh
  // eslint-disable-next-line react-hooks/purity
  const to = Date.now()
  const from = to - WINDOW_HOURS * 3600_000
  const gapMs = Math.max((info?.offlineAfterSeconds ?? 180) * 1000, BUCKET_MS * 2)
  const describe = `${plural(rows.length, 'server')}, last ${WINDOW_HOURS} hours`

  return (
    <div className={`page${stale ? ' is-stale' : ''}`}>
      <h1 className="visually-hidden">Overview</h1>

      <p className="summary">
        {players === 0 ? 'Nobody online right now' : `${players.toLocaleString()} ${players === 1 ? 'player' : 'players'} online`}
        {rows.length > 0 && <span className="muted"> {reach(up.length, rows.length)}</span>}
      </p>
      {down.length > 0 && (
        <p className="summary-alert">
          {down.length === 1
            ? `${down[0].name} stopped reporting. Last heartbeat ${relativeTime(down[0].lastSeen)}.`
            : `${new Intl.ListFormat('en').format(down.map((s) => s.name))} have stopped reporting.`}
        </p>
      )}

      <ErrorNote>{error}</ErrorNote>

      {rows.length === 0 ? (
        <EmptyState title="No servers yet">
          Register a server to get its token, then put the token in the mod config. <a href="#/servers">Register a server</a>
        </EmptyState>
      ) : (
        <>
          <dl className="figures figures-network">
            <div>
              <dt>Players online</dt>
              <dd>
                {players.toLocaleString()}
                {slots > 0 && <span className="muted"> / {slots.toLocaleString()}</span>}
              </dd>
            </div>
            <div>
              <dt>Servers reporting</dt>
              <dd>
                {up.length}
                <span className="muted"> of {rows.length}</span>
              </dd>
            </div>
            <div>
              <dt>Memory used</dt>
              <dd>
                {memoryMax > 0 ? (
                  <>
                    {formatMemory(memoryUsed)}
                    <span className="muted"> / {formatMemory(memoryMax)}</span>
                  </>
                ) : (
                  '–'
                )}
              </dd>
            </div>
            <div>
              <dt>Busiest, last {WINDOW_HOURS} hours</dt>
              <dd>{busiest === null ? '–' : busiest.toLocaleString()}</dd>
            </div>
          </dl>

          {/* No yMax on players: the figures above already say how full the network is (23 / 64),
              so the plot is free to use its height for the shape. Memory keeps its ceiling, since
              how close the heap is to the limit is the whole question. The gap has to clear the
              bucket spacing -- 5-minute points would otherwise read as one silence after another. */}
          <div className="charts charts-network">
            <LineChart
              title="Players across the network"
              points={network.players}
              from={from}
              to={to}
              // A quiet network would otherwise get a ceiling of 1 and an axis reading 1, 1, 0 --
              // both halves rounding to the same label. Four is the smallest clean axis.
              yMax={busiest !== null && busiest < 4 ? 4 : undefined}
              format={(v) => Math.round(v).toLocaleString()}
              gapMs={gapMs}
              describe={describe}
            />
            <LineChart
              title="Memory across the network"
              points={network.memory}
              from={from}
              to={to}
              yMax={memoryMax || undefined}
              format={formatMemory}
              gapMs={gapMs}
              describe={describe}
            />
          </div>

          <Panel>
            <table className="table table-cards network">
              <thead>
                <tr>
                  <th>Server</th>
                  <th>Status</th>
                  <th className="num">Players</th>
                  <th className="num">Tick time</th>
                  <th className="num hide-sm">Memory</th>
                  <th className="hide-sm">Last {WINDOW_HOURS} hours</th>
                  <th className="hide-md">Last heartbeat</th>
                </tr>
              </thead>
              {rows.map((s) => (
                <ServerRow
                  key={s.id}
                  server={s}
                  laggingBelowTps={info?.laggingBelowTps}
                  from={from}
                  to={to}
                  report={report}
                  forget={forget}
                />
              ))}
            </table>
          </Panel>
        </>
      )}
    </div>
  )
}

/** "across 2 of 4 servers" — how much of the network the player count speaks for. */
function reach(up, total) {
  if (total === 1) return '' // the one row below says which server, and how it's doing
  if (up === 0) return `with ${plural(total, 'server')} not reporting`
  if (up < total) return `across ${up} of ${plural(total, 'server')}`
  return `across all ${plural(total, 'server')}`
}

/**
 * Every server's players at the same moment, added up. A server that stops reporting simply stops
 * contributing, so the network line steps down by what it was carrying rather than drawing a gap
 * the other servers never had.
 */
function sumByBucket(history, measure) {
  const buckets = new Map()
  for (const [id, series] of Object.entries(history)) {
    for (const point of series?.[measure] ?? []) {
      const at = Math.round(point.t / BUCKET_MS) * BUCKET_MS
      const servers = buckets.get(at) ?? buckets.set(at, new Map()).get(at)
      servers.set(id, point.v) // one value per server per bucket: the last it reported in it
    }
  }
  return [...buckets.entries()]
    .sort(([a], [b]) => a - b)
    .map(([t, servers]) => ({ t, v: [...servers.values()].reduce((sum, v) => sum + v, 0) }))
}

const tickTone = (mspt, laggingBelowTps) => {
  const tone = msptTone(mspt, laggingBelowTps)
  return tone === 'good' ? undefined : `tone-${tone}`
}

/** One server's line: the figures from its last heartbeat, and its own shape over the window. */
function ServerRow({ server, laggingBelowTps, from, to, report, forget }) {
  const { data: recent } = useApi(`/api/admin/servers/${encodeURIComponent(server.id)}/heartbeats?hours=${WINDOW_HOURS}`, {
    refreshMs: 120_000,
    topics: ['servers'],
    serverId: server.id,
  })
  // memoised so the identity only changes when the heartbeats do -- the page above stores it, and
  // a fresh object every render would have it storing (and redrawing) on a loop
  const series = useMemo(() => {
    if (!recent) return null
    const ascending = [...recent].reverse()
    const at = (h) => new Date(h.recordedAt).getTime()
    return {
      players: ascending.map((h) => ({ t: at(h), v: h.playerCount })),
      memory: ascending.map((h) => ({ t: at(h), v: h.memoryUsedMb })),
    }
  }, [recent])
  const points = series?.players
  const id = server.id
  useEffect(() => {
    if (series) report(id, series)
  }, [id, series, report])
  useEffect(() => () => forget(id), [id, forget])

  const latest = isUp(server.status) ? server.latest : null

  return (
    <tbody className="server" data-status={server.status}>
      <tr className="row-link">
        <td>
          <a href={`#/servers/${encodeURIComponent(server.id)}`} className="row-anchor">
            <span className="cell-title">{server.name}</span>
            <span className="cell-sub mono">{server.id}</span>
          </a>
        </td>
        <td data-label="Status">
          <StatusBadge status={server.status} />
        </td>
        <td className="num players" data-label="Players" data-empty={latest ? undefined : ''}>
          {latest ? (
            <>
              <span>
                {latest.playerCount}
                <span className="muted"> / {latest.maxPlayers}</span>
              </span>
              <span className="meter" aria-hidden="true">
                <span style={{ width: `${Math.min(100, (latest.playerCount / Math.max(1, latest.maxPlayers)) * 100)}%` }} />
              </span>
            </>
          ) : (
            '–'
          )}
        </td>
        {/* Tick time, not TPS, is the number that moves first: TPS sits at 20 until a server is
            already in trouble. TPS stays underneath as the figure people quote. Only a bad tick
            time is coloured -- a column of green ticks would be decoration, and would leave the
            one orange figure with nothing to stand out against. */}
        <td className="num" data-label="Tick time" data-empty={latest ? undefined : ''}>
          {latest?.msptAvg > 0 ? (
            <span>
              <span className={tickTone(latest.msptAvg, laggingBelowTps)}>{formatMspt(latest.msptAvg)}</span>
              <span className="cell-sub">{formatTps(latest.tps)} TPS</span>
            </span>
          ) : latest ? (
            `${formatTps(latest.tps)} TPS`
          ) : (
            '–'
          )}
        </td>
        <td className="num hide-sm" data-label="Memory" data-empty={latest ? undefined : ''}>
          {latest ? (
            <span>
              {formatMemory(latest.memoryUsedMb)}
              <span className="muted"> / {formatMemory(latest.memoryMaxMb)}</span>
            </span>
          ) : (
            '–'
          )}
        </td>
        <td className="hide-sm" data-label={`Last ${WINDOW_HOURS} hours`} data-empty={points?.length > 1 ? undefined : ''}>
          <Sparkline points={points} from={from} to={to} />
        </td>
        <td className="hide-md muted" data-label="Last heartbeat">
          {relativeTime(server.lastSeen)}
        </td>
      </tr>
    </tbody>
  )
}
