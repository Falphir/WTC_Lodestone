import { Area, AreaChart, XAxis, YAxis } from 'recharts'

/**
 * Tiny trend line with no axes; the row it sits in carries the actual numbers. `points` are { t, v } placed
 * on a from..to time axis, so a server that went quiet visibly stops short of "now". The y range is the
 * series' own (at least `minSpan` tall) so the shape shows without turning one player into a cliff.
 */
export function Sparkline({ points, from, to, minSpan = 4, width = 120, height = 28 }) {
  if (!points || points.length < 2) return <span className="sparkline-empty">No recent data</span>
  const values = points.map((p) => p.v)
  let lo = Math.min(...values)
  let hi = Math.max(...values)
  if (hi - lo < minSpan) {
    lo = Math.max(0, lo - (minSpan - (hi - lo)) / 2)
    hi = lo + minSpan
  }
  const lastIndex = points.length - 1
  return (
    <AreaChart className="sparkline" width={width} height={height} data={points} margin={{ top: 3, right: 4, bottom: 3, left: 0 }}>
      <XAxis dataKey="t" type="number" domain={[from, to]} hide />
      <YAxis domain={[lo, hi]} hide />
      <Area
        type="linear"
        dataKey="v"
        className="chart-series"
        isAnimationActive={false}
        activeDot={false}
        dot={(props) =>
          props.index === lastIndex ? (
            <circle key="last" cx={props.cx} cy={props.cy} r={3} className="chart-dot" />
          ) : (
            <g key={props.index} />
          )
        }
      />
    </AreaChart>
  )
}
