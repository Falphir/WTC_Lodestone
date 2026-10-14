import { formatMemory, formatMspt, formatTps, msptTone } from '../../format'

/** The numbers from a server's latest heartbeat. */
export function ServerFigures({ latest, laggingBelowTps }) {
  return (
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
      {/* Second row, on its own because more server figures are meant to join it. Skipped
          entirely for a mod too old to report tick time, which sends 0 rather than nothing. */}
      {latest.msptAvg > 0 && (
        <div className="figure-row">
          <dt>Tick time</dt>
          <dd className={`tone-${msptTone(latest.msptAvg, laggingBelowTps)}`}>
            {formatMspt(latest.msptAvg)}
          </dd>
        </div>
      )}
    </dl>
  )
}
