'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const db = require('../db');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('resetcooldown')
    .setDescription(t('resetcooldown.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addUserOption((o) =>
      o.setName('user').setDescription(t('resetcooldown.optionDescription')).setRequired(true)
    ),

  async execute(interaction) {
    if (!isStaff(interaction.member)) {
      return interaction.reply({ content: t('common.staffOnly'), ...EPHEMERAL });
    }

    const user = interaction.options.getUser('user');
    const app = await db.getApplication(user.id);

    // A pending application isn't a cooldown — leave it for staff to decide.
    if (app && app.status === 'pending') {
      return interaction.reply({ content: t('resetcooldown.pending', { discordId: user.id }), ...EPHEMERAL });
    }
    // Only a prior denial imposes a cooldown.
    if (!app || app.status !== 'denied') {
      return interaction.reply({ content: t('resetcooldown.noCooldown', { discordId: user.id }), ...EPHEMERAL });
    }

    await db.clearApplication(user.id);
    return interaction.reply({ content: t('resetcooldown.success', { discordId: user.id }), ...EPHEMERAL });
  },
};
