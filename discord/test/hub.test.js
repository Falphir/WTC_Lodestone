'use strict';

// Exercises hub.js's token-cache + 401-retry logic (the one genuinely new piece of
// logic in this module) against a stubbed global fetch, no real hub required.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.HUB_URL = 'http://hub.test';
process.env.HUB_USERNAME = 'bot';
process.env.HUB_PASSWORD = 'secret';

function freshHub() {
  delete require.cache[require.resolve('../src/hub')];
  return require('../src/hub');
}

function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

test('logs in once and reuses the cached token', async () => {
  const calls = [];
  globalThis.fetch = async (url, opts) => {
    calls.push({ url, auth: opts.headers.Authorization });
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok1', expiresAt: new Date(Date.now() + 8 * 3600_000).toISOString() });
    }
    return jsonResponse(201, {});
  };
  const hub = freshHub();

  await hub.whitelistAdd('Notch');
  await hub.whitelistAdd('Jeb_');

  const logins = calls.filter((c) => c.url.endsWith('/api/auth/token'));
  assert.equal(logins.length, 1, 'should only log in once for two calls');
  assert.equal(calls[1].auth, 'Bearer tok1');
  assert.equal(calls[2].auth, 'Bearer tok1');
});

test('treats a 409 on add and a 404 on remove as success', async () => {
  globalThis.fetch = async (url) => {
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    if (url.endsWith('/api/admin/whitelist')) return jsonResponse(409, { message: 'already whitelisted' });
    return jsonResponse(404, { message: 'not whitelisted' });
  };
  const hub = freshHub();

  await assert.doesNotReject(hub.whitelistAdd('Notch'));
  await assert.doesNotReject(hub.whitelistRemove('00000000-0000-0000-0000-000000000000'));
});

test('re-logs in and retries once on a 401', async () => {
  let loginCount = 0;
  let whitelistCalls = 0;
  globalThis.fetch = async (url) => {
    if (url.endsWith('/api/auth/token')) {
      loginCount++;
      return jsonResponse(200, { token: `tok${loginCount}`, expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    whitelistCalls++;
    // Pretend the cached token is stale the first time this is called.
    return whitelistCalls === 1 ? jsonResponse(401, {}) : jsonResponse(201, {});
  };
  const hub = freshHub();

  await assert.doesNotReject(hub.whitelistAdd('Notch'));
  assert.equal(loginCount, 2, 'should log in again after the 401');
  assert.equal(whitelistCalls, 2, 'should retry the whitelist call once');
});

test('throws with the hub message on an unexpected failure', async () => {
  globalThis.fetch = async (url) => {
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    return jsonResponse(500, { message: 'db is down' });
  };
  const hub = freshHub();

  await assert.rejects(hub.whitelistAdd('Notch'), /db is down/);
});
