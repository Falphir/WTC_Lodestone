'use strict';

// /review is the only place the Approve/Deny buttons exist: the application post is public so
// the community can see who's joining, and a message's components are visible to everyone who
// can see the message. These check the card is staff-gated and carries the controls.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '999';

let pendingList = [];
let listCalls = 0;

async function loadCommand(application) {
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: {
      getDiscordConfig: async () => ({ brandName: 'WTC', applicationsChannelId: '555', staffRoleIds: ['staff'] }),
      getApplication: async () => application,
      listApplications: async () => {
        listCalls++;
        return pendingList;
      },
    },
  };
  for (const mod of ['../src/config', '../src/db', '../src/lib/applications', '../src/commands/review']) {
    delete require.cache[require.resolve(mod)];
  }

  const config = require('../src/config');
  await config.init();
  listCalls = 0;
  return require('../src/commands/review');
}

function fakeGuild({ cached = {}, fetchable = {} } = {}) {
  return {
    members: {
      cache: { get: (id) => (cached[id] ? { displayName: cached[id] } : undefined) },
      fetch: async ({ user }) =>
        new Map(user.filter((id) => fetchable[id]).map((id) => [id, { displayName: fetchable[id] }])),
    },
  };
}
function fakeInteraction({ staff = true, picked = '400000000000000042', focused = '', guild = fakeGuild() } = {}) {
  const calls = { replies: [], edits: [], choices: [] };
  return {
    calls,
    guild,
    member: { roles: { cache: { has: (id) => staff && id === 'staff' } } },
    options: { getString: () => picked, getFocused: () => focused },
    respond: async (choices) => calls.choices.push(choices),
    reply: async (payload) => calls.replies.push(payload),
    deferReply: async () => {},
    editReply: async (payload) => calls.edits.push(payload),
  };
}

const pending = { discordId: '400000000000000042', status: 'pending', messageId: '777', createdAt: 1700000000000 };

test('refuses anyone without a staff role', async () => {
  const command = await loadCommand(pending);
  const interaction = fakeInteraction({ staff: false });

  await command.execute(interaction);

  assert.equal(interaction.calls.edits.length, 0, 'should not have looked anything up');
  assert.match(interaction.calls.replies[0].content, /Staff only/);
});

test('hands staff the application with approve and deny buttons', async () => {
  const command = await loadCommand(pending);
  const interaction = fakeInteraction();

  await command.execute(interaction);

  const [card] = interaction.calls.edits;
  const embed = card.embeds[0].toJSON();
  assert.match(embed.fields.find((f) => f.name === 'Applicant').value, /<@400000000000000042>/);
  // the written answers live on the public post, so the card links to it rather than copying it
  assert.match(embed.fields.find((f) => f.name === 'Application').value, /channels\/999\/555\/777/);

  const buttons = card.components[0].toJSON().components;
  assert.deepEqual(buttons.map((b) => b.custom_id), [
    'app_approve:400000000000000042',
    'app_deny:400000000000000042',
  ]);
});

test('offers no buttons for an application already decided', async () => {
  const command = await loadCommand({ ...pending, status: 'denied', decidedAt: 1700000600000 });
  const interaction = fakeInteraction();

  await command.execute(interaction);

  const [card] = interaction.calls.edits;
  assert.deepEqual(card.components, []);
  assert.match(card.content, /already reviewed/);
});

test('says so when the applicant has no application', async () => {
  const command = await loadCommand(null);
  const interaction = fakeInteraction();

  await command.execute(interaction);

  assert.match(interaction.calls.edits[0], /no application on file/);
});

// ---- autocomplete: staff pick an applicant instead of naming one ----

const twoPending = () => [
  { discordId: '400000000000000042', status: 'pending', createdAt: Date.now() - 3 * 86400000 },
  { discordId: '400000000000000043', status: 'pending', createdAt: Date.now() - 2 * 3600000 },
];

test('labels each suggestion with the Discord name', async () => {
  pendingList = twoPending();
  const command = await loadCommand(pending);
  const interaction = fakeInteraction({
    // one already in the member cache, one only reachable by fetching
    guild: fakeGuild({
      cached: { '400000000000000042': 'notch' },
      fetchable: { '400000000000000043': 'Jeb' },
    }),
  });

  await command.autocomplete(interaction);

  const [choices] = interaction.calls.choices;
  assert.deepEqual(choices.map((c) => c.name), ['notch', 'Jeb']);
  assert.deepEqual(
    choices.map((c) => c.value),
    ['400000000000000042', '400000000000000043'],
    'the value stays the applicant Discord id'
  );
});

test('keeps an applicant who has left the guild, under their Discord id', async () => {
  pendingList = twoPending();
  const command = await loadCommand(pending);
  const interaction = fakeInteraction({ guild: fakeGuild({ cached: { '400000000000000042': 'notch' } }) });

  await command.autocomplete(interaction);

  // no Discord name to resolve for the second one -- it must still be reviewable
  assert.deepEqual(interaction.calls.choices[0].map((c) => c.name), ['notch', '400000000000000043']);
});

test('narrows the suggestions on the Discord name', async () => {
  pendingList = twoPending();
  const command = await loadCommand(pending);
  const guild = fakeGuild({ cached: { '400000000000000042': 'notch', '400000000000000043': 'Jeb' } });

  const byPart = fakeInteraction({ focused: 'JE', guild });
  await command.autocomplete(byPart);
  assert.deepEqual(byPart.calls.choices[0].map((c) => c.value), ['400000000000000043']);

  const byDiscord = fakeInteraction({ focused: 'notc', guild });
  await command.autocomplete(byDiscord);
  assert.deepEqual(byDiscord.calls.choices[0].map((c) => c.value), ['400000000000000042']);
});

test('suggests nothing to a non-staff member', async () => {
  pendingList = [{ discordId: '400000000000000042', status: 'pending', createdAt: Date.now() }];
  const command = await loadCommand(pending);
  const interaction = fakeInteraction({ staff: false });

  await command.autocomplete(interaction);

  assert.deepEqual(interaction.calls.choices[0], []);
});

test('asks staff to use the list when they type something of their own', async () => {
  const command = await loadCommand(pending);
  const interaction = fakeInteraction({ picked: 'notch maybe?' });

  await command.execute(interaction);

  assert.match(interaction.calls.edits[0], /Pick an application from the list/);
});

test('finds a match past the 25 choices Discord allows', async () => {
  const cached = {};
  pendingList = Array.from({ length: 40 }, (_, i) => {
    const discordId = `4000000000000000${String(i).padStart(2, '0')}`;
    cached[discordId] = `member${i}`;
    return { discordId, status: 'pending', createdAt: Date.now() };
  });
  const command = await loadCommand(pending);

  const all = fakeInteraction({ guild: fakeGuild({ cached }) });
  await command.autocomplete(all);
  assert.equal(all.calls.choices[0].length, 25, 'never offers more than Discord accepts');

  // member39 sorts last and would be cut by a slice-before-filter
  const hunt = fakeInteraction({ focused: 'member39', guild: fakeGuild({ cached }) });
  await command.autocomplete(hunt);
  assert.deepEqual(hunt.calls.choices[0].map((c) => c.name), ['member39']);
});

test('builds the suggestion list once, not once per keystroke', async () => {
  pendingList = twoPending();
  const command = await loadCommand(pending);
  let fetches = 0;
  const guild = {
    members: {
      cache: { get: () => undefined },
      fetch: async ({ user }) => {
        fetches++;
        return new Map(user.map((id) => [id, { displayName: `member-${id.slice(-2)}` }]));
      },
    },
  };

  // what typing "jeb" actually sends: one interaction per character
  for (const focused of ['j', 'je', 'jeb']) {
    await command.autocomplete(fakeInteraction({ focused, guild }));
  }

  assert.equal(listCalls, 1, 'the hub should be asked once for the burst');
  assert.equal(fetches, 1, 'the gateway member fetch should happen once for the burst');
});
