'use strict';

// The /config panel: one ephemeral, self-updating message that configures everything the bot
// reads out of the hub's discord_config table. The embed is the confirmation -- a change is
// shown by the value it renders, so nothing here replies with an "updated" message.
//
// customId grammar: cfg:<kind>[:<section|configField>]
//   cfg:open:<section>         one of the section buttons
//   cfg:channel:<field>        one channel slot
//   cfg:role:<field>           member role / staff roles
//   cfg:rules, cfg:brand       modal submissions
//   cfg:back                   back to the section buttons
//
// Only the admin who ran /config can touch any of it: the panel is ephemeral, so Discord shows
// it to nobody else. That is what gates these components -- the Administrator permission on the
// command does not carry over to the component interactions it creates.

const {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  ChannelSelectMenuBuilder,
  ChannelType,
  ModalBuilder,
  RoleSelectMenuBuilder,
} = require('discord.js');

const config = require('../config');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { EPHEMERAL, inputRow } = require('./util');
const { brandEmbed } = require('./embeds');

const MAX_STAFF_ROLES = 25; // Discord's cap on values in one select menu

// The channel slots, in panel order. `field` is both the config getter and the hub's patch key.
const CHANNELS = [
  { field: 'applicationsChannelId', placeholder: 'config.pickApplications' },
  { field: 'welcomeChannelId', placeholder: 'config.pickWelcome' },
  { field: 'serverInfoChannelId', placeholder: 'config.pickServerInfo' },
  { field: 'staffLogChannelId', placeholder: 'config.pickStaffLog' },
];

const fmtChannel = (id) => (id ? `<#${id}>` : t('config.empty'));
const fmtRole = (id) => (id ? `<@&${id}>` : t('config.empty'));
const hex = (color) => `#${color.toString(16).padStart(6, '0')}`;

// A channel/role that was deleted since it was configured can't be a select's default value,
// so the select just opens unset -- picking a replacement is then what clears the stale id.
const known = (collection, id) => Boolean(id && collection.cache.has(id));

// ---------- rendering ----------

function panelEmbed() {
  const brand = config.brand;
  return brandEmbed()
    .setTitle(withIcon('config', t('config.viewTitle')))
    .setDescription(t('config.panelHint'))
    .addFields(
      { name: t('config.viewApplications'), value: fmtChannel(config.applicationsChannelId), inline: true },
      { name: t('config.viewWelcome'), value: fmtChannel(config.welcomeChannelId), inline: true },
      { name: t('config.viewServerInfo'), value: fmtChannel(config.serverInfoChannelId), inline: true },
      { name: t('config.viewStaffLog'), value: fmtChannel(config.staffLogChannelId), inline: true },
      { name: t('config.viewMemberRole'), value: fmtRole(config.memberRoleId), inline: true },
      {
        name: t('config.viewStaffRoles'),
        value: config.staffRoleIds.map(fmtRole).join(' ') || t('config.empty'),
        inline: true,
      },
      { name: t('config.viewMinAge'), value: String(config.minAge), inline: true },
      { name: t('config.viewCooldown'), value: t('config.cooldownDays', { days: config.applyCooldownDays }), inline: true },
      {
        name: t('config.viewBrand'),
        value: `${brand.name} • \`${hex(brand.color)}\`${brand.iconUrl ? ` • ${t('config.brandIconSet')}` : ''}`,
        inline: true,
      }
    );
}

const row = (component) => new ActionRowBuilder().addComponents(component);

const backRow = () =>
  row(new ButtonBuilder().setCustomId('cfg:back').setLabel(t('config.back')).setStyle(ButtonStyle.Secondary));

const SECTIONS = [
  { id: 'channels', label: 'config.sectionChannels' },
  { id: 'roles', label: 'config.sectionRoles' },
  { id: 'rules', label: 'config.sectionRules' },
  { id: 'brand', label: 'config.sectionBrand' },
];

function menuRows() {
  const buttons = SECTIONS.map(({ id, label }) =>
    new ButtonBuilder().setCustomId(`cfg:open:${id}`).setLabel(t(label)).setStyle(ButtonStyle.Secondary)
  );
  return [new ActionRowBuilder().addComponents(...buttons)];
}

function channelRows(guild) {
  const selects = CHANNELS.map(({ field, placeholder }) => {
    const select = new ChannelSelectMenuBuilder()
      .setCustomId(`cfg:channel:${field}`)
      .setPlaceholder(t(placeholder))
      // the bot only ever posts into these, so anything it can post in is fair game
      .addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement);
    if (known(guild.channels, config[field])) select.setDefaultChannels(config[field]);
    return row(select);
  });
  return [...selects, backRow()];
}

function roleRows(guild) {
  const member = new RoleSelectMenuBuilder()
    .setCustomId('cfg:role:memberRoleId')
    .setPlaceholder(t('config.pickMemberRole'));
  if (known(guild.roles, config.memberRoleId)) member.setDefaultRoles(config.memberRoleId);

  // The whole staff list is replaced by what's picked here, so an empty pick is how it's cleared.
  const staff = new RoleSelectMenuBuilder()
    .setCustomId('cfg:role:staffRoleIds')
    .setPlaceholder(t('config.pickStaffRoles'))
    .setMinValues(0)
    .setMaxValues(MAX_STAFF_ROLES);
  const currentStaff = config.staffRoleIds.filter((id) => known(guild.roles, id));
  if (currentStaff.length) staff.setDefaultRoles(...currentStaff);

  return [row(member), row(staff), backRow()];
}

const rulesModal = () =>
  new ModalBuilder()
    .setCustomId('cfg:rules')
    .setTitle(t('config.rulesModalTitle'))
    .addComponents(
      inputRow('minAge', t('config.labelMinAge'), { value: config.minAge, max: 3 }),
      inputRow('applyCooldownDays', t('config.labelCooldown'), { value: config.applyCooldownDays, max: 3 })
    );

const brandModal = () =>
  new ModalBuilder()
    .setCustomId('cfg:brand')
    .setTitle(t('config.brandModalTitle'))
    .addComponents(
      inputRow('brandName', t('config.labelBrandName'), { value: config.brand.name, max: 100 }),
      inputRow('brandColor', t('config.labelBrandColor'), {
        value: hex(config.brand.color),
        max: 7,
        placeholder: '#5865F2',
      }),
      inputRow('brandIconUrl', t('config.labelBrandIcon'), {
        value: config.brand.iconUrl,
        max: 300,
        required: false,
        placeholder: t('config.placeholderBrandIcon'),
      })
    );

// ---------- interactions ----------

const show = (interaction, components) => interaction.update({ embeds: [panelEmbed()], components });

async function open(interaction) {
  await interaction.reply({ embeds: [panelEmbed()], components: menuRows(), ...EPHEMERAL });
}

async function onSelect(interaction) {
  const [, kind, field] = interaction.customId.split(':');

  if (kind === 'channel') {
    await config.setConfig({ [field]: interaction.values[0] });
    return show(interaction, channelRows(interaction.guild));
  }

  if (kind === 'role') {
    await config.setConfig({ [field]: field === 'staffRoleIds' ? interaction.values : interaction.values[0] });
    return show(interaction, roleRows(interaction.guild));
  }
}

function onButton(interaction) {
  const [, kind, section] = interaction.customId.split(':');
  if (kind !== 'open') return show(interaction, menuRows()); // cfg:back

  switch (section) {
    case 'channels':
      return show(interaction, channelRows(interaction.guild));
    case 'roles':
      return show(interaction, roleRows(interaction.guild));
    case 'rules':
      return interaction.showModal(rulesModal());
    case 'brand':
      return interaction.showModal(brandModal());
    default:
      return;
  }
}

async function onModal(interaction) {
  const value = (id) => interaction.fields.getTextInputValue(id).trim();

  if (interaction.customId === 'cfg:rules') {
    const minAge = Number(value('minAge'));
    const cooldown = Number(value('applyCooldownDays'));
    if (!Number.isInteger(minAge) || minAge < 1 || minAge > 120) {
      return interaction.reply({ content: t('config.invalidMinAge'), ...EPHEMERAL });
    }
    if (!Number.isInteger(cooldown) || cooldown < 0 || cooldown > 365) {
      return interaction.reply({ content: t('config.invalidCooldown'), ...EPHEMERAL });
    }
    await config.setConfig({ minAge, applyCooldownDays: cooldown });
  } else {
    const color = value('brandColor');
    if (!/^#?[0-9a-f]{6}$/i.test(color)) {
      return interaction.reply({ content: t('config.invalidColor'), ...EPHEMERAL });
    }
    // Emptying the icon field clears it: '' still counts as a change to the hub (only null is
    // "leave alone"), and the bot reads an empty icon back as "no icon".
    await config.setConfig({
      brandName: value('brandName'),
      brandColor: color,
      brandIconUrl: value('brandIconUrl'),
    });
  }

  return show(interaction, menuRows());
}

module.exports = { open, onSelect, onButton, onModal };
