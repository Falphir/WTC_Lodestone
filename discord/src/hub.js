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

// Long enough for a cold hub to answer, short enough to still fit inside the 15 minutes a
// deferred interaction gets -- and to fail visibly rather than hang.
const TIMEOUT_MS = 10_000;

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
    signal: AbortSignal.timeout(TIMEOUT_MS),
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

/**
 * One authenticated request. `what` is the "Could not ..." half of the error a caller shows the
 * user; `ok` lists the non-2xx statuses this particular call treats as success. Everything goes
 * through here so every call gets the timeout -- without one, a hung hub holds the interaction
 * open until Discord gives up and the user just sees "the application did not respond".
 */
async function request(method, path, { body, ok = [], what } = {}) {
  const res = await withAuth((t) =>
    fetch(`${baseUrl()}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${t}`,
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
      },
      ...(body !== undefined && { body: JSON.stringify(body) }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
  );
  if (res.ok || ok.includes(res.status)) return res;
  throw new Error(`${what}: ${await errorMessage(res)}`);
}

/** Runs an authenticated GET, returning null on a 404 instead of throwing. */
async function getOrNull(path) {
  const res = await request('GET', path, { ok: [404], what: `Could not load ${path}` });
  return res.status === 404 ? null : res.json();
}

/** Adds `name` to the network whitelist. A 409 (already whitelisted) is treated as success. */
async function whitelistAdd(name) {
  await request('POST', '/api/admin/whitelist', {
    body: { name },
    ok: [409],
    what: `Could not whitelist '${name}'`,
  });
}

/** Removes `uuid` from the network whitelist. A 404 (already not on it) is treated as success. */
async function whitelistRemove(uuid) {
  await request('DELETE', `/api/admin/whitelist/${uuid}`, {
    ok: [404],
    what: `Could not remove '${uuid}' from the whitelist`,
  });
}

/** Gets the bot's config (channels, roles, branding, application rules). */
async function getDiscordConfig() {
  const res = await request('GET', '/api/admin/discord/config', { what: 'Could not load the Discord config' });
  return res.json();
}

/** Merges `patch` into the bot's config (only non-null fields are changed) and returns the result. */
async function updateDiscordConfig(patch) {
  const res = await request('PATCH', '/api/admin/discord/config', {
    body: patch,
    what: 'Could not update the Discord config',
  });
  return res.json();
}

/**
 * The servers an admin has published for players to see, each with the listing /serverinfo shows
 * (address, modpack, version, launcher, link). Fetched per call rather than cached, since
 * publishing happens on the dashboard and the bot would never hear about it.
 */
async function listPublishedServers() {
  const res = await request('GET', '/api/admin/servers', { what: 'Could not load the server list' });
  const servers = await res.json();
  return servers.filter((s) => s.listing.published);
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
  const res = await request('PUT', `/api/admin/whitelist/${encodeURIComponent(uuid)}/discord-link`, {
    body: { discordId, username },
    what: `Could not link Discord account for '${uuid}'`,
  });
  return res.json();
}

// ---- applications ----

async function getApplication(discordId) {
  return getOrNull(`/api/admin/discord/applications/${encodeURIComponent(discordId)}`);
}

async function setApplicationPending(discordId, { messageId, uuid, username }) {
  const res = await request('PUT', `/api/admin/discord/applications/${encodeURIComponent(discordId)}/pending`, {
    body: { messageId, uuid, username },
    what: `Could not mark application pending for '${discordId}'`,
  });
  return res.json();
}

async function setApplicationDecision(discordId, status) {
  const res = await request('PUT', `/api/admin/discord/applications/${encodeURIComponent(discordId)}/decision`, {
    body: { status },
    what: `Could not record decision for '${discordId}'`,
  });
  return res.json();
}

/** A no-op if there wasn't an application. */
async function clearApplication(discordId) {
  await request('DELETE', `/api/admin/discord/applications/${encodeURIComponent(discordId)}`, {
    what: `Could not clear application for '${discordId}'`,
  });
}

module.exports = {
  whitelistAdd,
  whitelistRemove,
  getDiscordConfig,
  updateDiscordConfig,
  listPublishedServers,
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
