'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const config = require('../config');
const db = require('../db');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');
const { revokeAccess } = require('../lib/access');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('unlink')
    .setDescription(t('unlink.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addUserOption((o) =>
      o.setName('user').setDescription(t('unlink.optionDescription')).setRequired(true)
    ),

  async execute(interaction) {
    if (!isStaff(interaction.member)) {
      return interaction.reply({ content: t('common.staffOnly'), ...EPHEMERAL });
    }
    await interaction.deferReply(EPHEMERAL);

    const user = interaction.options.getUser('user');
    const link = await db.getByDiscord(user.id);
    if (!link) return interaction.editReply(t('unlink.noLink', { discordId: user.id }));

    try {
      await revokeAccess(link);
    } catch (err) {
      return interaction.editReply(t('unlink.hubUnreachable', { error: err.message }));
    }
    try {
      const member = await interaction.guild.members.fetch(user.id);
      if (config.memberRoleId) await member.roles.remove(config.memberRoleId);
    } catch {
      /* member may have left */
    }

    await interaction.editReply(t('unlink.success', { discordId: user.id, name: link.username }));
  },
};
