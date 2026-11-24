# CI and releases

What each GitHub Actions workflow runs, and what a `v*` tag publishes.

## Per-push CI

`.github/workflows/hub.yml`, `mod.yml` and `discord.yml` are path-filtered per project:
- **The two Gradle workflows** run `./gradlew build`.
  - `mod.yml` (like `release.yml`) installs JDK 17 and 21, for the Forge subproject.
  - Hub CI spins up a real MariaDB service container, with the same credentials as `deploy/docker-compose.dev.yml`, since hub tests hit a real database instead of mocking it.
- **`discord.yml`** is just `npm ci && npm test` on Node 22, with no services, since the bot's tests stub `fetch` and never reach a hub or a database.

**The dashboard's `npm test`** (the MOTD model) and `npm run lint` aren't part of any workflow. Run them locally.

**CI's database is fresh, a dev database isn't.** That's why tests own their tables: see "Rules and conventions" in the root `CLAUDE.md`.

## Releases

**`release.yml`** is the fourth workflow. A `v*` tag builds the hub and the mod, then attaches these to a GitHub Release:
- **`wtc_lodestone-hub.jar`:** a fixed name, so the deploy script needs no version.
- **The NeoForge and Forge jars:** their `wtc_lodestone-<loader>-<mc>-<version>.jar` names are what `ModRelease.jarFor` matches when the hub creates a server.
- **`wtc_lodestone-bot.tar.gz`** of `discord/`.

**Tag after merging to `main`,** so the release matches it. The manual "Run workflow" button can't publish a release, since publishing needs a tag.
