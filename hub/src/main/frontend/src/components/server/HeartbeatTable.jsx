import { Panel } from '../ui/Panel'
import { formatDateTime, formatMemory, formatMspt, formatTps } from '../../format'

const TABLE_LIMIT = 500

/** The same heartbeats the charts draw, as raw rows. */
export function HeartbeatTable({ heartbeats }) {
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
              <th className="num">Tick time</th>
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
                <td className="num">{h.msptAvg > 0 ? formatMspt(h.msptAvg) : '–'}</td>
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
