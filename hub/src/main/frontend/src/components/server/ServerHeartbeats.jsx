import { useMemo, useState } from 'react'
import { ErrorNote } from '../ui/ErrorNote'
import { HeartbeatTable } from './HeartbeatTable'
import { LineChart } from '../charts/LineChart'
import { formatMemory, formatMspt, formatTps, TICK_BUDGET_MS } from '../../format'
import { useApi, useHubInfo } from '../../hooks'

const RANGES = [
  { hours: 1, label: 'Last hour' },
  { hours: 24, label: 'Last 24 hours' },
  { hours: 168, label: 'Last 7 days' },
]

/**
 * A server's heartbeat history: range picker, charts, and the same rows as a table. It owns the
 * fetch rather than taking the rows as a prop, because the range the fetch depends on is picked
 * here and the page above has no other use for either. `latest` only supplies the chart ceilings.
 */
export function ServerHeartbeats({ id, latest }) {
  const [hours, setHours] = useState(24)
  const [showTable, setShowTable] = useState(false)
  const info = useHubInfo()
  const { data: heartbeats, error, stale } = useApi(
    `/api/admin/servers/${encodeURIComponent(id)}/heartbeats?hours=${hours}`,
    { refreshMs: 120_000, topics: ['servers'], serverId: id },
  )

  const series = useMemo(() => {
    const ascending = [...(heartbeats ?? [])].reverse()
    const at = (h) => new Date(h.recordedAt).getTime()
    return {
      players: ascending.map((h) => ({ t: at(h), v: h.playerCount })),
      tps: ascending.map((h) => ({ t: at(h), v: h.tps })),
      // 0 for heartbeats recorded before the mod reported tick time, so don't draw the chart at all
      // until there is something real in it rather than a flat line along the axis
      mspt: ascending.some((h) => h.msptAvg > 0) ? ascending.map((h) => ({ t: at(h), v: h.msptAvg })) : [],
      memory: ascending.map((h) => ({ t: at(h), v: h.memoryUsedMb })),
    }
  }, [heartbeats])

  // the range is anchored at "now" on every render so the axis keeps moving with the auto-refresh
  // eslint-disable-next-line react-hooks/purity
  const to = Date.now()
  const from = to - hours * 3600_000
  // the hub's offline threshold; until /api/info loads, assume the default 3 missed minutes
  const gapMs = (info?.offlineAfterSeconds ?? 180) * 1000
  const describe = `${heartbeats?.length ?? 0} heartbeats, ${RANGES.find((r) => r.hours === hours).label.toLowerCase()}`

  return (
    <>
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

      <ErrorNote>{error}</ErrorNote>

      {!heartbeats ? (
        !error && <p className="muted">Loading heartbeats…</p>
      ) : showTable ? (
        <HeartbeatTable heartbeats={heartbeats} />
      ) : (
        <div className={`charts${stale ? ' is-stale' : ''}`}>
          <LineChart
            title="Players"
            points={series.players}
            from={from}
            to={to}
            yMax={latest?.maxPlayers}
            format={(v) => Math.round(v).toLocaleString()}
            gapMs={gapMs}
            describe={describe}
          />
          <LineChart
            title="TPS"
            points={series.tps}
            from={from}
            to={to}
            yMax={20}
            format={formatTps}
            gapMs={gapMs}
            describe={describe}
          />
          {series.mspt.length > 0 && (
            <LineChart
              title="MSPT"
              points={series.mspt}
              from={from}
              to={to}
              yMax={TICK_BUDGET_MS}
              format={formatMspt}
              gapMs={gapMs}
              describe={describe}
            />
          )}
          <LineChart
            title="Memory"
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
    </>
  )
}
