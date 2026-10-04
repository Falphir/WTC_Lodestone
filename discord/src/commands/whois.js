'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const db = require('../db');
const mojang = require('../mojang');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { brandEmbed, mcHead } = require('../lib/embeds');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('whois')
    .setDescription(t('whois.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addUserOption((o) => o.setName('user').setDescription(t('whois.optionUserDescription')))
    .addStringOption((o) => o.setName('mc_username').setDescription(t('whois.optionMcDescription'))),

  async execute(interaction) {
    if (!isStaff(interaction.member)) {
      return interaction.reply({ content: t('common.staffOnly'), ...EPHEMERAL });
    }
    await interaction.deferReply(EPHEMERAL);

    const user = interaction.options.getUser('user');
    const uname = interaction.options.getString('mc_username');

    let link = null;
    if (user) {
      link = await db.getByDiscord(user.id);
    } else if (uname) {
      link = await db.getByName(uname);
      if (!link) {
        // fall back to a live Mojang lookup so renamed accounts still resolve
        const profile = await mojang.lookupProfile(uname);
        if (profile) link = await db.getByUuid(profile.uuid);
      }
    } else {
      return interaction.editReply(t('whois.needArg'));
    }

    if (!link) return interaction.editReply(t('whois.notFound'));

    const embed = brandEmbed()
      .setTitle(withIcon('link', t('whois.embedTitle')))
      .setThumbnail(await mcHead(link.uuid))
      .addFields(
        { name: t('whois.fieldDiscord'), value: `<@${link.discordId}>`, inline: true },
        { name: t('whois.fieldMinecraft'), value: `\`${link.username}\``, inline: true },
        { name: t('whois.fieldLinked'), value: `<t:${Math.floor(link.linkedAt / 1000)}:R>`, inline: true },
        { name: t('whois.fieldUuid'), value: `\`${link.uuid}\`` }
      );
    await interaction.editReply({ embeds: [embed] });
  },
};
