'use strict';

// Talks to the WTC Lodestone hub's admin API. The bot signs in as an ordinary admin
// account (added to `lodestone.admins` on the hub), exactly like a dashboard user,
// and uses the resulting JWT as a Bearer token. Servers pick up whitelist changes
// on their own poll (see the hub's WhitelistService / the mod's syncWhitelist) --
// this module only has to tell the hub, never a specific server.
//
// Kept independent of config.js so this module can be loaded -- and tested -- on its own.
require('dotenv').config();

function requireEnv(name) {
  const val = process.env[name];
  if (!val) throw new Error(`Missing required environment variable: ${name} (see .env.example)`);
  return val;
}

let token = null;
let expiresAt = 0;

function baseUrl() {
  const url = requireEnv('HUB_URL');
  return url.endsWith('/') ? url.slice(0, -1) : url;
}

async function login() {
  const basic = Buffer.from(`${requireEnv('HUB_USERNAME')}:${requireEnv('HUB_PASSWORD')}`).toString('base64');
  const res = await fetch(`${baseUrl()}/api/auth/token`, {
    method: 'POST',
    headers: { Authorization: `Basic ${basic}` },
  });
  if (!res.ok) throw new Error(`Hub login failed (HTTP ${res.status}) -- check HUB_USERNAME/HUB_PASSWORD`);
  const body = await res.json();
  token = body.token;
  expiresAt = Date.parse(body.expiresAt);
}

async function ensureToken() {
  if (!token || Date.now() > expiresAt - 60_000) await login();
}

// Runs one authenticated request, retrying once with a fresh token on a 401
// (e.g. the hub restarted and rotated its signing key -- see SecurityConfig.jwtKey()).
async function withAuth(request) {
  await ensureToken();
  let res = await request(token);
  if (res.status === 401) {
    await login();
    res = await request(token);
  }
  return res;
}

async function errorMessage(res) {
  const body = await res.json().catch(() => null);
  return body?.message || `HTTP ${res.status}`;
}

/** Runs an authenticated GET, returning null on a 404 instead of throwing. */
async function getOrNull(path) {
  const res = await withAuth((t) => fetch(`${baseUrl()}${path}`, { headers: { Authorization: `Bearer ${t}` } }));
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Could not load ${path}: ${await errorMessage(res)}`);
  return res.json();
}

/** Adds `name` to the network whitelist. A 409 (already whitelisted) is treated as success. */
async function whitelistAdd(name) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/whitelist`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    })
  );
  if (res.ok || res.status === 409) return;
  throw new Error(`Could not whitelist '${name}': ${await errorMessage(res)}`);
}

/** Removes `uuid` from the network whitelist. A 404 (already not on it) is treated as success. */
async function whitelistRemove(uuid) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/whitelist/${uuid}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${t}` },
    })
  );
  if (res.ok || res.status === 404) return;
  throw new Error(`Could not remove '${uuid}' from the whitelist: ${await errorMessage(res)}`);
}

/** Gets the bot's config (channels, roles, branding, application rules). */
async function getDiscordConfig() {
  const res = await withAuth((t) => fetch(`${baseUrl()}/api/admin/discord/config`, { headers: { Authorization: `Bearer ${t}` } }));
  if (!res.ok) throw new Error(`Could not load the Discord config: ${await errorMessage(res)}`);
  return res.json();
}

/** Merges `patch` into the bot's config (only non-null fields are changed) and returns the result. */
async function updateDiscordConfig(patch) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/config`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    })
  );
  if (!res.ok) throw new Error(`Could not update the Discord config: ${await errorMessage(res)}`);
  return res.json();
}

/** Lists the display-only servers shown by /serverinfo. */
async function listDiscordServers() {
  const res = await withAuth((t) => fetch(`${baseUrl()}/api/admin/discord/servers`, { headers: { Authorization: `Bearer ${t}` } }));
  if (!res.ok) throw new Error(`Could not load the Discord server list: ${await errorMessage(res)}`);
  return res.json();
}

async function addDiscordServer(server) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/servers`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(server),
    })
  );
  if (!res.ok) throw new Error(`Could not add server '${server.id}': ${await errorMessage(res)}`);
  return res.json();
}

/** Merges `patch` into server `id` (only non-null fields are changed) and returns the result. */
async function updateDiscordServer(id, patch) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/servers/${id}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    })
  );
  if (!res.ok) throw new Error(`Could not update server '${id}': ${await errorMessage(res)}`);
  return res.json();
}

async function removeDiscordServer(id) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/servers/${id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${t}` },
    })
  );
  if (res.ok || res.status === 404) return;
  throw new Error(`Could not remove server '${id}': ${await errorMessage(res)}`);
}

/**
 * Resolves a Minecraft username via the hub (which already does this for its whitelist) instead
 * of the bot calling Mojang's API itself. Returns { uuid, name } or null if no such account.
 */
async function mojangLookup(name) {
  return getOrNull(`/api/admin/mojang/${encodeURIComponent(name)}`);
}

// ---- whitelisted players, incl. the Discord link each one may have ----
// A Discord link is just two columns on a whitelisted_players row now (merged from what used to
// be a separate discord_links table/local SQLite) -- a link only ever makes sense for an account
// that's actually whitelisted, and removing a player drops its link for free. db.js adapts these
// "player" views into the plain {discordId, uuid, username, linkedAt} shape its callers expect.

async function getPlayerByUuid(uuid) {
  return getOrNull(`/api/admin/whitelist/${encodeURIComponent(uuid)}`);
}

async function getPlayerByDiscord(discordId) {
  return getOrNull(`/api/admin/whitelist/by-discord/${encodeURIComponent(discordId)}`);
}

async function getPlayerByName(name) {
  return getOrNull(`/api/admin/whitelist/by-name/${encodeURIComponent(name)}`);
}

/** Links `discordId` to the already-whitelisted account `uuid`. */
async function linkDiscord(uuid, discordId, username) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/whitelist/${encodeURIComponent(uuid)}/discord-link`, {
      method: 'PUT',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ discordId, username }),
    })
  );
  if (!res.ok) throw new Error(`Could not link Discord account for '${uuid}': ${await errorMessage(res)}`);
  return res.json();
}

// ---- applications ----

async function getApplication(discordId) {
  return getOrNull(`/api/admin/discord/applications/${encodeURIComponent(discordId)}`);
}

async function setApplicationPending(discordId, { messageId, uuid, username }) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/applications/${encodeURIComponent(discordId)}/pending`, {
      method: 'PUT',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ messageId, uuid, username }),
    })
  );
  if (!res.ok) throw new Error(`Could not mark application pending for '${discordId}': ${await errorMessage(res)}`);
  return res.json();
}

async function setApplicationDecision(discordId, status) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/applications/${encodeURIComponent(discordId)}/decision`, {
      method: 'PUT',
      headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    })
  );
  if (!res.ok) throw new Error(`Could not record decision for '${discordId}': ${await errorMessage(res)}`);
  return res.json();
}

/** A no-op if there wasn't an application. */
async function clearApplication(discordId) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}/api/admin/discord/applications/${encodeURIComponent(discordId)}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${t}` },
    })
  );
  if (!res.ok) throw new Error(`Could not clear application for '${discordId}': ${await errorMessage(res)}`);
}

module.exports = {
  whitelistAdd,
  whitelistRemove,
  getDiscordConfig,
  updateDiscordConfig,
  listDiscordServers,
  addDiscordServer,
  updateDiscordServer,
  removeDiscordServer,
  mojangLookup,
  getPlayerByUuid,
  getPlayerByDiscord,
  getPlayerByName,
  linkDiscord,
  getApplication,
  setApplicationPending,
  setApplicationDecision,
  clearApplication,
};
