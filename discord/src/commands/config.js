'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const config = require('../config');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { brandEmbed } = require('../lib/embeds');

const fmtChannel = (id) => (id ? `<#${id}>` : '—');
const fmtRole = (id) => (id ? `<@&${id}>` : '—');

async function viewConfig(interaction) {
  const b = config.brand;
  const embed = brandEmbed()
    .setTitle(withIcon('config', t('config.viewTitle')))
    .addFields(
      { name: t('config.viewApplications'), value: fmtChannel(config.applicationsChannelId), inline: true },
      { name: t('config.viewWelcome'), value: fmtChannel(config.welcomeChannelId), inline: true },
      { name: t('config.viewServerInfo'), value: fmtChannel(config.serverInfoChannelId), inline: true },
      { name: t('config.viewStaffLog'), value: fmtChannel(config.staffLogChannelId), inline: true },
      { name: t('config.viewMemberRole'), value: fmtRole(config.memberRoleId), inline: true },
      { name: t('config.viewStaffRoles'), value: config.staffRoleIds.map(fmtRole).join(' ') || t('config.empty'), inline: true },
      { name: t('config.viewMinAge'), value: String(config.minAge), inline: true },
      { name: t('config.viewCooldown'), value: t('config.cooldownDays', { days: config.applyCooldownDays }), inline: true },
      {
        name: t('config.viewBrand'),
        value: `${b.name} • \`#${b.color.toString(16).padStart(6, '0')}\`${b.iconUrl ? ' • icon set' : ''}`,
      }
    );
  await interaction.reply({ embeds: [embed], ...EPHEMERAL });
}

async function setChannels(interaction) {
  const patch = {};
  const app = interaction.options.getChannel('applications');
  const wel = interaction.options.getChannel('welcome');
  const inf = interaction.options.getChannel('serverinfo');
  const log = interaction.options.getChannel('stafflog');
  if (app) patch.applicationsChannelId = app.id;
  if (wel) patch.welcomeChannelId = wel.id;
  if (inf) patch.serverInfoChannelId = inf.id;
  if (log) patch.staffLogChannelId = log.id;
  if (!Object.keys(patch).length) {
    return interaction.reply({ content: t('config.needChannel'), ...EPHEMERAL });
  }
  await config.setConfig(patch);
  await interaction.reply({ content: t('config.channelsUpdated'), ...EPHEMERAL });
}

async function setMemberRole(interaction) {
  const role = interaction.options.getRole('role');
  await config.setConfig({ memberRoleId: role.id });
  await interaction.reply({ content: t('config.memberRoleUpdated', { roleId: role.id }), ...EPHEMERAL });
}

async function setStaff(interaction) {
  const action = interaction.options.getString('action');
  const role = interaction.options.getRole('role');
  const set = new Set(config.staffRoleIds);
  if (action === 'add') set.add(role.id);
  else set.delete(role.id);
  await config.setConfig({ staffRoleIds: [...set] });
  await interaction.reply({
    content: action === 'add'
      ? t('config.staffAdded', { roleId: role.id })
      : t('config.staffRemoved', { roleId: role.id }),
    ...EPHEMERAL,
  });
}

async function setMinAge(interaction) {
  const age = interaction.options.getInteger('age');
  await config.setConfig({ minAge: age });
  await interaction.reply({ content: t('config.minAgeUpdated', { age }), ...EPHEMERAL });
}

async function setCooldown(interaction) {
  const days = interaction.options.getInteger('days');
  await config.setConfig({ applyCooldownDays: days });
  await interaction.reply({ content: t('config.cooldownUpdated', { days }), ...EPHEMERAL });
}

async function setBrand(interaction) {
  const patch = {};
  const name = interaction.options.getString('name');
  const color = interaction.options.getString('color');
  const icon = interaction.options.getString('icon');
  if (name !== null) patch.brandName = name;
  if (color !== null) patch.brandColor = color;
  if (icon !== null) patch.brandIconUrl = icon;
  if (!Object.keys(patch).length) {
    return interaction.reply({ content: t('config.needBrandField'), ...EPHEMERAL });
  }
  await config.setConfig(patch);
  await interaction.reply({ content: t('config.brandUpdated'), ...EPHEMERAL });
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName('config')
    .setDescription(t('config.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addSubcommand((sc) => sc.setName('view').setDescription(t('config.subViewDescription')))
    .addSubcommand((sc) =>
      sc
        .setName('channels')
        .setDescription(t('config.subChannelsDescription'))
        .addChannelOption((o) => o.setName('applications').setDescription(t('config.optChannelApplications')))
        .addChannelOption((o) => o.setName('welcome').setDescription(t('config.optChannelWelcome')))
        .addChannelOption((o) => o.setName('serverinfo').setDescription(t('config.optChannelServerInfo')))
        .addChannelOption((o) => o.setName('stafflog').setDescription(t('config.optChannelStaffLog')))
    )
    .addSubcommand((sc) =>
      sc
        .setName('member-role')
        .setDescription(t('config.subMemberRoleDescription'))
        .addRoleOption((o) => o.setName('role').setDescription(t('config.optMemberRole')).setRequired(true))
    )
    .addSubcommand((sc) =>
      sc
        .setName('staff')
        .setDescription(t('config.subStaffDescription'))
        .addStringOption((o) =>
          o
            .setName('action')
            .setDescription(t('config.optStaffAction'))
            .setRequired(true)
            .addChoices({ name: 'add', value: 'add' }, { name: 'remove', value: 'remove' })
        )
        .addRoleOption((o) => o.setName('role').setDescription(t('config.optStaffRole')).setRequired(true))
    )
    .addSubcommand((sc) =>
      sc
        .setName('min-age')
        .setDescription(t('config.subMinAgeDescription'))
        .addIntegerOption((o) =>
          o.setName('age').setDescription(t('config.optMinAge')).setRequired(true).setMinValue(1).setMaxValue(120)
        )
    )
    .addSubcommand((sc) =>
      sc
        .setName('apply-cooldown')
        .setDescription(t('config.subCooldownDescription'))
        .addIntegerOption((o) =>
          o.setName('days').setDescription(t('config.optCooldownDays')).setRequired(true).setMinValue(0).setMaxValue(365)
        )
    )
    .addSubcommand((sc) =>
      sc
        .setName('brand')
        .setDescription(t('config.subBrandDescription'))
        .addStringOption((o) => o.setName('name').setDescription(t('config.optBrandName')))
        .addStringOption((o) => o.setName('color').setDescription(t('config.optBrandColor')))
        .addStringOption((o) => o.setName('icon').setDescription(t('config.optBrandIcon')))
    ),

  async execute(interaction) {
    switch (interaction.options.getSubcommand()) {
      case 'view':
        return viewConfig(interaction);
      case 'channels':
        return setChannels(interaction);
      case 'member-role':
        return setMemberRole(interaction);
      case 'staff':
        return setStaff(interaction);
      case 'min-age':
        return setMinAge(interaction);
      case 'apply-cooldown':
        return setCooldown(interaction);
      case 'brand':
        return setBrand(interaction);
      default:
        return interaction.reply({ content: t('common.unknownSubcommand'), ...EPHEMERAL });
    }
  },
};
