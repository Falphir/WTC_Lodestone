/** The hub decides each server's status (see ServerService.status); this only maps it for display. */
export const statusOf = (server) => server.status.toLowerCase()

export function isUp(status) {
  return status === 'online' || status === 'lagging'
}

/** How a server stands with the network whitelist, from what its mod last reported. */
export function whitelistSync(server) {
  const { enabled, syncedAt, current, enforced } = server.whitelist
  if (enabled === false) return { tone: 'none', label: 'Sync off', detail: 'syncWhitelist is false in its mod config' }
  if (enabled == null) return { tone: 'none', label: 'Unknown', detail: "Its mod hasn't reported whether it syncs" }
  // Syncing is working, but Minecraft itself is ignoring the result: reported before "up to date",
  // because an admin who removes someone needs to know they are still on the server.
  if (enforced === false) {
    return {
      tone: 'warn',
      label: 'Not enforced',
      detail: 'enforce-whitelist is false in its server.properties, so removed players are never kicked',
    }
  }
  if (current) return { tone: 'good', label: 'Up to date', detail: `Applied ${relativeTime(syncedAt)}` }
  if (!syncedAt) return { tone: 'warn', label: 'Not synced yet', detail: "Hasn't applied the network whitelist yet" }
  return { tone: 'warn', label: 'Behind', detail: `Last applied ${relativeTime(syncedAt)}` }
}

const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

export function relativeTime(iso) {
  if (!iso) return 'never'
  const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return rtf.format(seconds, 'second')
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), 'hour')
  return rtf.format(Math.round(seconds / 86400), 'day')
}

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })
const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export const formatDate = (iso) => dateFormat.format(new Date(iso))
export const formatDateTime = (iso) => dateTimeFormat.format(new Date(iso))

export const formatTps = (tps) => tps.toFixed(1)

/** Average tick time. A tick's budget is 50ms, which is where 20 TPS stops being achievable. */
export const formatMspt = (mspt) => `${mspt.toFixed(1)} ms`

export function formatMemory(mb) {
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`
}

export const plural = (n, word) => `${n.toLocaleString()} ${word}${n === 1 ? '' : 's'}`
