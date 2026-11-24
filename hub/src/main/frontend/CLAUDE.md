# Dashboard (hub/src/main/frontend)

Plain JS/JSX (no TypeScript), React 19 + Vite. Commands are in the root `CLAUDE.md`. `npm test` (the MOTD model) and `npm run lint` aren't run by `./gradlew build` or CI, so run them yourself after changing `motd.js` or anything else.

## Data flow

No router, no data-fetching library. Four modules in `src/` cover it, and pages go through them, never calling `fetch` or adding a dependency:
- **`api.js`:** `api(path, { method, body })` attaches the admin JWT and throws an `Error` with a readable message.
  - The JWT is kept in `localStorage` as `lodestone-auth`, as a plain string. The password never is.
  - Any 401 from any page fires the one handler `App.jsx` registered via `setSignedOutHandler`, dropping back to sign-in. Individual callers don't handle expiry.
  - `currentAdmin()` reads the username out of the JWT's `sub`; there's no `/me` endpoint.
- **`live.js`:** one WebSocket for the whole dashboard, opened lazily and only while something is subscribed, with backoff reconnects.
- **`hooks.js`:**
  - **`useApi(path, { topics, serverId, refreshMs })`** is the only read path. It loads once, reloads on a matching `HubEvent` topic (debounced 300ms, since a burst of heartbeats arrives together), and polls `refreshMs` as the fallback for a dead socket. `useApi(null)` loads nothing.
  - **`useRoute()`** is the router: `window.location.hash` split into segments, matched by the if-chain in `App.jsx`. The routes are `#/`, `#/servers`, `#/servers/{id}[/{tab}]`, `#/whitelist`, `#/bans`, `#/admins`, `#/history` (the audit log) and `#/config` (network-wide settings, the MOTDs today).
  - **A server page** has three tabs, `stats`, `details` and `manage`. One shows at a time, with a `ServerTabBar` pinned to the bottom like PufferPanel's, and power controls in the header.
  - **Also here:** `useAction` (mutations), `useHubInfo` (`/api/info`, fetched once per process), `useLiveStatus` and `useTheme`.
- **`format.js`:** the shared display vocabulary, and the reason two pages never disagree about a badge. New formatting goes here, not inline in a page. It holds:
  - **Status:** `statusOf`/`isUp`.
  - **Sync badges:** `whitelistSync` (including the "Not enforced" override) and `banSync`.
  - **Times:** `relativeTime`, `formatDate`/`formatDateTime`.
  - **Figures:** `formatTps`, `formatMspt`/`msptTone` (`TICK_BUDGET_MS`), `formatMemory`, `plural`.
  - **The New server dialog's helpers:** `slugify`, `packRunsOn`, `loaderName`, `minecraftVersions`, `packVersion`.
  - **MOTD previews:** `renderMotd`/`motdMissing`.
  - **Capabilities:** `unsupported(server, capability)`, the reason a server's mod build can't use a feature (null when it can). `whitelistSync`/`banSync` and `ServerMotd` gray out on it.
  - **The History page's wording:** `auditAction`.

**A fifth module, `motd.js`,** is the MOTD editor's model and the one parser of Minecraft `§`/`&` codes in the dashboard. It has no React in it, so `motd.test.js` runs under plain Node. Read `docs/motd.md` before touching it.

**Charts are recharts** (`LineChart`, `Sparkline`). It's already a dependency, so use it instead of hand-rolled SVG or a second chart library.

## Component library

`src/components/` is one file per component, grouped by how much each knows about the domain. Everything is re-exported flat through `index.js`, so pages just `import { X } from '../components'`, and a component can move between groups without touching a page.

- **`ui/`:** the look. Nothing in it knows what a server or a player is.
  - `Panel`: the card wrapper (`as` picks `div`/`form`/`section`).
  - `PageHead`: title + actions row.
  - `Dialog`: a centered `<dialog>`/`showModal()` wrapper, so Escape-to-close, the focus trap and the backdrop come free from the browser. `ConfirmDialog` sits on top of it.
  - `Field`: label + hint around a caller-supplied `<input>`.
  - `EmptyState`, `Note`/`ErrorNote`, `Badge`, `CopyButton`, `BrandLogo` (`assets/logo.png`), `Icon` (wraps Font Awesome; new icons are added to its map), `Skeleton`.
  - `MinecraftText`: text with Minecraft codes, as the server list draws it.
  - `RelativeTime`: a self-refreshing "7 seconds ago". Use it rather than calling `relativeTime()` in a render, which freezes at whatever the last re-render said.
- **`charts/`:** `LineChart` and `Sparkline`, both thin `Suspense` wrappers whose fallback holds the plot area open at its real height.
  - The actual charts, and the only `import … from 'recharts'`, live in `charts/recharts.jsx`, reached solely through `lazy(() => import('./recharts'))`.
  - recharts is most of the bundle and only two pages draw anything. A static recharts import anywhere else puts ~500 kB back in the entry chunk the sign-in screen waits for.
- **`player/`:** `PlayerHead`, `OnlinePlayers`.
- **`server/`:** everything shaped like the hub's server row.
  - **Status and stats:** `StatusBadge`, `ServerFigures`, `ServerHeartbeats`/`HeartbeatTable`.
  - **Listing and setup:** `ListingFields`, `ServerListing`, `ServerSetup`, `ServerEditDialog`, `TokenNotice`.
  - **The server page's chrome:** `ServerPower`, `ServerTabBar`.
  - **Its MOTD pick:** `ServerMotd`.
  - **Creating a server:** `CreateServerDialog`, `ModpackPicker`, and `ModpackSetupNote` for the background pack setup's progress.
  - **`SyncPanel`:** the per-server sync list the Whitelist and Bans pages both show, given `whitelistSync` or `banSync` as `syncOf`.
- **`whitelist/`:** `WhitelistTable`/`WhitelistRow`, `KickMessagePanel`.
- **`bans/`:** `BanTable`/`BanRow`, `BanMessagePanel`.
- **`motd/`:** the Configuration page's MOTD list (`MotdPanel`), and its `MotdEditDialog`/`MotdEditor`/`MotdToolbar`.
- **`SidebarNav.jsx` stays loose at the top:** it's the app shell's own nav, belonging to `App.jsx` rather than to a reusable group of one.

**Mutations use `useAction`,** in `hooks.js`, not a component. It's the busy/message/error/reload cycle every mutating form repeats.

**Build new pages from these components,** rather than re-rolling `<div className="panel">` and the like by hand.

## Pages and bundle size

**Every page in `pages/` is `lazy()`-imported by `App.jsx`,** and rendered inside one `<Suspense>` whose fallback is a `PageSkeleton`. So each route is its own chunk, and only sign-in is in the entry bundle.

**A new page follows that:** add the `lazy()` line next to the others, not a static import. Anything heavy a page pulls in should sit behind the same boundary the charts use.

**Pages derive what they show during render.** The lint config forbids calling `setState` directly inside an effect (`react-hooks/set-state-in-effect`).

## Mobile

**Below 760px,** a `.mobile-bar` with a hamburger button replaces the sidebar. The button opens `SidebarNav` a second time, inside a nav-drawer `<dialog>` (`App.jsx`).

**The drawer stays mounted at all times,** opened and closed imperatively with `showModal()`/`close()` by an effect watching `menuOpen`. That's so its CSS transition (`@starting-style` + `transition-behavior: allow-discrete`) actually gets to play: a conditionally rendered dialog would be yanked out mid-animation.

**`.shell` needs `align-content: start` on its mobile grid,** or the two stacked rows (`.mobile-bar`/`.main`) stretch to fill `min-height: 100svh` instead of hugging their content.

**Tables become stacked cards** instead of scrolling sideways. Opt in with a `table-cards` class on the `<table>` and `data-label` on each `<td>`.
- **Empty cells:** a cell with nothing to show gets `data-empty` and is hidden on a card.
- **Mixed content:** wrap a cell's mixed text and markup in one `<span>`, so the card keeps it together on the right.
