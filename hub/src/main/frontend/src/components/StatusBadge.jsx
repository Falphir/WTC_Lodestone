import { Badge } from './Badge'

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
