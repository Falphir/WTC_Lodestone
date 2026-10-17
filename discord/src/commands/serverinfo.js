'use strict';

const { EmbedBuilder, SlashCommandBuilder } = require('discord.js');
const config = require('../config');
const hub = require('../hub');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { COLORS, brandEmbed } = require('../lib/embeds');

// Discord takes at most 10 embeds per message, and the header is one of them -- so each server
// gets its own card (and so its own modpack thumbnail) until there's no room left.
const MAX_SERVER_CARDS = 9;

// Enough names to see who's about; past that the player count is the figure that matters.
const MAX_ONLINE_NAMES = 10;

// How each status the hub derives (ServerService.status) reads on a card. NEVER and DISABLED
// are absent on purpose: both mean the hub has never heard from the server, which tells a
// player nothing about whether it's up -- so those cards just show no status at all.
const STATUS = {
  ONLINE: { icon: 'online', label: 'serverinfo.statusOnline', color: COLORS.success },
  LAGGING: { icon: 'lagging', label: 'serverinfo.statusLagging', color: COLORS.warning },
  OFFLINE: { icon: 'offline', label: 'serverinfo.statusOffline', color: COLORS.danger },
};

// Status, player count and who's on -- all of it already in the hub's server row, so the card
// costs no extra call. An empty `online` means "not known" (an older mod, or the first minute
// after a hub restart), never "nobody is playing", so it renders nothing rather than an empty
// state; the player count stays the figure that says how many.
function liveLines(server) {
  const status = STATUS[server.status];
  if (!status) return [];

  const lines = [t('serverinfo.lineStatus', { status: withIcon(status.icon, t(status.label)) })];
  const up = server.status !== 'OFFLINE';
  if (up && server.latest) {
    lines.push(
      t('serverinfo.linePlayers', { count: server.latest.playerCount, max: server.latest.maxPlayers })
    );
  }
  if (up && server.online?.length) {
    const names = server.online.slice(0, MAX_ONLINE_NAMES).map((p) => p.name).join(', ');
    const rest = server.online.length - MAX_ONLINE_NAMES;
    lines.push(
      t('serverinfo.lineOnline', { names: rest > 0 ? `${names} ${t('serverinfo.onlineMore', { count: rest })}` : names })
    );
  }
  return lines;
}

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
  const live = liveLines(server);
  const body = [live.join('\n'), details(server.listing)].filter(Boolean).join('\n');
  const card = new EmbedBuilder()
    .setColor(STATUS[server.status]?.color ?? config.brand.color)
    .setTitle(server.name)
    .setDescription(body || t('serverinfo.noDetails'));
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
