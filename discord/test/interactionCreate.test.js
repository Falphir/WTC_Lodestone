'use strict';

// The dispatcher's last line of defence. Whatever throws, the person who clicked has to be told
// something, and the log has to carry enough to find out what.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '2';

function loadDispatcher() {
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: { getDiscordConfig: async () => ({ brandName: 'WTC' }) },
  };
  delete require.cache[require.resolve('../src/events/interactionCreate')];
  return require('../src/events/interactionCreate');
}

/** Swallows console.error so a deliberate failure doesn't look like a broken test run. */
async function captureLogs(fn) {
  const original = console.error;
  const lines = [];
  console.error = (...args) => lines.push(args.map(String).join(' '));
  try {
    await fn();
  } finally {
    console.error = original;
  }
  return lines;
}

function failingCommand(interaction) {
  return {
    ...interaction,
    client: { commands: { get: () => ({ execute: async () => { throw new Error('kaboom'); } }) } },
    commandName: 'apply',
    user: { id: '400000000000000042' },
    isChatInputCommand: () => true,
    isAutocomplete: () => false,
    isButton: () => false,
    isAnySelectMenu: () => false,
    isModalSubmit: () => false,
  };
}

test('tells the user something useful, with a reference that is in the log', async () => {
  const dispatcher = loadDispatcher();
  const replies = [];
  const interaction = failingCommand({
    deferred: false,
    replied: false,
    reply: async (payload) => replies.push(payload),
  });

  const logs = await captureLogs(() => dispatcher.execute(interaction));

  assert.equal(replies.length, 1, 'a thrown handler must still answer the interaction');
  const ref = replies[0].content.match(/`([0-9a-f]{8})`/)?.[1];
  assert.ok(ref, `no reference in: ${replies[0].content}`);
  assert.ok(replies[0].flags, 'the apology should stay ephemeral');

  const logged = logs.join('\n');
  assert.ok(logged.includes(ref), 'the same reference must appear in the log');
  assert.ok(logged.includes('/apply'), 'the log should name what failed');
  assert.ok(logged.includes('400000000000000042'), 'and who it failed for');
});

test('follows up instead of replying when the interaction was already deferred', async () => {
  const dispatcher = loadDispatcher();
  const followUps = [];
  const interaction = failingCommand({
    deferred: true,
    replied: false,
    reply: async () => assert.fail('a deferred interaction cannot be replied to again'),
    followUp: async (payload) => followUps.push(payload),
  });

  await captureLogs(() => dispatcher.execute(interaction));

  assert.equal(followUps.length, 1);
});

test('answers a failing autocomplete with no choices rather than a message', async () => {
  const dispatcher = loadDispatcher();
  const responses = [];
  const interaction = {
    client: { commands: { get: () => ({ autocomplete: async () => { throw new Error('hub down'); } }) } },
    commandName: 'review',
    user: { id: '400000000000000042' },
    isChatInputCommand: () => false,
    isAutocomplete: () => true,
    respond: async (choices) => responses.push(choices),
    reply: async () => assert.fail('an autocomplete interaction cannot be replied to'),
  };

  await captureLogs(() => dispatcher.execute(interaction));

  assert.deepEqual(responses, [[]]);
});
