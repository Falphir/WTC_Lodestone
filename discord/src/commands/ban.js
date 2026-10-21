'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const hub = require('../hub');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { COLORS, brandEmbed } = require('../lib/embeds');
const { postStaffLog } = require('../lib/applications');

// The same choices the dashboard's Bans page offers. No duration is a permanent ban.
const DURATION_DAYS = { '1d': 1, '7d': 7, '30d': 30 };

// Bans go through the hub like a dashboard ban: every server applies them on its next poll and
// kicks the player if they're on. The account link and whitelist entry are left as they are.
module.exports = {
  data: new SlashCommandBuilder()
    .setName('ban')
    .setDescription(t('ban.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addStringOption((o) =>
      o.setName('mc_username').setDescription(t('ban.optionName')).setRequired(true).setMaxLength(16)
    )
    .addStringOption((o) =>
      o
        .setName('duration')
        .setDescription(t('ban.optionDuration'))
        .addChoices(
          { name: t('ban.durationPermanent'), value: 'permanent' },
          { name: t('ban.duration1d'), value: '1d' },
          { name: t('ban.duration7d'), value: '7d' },
          { name: t('ban.duration30d'), value: '30d' }
        )
    )
    .addStringOption((o) => o.setName('reason').setDescription(t('ban.optionReason')).setMaxLength(255)),

  async execute(interaction) {
    if (!isStaff(interaction.member)) {
      return interaction.reply({ content: t('common.staffOnly'), ...EPHEMERAL });
    }
    await interaction.deferReply(EPHEMERAL);

    const name = interaction.options.getString('mc_username').trim();
    const reason = interaction.options.getString('reason')?.trim() || null;
    const days = DURATION_DAYS[interaction.options.getString('duration')];
    const expiresAt = days ? new Date(Date.now() + days * 86_400_000).toISOString() : null;

    let banned;
    try {
      banned = await hub.ban(name, reason, expiresAt);
    } catch (err) {
      return interaction.editReply(t('ban.hubUnreachable', { error: err.message }));
    }
    if (!banned) return interaction.editReply(t('ban.notFound', { name }));

    const until = banned.expiresAt
      ? t('ban.until', { when: `<t:${Math.floor(new Date(banned.expiresAt).getTime() / 1000)}:R>` })
      : t('ban.permanent');
    await interaction.editReply(t('ban.success', { name: banned.name, until }));

    await postStaffLog(
      interaction.client,
      brandEmbed()
        .setColor(COLORS.danger)
        .setTitle(withIcon('denied', t('ban.logTitle')))
        .addFields(
          { name: t('ban.logFieldPlayer'), value: `\`${banned.name}\``, inline: true },
          { name: t('ban.logFieldBy'), value: `<@${interaction.user.id}>`, inline: true },
          { name: t('ban.logFieldUntil'), value: until, inline: true },
          { name: t('ban.logFieldReason'), value: banned.reason || t('ban.noReason') }
        )
    );
  },
};
