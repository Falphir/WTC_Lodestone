# Deploying WTC Lodestone

One Linux box, no Docker. It runs the hub, the Discord bot, MariaDB, nginx — and the modpack
servers, so mods reach the hub over loopback and nothing but nginx is exposed.

| | |
|---|---|
| Hub | `127.0.0.1:8081`, systemd `lodestone-hub` |
| Bot | systemd `lodestone-bot` |
| Public | nginx on 80/443 only |
| Database | MariaDB on `127.0.0.1:3306`, nightly dump by `lodestone-backup.timer` |

Local development is a different thing entirely: `docker-compose.dev.yml` in this folder, see the
root `README.md`.

A separate server that doesn't run the modpack servers can use Docker instead: see
[`docker/README.md`](docker/README.md). Not this box.

---

## First-time setup

### 1. Packages

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless mariadb-server nginx curl git \
                    certbot python3-certbot-nginx
# The bot needs Node >= 22, newer than Debian/Ubuntu ship:
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

### 2. Service account and layout

```bash
sudo useradd --system --home /opt/lodestone --shell /usr/sbin/nologin lodestone
sudo install -d -o lodestone -g lodestone /opt/lodestone
sudo install -d -o root -g root -m 755 /etc/lodestone
```

The unit files, the nginx config and `deploy.sh` all live in the repo, so keep a shallow checkout on
the box for them — it needs no toolchain, nothing is built here. Every `deploy/...` path below is
relative to it:

```bash
sudo git clone --depth 1 https://github.com/Falphir/WTC_Lodestone.git /opt/lodestone/repo
cd /opt/lodestone/repo      # `sudo git -C /opt/lodestone/repo pull` to refresh these files later
```

### 3. Database

```bash
sudo mariadb-secure-installation        # set a root password, drop the test database
sudo mariadb -e "CREATE DATABASE wtc_lodestone CHARACTER SET utf8mb4;"
sudo mariadb -e "CREATE USER 'lodestone'@'127.0.0.1' IDENTIFIED BY 'PICK-A-PASSWORD';"
sudo mariadb -e "GRANT ALL PRIVILEGES ON wtc_lodestone.* TO 'lodestone'@'127.0.0.1'; FLUSH PRIVILEGES;"
```

Flyway creates and migrates every table on the hub's first boot — don't import a schema by hand.

### 4. Configuration

Everything is an environment variable; nothing in the jar is edited. **Write these files before the
first boot** (see the traps below — the admin passwords are only read once, ever).

```bash
sudo tee /etc/lodestone/hub.env >/dev/null <<'EOF'
SERVER_PORT=8081
SERVER_ADDRESS=127.0.0.1
SPRING_DATASOURCE_URL=jdbc:mariadb://127.0.0.1:3306/wtc_lodestone
SPRING_DATASOURCE_USERNAME=lodestone
SPRING_DATASOURCE_PASSWORD=PICK-A-PASSWORD
# Keeps admins signed in across the weekly reboot. Any passphrase: openssl rand -hex 32
LODESTONE_JWT_SECRET=GENERATE-ME
LODESTONE_ADMIN_PASSWORD=PICK-A-PASSWORD
LODESTONE_BOT_PASSWORD=PICK-ANOTHER-PASSWORD
# Nothing on the internet needs the API browser
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
EOF
sudo chown root:lodestone /etc/lodestone/hub.env && sudo chmod 640 /etc/lodestone/hub.env
```

```bash
sudo tee /etc/lodestone/bot.env >/dev/null <<'EOF'
DISCORD_TOKEN=
CLIENT_ID=
GUILD_ID=
HUB_URL=http://127.0.0.1:8081
HUB_USERNAME=discord-bot
HUB_PASSWORD=PICK-ANOTHER-PASSWORD   # == LODESTONE_BOT_PASSWORD above
EOF
sudo chown root:lodestone /etc/lodestone/bot.env && sudo chmod 640 /etc/lodestone/bot.env
```

### 5. Services

```bash
sudo cp deploy/systemd/lodestone-*.service deploy/systemd/lodestone-*.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable lodestone-hub lodestone-bot lodestone-backup.timer
```

`enable` is what makes the weekly reboot a non-event: systemd brings all three back, the mod checks
in again on its own, and `Persistent=true` on the timer runs a dump that the reboot would have missed.

### 6. First deploy

```bash
sudo /opt/lodestone/repo/deploy/deploy.sh   # pulls the newest release, starts everything, waits for health
```

If the repo is private, `curl` can't fetch release assets — use `gh release download` (GitHub CLI,
authenticated) and place `wtc_lodestone-hub.jar` in `/opt/lodestone/` yourself.

Fill in the placeholders in `PRIVACY.md` (repo root) and pin it in the Discord channel where people
run `/apply`. It's the privacy notice GDPR asks for, sized to fit one Discord message.

Then register the bot's slash commands with Discord — once, on the first install, and again whenever a
command or option description changes:

```bash
sudo runuser -u lodestone -- sh -c 'cd /opt/lodestone/bot && npm run deploy'
```

### 7. nginx and TLS

```bash
sudo cp deploy/nginx/lodestone.conf /etc/nginx/sites-available/lodestone
sudo sed -i 's/<your-host>/YOUR-HOST/' /etc/nginx/sites-available/lodestone
sudo ln -s /etc/nginx/sites-available/lodestone /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d YOUR-HOST       # adds the 443 block and the http -> https redirect
```

The vhost rate-limits sign-in (`/api/auth/`, `/login`) to 10 attempts a minute per IP and keeps
the live socket out of the access log, since its URL carries an admin token.

No domain yet? `<your-public-ip-with-dashes>.sslip.io` resolves to that IP with no signup, and
Let's Encrypt issues for it — e.g. `203-0-113-7.sslip.io`. If issuance is rate-limited, a free
DuckDNS name works the same way. Swap in a real domain later by re-running `certbot` and the `sed`.

### 8. Firewall — the one step that can lock you out

This is a console-less VPS: if a network change cuts SSH, the only way back is a provider-level
reset. So arm a dead-man switch first — if you lose the session, ufw turns itself off in ten
minutes and nothing is lost:

```bash
sudo systemd-run --on-active=10m --unit=ufw-undo systemctl disable --now ufw
```

```bash
sudo ufw allow OpenSSH            # FIRST, or enabling ufw locks you out
sudo ufw allow 80,443/tcp
sudo ufw enable                   # 8081 and 3306 stay loopback-only
```

Open a **second** SSH session and confirm it works before disarming:

```bash
sudo systemctl stop ufw-undo.timer    # only once you are sure you still have access
```

Same trick for any future change to routing, firewalls or network interfaces on this box.

### 9. Game servers (same box)

Per modpack server, register it on the dashboard first (that is the only time its token is shown),
then in `config/wtc_lodestone-common.toml`:

```toml
enabled = true
dryRun = true                      # flip to false once you see it check in
hubUrl = "http://127.0.0.1:8081"
serverId = "from-the-dashboard"
token = "from-the-dashboard"
syncWhitelist = false              # see the whitelist trap below
syncBans = true                    # apply the network ban list; this server's own /ban bans are kept
```

Also set `enforce-whitelist=true` in that server's `server.properties`, or removing someone from the
network whitelist won't kick them until they log out on their own. The dashboard flags this as
"Not enforced".

Players link their accounts with a code: a member who isn't whitelisted yet tries to join, gets
kicked with a code, and runs `/link <code>` in Discord. For that to prove anything, every server
needs `online-mode=true` (otherwise anyone can join under any name and claim it) and
`white-list=true` in `server.properties`. When switching an existing server over, empty its
`whitelist.json`, or everyone on the old list keeps getting in unlinked until the hub's whitelist
has had its first player.

Mod jars come from the release page and go in each server's `mods/` — that server has to restart to
pick one up, which is why `deploy.sh` leaves them alone.

### 10. PufferPanel (optional)

The hub can talk to PufferPanel (written against **2.7.1**). Leave this out and nothing changes.

1. In PufferPanel, signed in as an **admin**, open your account settings and create an OAuth2 client.
   Copy the client id and secret; the secret is shown once. A client with no scopes of its own
   inherits the account's permissions, which is why it should be an admin's.
2. Add to `/etc/lodestone/hub.env`, then `sudo systemctl restart lodestone-hub`:
   ```bash
   LODESTONE_PUFFERPANEL_URL=http://127.0.0.1:8080     # the panel's own address; likely what holds 8080 on this box
   LODESTONE_PUFFERPANEL_CLIENT_ID=...
   LODESTONE_PUFFERPANEL_CLIENT_SECRET=...
   # the hub as a game server reaches it: written into servers made with "New server"
   LODESTONE_PUFFERPANEL_SERVER_HUB_URL=http://127.0.0.1:8081
   ```
3. Check it: signed in to the dashboard, open `/api/admin/pufferpanel` (or call it with your admin
   token). `connected: true` with the right `serverCount` means it works; otherwise `error` says why.
4. To add a server: **New server** on the Servers page. Pick a template and its settings; the hub
   creates it on PufferPanel, registers it, writes its mod config and the mod jar, and starts the
   install. Then upload the modpack's server files in PufferPanel and start it from its page.
5. Optional, to skip that upload: a CurseForge API key (apply at
   [console.curseforge.com](https://console.curseforge.com/)) adds a **Modpack** step to New server.
   Add it to `/etc/lodestone/hub.env` and restart the hub:
   ```bash
   LODESTONE_CURSEFORGE_API_KEY=...
   ```
   The hub then downloads the chosen version's server files, keeps the pack's folders (not its start
   scripts or installers), puts them on the server and runs the install after them. That takes a few
   minutes in the background; the server's page shows how it's going. Packs whose authors only allow
   the CurseForge app to download them can't be picked, and are still uploaded by hand.

---

## Three things that will bite

1. **`LODESTONE_JWT_SECRET` must be set.** Without it the hub signs admin tokens with a key it
   generates per boot (`SecurityConfig.jwtKey()`), so the weekly reboot signs every admin out.
2. **Admin passwords are only read on the boot that creates them.** `AdminUserSeeder` skips
   usernames that already exist, so one boot without `LODESTONE_ADMIN_PASSWORD` leaves `admin`/`admin`
   in the database for good — changing the env file afterwards does nothing. Fix it from the
   dashboard's Admins page, not the env file.
3. **Import each server's existing `whitelist.json` before turning `syncWhitelist` on.** The hub
   answers `204` to a server's whitelist poll until its whitelist has been populated at least once,
   which is the only thing keeping a fresh hub from emptying a live server's whitelist. Import on the
   dashboard first, confirm the list looks right, then flip `syncWhitelist = true`.

---

## Day to day

```bash
sudo /opt/lodestone/repo/deploy/deploy.sh            # deploy the newest release
sudo /opt/lodestone/repo/deploy/deploy.sh v0.1.0     # deploy a tag (see Rolling back for older ones)
sudo /opt/lodestone/repo/deploy/deploy.sh --rollback # undo the last deploy, database included
systemctl status lodestone-hub lodestone-bot
journalctl -u lodestone-hub -f
systemctl list-timers lodestone-backup.timer
cat /opt/lodestone/deploys.log                        # what was deployed when, and the dump before it
curl -fsS localhost:8081/actuator/health
```

Changing a bot command or option description needs the commands re-registered once:

```bash
sudo runuser -u lodestone -- sh -c 'cd /opt/lodestone/bot && npm run deploy'
```

---

## Rolling back

Flyway has no down-migrations: the hub migrates the database on boot, and an older hub can't undo
that. So how you go back depends on whether the release you're leaving added a migration. Check by
comparing its newest migrations with the deploy's time in `/opt/lodestone/deploys.log`:

```bash
sudo mariadb wtc_lodestone -e "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"
```

- **Nothing installed since the deploy:** the database is still fine for the older release, so just
  deploy it again: `sudo /opt/lodestone/repo/deploy/deploy.sh v0.2.0`.
- **Something was installed:** the database has to go back too, using the dump `deploy.sh` took
  right before the swap.

```bash
sudo /opt/lodestone/repo/deploy/deploy.sh --rollback          # undo the last deploy
sudo /opt/lodestone/repo/deploy/deploy.sh --rollback v0.2.0 /var/backups/lodestone/wtc_lodestone-20261120-100000.sql.gz
```

With no arguments it reads the last line of `deploys.log` and puts back the tag that deploy replaced,
restoring the dump that deploy took. It refuses to guess when the last line is already a rollback,
or names no previous tag. In that case pass both. In order, it:

1. **Checks** the dump (`gzip -t`) and downloads the older release, while the hub is still up. A bad
   dump or a missing tag stops it there.
2. **Dumps the current state,** so the rollback itself can be undone. Its path is printed and logged
   as `leaving=`.
3. **Stops** the bot and the hub.
4. **Drops and recreates the database, then restores the dump into it.** Restoring over the live
   database is not enough: the dump only replaces the tables it contains. A table the newer
   migration created would survive while the restored Flyway history says it was never made, and
   deploying that release again would fail on "table already exists". The `lodestone` user's grants
   are on the database's name, so they survive the drop.
5. **Swaps in the older jar and bot, starts them,** waits for health and logs a `rollback` line.

If the script itself is the problem, these are the same steps by hand:

```bash
DUMP=/var/backups/lodestone/wtc_lodestone-TIMESTAMP.sql.gz
gzip -t "$DUMP"                                    # stop here if this complains
sudo systemctl start lodestone-backup.service      # the state you're leaving, just in case
sudo systemctl stop lodestone-bot lodestone-hub
sudo mariadb -e "DROP DATABASE wtc_lodestone; CREATE DATABASE wtc_lodestone CHARACTER SET utf8mb4;"
gunzip < "$DUMP" | sudo mariadb wtc_lodestone
sudo /opt/lodestone/repo/deploy/deploy.sh v0.2.0   # the release that dump came from
```

**A rollback loses everything written since that dump:** whitelist adds and removals, Discord
links, bans, applications, MOTD and server edits, heartbeat history. It also reaches the game
servers. Within one whitelist poll (10s), the mods bring each server's whitelist and the hub's bans
back in line with the restored lists. So someone whitelisted after the deploy is off again, and
someone unbanned is banned again. Redo those changes once the hub is back.

Dumps are kept 14 days (`lodestone-backup.service`). After that, rolling back needs a copy kept
somewhere else. The mod jars on the game servers aren't touched either way.

### Rehearsing a restore

A dump that has never been restored is a hope, not a backup. After the first deploy, and now and
then after that, restore the newest one into a scratch database next to the live one. Nothing is
stopped and the live database is only read:

```bash
DUMP=$(ls -t /var/backups/lodestone/*.sql.gz | head -n 1)
gzip -t "$DUMP"
sudo mariadb -e "CREATE DATABASE wtc_lodestone_restore_test CHARACTER SET utf8mb4;"
gunzip < "$DUMP" | sudo mariadb wtc_lodestone_restore_test
for db in wtc_lodestone wtc_lodestone_restore_test; do
  sudo mariadb -N -e "SELECT '$db',
    (SELECT MAX(CAST(version AS UNSIGNED)) FROM $db.flyway_schema_history) AS migration,
    (SELECT COUNT(*) FROM $db.servers) AS servers,
    (SELECT COUNT(*) FROM $db.whitelisted_players) AS whitelist,
    (SELECT COUNT(*) FROM $db.network_bans) AS bans"
done
sudo mariadb -e "DROP DATABASE wtc_lodestone_restore_test;"
```

The two lines should agree, apart from rows written since the dump. A restore that errors, or a
migration number that doesn't match, means the dumps need fixing before they're needed.
