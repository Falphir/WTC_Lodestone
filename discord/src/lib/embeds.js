'use strict';

const { EmbedBuilder } = require('discord.js');
const config = require('../config');

// Fixed status colours; the brand colour is read live from config.
const COLORS = {
  success: 0x57f287,
  danger: 0xed4245,
};

// ---- Minecraft head rendering ----
// Primary: Visage (https://visage.surgeplay.com) — 3D head renders.
//   URL: /head/<size>/<player-or-uuid>
//   Requires a real, identifying User-Agent (Visage rejects browser-spoofed ones).
// Fallback (if Visage errors or times out): the mc-heads 3D head.
const VISAGE_SIZE = 256;
const VISAGE_USER_AGENT = 'WTC-Bot/1.0 (Discord whitelist bot)';
const HEAD_TIMEOUT_MS = 4000;
const HEAD_CACHE_TTL_MS = 10 * 60 * 1000;
const DEFAULT_ID = 'MHF_Steve';

const visageUrl = (id) => `https://visage.surgeplay.com/head/${VISAGE_SIZE}/${encodeURIComponent(id)}`;
const mcHeadsUrl = (id) => `https://mc-heads.net/head/${encodeURIComponent(id)}/128`;

const headCache = new Map(); // id -> { url, at }

// Resolve the best available head image URL for a username/UUID: try the
// Visage renderer, falling back to the mc-heads head if it fails or times out.
// Cached briefly so we don't re-check on every interaction.
async function mcHead(nameOrUuid) {
  const id = nameOrUuid || DEFAULT_ID;

  const cached = headCache.get(id);
  if (cached && Date.now() - cached.at < HEAD_CACHE_TTL_MS) return cached.url;

  let url = mcHeadsUrl(id); // fallback unless Visage returns an actual image
  try {
    const res = await fetch(visageUrl(id), {
      headers: { 'User-Agent': VISAGE_USER_AGENT },
      signal: AbortSignal.timeout(HEAD_TIMEOUT_MS),
    });
    const isImage = (res.headers.get('content-type') || '').startsWith('image/');
    try {
      await res.body?.cancel(); // headers are enough; don't download the image
    } catch {
      /* ignore */
    }
    if (res.ok && isImage) url = visageUrl(id);
  } catch {
    /* network error / timeout -> keep the mc-heads fallback */
  }

  headCache.set(id, { url, at: Date.now() });
  return url;
}

// A branded base embed (brand colour + footer) reused across the bot.
function brandEmbed() {
  const brand = config.brand;
  const embed = new EmbedBuilder().setColor(brand.color).setTimestamp();
  return brand.iconUrl
    ? embed.setFooter({ text: brand.name, iconURL: brand.iconUrl })
    : embed.setFooter({ text: brand.name });
}

module.exports = { COLORS, brandEmbed, mcHead };
