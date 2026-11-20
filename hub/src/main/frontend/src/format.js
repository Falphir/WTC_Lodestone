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

/** The ban-list counterpart of whitelistSync. Bans don't depend on enforce-whitelist: vanilla checks them first. */
export function banSync(server) {
  const { enabled, syncedAt, current } = server.bans
  if (enabled === false) return { tone: 'none', label: 'Sync off', detail: 'syncBans is false in its mod config' }
  if (enabled == null) return { tone: 'none', label: 'Unknown', detail: "Its mod hasn't reported whether it syncs bans" }
  if (current) return { tone: 'good', label: 'Up to date', detail: `Applied ${relativeTime(syncedAt)}` }
  if (!syncedAt) return { tone: 'warn', label: 'Not synced yet', detail: "Hasn't applied the network ban list yet" }
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

/** A tick's budget. Past this, 20 TPS stops being achievable. */
export const TICK_BUDGET_MS = 50

/** Average tick time. */
export const formatMspt = (mspt) => `${mspt.toFixed(1)} ms`

/**
 * Green / orange / red for a tick time. `crit` starts where the hub itself would call the server
 * LAGGING (`laggingBelowTps` from /api/info), so the figure and the status badge never disagree.
 */
export function msptTone(mspt, laggingBelowTps = 15) {
  if (mspt >= 1000 / laggingBelowTps) return 'crit'
  if (mspt >= TICK_BUDGET_MS) return 'warn'
  return 'good'
}

export function formatMemory(mb) {
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`
}

export const plural = (n, word) => `${n.toLocaleString()} ${word}${n === 1 ? '' : 's'}`

/**
 * A network MOTD with a server's listing filled in, for previews: the hub's own render is the one servers
 * get, and this mirrors its rule. Null when a value the text uses is blank, so nothing half-filled is shown.
 */
export function renderMotd(text, { modpack, version, name } = {}) {
  const values = { modpack, version, name }
  let blank = false
  const filled = (text ?? '').replace(/\{(modpack|version|name)\}/g, (_, key) => {
    const value = values[key]?.trim()
    if (!value) blank = true
    return value ?? ''
  })
  return blank ? null : filled
}

/**
 * Just the version out of a CurseForge pack version's name, as the hub puts it in the listing:
 * "All the Mods 10-8.4" → 8.4. The whole name when nothing in it looks like a version.
 */
export function packVersion(displayName) {
  const found = displayName?.match(/\d+(?:\.\d+)+[A-Za-z0-9]*/g)
  return found ? found[found.length - 1] : displayName
}

/** The listing values a MOTD needs and this server lacks: "the listing's version", for a hint. */
export function motdMissing(text, { modpack, version, name } = {}) {
  const labels = { modpack: 'modpack', version: 'version', name: 'name' }
  const values = { modpack, version, name }
  return [...new Set([...(text ?? '').matchAll(/\{(modpack|version|name)\}/g)].map((m) => m[1]))]
    .filter((key) => !values[key]?.trim())
    .map((key) => labels[key])
}

/** A server id made from a name: lowercase, anything but letters/digits/_ becomes "-", as the hub's id rule allows. */
export const slugify = (name) =>
  name
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64)

const LOADERS = { neoforge: 'NeoForge', forge: 'Forge', fabric: 'Fabric', quilt: 'Quilt' }

/** A loader's name as its project writes it, from the lower-case id a manifest or the mod reports. */
export const loaderName = (loader) => LOADERS[loader] ?? loader

/** What a pack runs on, from the hub's pack setup: "NeoForge 21.1.265, Minecraft 1.21.1". */
export function packRunsOn(setup) {
  const loader = setup?.loader && [loaderName(setup.loader), setup.loaderVersion].filter(Boolean).join(' ')
  const minecraft = setup?.minecraftVersion && `Minecraft ${setup.minecraftVersion}`
  return [loader, minecraft].filter(Boolean).join(', ')
}

/** The Minecraft version(s) of a CurseForge pack version, out of its mix of game versions and loader names. */
export const minecraftVersions = (packFile) => packFile?.gameVersions?.filter((v) => /^\d/.test(v)).join(', ') ?? ''

// What each audit action reads as on the History page. An action the hub adds later and this
// doesn't know yet shows as its key, which still says what it is.
const AUDIT_ACTIONS = {
  'whitelist.add': 'Whitelisted',
  'whitelist.import': 'Imported a whitelist',
  'whitelist.remove': 'Removed from the whitelist',
  'whitelist.link': 'Linked to Discord',
  'whitelist.link-code': 'Linked with a code',
  'whitelist.kick-message': 'Changed the kick message',
  'ban.add': 'Banned',
  'ban.remove': 'Unbanned',
  'ban.message': 'Changed the ban message',
  'application.approved': 'Approved an application',
  'application.denied': 'Denied an application',
  'server.register': 'Registered a server',
  'server.create': 'Created a server',
  'server.edit': 'Edited a server',
  'server.reset-token': "Reset a server's token",
  'server.remove': 'Removed a server',
  'server.start': 'Started a server',
  'server.stop': 'Stopped a server',
  'server.restart': 'Restarted a server',
  'server.kill': 'Killed a server',
  'motd.create': 'Added a MOTD',
  'motd.edit': 'Edited a MOTD',
  'motd.default': 'Made a MOTD the default',
  'motd.delete': 'Deleted a MOTD',
  'admin.create': 'Added an admin',
  'admin.edit': 'Edited an admin',
  'admin.delete': 'Removed an admin',
  'discord.config': 'Changed the Discord bot settings',
}

export const auditAction = (action) => AUDIT_ACTIONS[action] ?? action
