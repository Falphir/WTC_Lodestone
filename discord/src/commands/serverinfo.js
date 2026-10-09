'use strict';

const { EmbedBuilder, SlashCommandBuilder } = require('discord.js');
const config = require('../config');
const hub = require('../hub');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { brandEmbed } = require('../lib/embeds');

// Discord takes at most 10 embeds per message, and the header is one of them -- so each server
// gets its own card (and so its own modpack thumbnail) until there's no room left.
const MAX_SERVER_CARDS = 9;

// One "> **Label:** value" line per detail an admin actually filled in, so a half-filled
// listing reads as a shorter card rather than one with blanks in it.
function details(listing) {
  const lines = [
    listing.publicAddress && t('serverinfo.lineAddress', { address: listing.publicAddress }),
    listing.modpack && t('serverinfo.lineModpack', { modpack: listing.modpack }),
    listing.modpackVersion && t('serverinfo.lineVersion', { version: listing.modpackVersion }),
    listing.launcher && t('serverinfo.lineLauncher', { launcher: listing.launcher }),
    listing.modpackUrl && t('serverinfo.lineModpackUrl', { url: listing.modpackUrl }),
  ];
  return lines.filter(Boolean).join('\n');
}

function serverCard(server) {
  const card = new EmbedBuilder()
    .setColor(config.brand.color)
    .setTitle(server.name)
    .setDescription(details(server.listing) || t('serverinfo.noDetails'));
  return server.listing.iconUrl ? card.setThumbnail(server.listing.iconUrl) : card;
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName('serverinfo')
    .setDescription(t('serverinfo.commandDescription')),

  async execute(interaction) {
    await interaction.deferReply(EPHEMERAL); // the hub round-trip can outlast Discord's 3s window

    const servers = await hub.listPublishedServers();
    const shown = servers.slice(0, MAX_SERVER_CARDS);
    const header = brandEmbed().setTitle(withIcon('servers', t('serverinfo.embedTitle', { brand: config.brand.name })));

    if (!servers.length) header.setDescription(t('serverinfo.empty'));
    else if (shown.length < servers.length) {
      header.setDescription(`${t('serverinfo.embedDescription')}\n${t('serverinfo.truncated', { shown: shown.length })}`);
    } else header.setDescription(t('serverinfo.embedDescription'));

    if (config.brand.iconUrl) header.setThumbnail(config.brand.iconUrl);

    await interaction.editReply({ embeds: [header, ...shown.map(serverCard)] });
  },
};
