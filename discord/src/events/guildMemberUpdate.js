'use strict';

const { Events } = require('discord.js');
const config = require('../config');
const db = require('../db');
const { revokeAccess } = require('../lib/access');

// Member lost the approved-member role -> pull them off every whitelist and drop their link.
module.exports = {
  name: Events.GuildMemberUpdate,
  async execute(oldMember, newMember) {
    if (!config.memberRoleId) return;
    const had = oldMember.roles.cache.has(config.memberRoleId);
    const has = newMember.roles.cache.has(config.memberRoleId);
    if (!(had && !has)) return;

    const link = await db.getByDiscord(newMember.id);
    if (!link) return;
    try {
      await revokeAccess(link);
      console.log(`Auto-revoked ${link.username} (member role removed from ${newMember.id}).`);
    } catch (err) {
      console.error('Auto-revoke on role loss failed:', err);
    }
  },
};
