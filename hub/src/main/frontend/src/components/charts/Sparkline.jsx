import { lazy, Suspense } from 'react'
import { Skeleton } from '../ui/Skeleton'

// Same dynamic-import boundary as LineChart: the server list renders one of these per row, and
// they all share the single recharts chunk. See recharts.jsx.
const Chart = lazy(() => import('./recharts').then((m) => ({ default: m.Sparkline })))

// Defaults mirror the real Sparkline's, so a row is the same height before and after the swap.
export function Sparkline({ width = 120, height = 28, ...props }) {
  return (
    <Suspense fallback={<Skeleton width={width} height={height} radius={4} />}>
      <Chart width={width} height={height} {...props} />
    </Suspense>
  )
}
