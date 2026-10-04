'use strict';

const { SlashCommandBuilder } = require('discord.js');
const config = require('../config');
const db = require('../db');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const { openApplyModal } = require('../lib/applications');

// Confirm a pending application's post still exists; if staff deleted it without
// deciding, we shouldn't lock the applicant out forever.
async function pendingPostExists(client, messageId) {
  if (!messageId) return false;
  try {
    const ch = await client.channels.fetch(config.applicationsChannelId);
    await ch.messages.fetch(messageId);
    return true;
  } catch {
    return false;
  }
}

module.exports = {
  data: new SlashCommandBuilder().setName('apply').setDescription(t('apply.commandDescription')),

  async execute(interaction) {
    const member = interaction.member;
    const alreadyMember = config.memberRoleId && member?.roles?.cache?.has(config.memberRoleId);
    const link = await db.getByDiscord(interaction.user.id);

    if (alreadyMember || link) {
      return interaction.reply({
        content: t('apply.alreadyWhitelisted'),
        ...EPHEMERAL,
      });
    }

    const app = await db.getApplication(interaction.user.id);

    // One open application at a time.
    if (app && app.status === 'pending') {
      if (await pendingPostExists(interaction.client, app.messageId)) {
        return interaction.reply({ content: t('apply.pendingExists'), ...EPHEMERAL });
      }
      await db.clearApplication(interaction.user.id); // post was removed — let them re-apply
    }

    // Re-apply cooldown after a denial.
    if (app && app.status === 'denied' && config.applyCooldownDays > 0 && app.decidedAt) {
      const until = app.decidedAt + config.applyCooldownDays * 86400000;
      if (Date.now() < until) {
        return interaction.reply({
          content: t('apply.cooldownActive', { when: `<t:${Math.floor(until / 1000)}:R>` }),
          ...EPHEMERAL,
        });
      }
    }

    return openApplyModal(interaction);
  },
};
