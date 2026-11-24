# PufferPanel

The hub's PufferPanel 2.7.1 client, how a hub server is linked to the panel server it runs as, power controls, and removing a server.

## The client

`PufferPanelClient` is written against **2.7.1**, the version in use. 3.x moved routes, so check the v2.7.1 source on GitHub, not the 3.x docs.

**Sign-in:** an OAuth2 client at `POST /oauth2/token`.
- The body is **form-encoded** (JSON is rejected), with `grant_type=client_credentials`.
- The answer is `access_token`/`expires_in` 3600. The token is cached until 60s before expiry.
- On a 401 it fetches a fresh token and retries once.

**Routes:**
- **Panel routes** are under `/api`, e.g. `GET /api/servers?limit=&page=` → `{servers, paging}`.
- **Daemon routes** (`start`/`stop`/`kill`/`status`/`console`/`file/*`) must be called as `/proxy/daemon/server/{id}/...`, since an OAuth token isn't accepted on `/daemon` directly.
- Later features build on the client's generic `get`/`post`.

**Config:** `lodestone.pufferpanel.url`/`client-id`/`client-secret` (`LODESTONE_PUFFERPANEL_URL`, `_CLIENT_ID`, `_CLIENT_SECRET`). They're spelled out in `application.yaml` because relaxed binding alone would want `CLIENTID`. With any of them blank, `isConfigured()` is false and nothing calls out.

**Failures** come back as `PanelException` with an admin-readable message, never the secret or token.

**The connection check:** `GET /api/admin/pufferpanel` always answers 200, with `configured`, `connected`, `serverCount` and `error`.

**Tests** use `MockRestServiceServer` bound to the builder the package-private constructor takes.

## Linking a hub server to a panel server

**The link column:** `servers.pufferpanel_id` (V18, `VARCHAR(8)`, unique key, NULL = unlinked).

**Setting it:** send `pufferpanelId` on `POST`/`PATCH /api/admin/servers`. Null leaves it, `""` unlinks. Both routes go through `ServerService.applyListing`.

**Checks when linking:**
- The id must be in `listServers()`: 503 if PufferPanel isn't configured, 502 if it's unreachable, 404 if the id is unknown.
- One already held by another hub server is refused (409).
- A rename keeps the link, since it's on the row.

**Listing panel servers:** `GET /api/admin/pufferpanel/servers` lists them with `linkedTo` (the hub server holding each, from `ServerService.panelLinks()`), or `[]` when PufferPanel isn't configured.

**The link is optional.** Every server is meant to live on PufferPanel, but the hub has to work with PufferPanel unset. The dashboard parts (registering from the panel list, a link dropdown) are separate follow-ups.

## Power controls

`ServerPowerService`, `GET`/`POST /api/admin/servers/{id}/power[/{action}]`, the dashboard's `ServerPower` panel (shown only for linked servers).

**Start, stop and kill** go to `/proxy/daemon/server/{panelId}/{action}` without `?wait`. **Status** is `/status` → `{running}`.

**2.7.1 has no restart**, so restart:
1. sends stop;
2. on a virtual thread, polls status every 2s;
3. starts the server once it's stopped.

It gives up after 3 minutes without starting, so a server can never run twice. Only one restart can run per server at a time (409).

**`?wait` is never used,** since stop's wait outlasts the client's 10s read timeout.

**Logging:** each action goes to the hub's log and, once the panel has taken it, to the audit log (`server.<action>`).

## Removing a server

`DELETE /api/admin/servers/{id}?pufferpanel=true`. In the dashboard it's the checkbox in the remove dialog, on by default for linked servers. It can delete the PufferPanel server too, files and world included.
- The panel goes first. If it refuses (502), the hub keeps its entry.
- A panel server that's already gone counts as deleted.

Creating a server on the panel from the dashboard is in `docs/server-creation.md`.
