'use strict';

const hub = require('../hub');

// De-whitelist someone (network-wide, via the hub). Used on unlink, member leave, or the
// member-role being removed. Also drops their Discord link for free -- it's just two columns
// on the same whitelist row now, so removing the row removes the link with it.
async function revokeAccess(link) {
  if (!link) return;
  await hub.whitelistRemove(link.uuid);
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
