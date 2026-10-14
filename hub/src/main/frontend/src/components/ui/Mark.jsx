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
