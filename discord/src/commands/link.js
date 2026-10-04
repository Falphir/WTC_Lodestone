'use strict';

const { SlashCommandBuilder } = require('discord.js');
const config = require('../config');
const db = require('../db');
const mojang = require('../mojang');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');
const { relinkWhitelist } = require('../lib/access');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('link')
    .setDescription(t('link.commandDescription'))
    .addStringOption((o) =>
      o
        .setName('mc_username')
        .setDescription(t('link.optionDescription'))
        .setRequired(true)
        .setMaxLength(16)
    ),

  async execute(interaction) {
    const member = interaction.member;
    const hasMemberRole = config.memberRoleId && member.roles.cache.has(config.memberRoleId);
    if (!hasMemberRole && !isStaff(member)) {
      return interaction.reply({
        content: t('link.needMember'),
        ...EPHEMERAL,
      });
    }

    await interaction.deferReply(EPHEMERAL);
    const name = interaction.options.getString('mc_username').trim();

    const profile = await mojang.lookupProfile(name);
    if (!profile) return interaction.editReply(t('link.mojangNotFound', { name }));

    const byUuid = await db.getByUuid(profile.uuid);
    if (byUuid && byUuid.discordId !== interaction.user.id) {
      return interaction.editReply(t('link.alreadyLinked', { discordId: byUuid.discordId }));
    }

    const current = await db.getByDiscord(interaction.user.id);

    // Cleanly (re)apply the whitelist — removes any stale prior entry incl. renames.
    try {
      await relinkWhitelist(current, profile);
    } catch (err) {
      return interaction.editReply(t('link.hubUnreachable', { error: err.message }));
    }
    await db.setLink({ discordId: interaction.user.id, uuid: profile.uuid, username: profile.name });

    const verb = current ? t('link.verbUpdated') : t('link.verbLinked');
    await interaction.editReply(
      t('link.success', { verb, name: profile.name }) +
        (current && current.username.toLowerCase() !== profile.name.toLowerCase()
          ? t('link.previousRemoved', { name: current.username })
          : '')
    );
  },
};
