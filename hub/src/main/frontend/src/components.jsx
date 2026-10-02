import { useState } from 'react'
import { Area, AreaChart, XAxis, YAxis } from 'recharts'

const ICONS = {
  overview: 'M3 12h4l3-8 4 16 3-8h4',
  servers: 'M4 5h16v5H4zM4 14h16v5H4zM8 7.5h.01M8 16.5h.01',
  whitelist: 'M15 19v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1M9 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6M16 11l2 2 4-4',
  admins: 'M12 3 5 6v5c0 4.5 3 8.5 7 10 4-1.5 7-5.5 7-10V6z',
  signOut: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5',
  system: 'M3 5h18v11H3zM8 20h8M12 16v4',
  copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
  check: 'M5 12.5 10 17l9-10',
  plus: 'M12 5v14M5 12h14',
  back: 'M15 18l-6-6 6-6',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14M20 20l-4-4',
  upload: 'M12 15V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
}

export function Icon({ name, size = 16 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="icon"
    >
      <path d={ICONS[name]} />
    </svg>
  )
}

/** The lodestone: a stone block with a compass needle, as in the game. */
export function Mark({ size = 24 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect x="2" y="2" width="28" height="28" rx="7" fill="var(--mark-stone)" />
      <path d="M23 9 18.5 18.5 13.5 13.5Z" fill="var(--chart)" />
      <path d="M9 23 13.5 13.5 18.5 18.5Z" fill="var(--mark-needle)" />
    </svg>
  )
}

const GLYPH = {
  good: 'M8 12.5l2.5 2.5L16 9.5',
  warn: 'M12 8v5M12 16.5h.01',
  crit: 'M9 9l6 6M15 9l-6 6',
  none: 'M8.5 12h7',
}

/** A state shown as icon shape + word, so it never depends on color alone. */
export function Badge({ tone, children, title }) {
  return (
    <span className={`status status-${tone}`} title={title}>
      <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="10" className="status-disc" />
        <path d={GLYPH[tone]} fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="status-glyph" />
      </svg>
      {children}
    </span>
  )
}

const STATUS = {
  online: { label: 'Online', tone: 'good' },
  lagging: { label: 'Lagging', tone: 'warn' },
  offline: { label: 'Offline', tone: 'crit' },
  never: { label: 'Never connected', tone: 'none' },
  disabled: { label: 'Disabled', tone: 'none' },
}

export function StatusBadge({ status }) {
  const { label, tone } = STATUS[status]
  return <Badge tone={tone}>{label}</Badge>
}

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

export function CopyButton({ text, label = 'Copy' }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // clipboard blocked (e.g. plain http on another host): the text is selectable anyway
    }
  }
  return (
    <button type="button" className="button button-quiet" onClick={copy}>
      <Icon name={copied ? 'check' : 'copy'} />
      {copied ? 'Copied' : label}
    </button>
  )
}

export function ErrorNote({ children }) {
  if (!children) return null
  return (
    <p className="note note-error" role="alert">
      {children}
    </p>
  )
}
