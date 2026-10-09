'use strict';

// Drives the /config panel's handlers with fake interactions against a stubbed hub. No Discord
// connection, but it builds the real discord.js components -- so a section that outgrows the
// five-row limit, or a select wired to the wrong config field, fails here and not in the client.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '2';

const FIXTURE = {
  applicationsChannelId: '100',
  welcomeChannelId: '101',
  serverInfoChannelId: '102',
  staffLogChannelId: null,
  memberRoleId: '200',
  staffRoleIds: ['201', '202'],
  minAge: 18,
  applyCooldownDays: 7,
  brandName: 'WTC',
  brandColor: '#5865F2',
  brandIconUrl: 'https://example.com/logo.png',
};

// Stubs src/hub in the require cache before src/config can pull the real one in, so every
// setConfig lands in `patches` instead of going to the hub.
async function loadPanel() {
  const patches = [];
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: {
      getDiscordConfig: async () => ({ ...FIXTURE }),
      updateDiscordConfig: async (patch) => {
        patches.push(patch);
        return { ...FIXTURE, ...patch };
      },
    },
  };
  for (const mod of ['../src/config', '../src/lib/configPanel']) delete require.cache[require.resolve(mod)];

  const config = require('../src/config');
  await config.init();
  return { panel: require('../src/lib/configPanel'), patches };
}

// Only ids present in the guild can be a select's default value, so the fixture ids are cached.
const guild = {
  channels: { cache: new Map([['100', {}], ['101', {}], ['102', {}]]) },
  roles: { cache: new Map([['200', {}], ['201', {}], ['202', {}]]) },
};

function fakeInteraction(extra) {
  const calls = { replies: [], updates: [], modals: [] };
  return {
    guild,
    calls,
    reply: async (payload) => calls.replies.push(payload),
    update: async (payload) => calls.updates.push(payload),
    showModal: async (modal) => calls.modals.push(modal.toJSON()),
    ...extra,
  };
}

const fields = (values) => ({ getTextInputValue: (id) => values[id] });

test('every section renders within Discord\'s five-row limit', async () => {
  const { panel } = await loadPanel();

  for (const section of ['channels', 'roles']) {
    const interaction = fakeInteraction({ customId: `cfg:open:${section}` });
    await panel.onButton(interaction);

    const [payload] = interaction.calls.updates;
    assert.ok(payload.components.length <= 5, `${section} needs ${payload.components.length} rows`);
    payload.components.forEach((row) => row.toJSON()); // throws if the builder is invalid
    payload.embeds.forEach((embed) => embed.toJSON());
  }

  for (const section of ['rules', 'brand']) {
    const interaction = fakeInteraction({ customId: `cfg:open:${section}` });
    await panel.onButton(interaction);
    assert.equal(interaction.calls.modals.length, 1, `${section} should open a modal`);
  }

  // the section buttons themselves have to fit one row
  const back = fakeInteraction({ customId: 'cfg:back' });
  await panel.onButton(back);
  const [menu] = back.calls.updates;
  assert.equal(menu.components.length, 1);
  assert.ok(menu.components[0].toJSON().components.length <= 5);
});

test('each select writes the config field its customId names', async () => {
  const { panel, patches } = await loadPanel();

  await panel.onSelect(fakeInteraction({ customId: 'cfg:channel:staffLogChannelId', values: ['999'] }));
  assert.deepEqual(patches.at(-1), { staffLogChannelId: '999' });

  await panel.onSelect(fakeInteraction({ customId: 'cfg:role:memberRoleId', values: ['205'] }));
  assert.deepEqual(patches.at(-1), { memberRoleId: '205' });
});

test('staff roles are replaced wholesale, so picking none clears them', async () => {
  const { panel, patches } = await loadPanel();

  await panel.onSelect(fakeInteraction({ customId: 'cfg:role:staffRoleIds', values: [] }));

  assert.deepEqual(patches.at(-1), { staffRoleIds: [] });
});

test('application rules are validated before anything is saved', async () => {
  const { panel, patches } = await loadPanel();

  const bad = fakeInteraction({ customId: 'cfg:rules', fields: fields({ minAge: '0', applyCooldownDays: '7' }) });
  await panel.onModal(bad);
  assert.equal(patches.length, 0, 'an out-of-range age must not be saved');
  assert.equal(bad.calls.replies.length, 1, 'and should say why');

  // 0 is a real cooldown value ("off"), not a missing one
  const ok = fakeInteraction({ customId: 'cfg:rules', fields: fields({ minAge: '21', applyCooldownDays: '0' }) });
  await panel.onModal(ok);
  assert.deepEqual(patches.at(-1), { minAge: 21, applyCooldownDays: 0 });
});

test('branding rejects a bad colour and clears the icon when emptied', async () => {
  const { panel, patches } = await loadPanel();

  const bad = fakeInteraction({
    customId: 'cfg:brand',
    fields: fields({ brandName: 'WTC', brandColor: 'nope', brandIconUrl: '' }),
  });
  await panel.onModal(bad);
  assert.equal(patches.length, 0, 'a malformed colour must not be saved');

  const cleared = fakeInteraction({
    customId: 'cfg:brand',
    fields: fields({ brandName: 'WTC', brandColor: '#000000', brandIconUrl: '  ' }),
  });
  await panel.onModal(cleared);
  assert.deepEqual(patches.at(-1), { brandName: 'WTC', brandColor: '#000000', brandIconUrl: '' });
});
