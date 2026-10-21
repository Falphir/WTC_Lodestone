'use strict';

// Staff-only review controls for an application. The application post itself is public -- the
// community is meant to see who is joining -- so the Approve/Deny buttons can't live on it:
// a message's components render for everyone who can see the message, and Discord has no
// per-user visibility for them. An ephemeral reply is the one place components ARE per-user,
// so staff pull the controls to themselves here instead.
const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const config = require('../config');
const db = require('../db');
const { EPHEMERAL, isStaff } = require('../lib/util');
const { t } = require('../i18n');
const { reviewCard } = require('../lib/applications');

// Discord takes at most 25 autocomplete choices, and at most 100 user ids in one member fetch.
const MAX_CHOICES = 25;
const RESOLVE_LIMIT = 100;

// Long enough to cover one person typing, short enough that a decision clears from the list
// while they're still looking at it.
const CACHE_MS = 15_000;
let cache = null;
const SNOWFLAKE = /^\d{17,20}$/;

module.exports = {
  data: new SlashCommandBuilder()
    .setName('review')
    .setDescription(t('review.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addStringOption((o) =>
      o
        .setName('application')
        .setDescription(t('review.optionDescription'))
        .setRequired(true)
        .setAutocomplete(true)
    ),

  /**
   * The pending applications, as choices. Staff pick an applicant rather than naming one, which
   * also means the list only ever offers applications that are actually reviewable -- and still
   * finds someone who has since left the server, where a member picker would not.
   */
  async autocomplete(interaction) {
    if (!isStaff(interaction.member)) return interaction.respond([]);

    const all = await choiceList(interaction.guild);
    const typed = interaction.options.getFocused().toLowerCase();
    const choices = all
      .filter((c) => !typed || c.name.toLowerCase().includes(typed))
      .slice(0, MAX_CHOICES);
    return interaction.respond(choices);
  },

  async execute(interaction) {
    if (!isStaff(interaction.member)) {
      return interaction.reply({ content: t('common.staffOnly'), ...EPHEMERAL });
    }
    await interaction.deferReply(EPHEMERAL);

    // A choice carries the applicant's Discord id; anything else is text they typed themselves.
    const picked = interaction.options.getString('application');
    if (!SNOWFLAKE.test(picked)) return interaction.editReply(t('review.pickFromList'));

    const app = await db.getApplication(picked);
    if (!app) return interaction.editReply(t('review.notFound', { discordId: picked }));

    await interaction.editReply(await reviewCard(app, config.guildId));
  },
};

/**
 * Every pending application as a ready-made choice, rebuilt at most once every CACHE_MS.
 *
 * Discord sends an autocomplete interaction per keystroke and gives the answer about 3s, so
 * doing a hub round trip plus a member fetch on each one is what makes the list feel slow to
 * fill. The applicant list doesn't change while someone types, so build it once and let the
 * keystrokes filter an array in memory. A decision made inside the window can leave a stale
 * entry on offer for a few seconds; picking it is harmless, since reviewCard re-reads the row
 * and answers "already reviewed".
 */
async function choiceList(guild) {
  if (cache && Date.now() - cache.at < CACHE_MS) return cache.choices;

  const pending = await db.listApplications('pending');
  // Resolve names before filtering, so typing a name finds someone who would have fallen
  // outside the 25 shown. ponytail: beyond RESOLVE_LIMIT applicants the tail is listed by
  // Discord id only; page the fetch if a queue ever gets that long.
  const names = await discordNames(guild, pending.slice(0, RESOLVE_LIMIT));

  // A choice label is plain text: a <@id> mention would show as raw markup, so the Discord name
  // has to be resolved here. Someone who has left the guild has no name to resolve -- they're
  // listed by their id rather than dropping out of the list.
  const choices = pending.map((a) => ({
    name: (names.get(a.discordId) ?? a.discordId).slice(0, 100),
    value: a.discordId,
  }));
  cache = { at: Date.now(), choices };
  return choices;
}

/**
 * Display names for the applicants, from the member cache, with one bulk fetch for whoever is
 * missing from it. Autocomplete runs on a 3s clock, so this stays at most one round trip --
 * never one per applicant -- and anyone still unresolved (they left) is simply left out.
 */
async function discordNames(guild, applications) {
  const names = new Map();
  if (!guild) return names;

  const missing = [];
  for (const { discordId } of applications) {
    const cached = guild.members.cache.get(discordId);
    if (cached) names.set(discordId, cached.displayName);
    else missing.push(discordId);
  }

  if (missing.length) {
    try {
      const fetched = await guild.members.fetch({ user: missing });
      for (const [id, member] of fetched) names.set(id, member.displayName);
    } catch (err) {
      console.error('Could not resolve applicant display names:', err.message);
    }
  }
  return names;
}
