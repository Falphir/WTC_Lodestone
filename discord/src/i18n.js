'use strict';

// Tiny translation layer. All user-facing wording lives in locales/<lang>.json
// so copy can be edited (or a language swapped) without touching code.
//
//   t('apply.submitted', { brand: 'WTC' })
//
// Keys are dot-paths into the JSON. `{name}` placeholders in a string are
// replaced from the vars object. A missing key returns the key itself (so a
// typo is visible in-product rather than throwing).

const fs = require('node:fs');
const path = require('node:path');

const LANG = process.env.BOT_LANG || 'en';
const localePath = path.join(__dirname, '..', 'locales', `${LANG}.json`);

let strings;
try {
  strings = JSON.parse(fs.readFileSync(localePath, 'utf8'));
} catch (err) {
  throw new Error(`Could not load locale file ${localePath}: ${err.message}`);
}

function lookup(key) {
  return key.split('.').reduce((o, k) => (o == null ? undefined : o[k]), strings);
}

function t(key, vars) {
  const raw = lookup(key);
  if (typeof raw !== 'string') {
    console.warn(`[i18n] missing string for key: ${key}`);
    return key;
  }
  if (!vars) return raw;
  return raw.replace(/\{(\w+)\}/g, (m, name) => (name in vars ? String(vars[name]) : m));
}

module.exports = { t };
