import { useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

const HEIGHT = 190
const BUCKETS = 400

/** Rounds up to 1, 2 or 5 times a power of ten, so the top gridline lands on a clean number. */
function niceCeil(value) {
  if (value <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  const n = value / power
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * power
}

// ponytail: plain bucket averaging keeps 7 days of minute heartbeats (~10k points) cheap for Recharts to draw.
// It smooths short spikes away; switch to min/max per bucket if spikes matter.
function downsample(points, from, to) {
  if (points.length <= BUCKETS) return points
  const size = (to - from) / BUCKETS
  const out = []
  let bucket = null
  for (const p of points) {
    const b = Math.floor((p.t - from) / size)
    if (!bucket || bucket.b !== b) {
      if (bucket) out.push({ t: bucket.t / bucket.n, v: bucket.v / bucket.n })
      bucket = { b, t: 0, v: 0, n: 0 }
    }
    bucket.t += p.t
    bucket.v += p.v
    bucket.n++
  }
  out.push({ t: bucket.t / bucket.n, v: bucket.v / bucket.n })
  return out
}

/** Recharts draws a gap at a null value; put one wherever reports stopped for longer than `gap`. */
function withGaps(points, gap) {
  const out = []
  points.forEach((p, i) => {
    if (i > 0 && p.t - points[i - 1].t > gap) out.push({ t: points[i - 1].t + 1, v: null })
    out.push(p)
  })
  return out
}

function ChartTooltip({ active, payload, format }) {
  const point = payload?.[0]?.payload
  if (!active || !point || point.v === null) return null
  return (
    <div className="chart-tooltip">
      <strong>{format(point.v)}</strong>
      <span>{new Date(point.t).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</span>
    </div>
  )
}

/**
 * One measure over time. `points` are { t: epoch ms, v } in ascending time order.
 * `gapMs` is the longest silence still drawn as a continuous line (the hub's offline threshold).
 */
export default function LineChart({ title, points, from, to, yMax, format, gapMs, describe }) {
  const data = useMemo(() => {
    const shown = downsample(points, from, to)
    return withGaps(shown, Math.max(gapMs, ((to - from) / BUCKETS) * 2.5))
  }, [points, from, to, gapMs])

  // a known ceiling (max players, 20 TPS, max memory) is the honest top; otherwise round up to a clean number
  const top = yMax || niceCeil(Math.max(...points.map((p) => p.v), 1))
  const timeFormat = new Intl.DateTimeFormat(
    undefined,
    to - from > 36 * 3600_000 ? { weekday: 'short', day: 'numeric' } : { hour: '2-digit', minute: '2-digit' },
  )
  const xTicks = [0, 1, 2, 3, 4].map((i) => from + ((to - from) * i) / 4)
  const last = points[points.length - 1]
  const lastIndex = data.length - 1

  return (
    <figure className="chart">
      <figcaption className="chart-head">
        <span className="chart-title">{title}</span>
        {last && <span className="chart-value">{format(last.v)}</span>}
      </figcaption>

      {!points.length ? (
        <p className="chart-empty">No heartbeats in this range.</p>
      ) : (
        <div className="chart-plot" role="img" aria-label={`${title}. ${describe}.`}>
          <ResponsiveContainer width="100%" height={HEIGHT}>
            <AreaChart data={data} margin={{ top: 12, right: 16, bottom: 0, left: 0 }}>
              <CartesianGrid vertical={false} />
              {/* no allowDataOverflow: it clips the plot, which halves a line sitting on 0 or the max */}
              <XAxis
                dataKey="t"
                type="number"
                domain={[from, to]}
                ticks={xTicks}
                tickFormatter={(t) => timeFormat.format(t)}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                domain={[0, top]}
                ticks={[0, top / 2, top]}
                tickFormatter={format}
                axisLine={false}
                tickLine={false}
                width={56}
              />
              <Tooltip content={<ChartTooltip format={format} />} isAnimationActive={false} />
              <Area
                type="linear"
                dataKey="v"
                className="chart-series"
                connectNulls={false}
                isAnimationActive={false}
                activeDot={{ r: 5, className: 'chart-dot' }}
                dot={(props) =>
                  props.index === lastIndex ? (
                    <circle key="last" cx={props.cx} cy={props.cy} r={5} className="chart-dot" />
                  ) : (
                    <g key={props.index} />
                  )
                }
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </figure>
  )
}
