'use strict';

const { Events } = require('discord.js');
const db = require('../db');
const { revokeAccess } = require('../lib/access');

// Someone left Discord -> pull them off every whitelist and drop their link.
module.exports = {
  name: Events.GuildMemberRemove,
  async execute(member) {
    const link = await db.getByDiscord(member.id);
    if (!link) return;
    try {
      await revokeAccess(link);
      console.log(`Auto-revoked ${link.username} (member ${member.id} left).`);
    } catch (err) {
      console.error('Auto-revoke on leave failed:', err);
    }
  },
};
