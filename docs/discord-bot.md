# The Discord bot's design

How the bot authenticates, why it keeps no state of its own, and the reasoning behind applications, `/review` and its autocomplete. The day-to-day rules for editing bot code are in `discord/CLAUDE.md`.

## Auth

**`discord/` is a separate process,** not a Gradle module.

**It authenticates as an ordinary hub admin.** There's no `ROLE_BOT` or other new hub auth scheme.
- **The account** is seeded through the same `lodestone.admins` mechanism as any dashboard admin: a `discord-bot` entry next to `admin` in `hub/src/main/resources/application.yaml`.
- **Signing in:** it signs in at `POST /api/auth/token` with that account's username and password, and uses the resulting JWT as a Bearer token against `/api/admin/**` (`discord/src/hub.js`).

**It never talks to game servers.** The hub's `WhitelistedPlayer` table is the only whitelist source of truth, applied to each server by the mod's poll-sync.

**Approving an application only grants the member role.** A player is whitelisted when they `/link` a code (`docs/whitelist-and-bans.md`), and reaches the servers on their next poll.

**Each call names the staff member it's for,** for the audit log: see `docs/audit-log.md`.

## No durable state

The bot keeps **no durable state at all.** Everything that used to be `config.json`/`servers.json`/`links.db` now lives on the hub, so a redeployed bot process has nothing to lose:
- **Channels, roles, branding and application rules:** the `DiscordConfig` entity (`discord_config` table), `DiscordBotService`, `AdminDiscordConfigController` at `/api/admin/discord/config`.
  - `discord/src/config.js` is an in-memory cache of it. `init()` pulls it once at startup.
  - Every `/config` edit writes through the hub first, and only then updates the cache.
- **What `/serverinfo` shows players:** the public-listing columns on `GameServer` (`docs/servers-and-status.md`), not a table of the bot's own.
  - It's read live on every invocation via `hub.listPublishedServers()`.
  - Never cached, since publishing happens on the dashboard and the bot would otherwise never hear about it.
- **Discord ↔ Minecraft account links:** columns on `WhitelistedPlayer`, not their own table.
  - `discord/src/db.js` keeps its old async lookups (`getByDiscord`/`getByUuid`/`getByName`) but adapts the hub's whitelist-player view into that shape, via `discord/src/hub.js`. There's no cache: every call hits the hub.
  - There's no `setLink` any more: links are only made by `hub.claimLinkCode`.
- **In-flight applications** (pending, approved or denied): the `DiscordApplication` entity (`discord_applications` table), `DiscordApplicationService`, `AdminDiscordApplicationController` at `/api/admin/discord/applications`.
  - They're independent of the whitelist, since an applicant may not be whitelisted yet.
  - Decided rows are deleted nightly by `ApplicationRetentionTask`, for GDPR retention. Approved ones go after 1 day, denied ones once `applyCooldownDays` has passed, which is exactly as long as the row is needed to block re-applying. Pending rows are kept.
  - With audit entries and heartbeats, these are the data the hub expires on purpose. Whitelist rows, links and staff accounts are kept while they're current. The privacy notice to pin in Discord is `PRIVACY.md`.

## In-memory caches

The only things the bot holds in memory are caches it can lose without consequence:
- **The config** (`config.js`), re-pulled every 60s.
- **`/review`'s suggestion list,** rebuilt at most every 15s.
- **Half-filled `/apply` answers:** the deliberate exception to "nothing local".
  - **Why they're kept:** a modal's answers can only be checked once it's submitted (age, the Minecraft name's shape). A rejection comes after all five answers are written, so the **Fix and resubmit** button hands them back instead of making someone retype.
  - **Why losing them is fine:** a draft is worth exactly one retype, which is what losing it already cost.
  - **When they go:** with the interaction token behind the button (15 min).

**The `/apply` form** is built from `LabelBuilder` components, so each field carries a description. That also sets a floor under the declared discord.js version: `addLabelComponents`, `setDescription` and selects in modals don't exist on the old `^14.16.3` range.

## Applications and `/review`

**The application post goes to a public channel on purpose:** the community is meant to see who's joining.

**That's why the Approve/Deny buttons aren't on it.** A message's components render for everyone who can see the message, and Discord has no per-user visibility for them. So staff pull the controls to themselves with `/review`, whose **ephemeral** reply is the one place components are per-user.

**`/review`'s option autocompletes** from `GET /api/admin/discord/applications?status=pending`. That's the only listing endpoint on `AdminDiscordApplicationController`; everything else there is keyed by `discordId`. So a choice's value is an applicant's Discord id, and staff never type one.

**A choice's label is the applicant's Discord name,** resolved bot-side when the list is built:
- **Why it's resolved:** the application row (V16) holds no Minecraft identity at all, only the Discord id and the post's `messageId`. And a plain-text choice label renders a `<@id>` mention as raw markup.
- **How:** member cache first, then one bulk `members.fetch` for the misses, never one call per applicant, because autocomplete is on a 3s clock.
- **An applicant who has left the guild** resolves to nothing, and is listed by their Discord id rather than dropped.

**Autocomplete fires per keystroke,** so the whole choice list is built at most once every 15s, and keystrokes filter it in memory. A hub round trip and a gateway fetch per character is what makes a suggestion list feel slow.

**`ready.js` warms the member cache once at login,** since Discord only ships members with `GUILD_CREATE` for small guilds. That way the first suggestion of a session doesn't pay for the fetch either.

**`interactionCreate` routes `isAutocomplete()` separately,** because an autocomplete interaction can only be answered with choices, never a reply. Choices are also all its error path can send.

**The hub's row is the record, not the post:**
- **The post is only ever stamped** with the outcome (`stampPost`).
- **Handlers read `status` and `messageId` from the hub's application row,** not the rendered embed. So rewording a locale key can't make an in-flight application unapprovable.
- **The Minecraft name an applicant typed exists only in that post.** Approval never derives an account from it.

**A decision is refused unless the row is still `pending`,** which is what stops two staff double-approving.

## No other integrations

**Minecraft username lookups go through the hub:** `GET /api/admin/mojang/{name}` (`AdminMojangController`), which wraps the same `MojangProfiles` bean `WhitelistService` uses. The bot doesn't call Mojang's API directly; `discord/src/mojang.js` is a one-line pass-through to `discord/src/hub.js`.

**What the bot still calls directly:**
- **Discord** itself.
- **Two purely cosmetic skin-render services** for embed thumbnails: Visage, falling back to mc-heads (`discord/src/lib/embeds.js`). They have no reason to go through the hub.
