'use strict';

// The /apply form. Two things it has to get right: explain itself while it's being filled in,
// and hand the answers back when a check that needs the network rejects them.
const test = require('node:test');
const assert = require('node:assert/strict');

process.env.DISCORD_TOKEN = 'token';
process.env.CLIENT_ID = '1';
process.env.GUILD_ID = '2';

async function loadApplications() {
  const hubPath = require.resolve('../src/hub');
  require.cache[hubPath] = {
    id: hubPath,
    filename: hubPath,
    loaded: true,
    exports: { getDiscordConfig: async () => ({ brandName: 'WTC', minAge: 18 }) },
  };
  for (const mod of ['../src/config', '../src/db', '../src/lib/applications']) {
    delete require.cache[require.resolve(mod)];
  }
  const config = require('../src/config');
  await config.init();
  return require('../src/lib/applications');
}

/** The modal as Discord would receive it. */
async function renderModal(draft) {
  const applications = await loadApplications();
  let modal;
  await applications.openApplyModal({ showModal: async (m) => { modal = m.toJSON(); } }, draft);
  return modal;
}

const fieldsOf = (modal) => Object.fromEntries(modal.components.map((c) => [c.component.custom_id, c]));

test('asks the same five questions, each with a line of guidance', async () => {
  const modal = await renderModal();
  const fields = fieldsOf(modal);

  assert.deepEqual(Object.keys(fields), ['mc_username', 'age', 'referral', 'experience', 'why']);
  for (const [id, field] of Object.entries(fields)) {
    assert.ok(field.description, `${id} should carry helper text`);
  }
  // the mistake the form exists to pre-empt
  assert.match(fields.mc_username.description, /Java/);
  assert.match(fields.mc_username.description, /not your Discord name/i);
  // a hint's placeholders are filled in, never shown raw
  assert.doesNotMatch(fields.age.description, /\{\w+\}/);
});

test('will not let a one-word application be submitted at all', async () => {
  const fields = fieldsOf(await renderModal());

  // client-enforced, so these never reach the bot rather than being rejected after they do
  assert.ok(fields.experience.component.min_length >= 40);
  assert.ok(fields.why.component.min_length >= 40);
  assert.equal(fields.mc_username.component.min_length, 3, 'the shortest a Minecraft name can be');
});

test('offers referral as an optional pick rather than free text', async () => {
  const referral = fieldsOf(await renderModal()).referral.component;

  assert.equal(referral.type, 3, 'a string select');
  // Discord rejects the modal outright for required + min_values 0, and a modal component is
  // required unless it says otherwise -- so optional needs both of these, not just min_values.
  assert.equal(referral.required, false);
  assert.equal(referral.min_values, 0, 'still optional, like the text field it replaces');
  assert.ok(referral.options.length >= 5);
  assert.ok(referral.options.every((o) => o.label && o.value));
});

test('a retry re-fills every answer, the referral pick included', async () => {
  const draft = {
    mc: 'Notch',
    age: '21',
    referral: 'reddit',
    experience: 'e'.repeat(50),
    why: 'w'.repeat(50),
  };
  const fields = fieldsOf(await renderModal(draft));

  assert.equal(fields.mc_username.component.value, 'Notch');
  assert.equal(fields.age.component.value, '21');
  assert.equal(fields.experience.component.value, draft.experience);
  assert.equal(fields.why.component.value, draft.why);

  const chosen = fields.referral.component.options.filter((o) => o.default);
  assert.deepEqual(chosen.map((o) => o.value), ['reddit']);
});

test('opens an empty form when there is no draft', async () => {
  const fields = fieldsOf(await renderModal());

  for (const id of ['mc_username', 'age', 'experience', 'why']) {
    assert.equal(fields[id].component.value, undefined, `${id} should start empty`);
  }
  assert.ok(fields.referral.component.options.every((o) => !o.default));
});

test('a stale retry button sends them back to /apply instead of an empty form', async () => {
  const applications = await loadApplications();
  const replies = [];
  const interaction = {
    customId: 'app_retry',
    user: { id: '400000000000000042' },
    reply: async (payload) => replies.push(payload),
    showModal: async () => assert.fail('should not reopen the form with nothing to re-fill'),
  };

  await applications.onButton(interaction);

  assert.match(replies[0].content, /expired/i);
});

// ---- age ----
// Discord text inputs take no pattern and no numeric type, so nothing stops letters being typed
// into the age box -- this check is the only thing between a typo and a bad application.

test('accepts digits only, and tidies a leading zero rather than refusing it', async () => {
  const { parseAge } = await loadApplications();

  assert.equal(parseAge('21'), 21);
  assert.equal(parseAge('018'), 18, 'someone being tidy, not someone failing to answer');
  assert.equal(parseAge('1'), 1);
  assert.equal(parseAge('120'), 120);

  for (const bad of ['', ' ', 'eighteen', '21a', 'a21', '2 1', ' 21', '21 ', '1e3', '-5', '2.5', '0', '121', '9999', '²¹']) {
    assert.equal(parseAge(bad), null, `${JSON.stringify(bad)} is not an age`);
  }
});
