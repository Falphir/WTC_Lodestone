'use strict';

require('dotenv').config();
const hub = require('./hub');

function requireEnv(name) {
  const val = process.env[name];
  if (!val) throw new Error(`Missing required environment variable: ${name} (see .env.example)`);
  return val;
}

// In-memory cache of the hub's discord_config table. init() must be awaited once at startup
// before anything below is read; every runtime edit (setConfig) writes through to the hub first
// and only then updates this cache. The hub is the only persistent store -- nothing here is ever
// written to disk. What /serverinfo shows players is NOT cached: it's a per-server listing an
// admin publishes on the dashboard, so the bot reads it live (see hub.listPublishedServers).
const REFRESH_MS = 60_000;

let cfg = {};

module.exports = {
  // Immutable, from environment.
  token: requireEnv('DISCORD_TOKEN'),
  clientId: requireEnv('CLIENT_ID'),
  guildId: requireEnv('GUILD_ID'),

  async init() {
    cfg = await hub.getDiscordConfig();
    // The /config panel writes through this cache, but the hub's config can also be edited
    // straight through its API, so re-pull periodically rather than needing a bot restart.
    // A failed refresh keeps the config we already have -- stale beats empty.
    setInterval(async () => {
      try {
        cfg = await hub.getDiscordConfig();
      } catch (err) {
        console.error('Config refresh failed, keeping the cached one:', err.message);
      }
    }, REFRESH_MS).unref();
  },

  // Live getters so runtime edits are reflected everywhere without a restart.
  get applicationsChannelId() {
    return cfg.applicationsChannelId;
  },
  get welcomeChannelId() {
    return cfg.welcomeChannelId;
  },
  get serverInfoChannelId() {
    return cfg.serverInfoChannelId;
  },
  get staffLogChannelId() {
    return cfg.staffLogChannelId || null;
  },
  get memberRoleId() {
    return cfg.memberRoleId;
  },
  get staffRoleIds() {
    return cfg.staffRoleIds || [];
  },
  // Mention string that pings every configured staff role (or '' if none).
  get staffMention() {
    return (cfg.staffRoleIds || []).map((id) => `<@&${id}>`).join(' ');
  },
  get minAge() {
    return Number.isInteger(cfg.minAge) ? cfg.minAge : 18;
  },
  // Days a denied applicant must wait before re-applying (0 = no cooldown).
  get applyCooldownDays() {
    return Number.isInteger(cfg.applyCooldownDays) ? cfg.applyCooldownDays : 7;
  },
  get brand() {
    return {
      name: cfg.brandName || 'The Community',
      color: cfg.brandColor ? parseInt(String(cfg.brandColor).replace(/^#/, ''), 16) : 0x5865f2,
      iconUrl: cfg.brandIconUrl || null,
    };
  },

  // ---- runtime mutation (writes through to the hub, then refreshes the cache) ----
  async setConfig(patch) {
    cfg = await hub.updateDiscordConfig(patch);
  },
};
