# Servers: heartbeats, status and public listings

How a server's health is reported and derived, who's online, and the public listing details players see.

## Heartbeats

**The hub owns the timing, not the mod.** `lodestone.heartbeat-interval-seconds` (default 60) is returned from `POST /api/bridge/hello` as `heartbeatIntervalSeconds`, which `HubClient` converts to ticks for `Lodestone.serverTick`. Change the interval on the hub and servers pick it up on their next restart.

**The whitelist poll has its own, faster interval,** delivered the same way: `lodestone.whitelist-sync-interval-seconds` (default 10), sent as `whitelistSyncIntervalSeconds` and floored at 5s by the mod. A poll stores nothing and publishes no `HubEvent`, so unlike a heartbeat it costs no history rows and no dashboard refetches. A mod talking to a hub too old to send it falls back to 60s.

**Each heartbeat** (`POST /api/bridge/heartbeat`: player count, max players, TPS, memory) is appended to `server_heartbeats` as history for the dashboard's `Sparkline`/`LineChart`. `HeartbeatRetentionTask` prunes it nightly (`lodestone.heartbeat-retention-days`, default 7).

## Capabilities

**Hello carries what the mod build can do** as `capabilities` (`HubClient.CAPABILITIES`, e.g. `["whitelist", "bans", "motd", "linkCodes"]`), stored on `servers.capabilities` (V22).
- **Null means the 1.0 set:** a mod from before the field gets `GameServer.V1_CAPABILITIES`, so the admin view's `mod.capabilities` is never null.
- **Unknown names and loaders are kept, never refused:** a newer mod can talk to an older hub.
- **The hub checks before sending what a server can't apply:** no `motd` capability, no MOTD in hello or heartbeat (`MotdService.renderFor`). The dashboard grays the feature out, saying why (`unsupported` in `format.js`).

## Who's online

Each heartbeat also carries **who** is on (`players`, uuid + name) and the raw **`msptAvg`**.

**Presence is the one piece of server state the hub deliberately doesn't persist.** `ServerService.online` is a `ConcurrentHashMap` replaced wholesale on every heartbeat, and `onlinePlayers(server)` returns empty for a server that has gone quiet past `offlineAfter()`.

**So an empty list means "not known", never "nobody is playing":** an older mod, or the first minute after a hub restart. The dashboard's `OnlinePlayers` renders nothing at all rather than an empty state, and the player count stays the figure that says how many.

**Anything keyed by server id moves with the row:** it has to follow a rename (`edit`) and go on removal (`remove`).

## Status

**Derived, never stored** (`ServerService.status`), checked in this order:
1. `DISABLED`;
2. `NEVER` (no `lastSeen`);
3. `OFFLINE` (`lastSeen` older than 3 missed intervals, `offlineAfter()`);
4. `LAGGING` (latest TPS below `LAGGING_BELOW_TPS`);
5. `ONLINE`.

**The dashboard must not hardcode those thresholds.** The public, unauthenticated `GET /api/info` hands it `heartbeatIntervalSeconds`, `offlineAfterSeconds` and `laggingBelowTps`, so charts draw gaps and badges agree with the server.

## Public server listings

Each `GameServer` carries the details players are shown alongside the operational ones: `publicAddress`, `modpack`, `modpackUrl`, `modpackVersion`, `launcher`, `iconUrl` and a `published` flag (`ListingView` in `AdminServerController`).

**Where they're set:** at registration (`POST /api/admin/servers`) or later through `PATCH /api/admin/servers/{id}`. Both go through `ServerService.applyListing`, the one place that maps patch fields onto the row. The dashboard renders both forms from one `ListingFields` component.

**`modpackVersion` is the pack's own version.** It has nothing to do with the `modVersion`/`minecraftVersion`/`loader`/`loaderVersion` the mod self-reports.

**`iconUrl` is the modpack's image:** a thumbnail on the dashboard's server page, and on that server's embed in `/serverinfo`. An embed only gets one thumbnail, so `/serverinfo` sends a header embed plus one card per server. That caps it at 9 servers, since Discord allows 10 embeds per message.

**This used to be a separate `discord_servers` table** with its own ids, so every server was entered twice: once on the dashboard for its token, once through the bot's `/server` to be listed, with nothing keeping the two in step. V12 folded it in.

**`published` is the single switch** that decides whether players see a server, and the bot filters on it (`hub.listPublishedServers`). A server that should be listed must therefore be registered here, token and all, even if it never connects.
