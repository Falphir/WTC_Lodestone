# Network whitelist and bans

How the network-wide whitelist and ban list reach every server, how Discord accounts are linked by code, and the messages refused players see.

## Network whitelist sync

`WhitelistedPlayer` is the network-wide whitelist, managed via `/api/admin/whitelist` (`AdminWhitelistController`/`WhitelistService`). Names are looked up through Mojang (`MojangProfiles`).

**Versions show sync drift:** `WhitelistService.version()` hashes the sorted UUID list, so servers and the dashboard can tell what's applied.
1. Servers fetch the list at `GET /api/bridge/whitelist`. The version rides in the `X-Whitelist-Version` response header (`BridgeController.WHITELIST_VERSION_HEADER`), not the body.
2. They echo it back at `POST /api/bridge/whitelist/applied`, which is what lets the dashboard show per-server sync drift.

**`enforce-whitelist=false` makes sync only half-work** on a server:
- **What still works:** rows still get added and removed.
- **What doesn't:** `kickUnlistedPlayers` is a no-op, so someone an admin just removed stays connected until they log out.
- **How it's flagged:** the mod reports the setting in `hello` (`GameServer.enforceWhitelist`) and warns in its own log at startup. `format.js`'s `whitelistSync` turns it into a "Not enforced" badge that deliberately outranks "Up to date", since that's the state an admin needs to see.

**The 204 guard protects existing whitelists.** `GET /api/bridge/whitelist` returns **204** (no body), not a 200 with an empty array, until the whitelist has been populated at least once (`WhitelistService.everPopulated()`, backed by the single-row `whitelist_state` table).
- **Why:** a fresh, never-imported hub can't wipe every server's existing whitelist on first sync, yet an admin can later empty the list back down to zero and have that reach the servers.
- **On the mod:** `HubClient.syncWhitelist` treats 204 as "leave it alone" and 200 as "apply exactly this, even if empty".
- **Before relying on sync for the first time,** import each server's existing `whitelist.json` via the dashboard.

## Discord links, by code only

Each whitelist row optionally carries a Discord link (`discordId`/`discordLinkedAt`, nullable; `discordId` is unique when set). Linking only ever happens with a code:
1. A player who isn't whitelisted tries to join.
2. `PlayerListMixin` puts a code from `HubClient.linkCode` on the kick screen. It's generated mod-side, so login never waits, and posted async to `POST /api/bridge/link-code`. `LinkCodeService` holds it in memory for 5 minutes, single use. Codes are six digits, so each Discord account gets at most 5 wrong codes per 5 minutes, then a 429.
3. The bot's `/link <code>` claims it via `POST /api/admin/whitelist/link-code` → `WhitelistService.linkByCode`. That creates or links the row, and drops the Discord user's previous account.

**Why a code proves ownership:** online mode authenticates the profile with Mojang *before* `canPlayerLogin`. So servers must run `online-mode=true`.

**"Linked" and "whitelisted" stay the same thing:** the bot only lets members (or staff) `/link`, and approving an application only grants the member role. It whitelists nothing. The dashboard's Add player and Import stay as a staff override that can add unlinked rows.

**Why the link lives on the whitelist row:** a link only makes sense for a whitelisted account. It used to be its own table (`discord_links`, merged in V10), which is exactly what let `WhitelistService.remove()` leave a stale link behind after a dashboard removal. Now removing the row removes the link with it, by structure rather than by remembering to cascade.

**Lookups:** `AdminWhitelistController` also exposes `GET /{uuid}`, `GET /by-discord/{discordId}` and `GET /by-name/{name}`.

## The kick message

**Where it's applied:** the mod's `PlayerListMixin` swaps in a literal message for vanilla's not-whitelisted one (why a mixin is needed is in `mod/CLAUDE.md`).

**The hub can override it network-wide.** The Whitelist page edits `whitelist_state.kick_message` (`PUT /api/admin/whitelist/kick-message`). It rides back to the mod in both the `hello` and the heartbeat response, so a change reaches running servers within one heartbeat.

**Which message wins:** `HubClient.whitelistMessage()` is what the mixin reads. It's the hub's message when non-blank, otherwise the mod config's `whitelistMessage`. So a blank message on the hub, an older hub, or a hub not yet heard from all fall back to per-server config.

**The link code goes in the same answer.** `{code}` in the message marks where it goes; otherwise `LinkCodes.withCode` appends a line under it (or under vanilla's text when the message is blank). No code is issued unless the mod is ready, `syncWhitelist` is on and it isn't a dry run.

## Network ban sync

`NetworkBan` (`network_bans`, V17) is the network-wide ban list, managed at `/api/admin/bans` (`AdminBanController`/`BanService`, names looked up through `MojangProfiles`) from the dashboard's Bans page and the bot's `/ban`/`/unban`.

**Sync works like the whitelist's:**
1. Servers poll `GET /api/bridge/bans` on the whitelist-poll interval; the version rides in `X-Ban-Version`.
2. They echo it to `POST /api/bridge/bans/applied`, which drives the per-server ban badge (`format.js` `banSync`, `BanSyncView`).

**The key difference: the mod only touches bans it wrote itself.** It adds and removes only entries tagged with the source `"WTC Lodestone"` (`HubClient.BAN_SOURCE`). A server's own `/ban` bans, and anything already in `banned-players.json`, are never touched. That's also why there's **no 204 "never populated" guard** here: an empty hub list can only lift the hub's own bans.

**Expiry:**
- A ban carries an optional `expiresAt`, which vanilla enforces itself.
- The version hash covers reason and expiry, so editing either reaches servers.
- Expired bans are left out of the bridge list and deleted nightly (`BanService.deleteExpired`).

**A ban doesn't touch the whitelist or the Discord link.** Vanilla checks bans first, so unbanning restores access as it was.

**The ban message** is what a network-banned player sees: `ban_settings`, a single row (`GET`/`PUT /api/admin/bans/message`, edited on the Bans page; `BanService.DEFAULT_MESSAGE` when unset).
- **Delivery:** it reaches the mod in the `hello` and heartbeat responses, like the kick message.
- **Rendering:** the mod fills in `{reason}`/`{expires}` (`BanMessages.render`) and decodes `&` colour codes.
- **Where it's shown:** when kicking an online player at ban time, and through `PlayerListMixin` for the login refusal. The mixin only uses it when the ban entry's source is `BAN_SOURCE`, so a server's own `/ban` keeps vanilla's wording.

**Per-server opt-out** is the mod's `syncBans` config key (default true, reported in `hello`).
