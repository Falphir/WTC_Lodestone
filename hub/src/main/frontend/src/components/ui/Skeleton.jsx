/**
 * A block standing in for something still on its way — a lazily-loaded chunk behind a Suspense
 * fallback, or data that hasn't arrived. Give it the size of the real thing so nothing jumps when
 * it lands. The sweep is pure CSS and stops being animated under prefers-reduced-motion, which
 * index.css already handles globally.
 */
export function Skeleton({ width = '100%', height = 16, radius = 8 }) {
  return <span className="skeleton" style={{ width, height, borderRadius: radius }} aria-hidden="true" />
}
