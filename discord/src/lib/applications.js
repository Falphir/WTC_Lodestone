'use strict';

// The whole application lifecycle: the /apply modal, the public application post, the staff
// review card /review hands out, and the Approve / Deny actions behind its buttons.
const {
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  MessageFlags,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
} = require('discord.js');

const config = require('../config');
const db = require('../db');
const hub = require('../hub');
const mojang = require('../mojang');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { COLORS, brandEmbed, mcHead } = require('./embeds');
const { EPHEMERAL, isStaff } = require('./util');

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

// Why a reviewed application can't be reviewed again. The hub row carries the decision, so the
// loser of a double-click learns what happened instead of silently running the whole flow twice.
// ponytail: this only narrows the race to one hub round-trip, which is enough for two staff
// clicking at once. A compare-and-set on `status` hub-side is the upgrade if it ever bites.
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
 * The staff-side card for one application: who, which account, when, and a link to the public
 * post that still holds their written answers -- the hub stores the applicant's identity, not
 * their essay, so the post stays the record of what they actually wrote.
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
    .setThumbnail(await mcHead(app.username || app.uuid))
    .addFields(
      { name: t('review.fieldApplicant'), value: `<@${app.discordId}>`, inline: true },
      { name: t('review.fieldMinecraft'), value: `\`${app.username}\``, inline: true },
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
      { name: t('apply.fieldReferral'), value: data.referral, inline: true },
      { name: t('apply.fieldExperience'), value: data.experience.slice(0, 1024) },
      { name: t('apply.fieldWhy'), value: data.why.slice(0, 1024) }
    )
    .setFooter({ text: t('apply.embedFooter', { brand: config.brand.name }) })
    .setTimestamp();
}

async function openApplyModal(interaction) {
  const modal = new ModalBuilder()
    .setCustomId('apply_modal')
    .setTitle(t('apply.modalTitle', { brand: config.brand.name }).slice(0, 45));
  modal.addComponents(
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('mc_username')
        .setLabel(t('apply.mcLabel'))
        .setStyle(TextInputStyle.Short)
        .setRequired(true)
        .setMaxLength(16)
    ),
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('age')
        .setLabel(t('apply.ageLabel'))
        .setStyle(TextInputStyle.Short)
        .setRequired(true)
        .setMaxLength(3)
    ),
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('referral')
        .setLabel(t('apply.referralLabel'))
        .setStyle(TextInputStyle.Short)
        .setRequired(false)
        .setMaxLength(200)
    ),
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('experience')
        .setLabel(t('apply.experienceLabel'))
        .setStyle(TextInputStyle.Paragraph)
        .setPlaceholder(t('apply.experiencePlaceholder'))
        .setRequired(true)
        .setMaxLength(1024)
    ),
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('why')
        .setLabel(t('apply.whyLabel'))
        .setStyle(TextInputStyle.Paragraph)
        .setPlaceholder(t('apply.whyPlaceholder'))
        .setRequired(true)
        .setMaxLength(1024)
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
    referral: interaction.fields.getTextInputValue('referral').trim() || t('apply.referralEmpty'),
    experience: interaction.fields.getTextInputValue('experience').trim(),
    why: interaction.fields.getTextInputValue('why').trim(),
  };

  const ageNum = Number.parseInt(data.age, 10);
  if (!Number.isInteger(ageNum) || String(ageNum) !== data.age || ageNum <= 0 || ageNum > 120) {
    return interaction.editReply(t('apply.ageInvalid', { age: data.age }));
  }
  if (ageNum < config.minAge) {
    return interaction.editReply(
      t('apply.ageTooYoung', { brand: config.brand.name, minAge: config.minAge })
    );
  }

  // Validate the Minecraft name up front so typos / already-linked accounts are
  // rejected before staff ever see the application.
  const profile = await mojang.lookupProfile(data.mc);
  if (!profile) {
    return interaction.editReply(t('apply.submitMojangNotFound', { name: data.mc }));
  }
  const existing = await db.getByUuid(profile.uuid);
  if (existing && existing.discordId !== interaction.user.id) {
    return interaction.editReply(t('apply.submitAlreadyLinked', { name: profile.name }));
  }
  data.mc = profile.name; // canonical casing for the post

  const channel = await interaction.client.channels.fetch(config.applicationsChannelId);
  const embed = await buildApplicationEmbed(interaction.user, data);

  // No Approve/Deny buttons here on purpose: this channel is public so the community can see
  // who is joining, and a message's components render for everyone who can see the message --
  // Discord has no per-user visibility for them. Staff pull the controls into an ephemeral
  // reply with /review instead (see reviewCard).
  const staffPing = config.staffMention;
  const post = await channel.send({
    content: [staffPing, t('apply.reviewHint')].filter(Boolean).join('\n'),
    ...(staffPing ? { allowedMentions: { roles: config.staffRoleIds } } : {}),
    embeds: [embed],
  });

  await db.setApplicationPending({
    discordId: interaction.user.id,
    messageId: post.id,
    uuid: profile.uuid,
    username: profile.name,
  });

  await interaction.editReply({
    content: t('apply.submitted', { brand: config.brand.name }),
  });
}

async function handleApprove(interaction, applicantId) {
  if (!isStaff(interaction.member)) {
    return interaction.reply({ content: t('apply.staffOnlyReview'), ...EPHEMERAL });
  }
  await acknowledge(interaction);

  const app = await db.getApplication(applicantId);
  if (!app) return finish(interaction, t('review.notFound', { discordId: applicantId }));
  if (app.status !== 'pending') return finish(interaction, reviewedAlready(app));

  // The hub stored the applicant's uuid/name when the post went up, so take them from there
  // rather than re-reading a rendered embed; Mojang is only the fallback for a row that
  // somehow lacks them.
  const profile = app.uuid && app.username
    ? { uuid: app.uuid, name: app.username }
    : await mojang.lookupProfile(app.username);
  if (!profile) {
    return finish(interaction, t('apply.mojangNotFound', { name: app.username }));
  }

  const existing = await db.getByUuid(profile.uuid);
  if (existing && existing.discordId !== applicantId) {
    return finish(interaction, t('apply.alreadyLinked', { discordId: existing.discordId }));
  }

  try {
    await hub.whitelistAdd(profile.name);
  } catch (err) {
    return finish(interaction, t('apply.hubUnreachable', { error: err.message }));
  }
  await db.setLink({ discordId: applicantId, uuid: profile.uuid, username: profile.name });
  await db.setApplicationDecision(applicantId, 'approved');

  // Assign the member role (unlocks the server-info channel).
  let roleNote = '';
  try {
    const member = await interaction.guild.members.fetch(applicantId);
    if (config.memberRoleId) await member.roles.add(config.memberRoleId);
  } catch (err) {
    console.error('Role assignment failed:', err);
    roleNote = `\n${withIcon('warning', t('apply.roleWarn'))}`;
  }

  // Public welcome message pointing them to the server-info channel (no DMs).
  try {
    const welcome = await interaction.client.channels.fetch(config.welcomeChannelId);
    const welcomeEmbed = brandEmbed()
      .setColor(COLORS.success)
      .setTitle(withIcon('welcome', t('apply.welcomeTitle', { brand: config.brand.name })))
      .setThumbnail(await mcHead(profile.name))
      .setDescription(
        t('apply.welcomeDescription', { serverInfoChannelId: config.serverInfoChannelId })
      );
    // Mention in content (so it pings); embed carries the polish.
    await welcome.send({ content: `<@${applicantId}>`, embeds: [welcomeEmbed] });
  } catch {
    roleNote += `\n${withIcon('warning', t('apply.welcomeWarn'))}`;
  }

  await stampPost(
    interaction.client,
    app.messageId,
    COLORS.success,
    withIcon('approved', t('apply.statusApproved', { staffId: interaction.user.id }))
  );

  const approvedLog = brandEmbed()
    .setColor(COLORS.success)
    .setTitle(withIcon('approved', t('apply.logTitleApproved')))
    .setThumbnail(await mcHead(profile.name))
    .addFields(
      { name: t('apply.logFieldApplicant'), value: `<@${applicantId}>`, inline: true },
      { name: t('apply.logFieldMinecraft'), value: `\`${profile.name}\``, inline: true },
      { name: t('apply.logFieldReviewer'), value: `<@${interaction.user.id}>`, inline: true }
    );
  await postStaffLog(interaction.client, approvedLog);

  await finish(interaction, withIcon('approved', t('apply.approveSuccess', { name: profile.name })) + roleNote);
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

  const app = await db.getApplication(applicantId);
  if (app && app.status !== 'pending') return finish(interaction, reviewedAlready(app));

  await stampPost(
    interaction.client,
    app?.messageId,
    COLORS.danger,
    withIcon('denied', t('apply.statusDenied', { staffId: interaction.user.id }))
  );

  try {
    const user = await interaction.client.users.fetch(applicantId);
    const dm = brandEmbed()
      .setColor(COLORS.danger)
      .setTitle(t('apply.denyDmTitle'))
      .setDescription(
        t('apply.denyDmDescription', { brand: config.brand.name }) +
          (reason ? t('apply.denyDmReason', { reason }) : '') +
          t('apply.denyDmClosing')
      );
    await user.send({ embeds: [dm] });
  } catch {
    /* user may have DMs closed */
  }

  // Record the denial so the re-apply cooldown can be enforced.
  await db.setApplicationDecision(applicantId, 'denied');

  const deniedLog = brandEmbed()
    .setColor(COLORS.danger)
    .setTitle(withIcon('denied', t('apply.logTitleDenied')))
    .addFields(
      { name: t('apply.logFieldApplicant'), value: `<@${applicantId}>`, inline: true },
      { name: t('apply.logFieldReviewer'), value: `<@${interaction.user.id}>`, inline: true }
    );
  if (reason) deniedLog.addFields({ name: t('apply.logFieldReason'), value: reason });
  await postStaffLog(interaction.client, deniedLog);

  await finish(interaction, withIcon('denied', t('apply.denied')));
}

// Routes application-related buttons / modals (called from events/interactionCreate).
async function onButton(interaction) {
  const [action, applicantId] = interaction.customId.split(':');
  if (action === 'app_approve') return handleApprove(interaction, applicantId);
  if (action === 'app_deny') return promptDeny(interaction, applicantId);
}

async function onModal(interaction) {
  if (interaction.customId === 'apply_modal') return submitApplication(interaction);
  if (interaction.customId.startsWith('deny_modal:')) {
    const [, applicantId] = interaction.customId.split(':');
    return submitDeny(interaction, applicantId);
  }
}

module.exports = { openApplyModal, reviewCard, onButton, onModal };
