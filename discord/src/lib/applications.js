'use strict';

// The whole application lifecycle: the /apply modal, the public application post,
// and the staff Approve / Deny actions (button + reason modal).
const {
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
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
const { EPHEMERAL, isStaff, getField, reviewedRow } = require('./util');

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
  const buttons = new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId(`app_approve:${interaction.user.id}`)
      .setLabel(t('apply.btnApprove'))
      .setStyle(ButtonStyle.Success),
    new ButtonBuilder()
      .setCustomId(`app_deny:${interaction.user.id}`)
      .setLabel(t('apply.btnDeny'))
      .setStyle(ButtonStyle.Danger)
  );

  // Ping the staff role(s) so nobody has to watch the channel.
  const staffPing = config.staffMention;
  const post = await channel.send({
    ...(staffPing ? { content: staffPing, allowedMentions: { roles: config.staffRoleIds } } : {}),
    embeds: [embed],
    components: [buttons],
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
  await interaction.deferReply(EPHEMERAL);

  const embed = interaction.message.embeds[0];
  const mcName = getField(embed, t('apply.fieldMinecraft'));
  const profile = await mojang.lookupProfile(mcName);
  if (!profile) {
    return interaction.editReply(t('apply.mojangNotFound', { name: mcName }));
  }

  const existing = await db.getByUuid(profile.uuid);
  if (existing && existing.discordId !== applicantId) {
    return interaction.editReply(t('apply.alreadyLinked', { discordId: existing.discordId }));
  }

  try {
    await hub.whitelistAdd(profile.name);
  } catch (err) {
    return interaction.editReply(t('apply.hubUnreachable', { error: err.message }));
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

  // Stamp the application post as approved and disable the buttons.
  const stamped = EmbedBuilder.from(embed)
    .setColor(COLORS.success)
    .addFields({
      name: t('apply.statusField'),
      value: withIcon('approved', t('apply.statusApproved', { staffId: interaction.user.id })),
    });
  await interaction.message.edit({
    embeds: [stamped],
    components: [reviewedRow(withIcon('approved', t('apply.reviewedApproved')), ButtonStyle.Success)],
  });

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

  await interaction.editReply(
    withIcon('approved', t('apply.approveSuccess', { name: profile.name })) + roleNote
  );
}

async function promptDeny(interaction, applicantId) {
  if (!isStaff(interaction.member)) {
    return interaction.reply({ content: t('apply.staffOnlyReview'), ...EPHEMERAL });
  }
  const modal = new ModalBuilder()
    .setCustomId(`deny_modal:${applicantId}:${interaction.message.id}`)
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

async function submitDeny(interaction, applicantId, messageId) {
  await interaction.deferReply(EPHEMERAL);
  const reason = interaction.fields.getTextInputValue('reason').trim();

  try {
    const ch = await interaction.client.channels.fetch(config.applicationsChannelId);
    const msg = await ch.messages.fetch(messageId);
    const stamped = EmbedBuilder.from(msg.embeds[0])
      .setColor(COLORS.danger)
      .addFields({
        name: t('apply.statusField'),
        value: withIcon('denied', t('apply.statusDenied', { staffId: interaction.user.id })),
      });
    await msg.edit({
      embeds: [stamped],
      components: [reviewedRow(withIcon('denied', t('apply.reviewedDenied')), ButtonStyle.Danger)],
    });
  } catch {
    /* message may have been deleted */
  }

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

  await interaction.editReply(withIcon('denied', t('apply.denied')));
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
    const [, applicantId, messageId] = interaction.customId.split(':');
    return submitDeny(interaction, applicantId, messageId);
  }
}

module.exports = { openApplyModal, onButton, onModal };
