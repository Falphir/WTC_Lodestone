'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const hub = require('../hub');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { COLORS, brandEmbed } = require('../lib/embeds');
const { postStaffLog } = require('../lib/applications');

// Matched against the hub's ban list rather than looked up with Mojang: only someone actually
// banned can be unbanned, and that list already has their uuid.
module.exports = {
  data: new SlashCommandBuilder()
    .setName('unban')
    .setDescription(t('unban.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addStringOption((o) =>
      o.setName('mc_username').setDescription(t('unban.optionName')).setRequired(true).setMaxLength(16)
    ),

  async execute(interaction) {
    if (!isStaff(interaction.member)) {
      return interaction.reply({ content: t('common.staffOnly'), ...EPHEMERAL });
    }
    await interaction.deferReply(EPHEMERAL);

    const name = interaction.options.getString('mc_username').trim();
    let lifted;
    try {
      const bans = await hub.listBans();
      lifted = bans.find((b) => b.name.toLowerCase() === name.toLowerCase());
      if (!lifted) return interaction.editReply(t('unban.notBanned', { name }));
      await hub.unban(lifted.uuid);
    } catch (err) {
      return interaction.editReply(t('unban.hubUnreachable', { error: err.message }));
    }

    await interaction.editReply(t('unban.success', { name: lifted.name }));
    await postStaffLog(
      interaction.client,
      brandEmbed()
        .setColor(COLORS.success)
        .setTitle(withIcon('approved', t('unban.logTitle')))
        .addFields(
          { name: t('ban.logFieldPlayer'), value: `\`${lifted.name}\``, inline: true },
          { name: t('ban.logFieldBy'), value: `<@${interaction.user.id}>`, inline: true }
        )
    );
  },
};
