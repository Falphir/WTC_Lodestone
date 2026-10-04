'use strict';

require('dotenv').config();
const hub = require('./hub');

function requireEnv(name) {
  const val = process.env[name];
  if (!val) throw new Error(`Missing required environment variable: ${name} (see .env.example)`);
  return val;
}

// In-memory cache of the hub's discord_config/discord_servers tables. init() must be awaited
// once at startup before anything below is read; every runtime edit (setConfig/addServer/
// updateServer/removeServer) writes through to the hub first and only then updates this cache.
// The hub is the only persistent store -- nothing here is ever written to disk.
let cfg = {};
let servers = [];

module.exports = {
  // Immutable, from environment.
  token: requireEnv('DISCORD_TOKEN'),
  clientId: requireEnv('CLIENT_ID'),
  guildId: requireEnv('GUILD_ID'),

  async init() {
    cfg = await hub.getDiscordConfig();
    servers = await hub.listDiscordServers();
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

  get servers() {
    return servers;
  },
  serverById(id) {
    return servers.find((s) => s.id === id);
  },

  // ---- runtime mutation (each writes through to the hub, then refreshes the cache) ----
  async setConfig(patch) {
    cfg = await hub.updateDiscordConfig(patch);
  },
  async addServer(server) {
    servers.push(await hub.addDiscordServer(server));
  },
  async updateServer(id, patch) {
    const updated = await hub.updateDiscordServer(id, patch);
    const i = servers.findIndex((s) => s.id === id);
    if (i !== -1) servers[i] = updated;
    return updated;
  },
  async removeServer(id) {
    await hub.removeDiscordServer(id);
    const i = servers.findIndex((s) => s.id === id);
    if (i === -1) return false;
    servers.splice(i, 1);
    return true;
  },
};
