'use strict';

// /link takes only a code a game server showed on its kick screen; the hub decides whether it's good.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '999';

let claims = [];

async function loadCommand({ claim = { status: 200, player: { uuid: 'u-1', name: 'Notch' } }, current = null } = {}) {
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: {
      getDiscordConfig: async () => ({ memberRoleId: 'member', staffRoleIds: ['staff'] }),
      getPlayerByDiscord: async () => current,
      claimLinkCode: async (code, discordId) => {
        claims.push({ code, discordId });
        return claim;
      },
    },
  };
  for (const mod of ['../src/config', '../src/db', '../src/commands/link']) delete require.cache[require.resolve(mod)];
  await require('../src/config').init();
  claims = [];
  return require('../src/commands/link');
}

function fakeInteraction({ roles = ['member'], code = '042137' } = {}) {
  const calls = { replies: [], edits: [] };
  return {
    calls,
    user: { id: '400000000000000042' },
    member: { roles: { cache: { has: (id) => roles.includes(id) } } },
    options: { getString: () => code },
    reply: async (payload) => calls.replies.push(payload),
    deferReply: async () => {},
    editReply: async (payload) => calls.edits.push(payload),
  };
}

test('links a member with the code', async () => {
  const command = await loadCommand();
  const interaction = fakeInteraction();

  await command.execute(interaction);

  assert.deepEqual(claims, [{ code: '042137', discordId: '400000000000000042' }]);
  assert.match(interaction.calls.edits[0], /Linked to \*\*Notch\*\*/);
});

test('says so when the code is wrong or expired', async () => {
  const command = await loadCommand({ claim: { status: 404 } });
  const interaction = fakeInteraction();

  await command.execute(interaction);

  assert.match(interaction.calls.edits[0], /isn't a valid link code/);
});

test('mentions the account a relink replaced', async () => {
  const command = await loadCommand({
    current: { uuid: 'u-old', name: 'OldName', linkedDiscordId: '400000000000000042' },
  });
  const interaction = fakeInteraction();

  await command.execute(interaction);

  assert.match(interaction.calls.edits[0], /Previous account `OldName` was removed/);
});

test('refuses someone without the member role, without spending their code', async () => {
  const command = await loadCommand();
  const interaction = fakeInteraction({ roles: [] });

  await command.execute(interaction);

  assert.deepEqual(claims, []);
  assert.match(interaction.calls.replies[0].content, /approved member/);
});

test('turns away something that is not six digits without asking the hub', async () => {
  const command = await loadCommand();
  const interaction = fakeInteraction({ code: 'abc123' });

  await command.execute(interaction);

  assert.deepEqual(claims, []);
  assert.match(interaction.calls.edits[0], /isn't a valid link code/);
});

test('tells someone who guessed wrong too often to wait', async () => {
  const command = await loadCommand({ claim: { status: 429 } });
  const interaction = fakeInteraction();

  await command.execute(interaction);

  assert.match(interaction.calls.edits[0], /Too many wrong codes/);
});
