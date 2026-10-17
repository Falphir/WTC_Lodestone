'use strict';

const { Events } = require('discord.js');
const db = require('../db');
const { revokeAccess } = require('../lib/access');

// Someone left Discord -> pull them off every whitelist and drop their link.
module.exports = {
  name: Events.GuildMemberRemove,
  async execute(member) {
    // The lookup is inside the try too: an unreachable hub here is an unhandled rejection
    // otherwise, and nothing above this is holding a catch.
    try {
      const link = await db.getByDiscord(member.id);
      if (!link) return;
      await revokeAccess(link);
      console.log(`Auto-revoked ${link.username} (member ${member.id} left).`);
    } catch (err) {
      console.error('Auto-revoke on leave failed:', err);
    }
  },
};
