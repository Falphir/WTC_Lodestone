'use strict';

// Discord<->Minecraft account links and in-flight applications. Used to be a local SQLite file
// (links.db), then a separate hub table; a link is now just two columns on the hub's whitelisted
// player row (see hub.js), since a link only ever makes sense for an account that's actually
// whitelisted. This module adapts that into the {discordId, uuid, username, linkedAt} shape every
// caller here already expects, so none of them had to change across any of these moves.
const hub = require('./hub');

function asLink(player) {
  return player && player.linkedDiscordId
    ? { discordId: player.linkedDiscordId, uuid: player.uuid, username: player.name, linkedAt: player.linkedDiscordAt }
    : null;
}

module.exports = {
  async getByDiscord(id) {
    return asLink(await hub.getPlayerByDiscord(id));
  },
  async getByUuid(uuid) {
    return asLink(await hub.getPlayerByUuid(uuid));
  },
  async getByName(name) {
    return asLink(await hub.getPlayerByName(name));
  },
  async setLink({ discordId, uuid, username }) {
    await hub.linkDiscord(uuid, discordId, username);
  },

  // ---- applications ----
  async getApplication(discordId) {
    return hub.getApplication(discordId);
  },
  async setApplicationPending({ discordId, messageId, uuid, username }) {
    await hub.setApplicationPending(discordId, { messageId, uuid, username });
  },
  async setApplicationDecision(discordId, status) {
    await hub.setApplicationDecision(discordId, status);
  },
  async clearApplication(discordId) {
    await hub.clearApplication(discordId);
  },
};
