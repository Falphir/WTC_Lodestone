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
   ```
3. Check it: signed in to the dashboard, open `/api/admin/pufferpanel` (or call it with your admin
   token). `connected: true` with the right `serverCount` means it works; otherwise `error` says why.

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
sudo /opt/lodestone/repo/deploy/deploy.sh v0.1.0     # deploy (or roll back to) a tag
systemctl status lodestone-hub lodestone-bot
journalctl -u lodestone-hub -f
systemctl list-timers lodestone-backup.timer
curl -fsS localhost:8081/actuator/health
```

Rolling back a release that added a Flyway migration means restoring the dump `deploy.sh` took
before the swap — there are no down-migrations:

```bash
gunzip < /var/backups/lodestone/wtc_lodestone-TIMESTAMP.sql.gz | sudo mariadb wtc_lodestone
```

Changing a bot command or option description needs the commands re-registered once:

```bash
sudo runuser -u lodestone -- sh -c 'cd /opt/lodestone/bot && npm run deploy'
```
