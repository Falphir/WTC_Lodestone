# Production deploy

The production layout, the constraints the code puts on it, and how rollback works. The step-by-step runbook is `deploy/README.md` (and `deploy/docker/README.md` for the Docker variant). This doc only says what code changes need to keep in mind.

## Layout

**One Linux box, no Docker:**
- **systemd** runs the hub (`127.0.0.1:8081`) and the bot.
- **nginx** terminates TLS and is the only thing exposed.
- **MariaDB** is an `apt` install with a nightly dump timer.
- **The modpack servers are on the same box,** so mods use the loopback `hubUrl`.

**Everything lives in `deploy/`:**
- `README.md`: the runbook.
- `systemd/`: the units.
- `nginx/lodestone.conf`: the vhost. Its `/api/admin/live` location carries the WebSocket upgrade headers and a long read timeout.
- `deploy.sh`: what ships a release. It dumps the database, stops, swaps the jar and bot tree, runs `npm ci --omit=dev`, starts, and polls `/actuator/health`.

**`deploy/docker/`** is the alternative for a *separate* server that doesn't host the modpack servers.
- **Never on the game box:** a Docker install there once cut SSH by colliding with the provider's `172.16.0.0/12` network.
- **What it runs:** `docker-compose.yml` runs mariadb + hub + bot from images built on the server out of `hub/Dockerfile` and `discord/Dockerfile` (tests skipped in the image build).
- **What it exposes:** only the hub, on `127.0.0.1:8081`, so the same nginx vhost fronts it.
- **Docker's networks:** `daemon.json` pins Docker's address pools away from `172.16.0.0/12`.
- **Its scripts:** `update.sh` (dump, `git checkout`, build, up, health poll) and `backup.sh` are run with `sh`, not executed directly, since they may be committed from Windows without an exec bit.

## Constraints that come from the code

**No config files in production.** `application.yaml` stays as committed, and every value is an env var in `/etc/lodestone/hub.env`, via relaxed binding (`SERVER_PORT`, `SPRING_DATASOURCE_*`, `LODESTONE_*`).

**Three things the code forces:**
- **`LODESTONE_JWT_SECRET` must be set,** or the box's weekly reboot signs every admin out (`SecurityConfig.jwtKey()`).
- **`LODESTONE_ADMIN_PASSWORD` is read once.** `AdminUserSeeder` only creates admins that don't exist, so it's read on the boot that creates the row and never again.
- **Flyway has no down-migrations,** so the pre-deploy dump is the only rollback for a release that migrates.

## Rollback

**The command:** `deploy.sh --rollback [TAG] [DUMP]`. The runbook is `deploy/README.md`, under "Rolling back".

**Where the defaults come from:** every deploy appends `deploy from=<tag> to=<tag> dump=<path>` to `/opt/lodestone/deploys.log`, with `latest` resolved to its real tag through GitHub's redirect. `--rollback` takes its defaults from that last line.

**What a rollback does, in order:**
1. **Checks the dump and downloads the release** before stopping anything.
2. **Dumps the state it's leaving.**
3. **Drops and recreates the database before restoring.** Restoring over it would keep any table a newer migration created, while the restored Flyway history says that migration never ran. The next deploy of that release would then fail.

**A rollback also reverts the servers:** the mods re-apply the restored whitelist and bans within one poll.

**The Docker variant has the manual steps only** (`deploy/docker/README.md`), no `--rollback`.
