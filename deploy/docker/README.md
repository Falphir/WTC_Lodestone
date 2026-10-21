# Deploying WTC Lodestone with Docker

For an Ubuntu server that does **not** run the modpack servers. The box that does stays on the
systemd setup in [`../README.md`](../README.md): a Docker install there once cut SSH and the
provider's private network at the same time, and that VPS has no console to recover from.

| | |
|---|---|
| Hub | container `hub`, published on `127.0.0.1:8081` only |
| Bot | container `bot`, not published |
| Database | container `mariadb`, not published, data in the `mariadb-data` volume |
| Public | host nginx on 80/443, the same `deploy/nginx/lodestone.conf` as the systemd setup |
| Backups | `backup.sh` nightly from cron, and before every deploy |

Both images are built on the server from this checkout, so the repo being private doesn't matter
and there is no registry to run. Tests aren't run in the build; CI already ran them on the commit.

---

## First-time setup

### 1. Docker, without letting it take over the network

Docker gives its networks addresses from `172.17.0.0/16` and up by default. If your provider uses
any of `172.16.0.0/12` for its private network, those routes get hijacked the moment the daemon
starts, and SSH can go with them. `daemon.json` moves Docker to `10.249.0.0/24` and `10.250.0.0/16`
instead, and it has to be in place **before** Docker is installed, since installing starts it.

First check those ranges are free on this box. Neither should appear here:

```bash
ip route
```

If one does, pick another range and edit `deploy/docker/daemon.json` after cloning (step 2).

```bash
sudo apt update
sudo apt install -y git curl nginx certbot python3-certbot-nginx
sudo git clone --depth 1 https://github.com/Falphir/WTC_Lodestone.git /opt/lodestone/repo
cd /opt/lodestone/repo
sudo install -d /etc/docker
sudo cp deploy/docker/daemon.json /etc/docker/daemon.json
```

If this server has no console either, arm a dead-man switch before installing. If you lose your SSH
session, it stops Docker and removes its bridge in ten minutes:

```bash
sudo systemd-run --on-active=10m --unit=docker-undo \
  sh -c 'systemctl disable --now docker.socket docker; ip link delete docker0'
```

```bash
sudo apt install -y docker.io docker-compose-v2
ip addr show docker0            # should say 10.249.0.1, not 172.17.0.1
```

Open a **second** SSH session to confirm you still have access, then disarm:

```bash
sudo systemctl stop docker-undo.timer
```

### 2. Configuration

```bash
cd /opt/lodestone/repo/deploy/docker
sudo cp .env.example .env && sudo chmod 600 .env
sudo nano .env                  # every value; generate secrets with: openssl rand -hex 32
```

Fill in **every** value before the first start. Compose refuses to start with one missing, and
the admin passwords are only read on the boot that creates those accounts (see the traps below).

### 3. First deploy

```bash
sudo sh /opt/lodestone/repo/deploy/docker/update.sh
```

The first build takes a few minutes: Gradle, the dashboard's Node and npm packages all download
inside the build. Later builds reuse Docker's cache.

Then register the bot's slash commands with Discord. Do this once, and again whenever a command or
option description changes:

```bash
cd /opt/lodestone/repo/deploy/docker
sudo docker compose run --rm bot npm run deploy
```

### 4. Nightly backups

```bash
echo '30 4 * * * root sh /opt/lodestone/repo/deploy/docker/backup.sh >> /var/log/lodestone-backup.log 2>&1' \
  | sudo tee /etc/cron.d/lodestone-backup
```

Dumps land in `/var/backups/lodestone/` and are kept for two weeks. They're on the same disk as the
database, so copy them somewhere else now and then.

### 5. nginx and TLS

Same as the systemd setup. The hub is on `127.0.0.1:8081`, which is where the vhost already points:

```bash
sudo cp /opt/lodestone/repo/deploy/nginx/lodestone.conf /etc/nginx/sites-available/lodestone
sudo sed -i 's/<your-host>/YOUR-HOST/' /etc/nginx/sites-available/lodestone
sudo ln -s /etc/nginx/sites-available/lodestone /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d YOUR-HOST
```

No domain yet? `<your-public-ip-with-dashes>.sslip.io` works with Let's Encrypt, as in the main
runbook.

### 6. Firewall

Only SSH, 80 and 443 need to be reachable. Docker's published ports skip ufw completely, which is
why the compose file binds the hub to `127.0.0.1` and publishes nothing else. Arm the same
dead-man switch as the main runbook before enabling ufw on a console-less box:

```bash
sudo systemd-run --on-active=10m --unit=ufw-undo systemctl disable --now ufw
sudo ufw allow OpenSSH
sudo ufw allow 80,443/tcp
sudo ufw enable
# second SSH session works? then:
sudo systemctl stop ufw-undo.timer
```

### 7. Game servers

The modpack servers are on other machines, so their mods reach the hub through nginx. In each
server's `config/wtc_lodestone-common.toml`:

```toml
hubUrl = "https://YOUR-HOST"
```

Everything else (registering the server on the dashboard first, `dryRun`, `syncWhitelist`,
`enforce-whitelist=true`, and `online-mode=true` + `white-list=true` for code linking) is the same
as section 9 of the main runbook, including emptying each server's `whitelist.json` when you
switch it over.

---

## Three things that will bite

1. **Admin passwords are only read on the boot that creates them.** Changing `ADMIN_PASSWORD` or
   `BOT_PASSWORD` in `.env` afterwards does nothing. Change them on the dashboard's Admins page.
   For the bot, update the account there and then `BOT_PASSWORD` in `.env` to match.
2. **`docker compose down -v` deletes the database.** `-v` removes the `mariadb-data` volume. Plain
   `down`, `restart` and `update.sh` never touch it.
3. **Import each server's existing `whitelist.json` before turning `syncWhitelist` on**, same as
   the main runbook.

---

## Day to day

All from `/opt/lodestone/repo/deploy/docker`:

```bash
sudo sh update.sh                         # deploy the latest main
sudo sh update.sh v0.1.0                  # deploy (or roll back to) a tag
sudo docker compose ps
sudo docker compose logs -f hub           # or bot, mariadb
sudo docker compose restart bot
sudo sh backup.sh                         # a dump right now
curl -fsS localhost:8081/actuator/health
```

`restart: unless-stopped` brings everything back after a reboot on its own.

Rolling back a release that added a Flyway migration means restoring the dump `update.sh` took
before it, then deploying the older tag:

```bash
sudo docker compose stop hub bot
gunzip < /var/backups/lodestone/wtc_lodestone-TIMESTAMP.sql.gz | sudo docker compose exec -T mariadb \
  sh -c 'MYSQL_PWD="$MARIADB_PASSWORD" mariadb -u"$MARIADB_USER" "$MARIADB_DATABASE"'
sudo sh update.sh v0.1.0
```
