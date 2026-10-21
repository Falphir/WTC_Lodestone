'use strict';

const hub = require('../hub');

// De-whitelist someone (network-wide, via the hub). Used on unlink, member leave, or the
// member-role being removed. Also drops their Discord link for free -- it's just two columns
// on the same whitelist row now, so removing the row removes the link with it.
async function revokeAccess(link) {
  if (!link) return;
  await hub.whitelistRemove(link.uuid);
}

module.exports = { revokeAccess };
