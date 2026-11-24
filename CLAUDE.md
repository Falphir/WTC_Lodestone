# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

WTC Lodestone: cross-server integration for the WTC Network. Three independent projects that share no code, only HTTP:

- **`mod/`** — server-side mod (`wtc_lodestone`), built for NeoForge 1.21.1 (Java 21) and Forge 1.20.1 (Java 17) from one tree. Runs on each modpack server and calls out to the hub. Its own guide: `mod/CLAUDE.md`.
- **`hub/`** — Spring Boot service, Java 21. The central API the mods talk to; also serves the React dashboard (`hub/src/main/frontend/`, guide: `hub/src/main/frontend/CLAUDE.md`) built into its jar.
- **`discord/`** — Node.js/discord.js bot: applications, account linking, bans. Calls the hub's admin API like a dashboard admin would. Guide: `discord/CLAUDE.md`.

Servers never talk to each other, and the mod stays fully functional (idle) if the hub is offline or unconfigured.

Other folders: `deploy/` (production runbook, systemd units, nginx, `deploy.sh`, the Docker variant), `docs/` (the reference docs listed at the end), `.github/workflows/` (CI and release).

## Commands

### Hub (`hub/`)
```bash
./gradlew build          # compiles, builds the frontend, runs tests
./gradlew bootRun         # run the hub locally (http://localhost:8080)
./gradlew test            # run tests only
./gradlew test --tests io.github.falphir.hub.server.TokenServiceTest   # single test class
```

### Dashboard (`hub/src/main/frontend/`)
```bash
npm install
npm run dev       # vite dev server; proxies /api (WebSocket included) to localhost:8080, so run bootRun alongside
npm run build     # also runs as part of the hub's ./gradlew build (buildFrontend task)
npm run lint
npm test          # node --test on src/**/*.test.js; not part of ./gradlew build or CI
```

### Mod (`mod/`)
```bash
./gradlew build                     # both jars; runs common/'s tests under each loader
./gradlew :neoforge:runServer       # dev server in mod/run/ (accept the EULA in mod/run/eula.txt on first run)
./gradlew :forge-1.20.1:runServer   # Forge dev server in mod/run-forge-1.20.1/
./gradlew :neoforge:test            # the config parser's tests (common/src/test/)
./gradlew :neoforge:test --tests io.github.falphir.lodestone.LodestoneConfigTest   # single test class
```

### Discord bot (`discord/`)
```bash
npm install
npm run deploy    # registers slash commands with Discord (run once, and after changing one)
npm start         # run the bot
npm test          # node:test, no runner; test/*.test.js stub fetch and the hub
npm test -- test/hub.test.js   # single file
```
Needs `.env` copied from `.env.example`, and a running hub. Everything else the bot needs lives on the hub.

On Windows use `gradlew.bat` (in PowerShell, `.\gradlew.bat`) instead of `./gradlew`.

## Environment gotchas

- **The hub's tests need MariaDB.** `docker compose -f deploy/docker-compose.dev.yml up -d --wait` (`--wait` blocks on the healthcheck, so a hub started right after doesn't race MariaDB's startup), matching `hub/src/main/resources/application.yaml`. There's no in-memory profile, so even `./gradlew build` needs it up. `./gradlew build` needs no local Node: the `node` plugin downloads its own (`CI` switches `npm install` to `npm ci`).
- **Flyway migrations** live in `hub/src/main/resources/db/migration/` (`V<n>__name.sql`). Find the highest `n` with a version sort, since a plain `ls` puts V14 before V7. V22 (`server_capabilities`) is the newest on `main`. The unmerged `feat/update-server` (its work is in a GitHub Desktop stash) still names its pack migration V20 and must rename it to **V23** before it runs anywhere, or Flyway fails on a checksum mismatch.
- **Spring Boot 4.1** split the old umbrella starters: the deps are `spring-boot-starter-webmvc`, `-flyway`, `-security-oauth2-resource-server`, and per-slice test starters (`-webmvc-test`, `-data-jpa-test`, …). Boot 3's `spring-boot-starter-web` or `spring-boot-starter-test` won't resolve. springdoc is on its 3.x (Boot 4) line for the same reason.
- **Running `HubApplication` from an IDE** boots fine, then 500s on every `@PathVariable`/`@RequestParam` endpoint ("parameter name information not available via reflection"). Controllers bind by parameter name, which needs `javac -parameters`: the Spring Boot Gradle plugin adds it, an IDE's own compiler (VS Code/Eclipse JDT) doesn't. `bootRun` is never affected.
- **The Forge subproject compiles with a Java 17 toolchain,** so a JDK 17 has to be installed next to the 21 Gradle runs on (Gradle won't download one).

## Rules and conventions

Hub:
- **Package by role, not feature:** `controller/` → `service/` → `repository/` → `entity/`, plus `config/`.
- **Flyway owns the schema** (`spring.jpa.hibernate.ddl-auto: validate`). An entity field without a migration fails the hub at boot, not at the first query.
- **Every controller is annotated for OpenAPI** (springdoc, `/swagger-ui.html`): `@Tag` on the class, `@Operation` per method, and the right `@SecurityRequirement`: `serverToken` for bridge, `adminToken` for admin JWT, `adminLogin` for the Basic exchange (names in `OpenApiConfig`).
- **Every admin change records itself:** call `audit.record(action, target, detail)` in the service method, after the change and inside its transaction. Edits record field names, never values or passwords. See `docs/audit-log.md`.
- **Dashboard updates are invalidations:** after a change, publish a `HubEvent` (topic `servers`, `whitelist`, `bans`, `admins` or `audit`, optional `serverId`). `LiveUpdates.broadcast` (`config/`, a `@TransactionalEventListener` behind the `/api/admin/live` WebSocket) pushes `{topic, serverId}` to open dashboards after commit, and they refetch over REST. Never push data down the socket.
- **Bridge tokens and admin JWTs are both `Authorization: Bearer`, and must never reach each other's validator.** `SecurityConfig.adminBearerTokens()` returns `null` for `/api/bridge/**` on purpose. See `docs/hub-auth.md`.
- **Transactions run at `READ_COMMITTED`, and `GameServer` is `@DynamicUpdate`.** A mod's startup writes the same `servers` row three times at once. Under MariaDB's default REPEATABLE READ those writes fail on 11.8 with `innodb_snapshot_isolation` on, as the dev image has it ("Record has changed since last read"), and older versions let full-row updates silently put back each other's columns. `ConcurrentServerWritesTest` pins it.
- **Server status is derived, never stored** (`ServerService.status`), and the dashboard never hardcodes its thresholds: it reads them from `GET /api/info`.

Tests:
- **The hub's tests hit a real database,** so a test that asserts on a whole table only passes on an empty one: CI's is fresh, a dev database isn't. A test that needs to own a table clears it inside its own `@Transactional` method, which rolls back (`AdminUserServiceTest`, `BridgeSyncTest`, `ServerHeartbeatRepositoryTest`). Otherwise assert on rows the test made (`contains`, not `containsExactly`), or on a status range (`/api/bridge/whitelist` is 204 on a never-populated hub, 200 otherwise). `ConcurrentServerWritesTest` is the one that commits, so it deletes what it leaves, audit rows included.

Mod and bot (details in their own `CLAUDE.md`):
- **Mod → hub calls are a static async method in `HubClient`:** never block the server thread, log and swallow failures, and short-circuit on `LodestoneConfig.dryRun()` before touching the network.
- **The bot keeps no durable state,** and writes no user-facing string inline: every one comes from `discord/locales/en.json` through `t()`.

## Further docs

Read these when the task touches their area. They aren't loaded automatically.

- **Sign-in, tokens, the WebSocket's auth, `SecurityConfig`:** `docs/hub-auth.md`
- **Heartbeats, status, who's online, public server listings:** `docs/servers-and-status.md`
- **The audit log, or the bot's on-behalf-of header:** `docs/audit-log.md`
- **Network whitelist, Discord link codes, network bans, the kick or ban message:** `docs/whitelist-and-bans.md`
- **PufferPanel client, linking a server to it, power controls, removing a server:** `docs/pufferpanel.md`
- **Creating a server from the dashboard, CurseForge packs, mod-jar choice, pack updates:** `docs/server-creation.md`
- **Network MOTDs, their alignment, the MOTD editor:** `docs/motd.md`
- **The bot's design (state, applications, `/review`, autocomplete):** `docs/discord-bot.md`
- **CI workflows or the release pipeline:** `docs/ci-and-release.md`
- **Production deploy, rollback, the Docker variant:** `docs/deploy.md` (the runbook itself is `deploy/README.md`)
- **Dashboard code:** `hub/src/main/frontend/CLAUDE.md`. **Mod code:** `mod/CLAUDE.md`. **Bot code:** `discord/CLAUDE.md`.
