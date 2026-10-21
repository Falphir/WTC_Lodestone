'use strict';

// /ban and /unban go through the hub like the dashboard does; these check the staff gate, the
// duration turning into an expiry, and unbanning someone the network never banned.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '999';

let calls = [];

async function load(command, { banned = { uuid: 'u-1', name: 'Griefer', reason: null, expiresAt: null }, list = [] } = {}) {
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: {
      getDiscordConfig: async () => ({ staffRoleIds: ['staff'] }),
      ban: async (name, reason, expiresAt) => {
        calls.push({ ban: { name, reason, expiresAt } });
        return banned;
      },
      listBans: async () => list,
      unban: async (uuid) => calls.push({ unban: uuid }),
    },
  };
  for (const mod of ['../src/config', '../src/lib/applications', `../src/commands/${command}`]) {
    delete require.cache[require.resolve(mod)];
  }
  await require('../src/config').init();
  calls = [];
  return require(`../src/commands/${command}`);
}

function fakeInteraction({ staff = true, options = {} } = {}) {
  const sent = { replies: [], edits: [] };
  return {
    sent,
    user: { id: '400000000000000042' },
    client: { channels: { fetch: async () => ({ send: async () => {} }) } },
    member: { roles: { cache: { has: (id) => staff && id === 'staff' } } },
    options: { getString: (key) => options[key] ?? null },
    reply: async (payload) => sent.replies.push(payload),
    deferReply: async () => {},
    editReply: async (payload) => sent.edits.push(payload),
  };
}

test('refuses /ban to anyone without a staff role', async () => {
  const command = await load('ban');
  const interaction = fakeInteraction({ staff: false, options: { mc_username: 'Griefer' } });

  await command.execute(interaction);

  assert.deepEqual(calls, []);
  assert.match(interaction.sent.replies[0].content, /Staff only/);
});

test('a 7-day ban is sent with an expiry a week out', async () => {
  const command = await load('ban');
  const before = Date.now();

  await command.execute(fakeInteraction({ options: { mc_username: 'Griefer', duration: '7d', reason: 'x-ray' } }));

  const { name, reason, expiresAt } = calls[0].ban;
  assert.equal(name, 'Griefer');
  assert.equal(reason, 'x-ray');
  const week = 7 * 86_400_000;
  assert.ok(Math.abs(new Date(expiresAt).getTime() - (before + week)) < 5000, expiresAt);
});

test('no duration is a permanent ban', async () => {
  const command = await load('ban');
  const interaction = fakeInteraction({ options: { mc_username: 'Griefer' } });

  await command.execute(interaction);

  assert.equal(calls[0].ban.expiresAt, null);
  assert.match(interaction.sent.edits[0], /Banned \*\*Griefer\*\* on every server permanently/);
});

test('says so when Mojang has no such account', async () => {
  const command = await load('ban', { banned: null });
  const interaction = fakeInteraction({ options: { mc_username: 'Nobody' } });

  await command.execute(interaction);

  assert.match(interaction.sent.edits[0], /Mojang has no Java account named `Nobody`/);
});

test('/unban finds the player on the ban list, whatever the casing', async () => {
  const command = await load('unban', { list: [{ uuid: 'u-1', name: 'Griefer' }] });
  const interaction = fakeInteraction({ options: { mc_username: 'griefer' } });

  await command.execute(interaction);

  assert.deepEqual(calls, [{ unban: 'u-1' }]);
  assert.match(interaction.sent.edits[0], /Unbanned \*\*Griefer\*\*/);
});

test('/unban says so for someone the network never banned', async () => {
  const command = await load('unban', { list: [] });
  const interaction = fakeInteraction({ options: { mc_username: 'Innocent' } });

  await command.execute(interaction);

  assert.deepEqual(calls, []);
  assert.match(interaction.sent.edits[0], /isn't banned on the network/);
});
