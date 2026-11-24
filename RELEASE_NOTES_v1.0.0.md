# WTC Lodestone 1.0.0

The first stable release of WTC Lodestone, the glue between the WTC Network's modpack servers, its
Discord and one central hub. Staff run the whole network from one dashboard: who can join, who is banned,
what every server shows in the server list, and the servers themselves.

**This release migrates the production database.** Read the upgrade steps before deploying.

## What's in 1.0

- **Network whitelist:** one whitelist for every server, kept in sync by the mod, with a network-wide kick message.
- **Network bans:** one ban list applied on every server. A server's own `/ban` bans are kept.
- **Discord account linking with codes:** a player who isn't whitelisted gets a code when they try to join, and runs `/link <code>` in Discord. Applications (`/apply`, `/review`) and auto-revoke when someone leaves the Discord or loses the member role.
- **Network MOTDs and the Configuration page:** MOTD templates with an editor and live preview, a network default, and a per-server pick.
- **PufferPanel integration:** start, stop, restart and kill servers from the dashboard, with each server's process state.
- **Creating servers from the dashboard:** New server creates the server on PufferPanel, registers it with the hub, and writes its mod config and mod jar. With a CurseForge API key it can also install a CurseForge modpack's server files (Beta).
- **Audit log and the History page:** every admin change, from the dashboard or the bot, records who did it and when. For the bot, that includes which Discord staff member it acted for.
- **Forge 1.20.1 build** next to NeoForge 1.21.1, from the same source.
- **Mod capabilities:** each server's mod says what it supports, so the dashboard shows its loader and Minecraft version and grays out a feature that server's build doesn't have.
- **Sign-in throttling:** repeated wrong passwords lock out that IP, and then that username, with `429` and `Retry-After`. Every lockout is recorded in History.
- **Rollback procedure:** `deploy.sh` takes a database dump before every deploy, logs every deploy, and can undo one with `--rollback`, database included.
- **A privacy notice** (`PRIVACY.md`), with decided applications deleted automatically.

New since v0.2.1: the audit log and History page, mod capabilities, sign-in throttling, and the rollback procedure.

## Upgrading

Production runs systemd on one box (`deploy/README.md`). These steps assume you're coming from v0.2.x.

### Database migrations

The hub migrates the database itself when it boots, and there are no down-migrations. From v0.2.1 it runs:

| Migration | What it does |
|---|---|
| `V21__audit_log.sql` | Creates the `audit_log` table (History). |
| `V22__server_capabilities.sql` | Adds a nullable `capabilities` column to `servers`. |

Both only add things; no existing data changes. Coming from v0.2.0 it also runs `V20__server_loader.sql`, and from v0.1.0, V15 to V20 as well.

### Steps

1. **Check that backups restore.** Restore the newest nightly dump into a scratch database and compare it with the live one: `deploy/README.md`, "Rehearsing a restore". Don't go on if it fails.
2. **Refresh the repo checkout on the box.** `deploy.sh` itself changed in this release: it now takes the dump and writes the deploy log that `--rollback` needs.
   ```bash
   sudo git -C /opt/lodestone/repo pull
   ```
3. **Optional new settings.** If you haven't already, add these to `/etc/lodestone/hub.env` (all optional; left out, those features stay off):
   - `LODESTONE_PUFFERPANEL_URL`, `LODESTONE_PUFFERPANEL_CLIENT_ID`, `LODESTONE_PUFFERPANEL_CLIENT_SECRET`, `LODESTONE_PUFFERPANEL_SERVER_HUB_URL`: power controls and New server (`deploy/README.md` step 10).
   - `LODESTONE_CURSEFORGE_API_KEY`: the Modpack step in New server.
   - The tuning settings in the new "Optional hub settings" table in `deploy/README.md`. Their defaults are fine.
4. **Deploy, naming the tag:**
   ```bash
   sudo /opt/lodestone/repo/deploy/deploy.sh v1.0.0
   ```
   It downloads the release, dumps the database (and prints where), stops the bot and the hub, swaps them, and starts them again. The hub runs the migrations above on that boot. The script waits for the health check. **Write down the dump's path.**
5. **Check the hub:**
   ```bash
   curl -fsS localhost:8081/actuator/health
   sudo mariadb wtc_lodestone -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history"   # 22
   ```
   Sign in to the dashboard and open History. It should be empty but load.
6. **Update the mod on every game server.** `deploy.sh` never touches the mod. On each server, replace the old `wtc_lodestone-*.jar` in `mods/` with the one from this release that matches its loader and Minecraft version, then restart that server.
   - NeoForge 1.21.1: `wtc_lodestone-neoforge-1.21.1-1.0.0.jar`
   - Forge 1.20.1: `wtc_lodestone-forge-1.20.1-1.0.0.jar`

   The v0.2.x jars have **the same file names**, so the name alone doesn't tell you which build is on a server. See the check under known limitations.
7. **`PRIVACY.md` changed** (it now mentions sign-in lockouts). Re-pin the updated text where people run `/apply`.

### Rolling back

v1.0.0 adds migrations, so going back to v0.2.1 means restoring the dump step 4 took. `deploy.sh v0.2.1` alone isn't enough.

This is the first deploy that writes `/opt/lodestone/deploys.log`, so it doesn't know which tag came before. Give it the tag and the dump:

```bash
sudo /opt/lodestone/repo/deploy/deploy.sh --rollback v0.2.1 /var/backups/lodestone/wtc_lodestone-TIMESTAMP.sql.gz
```

It checks the dump, dumps the current state first (so the rollback can be undone too), drops and restores the database, and puts v0.2.1 back. The manual version of the same steps is in `deploy/README.md`, "Rolling back".

**A rollback loses everything written since the dump:** whitelist and ban changes, Discord links, applications, MOTD and server edits. Within one whitelist poll (10s), the game servers follow the restored lists, so redo those changes afterwards. The mod jars aren't touched either way, and a 1.0.0 mod keeps working with a v0.2.1 hub.

## Supported versions

| Minecraft | Loader | Gets |
|---|---|---|
| 1.21.1 | NeoForge | New features, bug and security fixes |
| 1.20.1 | Forge | Bug and security fixes only |

- **Newest supported Minecraft version:** gets new features.
- **Older supported versions:** bug fixes and security fixes only. New mod features are ported only if a WTC server needs them.
- **Dropping a version:** once no WTC server runs it, its build is removed in the next release, and the release notes say so.
- **Hub ↔ mod compatibility:** a hub keeps working with mods from older releases. New fields are optional on both sides.

The hub needs Java 21 and MariaDB. The bot needs Node 22 or newer.

## Known limitations

- **Creating a server from a CurseForge modpack is Beta.** Check the new server's files and its first start before opening it to players. Packs whose authors only allow the CurseForge app to download them can't be picked; upload those by hand in PufferPanel.
- **v0.2.x mods also call themselves 1.0.0.** They report the same version and the same jar names, and the dashboard shows them as having every 1.0 feature. To find a server still on an old jar:
  ```bash
  sudo mariadb wtc_lodestone -e "SELECT id, loader, minecraft_version, last_seen FROM servers WHERE capabilities IS NULL"
  ```
  After every server has its new jar and has restarted, this should return no rows.
- **New server uses the latest GitHub release's mod jar.** Until this release is published, it would install v0.2.1's same-named jar. Wait for the release to be published before creating servers.
- **Sign-in throttling is in memory.** Restarting the hub clears every lock.
- **PufferPanel** was tested against 2.7.1.
