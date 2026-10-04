import { useMemo, useState } from 'react'
import { Badge, EmptyState, ErrorNote, Icon, LineChart, PageHead, Panel, StatusBadge } from '../components'
import { formatDateTime, formatMemory, formatTps, relativeTime, statusOf, whitelistSync } from '../format'
import { useApi, useHubInfo } from '../hooks'

const RANGES = [
  { hours: 1, label: 'Last hour' },
  { hours: 24, label: 'Last 24 hours' },
  { hours: 168, label: 'Last 7 days' },
]

export default function ServerDetail({ id }) {
  const [hours, setHours] = useState(24)
  const [showTable, setShowTable] = useState(false)
  const info = useHubInfo()
  const { data: servers, error: serversError } = useApi('/api/admin/servers', { refreshMs: 60_000, topics: ['servers'] })
  const { data: heartbeats, error, stale } = useApi(`/api/admin/servers/${encodeURIComponent(id)}/heartbeats?hours=${hours}`, {
    refreshMs: 120_000,
    topics: ['servers'],
    serverId: id,
  })
  const server = servers?.find((s) => s.id === id)

  const series = useMemo(() => {
    const ascending = [...(heartbeats ?? [])].reverse()
    const at = (h) => new Date(h.recordedAt).getTime()
    return {
      players: ascending.map((h) => ({ t: at(h), v: h.playerCount })),
      tps: ascending.map((h) => ({ t: at(h), v: h.tps })),
      memory: ascending.map((h) => ({ t: at(h), v: h.memoryUsedMb })),
    }
  }, [heartbeats])

  if (servers && !server) {
    return (
      <div className="page">
        <a href="#/servers" className="back">
          <Icon name="back" />
          Servers
        </a>
        <EmptyState title={`No server called “${id}”`}>
          It may have been removed. <a href="#/servers">See all servers</a>
        </EmptyState>
      </div>
    )
  }

  // the range is anchored at "now" on every render so the axis keeps moving with the auto-refresh
  // eslint-disable-next-line react-hooks/purity
  const to = Date.now()
  const from = to - hours * 3600_000
  const latest = server?.latest
  // the hub's offline threshold; until /api/info loads, assume the default 3 missed minutes
  const gapMs = (info?.offlineAfterSeconds ?? 180) * 1000
  const describe = `${heartbeats?.length ?? 0} heartbeats, ${RANGES.find((r) => r.hours === hours).label.toLowerCase()}`

  return (
    <div className="page">
      <a href="#/servers" className="back">
        <Icon name="back" />
        Servers
      </a>

      <PageHead
        actions={
          server && (
            <div className="head-status">
              <StatusBadge status={statusOf(server)} />
              <small className="muted">Last heartbeat {relativeTime(server.lastSeen)}</small>
            </div>
          )
        }
      >
        <h1>{server?.name ?? id}</h1>
        <p className="muted mono">{id}</p>
      </PageHead>

      <ErrorNote>{serversError || error}</ErrorNote>

      {latest && (
        <dl className="figures">
          <div>
            <dt>Players</dt>
            <dd>
              {latest.playerCount}
              <span className="muted"> / {latest.maxPlayers}</span>
            </dd>
          </div>
          <div>
            <dt>TPS</dt>
            <dd>{formatTps(latest.tps)}</dd>
          </div>
          <div>
            <dt>Memory</dt>
            <dd>
              {formatMemory(latest.memoryUsedMb)}
              <span className="muted"> / {formatMemory(latest.memoryMaxMb)}</span>
            </dd>
          </div>
        </dl>
      )}

      {server && <Setup server={server} />}

      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Time range">
          {RANGES.map((r) => (
            <button key={r.hours} type="button" aria-pressed={hours === r.hours} onClick={() => setHours(r.hours)}>
              {r.label}
            </button>
          ))}
        </div>
        <button type="button" className="button button-quiet" aria-pressed={showTable} onClick={() => setShowTable((v) => !v)}>
          {showTable ? 'Show charts' : 'Show as table'}
        </button>
      </div>

      {!heartbeats ? (
        !error && <p className="muted">Loading heartbeats…</p>
      ) : showTable ? (
        <HeartbeatTable heartbeats={heartbeats} />
      ) : (
        <div className={`charts${stale ? ' is-stale' : ''}`}>
          <LineChart
            title="Players online"
            points={series.players}
            from={from}
            to={to}
            yMax={latest?.maxPlayers}
            format={(v) => Math.round(v).toLocaleString()}
            gapMs={gapMs}
            describe={describe}
          />
          <LineChart
            title="Ticks per second"
            points={series.tps}
            from={from}
            to={to}
            yMax={20}
            format={formatTps}
            gapMs={gapMs}
            describe={describe}
          />
          <LineChart
            title="Memory used"
            points={series.memory}
            from={from}
            to={to}
            yMax={latest?.memoryMaxMb}
            format={formatMemory}
            gapMs={gapMs}
            describe={describe}
          />
        </div>
      )}
    </div>
  )
}

const TABLE_LIMIT = 500

/** What the mod reported about itself on check-in. */
function Setup({ server }) {
  const sync = whitelistSync(server)
  const { version, minecraftVersion, loaderVersion } = server.mod
  return (
    <dl className="setup">
      <div>
        <dt>Mod</dt>
        <dd className="mono">{version ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>Minecraft</dt>
        <dd className="mono">{minecraftVersion ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>NeoForge</dt>
        <dd className="mono">{loaderVersion ?? 'Not reported'}</dd>
      </div>
      <div>
        <dt>Whitelist</dt>
        <dd>
          <Badge tone={sync.tone}>{sync.label}</Badge>
          <small className="muted">{sync.detail}</small>
        </dd>
      </div>
    </dl>
  )
}

function HeartbeatTable({ heartbeats }) {
  if (heartbeats.length === 0) return <p className="muted">No heartbeats in this range.</p>
  return (
    <Panel>
      {heartbeats.length > TABLE_LIMIT && (
        <p className="panel-note muted">
          Showing the latest {TABLE_LIMIT} of {heartbeats.length.toLocaleString()} heartbeats. Pick a shorter range to see
          them all.
        </p>
      )}
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>Time</th>
              <th className="num">Players</th>
              <th className="num">TPS</th>
              <th className="num">Memory</th>
            </tr>
          </thead>
          <tbody>
            {heartbeats.slice(0, TABLE_LIMIT).map((h) => (
              <tr key={h.recordedAt}>
                <td className="muted">{formatDateTime(h.recordedAt)}</td>
                <td className="num">
                  {h.playerCount} / {h.maxPlayers}
                </td>
                <td className="num">{formatTps(h.tps)}</td>
                <td className="num">
                  {formatMemory(h.memoryUsedMb)} / {formatMemory(h.memoryMaxMb)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}
