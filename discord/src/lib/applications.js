'use strict';

// The whole application lifecycle: the /apply modal, the public application post, the staff
// review card /review hands out, and the Approve / Deny actions behind its buttons.
const {
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  LabelBuilder,
  MessageFlags,
  ModalBuilder,
  StringSelectMenuBuilder,
  TextInputBuilder,
  TextInputStyle,
} = require('discord.js');

const config = require('../config');
const db = require('../db');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { COLORS, brandEmbed, mcHead } = require('./embeds');
const { EPHEMERAL, isStaff } = require('./util');

// Enough to stop a one-word answer, not so much that a terse but genuine one is blocked.
const MIN_ESSAY = 40;

// Minecraft Java usernames: 3-16 of these characters, nothing else.
const MC_NAME = /^[A-Za-z0-9_]{3,16}$/;

// Age: digits only. Discord text inputs take no pattern and no numeric type -- setMaxLength is
// the only constraint the client will enforce -- so this is the check that actually decides.
const AGE = /^\d{1,3}$/;
const MAX_AGE = 120;

/**
 * The age as a number, or null if what they typed wasn't one. A leading zero is normalised
 * rather than rejected: "018" is someone being tidy, not someone failing to answer.
 * Exported for its own test -- the edge cases here are the whole point of it.
 */
function parseAge(raw) {
  if (!AGE.test(raw)) return null;
  const age = Number.parseInt(raw, 10);
  return age >= 1 && age <= MAX_AGE ? age : null;
}

// The referral options, in the order they're offered. Each needs an `apply.referral_<key>` string.
const REFERRALS = ['friend', 'youtube', 'reddit', 'listing', 'discord', 'other'];

/**
 * Half-filled applications, so a rejection hands the answers back instead of binning them.
 *
 * This is the one piece of state the bot keeps locally, against the rule that it keeps none
 * (see CLAUDE.md) -- deliberately: a draft is worth exactly one retype, which is what losing it
 * costs today anyway, and nothing durable moves off the hub. The TTL matches the life of the
 * interaction token behind the retry button, so the draft dies no sooner than the button does.
 */
const DRAFT_TTL_MS = 15 * 60 * 1000;
const drafts = new Map();

function saveDraft(userId, data) {
  const cutoff = Date.now() - DRAFT_TTL_MS;
  for (const [id, draft] of drafts) if (draft.at < cutoff) drafts.delete(id); // prune as we go
  drafts.set(userId, { at: Date.now(), data });
}

function takeDraft(userId) {
  const draft = drafts.get(userId);
  drafts.delete(userId);
  return draft && Date.now() - draft.at < DRAFT_TTL_MS ? draft.data : null;
}

/** A rejection the applicant can act on: the reason, and a button that reopens the filled form. */
function rejectWithRetry(interaction, content, data) {
  saveDraft(interaction.user.id, data);
  return interaction.editReply({
    content,
    components: [
      new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId('app_retry').setLabel(t('apply.btnRetry')).setStyle(ButtonStyle.Primary)
      ),
    ],
  });
}

// Post an embed to the private staff audit-log channel, if one is configured.
// Never pings anyone mentioned in the embed.
async function postStaffLog(client, embed) {
  if (!config.staffLogChannelId) return;
  try {
    const ch = await client.channels.fetch(config.staffLogChannelId);
    await ch.send({ embeds: [embed], allowedMentions: { parse: [] } });
  } catch (err) {
    console.error('Staff-log post failed:', err);
  }
}

// Why a reviewed application can't be reviewed again. The hub refuses a second decision, so the
// loser of a double-click (or of a race with the dashboard) learns what happened instead.
function reviewedAlready(app) {
  const when = app.decidedAt ? `<t:${Math.floor(app.decidedAt / 1000)}:R>` : '';
  return t('apply.alreadyReviewed', { when });
}

// Replace the staff member's ephemeral review card with the outcome, so the buttons go with it.
const finish = (interaction, content) => interaction.editReply({ content, embeds: [], components: [] });

/**
 * Acknowledge a decision so `finish` lands in the right place. Normally the button is on the
 * staff member's own ephemeral review card and the outcome replaces it. An application posted
 * before /review existed still has buttons on the *public* post, though, and replacing that
 * would wipe the applicant's answers -- so those answer with a separate ephemeral reply and
 * leave the post to stampPost.
 */
async function acknowledge(interaction) {
  if (interaction.message?.flags?.has(MessageFlags.Ephemeral)) await interaction.deferUpdate();
  else await interaction.deferReply(EPHEMERAL);
}

/**
 * The staff-side card for one application: who, when, and a link to the public post that holds
 * their written answers, Minecraft name included. (The hub keeps the answers too, for the
 * dashboard, but not for applications posted before it did.)
 */
async function reviewCard(app, guildId) {
  if (app.status !== 'pending') return { content: reviewedAlready(app), embeds: [], components: [] };

  const url =
    app.messageId && config.applicationsChannelId
      ? t('review.linkPost', {
          url: `https://discord.com/channels/${guildId}/${config.applicationsChannelId}/${app.messageId}`,
        })
      : t('review.postMissing');

  const embed = brandEmbed()
    .setTitle(withIcon('review', t('review.embedTitle')))
    .addFields(
      { name: t('review.fieldApplicant'), value: `<@${app.discordId}>`, inline: true },
      { name: t('review.fieldApplied'), value: `<t:${Math.floor(app.createdAt / 1000)}:R>`, inline: true },
      { name: t('review.fieldPost'), value: url }
    );

  const buttons = new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId(`app_approve:${app.discordId}`)
      .setLabel(t('review.btnApprove'))
      .setStyle(ButtonStyle.Success),
    new ButtonBuilder()
      .setCustomId(`app_deny:${app.discordId}`)
      .setLabel(t('review.btnDeny'))
      .setStyle(ButtonStyle.Danger)
  );

  return { embeds: [embed], components: [buttons] };
}

/**
 * Mark the public post with the decision. It carries no buttons to disable -- the colour and a
 * Status field are the whole signal -- and a deleted post is not an error worth surfacing.
 */
async function stampPost(client, messageId, color, status) {
  if (!messageId) return;
  try {
    const ch = await client.channels.fetch(config.applicationsChannelId);
    const msg = await ch.messages.fetch(messageId);
    const stamped = EmbedBuilder.from(msg.embeds[0])
      .setColor(color)
      .addFields({ name: t('apply.statusField'), value: status });
    // components: [] also strips the buttons from posts published before /review existed.
    await msg.edit({ embeds: [stamped], components: [] });
  } catch (err) {
    console.error('Could not stamp the application post:', err.message);
  }
}

async function buildApplicationEmbed(user, data) {
  return new EmbedBuilder()
    .setColor(config.brand.color)
    .setAuthor({ name: t('apply.embedAuthor', { tag: user.tag }), iconURL: user.displayAvatarURL() })
    .setThumbnail(await mcHead(data.mc))
    .addFields(
      { name: t('apply.fieldApplicant'), value: `<@${user.id}>`, inline: true },
      { name: t('apply.fieldMinecraft'), value: data.mc, inline: true },
      { name: t('apply.fieldAge'), value: data.age, inline: true },
      // data.referral is the option key the select returned, not something to show as-is
      { name: t('apply.fieldReferral'), value: data.referral ? t(`apply.referral_${data.referral}`) : t('apply.referralEmpty'), inline: true },
      { name: t('apply.fieldExperience'), value: data.experience.slice(0, 1024) },
      { name: t('apply.fieldWhy'), value: data.why.slice(0, 1024) }
    )
    .setFooter({ text: t('apply.embedFooter', { brand: config.brand.name }) })
    .setTimestamp();
}

// A Label wraps one input and carries the helper line under its title -- the place to pre-empt
// the mistakes the form can only otherwise catch after it has thrown the answers away.
function labelled(key, input) {
  return new LabelBuilder()
    .setLabel(t(`apply.${key}Label`))
    .setDescription(t(`apply.${key}Hint`, { minAge: config.minAge }))
    .setTextInputComponent(input);
}

function textInput(id, { style = TextInputStyle.Short, max, min, placeholder, value } = {}) {
  const input = new TextInputBuilder().setCustomId(id).setStyle(style).setRequired(true).setMaxLength(max);
  // Discord enforces minLength in the client, so a one-word essay stops being submittable at
  // all rather than arriving for staff to deal with.
  if (min) input.setMinLength(min);
  if (placeholder) input.setPlaceholder(placeholder);
  if (value) input.setValue(value);
  return input;
}

/** Where people say they heard about us -- fixed options so staff get an answer, not prose. */
function referralSelect(value) {
  const select = new StringSelectMenuBuilder()
    .setCustomId('referral')
    // Optional, same as the free-text field it replaces. Both halves are needed: a modal
    // component is required by default, and Discord rejects required + minValues(0) outright.
    .setRequired(false)
    .setMinValues(0)
    .setMaxValues(1)
    .addOptions(
      REFERRALS.map((key) => ({
        label: t(`apply.referral_${key}`),
        value: key,
        default: value === key,
      }))
    );
  return new LabelBuilder()
    .setLabel(t('apply.referralLabel'))
    .setDescription(t('apply.referralHint'))
    .setStringSelectMenuComponent(select);
}

/**
 * The referral option, or '' when they skipped it. An optional component that nobody touched may
 * simply be absent from the submission, and a lookup that throws here would cost the applicant
 * the whole form -- not worth risking to save a try/catch.
 */
function pickedReferral(interaction) {
  try {
    return interaction.fields.getStringSelectValues('referral')[0] || '';
  } catch {
    return '';
  }
}

/** `draft` re-fills the form after a rejection, so nothing anyone typed is thrown away. */
async function openApplyModal(interaction, draft = {}) {
  const modal = new ModalBuilder()
    .setCustomId('apply_modal')
    .setTitle(t('apply.modalTitle', { brand: config.brand.name }).slice(0, 45));

  modal.addLabelComponents(
    labelled('mc', textInput('mc_username', { max: 16, min: 3, value: draft.mc })),
    labelled('age', textInput('age', { max: 3, placeholder: t('apply.agePlaceholder'), value: draft.age })),
    referralSelect(draft.referral),
    labelled(
      'experience',
      textInput('experience', {
        style: TextInputStyle.Paragraph,
        max: 1024,
        min: MIN_ESSAY,
        placeholder: t('apply.experiencePlaceholder'),
        value: draft.experience,
      })
    ),
    labelled(
      'why',
      textInput('why', {
        style: TextInputStyle.Paragraph,
        max: 1024,
        min: MIN_ESSAY,
        placeholder: t('apply.whyPlaceholder'),
        value: draft.why,
      })
    )
  );
  await interaction.showModal(modal);
}

async function submitApplication(interaction) {
  // Defer first: building the embed may fetch the skin renderer, which can take
  // longer than Discord's 3s reply window.
  await interaction.deferReply(EPHEMERAL);

  const data = {
    mc: interaction.fields.getTextInputValue('mc_username').trim(),
    age: interaction.fields.getTextInputValue('age').trim(),
    referral: pickedReferral(interaction),
    experience: interaction.fields.getTextInputValue('experience').trim(),
    why: interaction.fields.getTextInputValue('why').trim(),
  };

  // Every rejection below hands the answers back with a button that reopens the filled form:
  // a modal can only be checked once it's submitted, after the applicant has written all five
  // -- losing the lot to a typo is not a fair trade.
  const ageNum = parseAge(data.age);
  if (ageNum === null) {
    return rejectWithRetry(interaction, t('apply.ageInvalid', { age: data.age }), data);
  }
  data.age = String(ageNum); // normalised, so the post shows 18 rather than 018
  if (ageNum < config.minAge) {
    // No retry here: another try at the same form cannot make them old enough.
    return interaction.editReply(t('apply.ageTooYoung', { brand: config.brand.name, minAge: config.minAge }));
  }

  // Shape first, so a name that could never exist is named as such instead of coming back as
  // "Mojang has no account" after a pointless round trip.
  if (!MC_NAME.test(data.mc)) {
    return rejectWithRetry(interaction, t('apply.mcInvalid', { name: data.mc }), data);
  }

  // The name is only shown to staff: which account gets whitelisted is decided later, when the
  // approved member links one with the code a server shows them -- so it isn't looked up here.
  const channel = await interaction.client.channels.fetch(config.applicationsChannelId);
  const embed = await buildApplicationEmbed(interaction.user, data);

  // No Approve/Deny buttons here on purpose: this channel is public so the community can see
  // who is joining, and a message's components render for everyone who can see the message --
  // Discord has no per-user visibility for them. Staff pull the controls into an ephemeral
  // reply with /review instead (see reviewCard).
  const staffPing = config.staffMention;
  const post = await channel.send({
    content: staffPing || undefined,
    ...(staffPing ? { allowedMentions: { roles: config.staffRoleIds } } : {}),
    embeds: [embed],
  });

  // The answers go to the hub too, for reviewers on the dashboard; they're deleted with the row.
  await db.setApplicationPending({
    discordId: interaction.user.id,
    messageId: post.id,
    discordName: interaction.user.tag,
    answers: data,
  });

  drafts.delete(interaction.user.id); // it went through; a stale retry button must not reopen it

  await interaction.editReply({
    content: t('apply.submitted', { brand: config.brand.name }),
  });
}

/**
 * Everything a decision does in Discord, wherever it was made: on approve the member role and the
 * public welcome, on deny the DM with the hub's stored reason; then the post stamp and the staff
 * log. `reviewerId` is the staff member on /review, or null for a dashboard decision, which has no
 * Discord account to name. Each step fails on its own; the returned warnings are for /review.
 */
async function applyDecision(client, app, reviewerId) {
  const approved = app.status === 'approved';
  const applicantId = app.discordId;
  let warnings = '';
  let avatar = null;

  if (approved) {
    // Approval is the member role and nothing else. It doesn't touch the whitelist: the member links
    // their own account with the code a server shows them on their first join, and /link
    // whitelists it then (only for members, which is what this grants).
    try {
      const guild = await client.guilds.fetch(config.guildId);
      const member = await guild.members.fetch(applicantId);
      avatar = member.displayAvatarURL();
      if (config.memberRoleId) await member.roles.add(config.memberRoleId);
    } catch (err) {
      console.error('Role assignment failed:', err);
      warnings += `\n${withIcon('warning', t('apply.roleWarn'))}`;
    }

    // Public welcome message pointing them to the server-info channel (no DMs).
    try {
      const welcome = await client.channels.fetch(config.welcomeChannelId);
      const welcomeEmbed = brandEmbed()
        .setColor(COLORS.success)
        .setTitle(withIcon('welcome', t('apply.welcomeTitle', { brand: config.brand.name })))
        .setThumbnail(avatar)
        .setDescription(t('apply.welcomeDescription', { serverInfoChannelId: config.serverInfoChannelId }));
      // Mention in content (so it pings); embed carries the polish.
      await welcome.send({ content: `<@${applicantId}>`, embeds: [welcomeEmbed] });
    } catch {
      warnings += `\n${withIcon('warning', t('apply.welcomeWarn'))}`;
    }
  } else {
    try {
      const user = await client.users.fetch(applicantId);
      const dm = brandEmbed()
        .setColor(COLORS.danger)
        .setTitle(t('apply.denyDmTitle'))
        .setDescription(
          t('apply.denyDmDescription', { brand: config.brand.name }) +
            (app.denyReason ? t('apply.denyDmReason', { reason: app.denyReason }) : '') +
            t('apply.denyDmClosing')
        );
      await user.send({ embeds: [dm] });
    } catch {
      /* user may have DMs closed */
    }
  }

  const color = approved ? COLORS.success : COLORS.danger;
  const icon = approved ? 'approved' : 'denied';
  const status = reviewerId
    ? t(approved ? 'apply.statusApproved' : 'apply.statusDenied', { staffId: reviewerId })
    : t(approved ? 'apply.statusApprovedDashboard' : 'apply.statusDeniedDashboard');
  await stampPost(client, app.messageId, color, withIcon(icon, status));

  const log = brandEmbed()
    .setColor(color)
    .setTitle(withIcon(icon, t(approved ? 'apply.logTitleApproved' : 'apply.logTitleDenied')))
    .setThumbnail(avatar)
    .addFields(
      { name: t('apply.logFieldApplicant'), value: `<@${applicantId}>`, inline: true },
      {
        name: t('apply.logFieldReviewer'),
        value: reviewerId ? `<@${reviewerId}>` : t('apply.logReviewerDashboard'),
        inline: true,
      }
    );
  if (app.denyReason) log.addFields({ name: t('apply.logFieldReason'), value: app.denyReason });
  await postStaffLog(client, log);

  return warnings;
}

/**
 * Records a decision from /review, then acts on it. The hub refuses one on an application that's
 * no longer pending, so whoever loses a race (another staff member, or the dashboard) learns what
 * happened instead of running the whole flow twice.
 */
async function decide(interaction, applicantId, status, reason) {
  const app = await db.setApplicationDecision(applicantId, status, { reason, applied: true });
  if (!app) {
    const current = await db.getApplication(applicantId);
    await finish(interaction, current ? reviewedAlready(current) : t('review.notFound', { discordId: applicantId }));
    return null;
  }
  return applyDecision(interaction.client, app, interaction.user.id);
}

async function handleApprove(interaction, applicantId) {
  if (!isStaff(interaction.member)) {
    return interaction.reply({ content: t('apply.staffOnlyReview'), ...EPHEMERAL });
  }
  await acknowledge(interaction);

  const warnings = await decide(interaction, applicantId, 'approved');
  if (warnings === null) return;
  await finish(interaction, withIcon('approved', t('apply.approveSuccess', { discordId: applicantId })) + warnings);
}

async function promptDeny(interaction, applicantId) {
  if (!isStaff(interaction.member)) {
    return interaction.reply({ content: t('apply.staffOnlyReview'), ...EPHEMERAL });
  }
  // The public post's id comes off the hub row in submitDeny, so it needn't ride in the custom id.
  const modal = new ModalBuilder()
    .setCustomId(`deny_modal:${applicantId}`)
    .setTitle(t('apply.denyModalTitle'));
  modal.addComponents(
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('reason')
        .setLabel(t('apply.denyReasonLabel'))
        .setStyle(TextInputStyle.Paragraph)
        .setRequired(false)
        .setMaxLength(500)
    )
  );
  await interaction.showModal(modal);
}

async function submitDeny(interaction, applicantId) {
  await acknowledge(interaction);
  const reason = interaction.fields.getTextInputValue('reason').trim();

  // Recorded first (it's what enforces the re-apply cooldown), so a lost race DMs nobody.
  const warnings = await decide(interaction, applicantId, 'denied', reason || undefined);
  if (warnings === null) return;
  await finish(interaction, withIcon('denied', t('apply.denied')));
}

let syncing = false;

/**
 * Acts on decisions made on the dashboard, which can't reach Discord itself: the hub keeps each
 * one flagged until this acknowledges it. Runs on a timer from events/ready. If the hub or Discord
 * is unreachable nothing is acknowledged, and the next run tries again.
 * ponytail: a crash between acting and acknowledging re-runs that one decision (a second welcome
 * post); the role and DM are harmless twice, so that's accepted rather than tracked.
 */
async function syncDashboardDecisions(client) {
  if (syncing) return; // a slow run mustn't overlap the next and act on the same decision twice
  syncing = true;
  try {
    const decisions = await db.listUnappliedDecisions();
    if (decisions.length === 0) return;
    await client.guilds.fetch(config.guildId); // Discord down: leave them all for the next run
    for (const app of decisions) {
      await applyDecision(client, app, null);
      await db.markDecisionApplied(app.discordId);
    }
  } catch (err) {
    console.error('Could not apply dashboard decisions:', err.message);
  } finally {
    syncing = false;
  }
}

// Routes application-related buttons / modals (called from events/interactionCreate).
async function onButton(interaction) {
  const [action, applicantId] = interaction.customId.split(':');
  if (action === 'app_approve') return handleApprove(interaction, applicantId);
  if (action === 'app_deny') return promptDeny(interaction, applicantId);
  if (action === 'app_retry') {
    // showModal needs an unacknowledged interaction, so this can't be deferred -- and mustn't be.
    const draft = takeDraft(interaction.user.id);
    if (!draft) return interaction.reply({ content: t('apply.draftExpired'), ...EPHEMERAL });
    return openApplyModal(interaction, draft);
  }
}

async function onModal(interaction) {
  if (interaction.customId === 'apply_modal') return submitApplication(interaction);
  if (interaction.customId.startsWith('deny_modal:')) {
    const [, applicantId] = interaction.customId.split(':');
    return submitDeny(interaction, applicantId);
  }
}

module.exports = { openApplyModal, reviewCard, parseAge, onButton, onModal, postStaffLog, syncDashboardDecisions };
