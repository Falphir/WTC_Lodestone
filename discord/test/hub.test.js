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

test('surfaces a timed-out request instead of hanging', async () => {
  globalThis.fetch = async (url, opts) => {
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    assert.ok(opts.signal, 'every hub call should carry an abort signal');
    // What fetch does when AbortSignal.timeout fires.
    throw Object.assign(new Error('The operation was aborted due to timeout'), { name: 'TimeoutError' });
  };
  const hub = freshHub();

  await assert.rejects(hub.whitelistAdd('Notch'), /timeout/);
});

test('a link code comes back as the linked player, or as the status the hub refused it with', async () => {
  const replies = { GOOD23: [200, { uuid: 'u-1', name: 'Notch' }], WRONG2: [404, { message: 'expired' }], TAKEN2: [409, { message: 'taken' }] };
  globalThis.fetch = async (url, opts) => {
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    const { code, discordId } = JSON.parse(opts.body);
    assert.equal(discordId, '400000000000000042');
    return jsonResponse(...replies[code]);
  };
  const hub = freshHub();

  assert.deepEqual(await hub.claimLinkCode('GOOD23', '400000000000000042'), { status: 200, player: { uuid: 'u-1', name: 'Notch' } });
  assert.deepEqual(await hub.claimLinkCode('WRONG2', '400000000000000042'), { status: 404 });
  assert.deepEqual(await hub.claimLinkCode('TAKEN2', '400000000000000042'), { status: 409 });
});

test('a ban for an unknown account comes back as null; an unban of someone not banned still succeeds', async () => {
  globalThis.fetch = async (url, opts) => {
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    if (url.endsWith('/api/admin/bans') && opts.method === 'POST') {
      return JSON.parse(opts.body).name === 'Griefer'
        ? jsonResponse(201, { uuid: 'u-1', name: 'Griefer' })
        : jsonResponse(404, { message: 'No Minecraft account' });
    }
    return jsonResponse(404, { message: 'Player is not banned' });
  };
  const hub = freshHub();

  assert.deepEqual(await hub.ban('Griefer', null, null), { uuid: 'u-1', name: 'Griefer' });
  assert.equal(await hub.ban('Nobody', null, null), null);
  await assert.doesNotReject(hub.unban('u-2'));
});

test('calls made for a Discord user name them for the audit log, and only those', async () => {
  const seen = [];
  globalThis.fetch = async (url, opts) => {
    seen.push({ url, onBehalfOf: opts.headers['X-Lodestone-On-Behalf-Of'] });
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    return jsonResponse(201, {});
  };
  const hub = freshHub();

  await hub.actingFor({ id: '123', username: 'staffér' }, () => hub.whitelistAdd('Notch'));
  await hub.whitelistAdd('Jeb_');

  const [login, forStaff, onItsOwn] = seen;
  assert.equal(login.onBehalfOf, undefined, 'signing in is the bot itself');
  assert.equal(forStaff.onBehalfOf, 'staff?r (123)', 'non-ASCII is replaced, since it goes in a header');
  assert.equal(onItsOwn.onBehalfOf, undefined);
});

test('a decision someone else already made comes back as null, not an error', async () => {
  const bodies = [];
  globalThis.fetch = async (url, opts) => {
    if (url.endsWith('/api/auth/token')) {
      return jsonResponse(200, { token: 'tok', expiresAt: new Date(Date.now() + 3600_000).toISOString() });
    }
    bodies.push(JSON.parse(opts.body));
    if (url.includes('/taken/')) return jsonResponse(409, { message: 'This application was already approved' });
    return jsonResponse(200, { discordId: 'free', status: 'denied', denyReason: 'too short' });
  };
  const hub = freshHub();

  assert.equal((await hub.setApplicationDecision('free', 'denied', { reason: 'too short' })).denyReason, 'too short');
  assert.equal(await hub.setApplicationDecision('taken', 'approved'), null);
  // the bot acts on its own decisions, so the hub mustn't queue them for it
  assert.deepEqual(bodies[0], { status: 'denied', reason: 'too short', applied: true });
  assert.equal(bodies[1].applied, true);
});
