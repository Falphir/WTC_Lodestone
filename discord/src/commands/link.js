'use strict';

const { SlashCommandBuilder } = require('discord.js');
const config = require('../config');
const db = require('../db');
const hub = require('../hub');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');

// The code is what proves the account is theirs: a server only shows it to whoever just logged in
// with that account, and online mode has Mojang vouch for them first. No username is ever typed.
module.exports = {
  data: new SlashCommandBuilder()
    .setName('link')
    .setDescription(t('link.commandDescription'))
    .addStringOption((o) =>
      o
        .setName('code')
        .setDescription(t('link.optionDescription'))
        .setRequired(true)
        .setMinLength(6)
        .setMaxLength(6)
    ),

  async execute(interaction) {
    const member = interaction.member;
    const hasMemberRole = config.memberRoleId && member.roles.cache.has(config.memberRoleId);
    // Linking whitelists the account, so it's for members only -- which is what keeps
    // "linked" and "whitelisted" the same thing.
    if (!hasMemberRole && !isStaff(member)) {
      return interaction.reply({
        content: t('link.needMember'),
        ...EPHEMERAL,
      });
    }

    await interaction.deferReply(EPHEMERAL);
    const code = interaction.options.getString('code').trim();
    // Not even a code -- answer here rather than spend one of their wrong guesses at the hub
    if (!/^\d{6}$/.test(code)) return interaction.editReply(t('link.codeInvalid', { code }));

    let previous, result;
    try {
      previous = await db.getByDiscord(interaction.user.id);
      result = await hub.claimLinkCode(code, interaction.user.id);
    } catch (err) {
      return interaction.editReply(t('link.hubUnreachable', { error: err.message }));
    }

    if (result.status === 404) return interaction.editReply(t('link.codeInvalid', { code }));
    if (result.status === 409) return interaction.editReply(t('link.alreadyLinked'));
    if (result.status === 429) return interaction.editReply(t('link.tooManyTries'));

    const { player } = result;
    await interaction.editReply(
      t('link.success', { name: player.name }) +
        (previous && previous.uuid !== player.uuid ? t('link.previousRemoved', { name: previous.username }) : '')
    );
  },
};
