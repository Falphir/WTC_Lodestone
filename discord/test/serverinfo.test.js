'use strict';

// Checks /serverinfo against a stubbed hub: it should render exactly the listing details an
// admin filled in, and say something useful when nothing is published yet.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '2';

async function loadCommand(published) {
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: {
      getDiscordConfig: async () => ({ brandName: 'WTC' }),
      listPublishedServers: async () => published,
    },
  };
  for (const mod of ['../src/config', '../src/commands/serverinfo']) delete require.cache[require.resolve(mod)];

  const config = require('../src/config');
  await config.init();
  return require('../src/commands/serverinfo');
}

function fakeInteraction() {
  const calls = { replies: [] };
  return {
    calls,
    deferReply: async () => {},
    editReply: async (payload) => calls.replies.push(payload),
  };
}

const listing = (set) => ({
  published: true,
  publicAddress: '',
  modpack: '',
  modpackUrl: '',
  modpackVersion: '',
  launcher: '',
  iconUrl: '',
  ...set,
});

/** The header embed plus one card per server, as the reply carries them. */
async function render(published) {
  const command = await loadCommand(published);
  const interaction = fakeInteraction();
  await command.execute(interaction);
  const [header, ...cards] = interaction.calls.replies[0].embeds;
  return { header: header.toJSON(), cards: cards.map((c) => c.toJSON()) };
}

const server = (name, set) => ({ name, listing: listing(set) });

test('gives each server its own card, with its modpack image as the thumbnail', async () => {
  const { cards } = await render([
    server('All The Mods 10 Aeronautics', {
      publicAddress: '65.108.39.134:25575',
      modpack: 'All The Mods 10 Aeronautics',
      modpackVersion: '0.7.1',
      launcher: 'CurseForge',
      modpackUrl: 'https://example.com/atm10',
      iconUrl: 'https://example.com/atm10.png',
    }),
    server('Bare Bones', { publicAddress: 'play.example.com' }),
  ]);

  assert.equal(cards.length, 2);
  assert.equal(cards[0].title, 'All The Mods 10 Aeronautics');
  assert.equal(cards[0].thumbnail.url, 'https://example.com/atm10.png');
  for (const detail of ['65.108.39.134:25575', '0.7.1', 'CurseForge', 'example.com/atm10']) {
    assert.ok(cards[0].description.includes(detail), `missing ${detail}`);
  }
  // the second server has only an address, so it gets that one line and no blanks
  assert.equal(cards[1].description.split('\n').length, 1);
  assert.ok(cards[1].description.includes('play.example.com'));
  assert.equal(cards[1].thumbnail, undefined);
});

test('says so when nothing is published', async () => {
  const { header, cards } = await render([]);

  assert.equal(cards.length, 0);
  assert.match(header.description, /No servers are published/);
});

test('stays inside Discord\'s ten-embed limit and says it truncated', async () => {
  const many = Array.from({ length: 12 }, (_, i) => server(`Server ${i}`, { publicAddress: `s${i}.example.com` }));

  const { header, cards } = await render(many);

  assert.ok(1 + cards.length <= 10, `sent ${1 + cards.length} embeds`);
  assert.match(header.description, /Showing the first 9/);
});
