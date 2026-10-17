'use strict';

const { Events } = require('discord.js');
const config = require('../config');

module.exports = {
  name: Events.ClientReady,
  once: true,
  async execute(client) {
    console.log(`Logged in as ${client.user.tag}.`);

    // Pull the member list in once so lookups that need a display name -- /review's autocomplete
    // especially -- read the cache instead of waiting on the gateway. Discord only ships members
    // with GUILD_CREATE for small guilds, so without this the first suggestion of every session
    // pays a fetch. Gateway events keep the cache current afterwards. Non-fatal: a failure just
    // means those lookups fetch on demand, the way they did before.
    try {
      const guild = await client.guilds.fetch(config.guildId);
      const members = await guild.members.fetch();
      console.log(`Cached ${members.size} guild members.`);
    } catch (err) {
      console.error('Could not warm the member cache:', err.message);
    }
  },
};
