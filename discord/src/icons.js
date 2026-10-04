'use strict';

// Central place for decorative glyphs, kept OUT of the translation files so the
// wording stays pure text and icons aren't duplicated across languages.
//
// To add or change an accent, set its value here; nothing else changes.
//
//   withIcon('link', t('whois.embedTitle'))   ->  "🔗 Account Link"
//   ICONS.link = ''  ->  withIcon(...)         ->  "Account Link"   (cleared)
//
// Two groups:
//  - accents: one subtle icon per embed title.
//  - status:  approved / denied / warning — deliberately kept to only these
//    three real states (not on every neutral reply), so they read as signal.

const ICONS = {
  // accents
  welcome: '👋', // approval welcome embed title
  link: '🔗', // /whois "Account Link" title
  servers: '🖥️', // /serverinfo and /server list titles
  config: '⚙️', // /config view title

  // status
  approved: '✅', // an application/link was approved
  denied: '❌', // an application was denied
  warning: '⚠️', // partial failure / heads-up (e.g. a server was unreachable)
};

// Prepend an icon (plus a space) to text, but only when that icon is set —
// so an empty icon leaves the text untouched (no stray leading space).
function withIcon(key, text) {
  const glyph = ICONS[key];
  return glyph ? `${glyph} ${text}` : text;
}

module.exports = { ICONS, withIcon };
