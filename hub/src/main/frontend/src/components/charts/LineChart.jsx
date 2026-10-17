import { lazy, Suspense } from 'react'
import { Skeleton } from '../ui/Skeleton'

// Call sites import { LineChart } from '../components' as before; the chart itself arrives in its
// own chunk the first time one renders. See recharts.jsx for why the library sits behind a
// dynamic import.
const Chart = lazy(() => import('./recharts').then((m) => ({ default: m.LineChart })))

// The real chart's frame and title, with the plot area held open at its own height, so the panel
// doesn't resize under the reader when the chunk lands. PLOT_HEIGHT tracks HEIGHT in recharts.jsx.
const PLOT_HEIGHT = 190

function ChartSkeleton({ title }) {
  return (
    <figure className="chart" aria-busy="true">
      <figcaption className="chart-head">
        <span className="chart-title">{title}</span>
      </figcaption>
      <div className="chart-plot">
        <Skeleton height={PLOT_HEIGHT} />
      </div>
    </figure>
  )
}

export function LineChart(props) {
  return (
    <Suspense fallback={<ChartSkeleton title={props.title} />}>
      <Chart {...props} />
    </Suspense>
  )
}
