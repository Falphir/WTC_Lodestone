import { useEffect, useReducer } from 'react'
import { formatDateTime, relativeTime } from '../../format'

export function RelativeTime({ value }) {
  const [, tick] = useReducer((n) => n + 1, 0)
  useEffect(() => {
    if (!value) return
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [value])

  if (!value) return relativeTime(value)
  return (
    <time dateTime={value} title={formatDateTime(value)}>
      {relativeTime(value)}
    </time>
  )
}
