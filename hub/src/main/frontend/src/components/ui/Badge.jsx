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
