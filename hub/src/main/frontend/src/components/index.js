// One import site for every component: pages do `import { X } from '../components'` and never reach
// into a folder, so a component can move between these groups without touching a single page.
//
//   ui/      the look: nothing in here knows what a server, a player or a heartbeat is
//   charts/  the two chart components; recharts itself is imported only by charts/recharts.jsx,
//            which both of them lazy() in so the library stays out of the entry chunk
//   player/  a Minecraft account: skin render, who's online
//   server/  the hub's server shape (listing, mod info, heartbeats, token)
//
// SidebarNav stays loose: it's the app shell's own nav -- it reads the signed-in admin and the
// current route -- so it belongs to App.jsx, not to a reusable group of one.

export { Badge } from './ui/Badge'
export { BrandLogo } from './ui/BrandLogo'
export { ConfirmDialog } from './ui/ConfirmDialog'
export { CopyButton } from './ui/CopyButton'
export { Dialog } from './ui/Dialog'
export { EmptyState } from './ui/EmptyState'
export { ErrorNote } from './ui/ErrorNote'
export { Field } from './ui/Field'
export { Icon } from './ui/Icon'
export { Note } from './ui/Note'
export { PageHead } from './ui/PageHead'
export { Panel } from './ui/Panel'
export { Skeleton } from './ui/Skeleton'

export { LineChart } from './charts/LineChart'
export { Sparkline } from './charts/Sparkline'

export { OnlinePlayers } from './player/OnlinePlayers'
export { PlayerHead } from './player/PlayerHead'

export { HeartbeatTable } from './server/HeartbeatTable'
export { ListingFields } from './server/ListingFields'
export { ServerEditDialog } from './server/ServerEditDialog'
export { ServerFigures } from './server/ServerFigures'
export { ServerHeartbeats } from './server/ServerHeartbeats'
export { ServerListing } from './server/ServerListing'
export { ServerSetup } from './server/ServerSetup'
export { StatusBadge } from './server/StatusBadge'
export { TokenNotice } from './server/TokenNotice'

export { SidebarNav } from './SidebarNav'
