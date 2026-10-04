'use strict';

const db = require('../db');
const hub = require('../hub');

// De-whitelist someone (network-wide, via the hub) and drop their link row. Used on
// unlink, member leave, or the member-role being removed.
async function revokeAccess(link) {
  if (!link) return;
  await hub.whitelistRemove(link.uuid);
  await db.removeLink(link.discordId);
}

// Whitelists `profile` when linking/relinking. The hub keys the whitelist by UUID, so
// a rename (same uuid) is a no-op there -- only switching to a different account needs
// the old one removed first.
async function relinkWhitelist(current, profile) {
  if (current && current.uuid !== profile.uuid) {
    await hub.whitelistRemove(current.uuid);
  }
  await hub.whitelistAdd(profile.name);
}

module.exports = { revokeAccess, relinkWhitelist };
