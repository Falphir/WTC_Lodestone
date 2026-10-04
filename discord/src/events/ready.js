'use strict';

const { Events } = require('discord.js');
const config = require('../config');

module.exports = {
  name: Events.ClientReady,
  once: true,
  execute(client) {
    console.log(`Logged in as ${client.user.tag}. Managing ${config.servers.length} server(s).`);
  },
};
