# Creating a server from the dashboard

The New server flow: a PufferPanel server made from a template, registered on the hub, given the mod; CurseForge modpacks and their background setup; and pack update checks (not on `main` yet).

## The five steps

`ServerProvisioningService`, `POST /api/admin/pufferpanel/servers`, the Servers page's `CreateServerDialog`. It creates a panel server from a template and registers it in one step:
1. **Panel server:** the template's own JSON (`GET /api/templates/{name}`) with the chosen values written into `data[key].value`, plus `node`/`name`/`users`. `environment` is the template's, else its first `supportedEnvironments`. Sent to `POST /api/servers` → `{id}`.
2. **Hub registration and link:** `ServerService.register` with `pufferpanelId` (unaudited; the creation records one `server.create` instead). If this fails, the panel server is deleted again.
3. **Mod config:** `config/wtc_lodestone-common.toml`, written via `PUT /proxy/daemon/server/{id}/file/...`.
   - `hubUrl` is `lodestone.pufferpanel.server-hub-url` (`LODESTONE_PUFFERPANEL_SERVER_HUB_URL`): the hub as a *game server* reaches it, not the hub's own address.
   - Creation is refused while it's unset.
4. **Mod jar:** `ModRelease.jarFor(loader, minecraftVersion)` picks the latest GitHub release's `wtc_lodestone-<loader>-<mc>-*.jar` (`lodestone.mod-release-repo`) and puts it in `mods/`.
   - **What the server runs** comes from the pack's manifest, or for a blank server from `PackTemplates.runsOn` (the template and its chosen settings, the inverse of `suggest`).
   - **The release's asset names are the only list of which builds exist.** No match is a "No WTC Lodestone build for …" warning, not a failure.
5. **Install:** `install` is started.

**Steps 3–5 failing don't undo anything;** they come back as `warnings`. Without a modpack, its files are uploaded by hand afterwards.

**The form is built from the template's non-internal variables** (`GET /api/admin/pufferpanel/templates/{name}`), so new templates need no code. `useApi(null)` loads nothing, which is what the template details use while they wait for a pick.

**Two 2.7.1 quirks met here:**
- **A file write won't create missing folders,** so `putFile` makes the parent with `?folder` first.
- **`ModRelease` splices the repo into the URL,** since a `{repo}` URI variable encodes its `/` as `%2F` and GitHub answers 404.

## CurseForge modpacks

**Enabled** by setting `lodestone.curseforge.api-key` (`LODESTONE_CURSEFORGE_API_KEY`).

**The dialog opens on a choice:**
- **Blank server:** Basics → Settings → Review.
- **From a CurseForge modpack:** Modpack → Basics → Settings → Review. The pack goes first because it decides the rest.

Each choice starts from empty state.

**The exact loader version comes from the pack's manifest.** CurseForge's API only says "NeoForge, 1.21.1", so the version comes from the `manifest.json` in the pack version's own (client) zip. `CurseForgeClient.manifest` streams that zip and stops at the first entries, since the manifest comes first: a few KB even for a 196 MB pack.

**`GET /api/admin/curseforge/modpacks/{id}/files/{fileId}/setup`** (`ServerProvisioningService.packSetup`) turns the manifest into a template and settings via `PackTemplates`:
- **NeoForge** → `minecraft-neoforge`, with `version` = the NeoForge version.
- **Forge** → `minecraft-forge` (`-legacy` before 1.17), with `version` = `<mc>-<forge>`.
- **Fabric** → `minecraft-fabric`, with `game-version` set.
- **Every template** also gets `javaversion` (21/17/8 by Minecraft version), and `memory` from `recommendedRam`.

**How the suggestion is applied:**
- **Only settings the template shows in the form** are filled in.
- **Leaving the Modpack step** applies it: name and id are suggested from the pack while untouched.
- **Filled-in settings stay editable,** and keep a "From the pack" hint until changed.
- **Another loader (Quilt), or no readable manifest,** leaves the template to the admin, with the versions shown for matching by hand.

**The API key never reaches a browser:** the pack search also goes through the hub (`AdminCurseForgeController` → `CurseForgeClient`, `x-api-key`, game 432, class 4471).

**A pack's version name becomes just the version** in the listing (`PackTemplates.versionOf`: "All the Mods 10-8.4" → `8.4`), so a MOTD's `v{version}` reads well.

## Modpack setup

**What a pack changes about creation:** a chosen `modpack {modId, fileId}` on the create request fills in the server's listing from the pack. In step 5 it hands over to `ModpackSetup` instead of starting the install.

**Why it runs in the background:** `ModpackSetup` runs on a virtual thread after the request answers, since a pack is hundreds of MB and nginx gives a request 60s. Its steps:
1. **Download** the version's `serverPackFileId`.
2. **Trim it with `ServerPack.filter`.** The pack's root is the folder holding `mods/`, so a wrapper folder is looked through. Every folder is kept except `libraries`/`logs`/`world`, and only `server-icon.png` among root files.
3. **Upload** it as one zip.
4. **Unpack:** `GET /proxy/daemon/server/{id}/extract/{zip}` unpacks it into the root, then the zip is deleted.
5. **Start the install.**

**Uploading and unpacking** use `PufferPanelClient`'s second client, with a 30-minute timeout.

**2.7.1's extract has two quirks:**
- **It requires the destination to exist,** so no `destination` is given, which means the root.
- **It refuses to overwrite an existing file,** so `ServerPack` drops the mod config and the `wtc_lodestone-*.jar` the hub has already written.

**Progress** is kept in memory by panel id, which survives a rename. It holds the `Stage`, the error on `FAILED`, and `done`/`total` bytes while downloading or uploading (counted by `TransferProgress.Counting`).
- **On the dashboard:** it rides on the server view as `modpackSetup`, drawn by `ModpackSetupNote`'s progress bar on the server page.
- **Live updates are throttled:** byte counts publish a `HubEvent` at most once a second, since each one makes every dashboard refetch the server list.
- **Unpacking has no byte count,** since the panel answers only when it's done, so its bar is indeterminate.

**A failed setup still runs the install.**

**Packs that can't be downloaded:** a pack whose `allowModDistribution` is false has no download URL. It's shown disabled and refused with a 400.

## Pack updates (not on `main` yet)

This is `feat/update-server`'s stashed work. `PackUpdateService`, the Details tab's pack panel and `packUpdate` don't exist on `main`, and the `ServerPack` above (the zip filter) is a different thing from that branch's Details-tab `ServerPack` component. Its migration must become **V22** before it runs.

A server can be linked to the CurseForge pack it runs:
- **The columns:** `servers.curseforge_mod_id`/`_file_id`/`_file_name` for the installed version, `curseforge_latest_*` for the newest with server files, and `curseforge_checked_at`. All are on V22 once renamed, and all null when unlinked.
- **Linking:** automatic when the server is created from a pack (`PackUpdateService.linkKnown`). By hand from the server's Details tab (`ServerPack`, `PUT /api/admin/servers/{id}/curseforge`).
- **Checking:** `PackUpdateService.checkAll` runs every 6 hours, and `POST …/check` is "Check now". CurseForge lists versions newest first, so "update available" means the latest isn't the installed one.
- **The check's write:** CurseForge is called outside a transaction, then the row is reloaded in a short one to write the answer, so a stale copy never overwrites a heartbeat's columns.
- **Applying an update is manual.** `POST …/mark-updated` records it, and sets the listing's `modpackVersion` to the new version, so a MOTD's `{version}` follows.
- **On the dashboard:** the badge is `format.js`'s `packUpdate`, on the Servers list and the server page's header.
