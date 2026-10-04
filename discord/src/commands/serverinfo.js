'use strict';

const { SlashCommandBuilder } = require('discord.js');
const config = require('../config');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { brandEmbed } = require('../lib/embeds');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('serverinfo')
    .setDescription(t('serverinfo.commandDescription')),

  async execute(interaction) {
    const embed = brandEmbed()
      .setTitle(withIcon('servers', t('serverinfo.embedTitle', { brand: config.brand.name })))
      .setDescription(t('serverinfo.embedDescription'));
    if (config.brand.iconUrl) embed.setThumbnail(config.brand.iconUrl);

    for (const s of config.servers) {
      embed.addFields({
        name: t('serverinfo.serverName', { name: s.name }),
        value:
          t('serverinfo.serverValue', {
            address: s.publicAddress,
            modpack: s.modpack,
            version: s.version,
          }) + (s.installUrl ? t('serverinfo.installLine', { url: s.installUrl }) : ''),
      });
    }
    await interaction.reply({ embeds: [embed], ...EPHEMERAL });
  },
};
