'use strict';

// Discord<->Minecraft account links and in-flight applications. Used to be a local SQLite
// file (links.db); now every call goes straight through to the hub's admin API (see hub.js)
// -- the hub is the only place this state lives, so the bot has nothing local to lose.
const hub = require('./hub');

module.exports = {
  async getAll() {
    return hub.listLinks();
  },
  async getByDiscord(id) {
    return hub.getLinkByDiscord(id);
  },
  async getByUuid(uuid) {
    return hub.getLinkByUuid(uuid);
  },
  async getByName(name) {
    return hub.getLinkByName(name);
  },
  async setLink({ discordId, uuid, username }) {
    await hub.setLink(discordId, uuid, username);
  },
  async removeLink(id) {
    await hub.removeLink(id);
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
