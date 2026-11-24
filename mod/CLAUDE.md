# Mod (mod/)

The server-side mod: how calls to the hub are written, how the multi-loader build is laid out, the Forge 1.20.1 build's quirks, the config file, and the one mixin. Commands are in the root `CLAUDE.md`.

## Calling the hub

**The mod is deliberately inert until fully configured:** `LodestoneConfig.isReady()` needs `serverId` and `token` non-blank.

**What happens on start:** on `ServerStartingEvent` it POSTs to `/api/bridge/hello` with its bearer token. The hub's `BridgeController.hello` records `lastSeen`, and echoes back the server's canonical id and name, so the mod can detect a config/token mismatch.

**New mod → hub calls follow the same pattern:**
- a static async call in `HubClient`;
- never block the server thread;
- log and swallow a failure;
- short-circuit on `LodestoneConfig.dryRun()` before touching the network, like `hello()` does.

**The hub is never needed.** The mod stays fully functional (idle) if the hub is offline or unconfigured: `HubClient` calls never throw into game logic.

What the calls carry (heartbeats, whitelist and ban sync, link codes, the MOTD) is in `docs/servers-and-status.md`, `docs/whitelist-and-bans.md` and `docs/motd.md`.

## Project layout

**`mod/` is a multi-project build, with one subproject per loader.**

**`common/src/main/java` holds everything the mod actually does:** `Lodestone` (lifecycle), `HubClient` (all hub calls), `LodestoneConfig`. It imports nothing but vanilla Minecraft and plain Java.

**Each loader subproject compiles `common/` itself:** it adds that directory to its own source set (`sourceSets.main.java.srcDir '../common/src/main/java'`) rather than depending on a built artifact.
- So every jar is self-contained.
- A loader-specific import in `common/` breaks the next loader's build immediately.
- `common/` has no `build.gradle`: there's nothing to compile without a loader to compile it against.

**There are two loader subprojects:**
- `neoforge/`: 1.21.1, `net.neoforged.moddev`.
- `forge-1.20.1/`: `net.neoforged.moddev.legacyforge`.

Both plugins are declared once in the root `build.gradle` with `apply false`, so they share a version and a classloader.

**A loader subproject is only an entrypoint** (`NeoForgeLodestone`, `ForgeLodestone`, ~35 lines each). It calls `Lodestone.init(configDir, platform)` once, and forwards server-starting and server-tick (on Forge, `TickEvent.ServerTickEvent` at `Phase.END`).

**`Lodestone.Platform` is everything that differs per loader:**
- the mod, Minecraft and loader versions;
- the loader's name: `"neoforge"` or `"forge"`, sent in hello and stored by the hub as `servers.loader` (V20);
- `averageTickMillis`, since 1.21.1's `getAverageTickTimeNanos()` is `getAverageTickTime()`, in ms, on 1.20.1.

**`common/` must therefore stay within Java 17 and 1.20.1's vanilla API.**

**What else a loader has to supply:**
- **The config dir,** passed into `init`, so nothing in `common/` guesses at paths.
- **An access transformer** widening `StoredUserEntry.getUser()`, which the whitelist sync needs.

**Per the versions decision,** 1.21.1 and up is NeoForge-only, and 1.20.1 is Forge-only. Both build from one branch, one subproject each, rather than a branch per Minecraft version or preprocessor directives.

## Forge 1.20.1's quirks

These only show up in its build:
- **SRG names.** Production Forge 1.20.1 runs on SRG names, and the Legacy plugin validates the access transformer against them. So `forge-1.20.1/`'s AT names `m_11373_`, not `getUser`.
  - Look a name up in `build/moddev/artifacts/namedToIntermediate.tsrg`.
  - The Legacy plugin also has to be pointed at the AT (`accessTransformers { from … }`).
- **Mixins.** There's no `[[mixins]]` in 1.20.1's mods.toml.
  - The config is found through the jar's `MixinConfigs` manifest attribute.
  - The mixin annotation processor writes a refmap: `wtc_lodestone.refmap.json`, mapping `canPlayerLogin` → `m_6418_`.
  - `PlayerListMixin` is a copy per loader, in its own package.
- **Two jars.** `reobfJar` makes the production (SRG) jar, in `build/libs/`. The dev (mojmap) jar is in `build/devlibs/`, which is why CI's `mod/*/build/libs/*.jar` never picks it up. Only the `build/libs/` one loads on a real server.

## Versions and metadata

**Every version lives in `mod/gradle.properties`:** Minecraft, NeoForge, Forge (the `forge_*` keys), Parchment, and the mod's own.

**Each loader's mods.toml is a template** (`<loader>/src/main/templates/`), expanded by its `generateModMetadata` task. Change a version in `gradle.properties`, never in the toml or a build output.

**A literal `${…}` in a template comment breaks the expansion.**

**Release jar names** are `wtc_lodestone-<loader>-<mc>-<version>.jar`. The hub picks the jar for a new server by that name (`docs/server-creation.md`), so keep the pattern.

## Config

**Config is read by hand** (`LodestoneConfig`), not through NeoForge's `ModConfigSpec`: that API is loader-specific, and the file is a handful of flat keys. It keeps the exact format `ModConfigSpec` wrote, so configs on already-deployed servers keep working. `LodestoneConfigTest` pins that.

**The file** is `config/wtc_lodestone-common.toml` (dev: `mod/run/config/`). Its keys:
- `enabled`, `dryRun`, `hubUrl`, `serverId`, `token`;
- `syncBans`: applies the network ban list;
- `syncWhitelist`: mirrors the hub's network whitelist into this server's `whitelist.json` on a timer;
- `whitelistMessage`.

## The kick-message mixin

**`whitelistMessage` replaces the kick message a non-whitelisted player gets,** and it's the one feature that needed a mixin: `<loader>/.../mixin/PlayerListMixin.java`. It's registered through `[[mixins]]` in `neoforge.mods.toml`, and through the manifest on Forge.

**Why a mixin is the only way:** vanilla's `PlayerList.canPlayerLogin` returns `Component.translatable("multiplayer.disconnect.not_whitelisted")`. A translation key is resolved on the *client*, so no server-side lang file or loader event can change that text. Only swapping in a literal component can.

**What it changes:** the injection is at RETURN, and it only rewrites the answer when the reason was the whitelist. So bans, IP bans and a full server keep vanilla's wording. The exception is a network ban, which gets the hub's ban message (`docs/whitelist-and-bans.md`).

**The config value:**
- Blank (the default) leaves vanilla's message alone.
- `\n` in the value becomes a line break. `LodestoneConfig` decodes it, since the file is one line per key.

**The hub can override it network-wide, and the message carries the link code:** see `docs/whitelist-and-bans.md`.
