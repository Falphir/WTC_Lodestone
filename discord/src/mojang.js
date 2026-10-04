'use strict';

// Resolves a Minecraft (Java) username to its account. The hub already has to do this for its
// own whitelist (see MojangProfiles.java), so the bot reuses that instead of hitting Mojang's
// API directly. Returns { uuid (dashed), name (canonical casing) } or null if no such account.
const hub = require('./hub');

async function lookupProfile(username) {
  return hub.mojangLookup(username);
}

module.exports = { lookupProfile };
