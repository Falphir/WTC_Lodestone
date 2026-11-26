'use strict';

// Decisions made on the dashboard reach Discord only through syncDashboardDecisions: these check
// each one is acted on and acknowledged, and that nothing is acknowledged when Discord is down.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '999';

async function loadApplications(unapplied) {
  const acked = [];
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: {
      getDiscordConfig: async () => ({ brandName: 'WTC', memberRoleId: 'member', applicationsChannelId: '555' }),
      listUnappliedDecisions: async () => unapplied,
      markDecisionApplied: async (id) => acked.push(id),
    },
  };
  for (const mod of ['../src/config', '../src/db', '../src/lib/applications']) {
    delete require.cache[require.resolve(mod)];
  }
  await require('../src/config').init();
  return { applications: require('../src/lib/applications'), acked };
}

function fakeClient({ guildDown = false } = {}) {
  const calls = { roles: [], dms: [] };
  const member = { displayAvatarURL: () => null, roles: { add: async (role) => calls.roles.push(role) } };
  const guild = { members: { fetch: async () => member } };
  const channel = { send: async () => {}, messages: { fetch: async () => { throw new Error('gone'); } } };
  return {
    calls,
    guilds: {
      fetch: async () => {
        if (guildDown) throw new Error('Discord is down');
        return guild;
      },
    },
    channels: { fetch: async () => channel },
    users: { fetch: async () => ({ send: async (payload) => calls.dms.push(payload) }) },
  };
}

test('grants the role for an approval and DMs the reason for a denial, then acknowledges both', async () => {
  const { applications, acked } = await loadApplications([
    { discordId: '1001', status: 'approved', messageId: null },
    { discordId: '1002', status: 'denied', messageId: null, denyReason: 'answers too short' },
  ]);
  const client = fakeClient();

  await applications.syncDashboardDecisions(client);

  assert.deepEqual(client.calls.roles, ['member']);
  assert.equal(client.calls.dms.length, 1);
  assert.match(client.calls.dms[0].embeds[0].toJSON().description, /answers too short/);
  assert.deepEqual(acked, ['1001', '1002']);
});

test('acknowledges nothing while Discord is unreachable, so the next run retries', async () => {
  const { applications, acked } = await loadApplications([{ discordId: '1001', status: 'approved' }]);
  const client = fakeClient({ guildDown: true });

  await applications.syncDashboardDecisions(client);

  assert.deepEqual(client.calls.roles, []);
  assert.deepEqual(acked, []);
});
