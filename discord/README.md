# WTC Bot — Discord → Minecraft whitelist & account linking

A small Discord bot for a multi-modpack community. It turns a public application into a
**one-click approve** that whitelists players on the WTC Lodestone hub's network-wide
**whitelist**, and keeps a **Discord ↔ Minecraft account link** (one MC account per Discord
account, and vice-versa).

> This bot talks to the hub in `../hub/` over HTTP, the same way the mod does for
> `syncWhitelist`. It does not touch Minecraft servers directly (no RCON) — the hub's
> `WhitelistedPlayer` table is the single source of truth, and each server picks up
> changes on its own poll.

> The public server **status** bot is intentionally *not* part of this project — use an
> off-the-shelf status bot (MCStatus-style) as decided in the plan.

## Features

- `/apply` — opens a form; the application is posted to a **public** channel (community can read
  it) with **staff-gated Approve / Deny** buttons.
- **On submit** the bot validates up front (before staff see anything): the age gate
  (`minAge`, default 18), the Minecraft name via Mojang (typos / nonexistent accounts rejected),
  and a duplicate check (an account already linked to someone else is rejected). It also enforces
  **one open application at a time** and a **re-apply cooldown** after a denial
  (`applyCooldownDays`, default 7). Applications that pass ping the staff role(s).
- **Approve** → validates the MC name via Mojang, creates the link, adds the player to the hub's
  network whitelist, assigns the member role, posts a welcome message pointing to the
  server-info channel. No DMs of IPs.
- **Deny** → optional reason, DMed to the applicant; application post is stamped.
- **Audit log** — if a `staffLogChannelId` is set, approvals and denials (who / when / reason) are
  logged there for a paper trail.
- `/link <mc_username>` — approved members self-service **connect or change** their MC account
  (removes the old name, adds the new one; always exactly one linked account).
- `/whois` (staff) — look up either direction of a link.
- `/unlink` (staff) — remove a link + de-whitelist.
- `/resetcooldown <user>` (staff) — clear a member's re-apply cooldown so they can `/apply` again.
- `/serverinfo` — list servers, versions and install links (from the hub's server list).
- **Auto-sync** — leaving Discord or losing the member role auto-removes the player from the
  whitelist.

### Admin commands (Administrator only)

These edit the hub's `discord_config` / `discord_servers` tables at runtime via its admin API — no restart needed, and no local state to lose.

- `/server list` — show configured servers.
- `/server add` — add a server via a private form (id, name, address, modpack, install URL —
  display info only; see `/serverinfo`).
- `/server edit <id>` — edit display info (name, address, modpack, version, install URL).
- `/server remove <id>` — stop listing a server in `/serverinfo`.
- `/config view` — show current channels, roles and branding.
- `/config channels [applications] [welcome] [serverinfo] [stafflog]` — set the bot's channels.
- `/config member-role <role>` — set the approved-member role.
- `/config staff <add|remove> <role>` — manage staff roles.
- `/config min-age <age>` — set the minimum age to apply (default 18).
- `/config apply-cooldown <days>` — set the re-apply cooldown after a denial (0 = off, default 7).
- `/config brand [name] [color] [icon]` — set branding.

Visibility is controlled by Discord's **Administrator** permission (adjustable per-role under
Server Settings → Integrations).

## Wording / translations

All user-facing text (command replies, embeds, modal labels, welcome/denial
messages, command descriptions) lives in **`locales/en.json`** — edit that file
to reword anything without touching code. Strings use `{placeholder}` tokens
(e.g. `{brand}`, `{name}`, `{error}`) that the bot fills in at runtime.

To add another language, copy `locales/en.json` to e.g. `locales/pt.json`,
translate the values (keep the keys and `{placeholders}` intact), and set
`BOT_LANG=pt` in `.env`. Runtime message edits take effect on the next restart;
if you change **command or option descriptions**, also re-run `npm run deploy`.

## Requirements

- **Node.js ≥ 22** (matches `package.json`'s `engines`; uses the built-in `fetch` — no extra HTTP
  client dependency).
- A running WTC Lodestone hub (`../hub/`), reachable at `HUB_URL`, with a dedicated admin account
  seeded for this bot via `lodestone.admins` in `hub/src/main/resources/application.yaml` (see
  `.env.example`).

> The bot keeps **no local state at all** — the hub is the only persistent store, and this is
> genuinely just an HTTP client: it requests, the hub replies. Channels/roles/branding/application
> rules and the `/serverinfo` server list live in `discord_config`/`discord_servers`
> (`src/config.js` caches them in memory, loaded on startup and kept live by every `/config`/
> `/server` edit); the Discord↔MC account links and in-flight applications that used to be a local
> SQLite file (`links.db`) now live in `discord_links`/`discord_applications` and are read/written
> per-call through `src/db.js` → `src/hub.js`, with no caching at all. A redeployed bot process has
> nothing to lose. Run `/config channels ...`, `/config member-role ...` etc. once after first boot
> against a fresh hub to populate it (there's no dashboard UI for this yet).

## Setup

```bash
npm install

cp .env.example .env
```

- **`.env`** — bot token, application (client) id, guild id, and the hub URL + the admin
  credentials this bot signs in with (see "Hub account" below).

### Hub account

Add a dedicated admin entry for the bot next to the existing `admin` one, following the same
pattern:
```yaml
lodestone:
  admins:
    - username: admin
      password: ${LODESTONE_ADMIN_PASSWORD:admin}
    - username: discord-bot
      password: ${LODESTONE_BOT_PASSWORD:bot}
```
Then set `HUB_USERNAME=discord-bot` / `HUB_PASSWORD=<that password>` in `.env`. The bot signs in
at `/api/auth/token` exactly like a dashboard user and uses the resulting JWT as a Bearer token.

### Discord Developer Portal

1. Create an application + bot; copy the **token** → `.env`.
2. Under **Bot → Privileged Gateway Intents**, enable **Server Members Intent** (required for role
   assignment and the auto-sync events).
3. Invite the bot with the `bot` + `applications.commands` scopes and permissions to
   *Manage Roles*, *Send Messages*, and *Read Message History*. Its role must sit **above** the
   member role it assigns.

### Register the slash commands (run once, and after editing a command)

```bash
npm run deploy
```

### Run

```bash
npm start
```

## Verify (matches the plan's verification section)

1. `/serverinfo` renders your servers.
2. Submit an `/apply`, click **Approve** as staff → check: link row created, the player shows up
   on the hub dashboard's Whitelist page, role assigned, welcome posted, post stamped.
3. `/link` a fresh name, then `/link` a different name → old name removed, new added, still one
   row.
4. Remove the member role from a test account → confirm auto de-whitelist on the dashboard.
