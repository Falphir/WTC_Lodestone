'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const { t } = require('../i18n');
const admin = require('../lib/serverAdmin');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('server')
    .setDescription(t('server.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addSubcommand((sc) => sc.setName('list').setDescription(t('server.subListDescription')))
    .addSubcommand((sc) => sc.setName('add').setDescription(t('server.subAddDescription')))
    .addSubcommand((sc) =>
      sc
        .setName('edit')
        .setDescription(t('server.subEditDescription'))
        .addStringOption((o) => o.setName('id').setDescription(t('server.optionIdDescription')).setRequired(true))
    )
    .addSubcommand((sc) =>
      sc
        .setName('remove')
        .setDescription(t('server.subRemoveDescription'))
        .addStringOption((o) => o.setName('id').setDescription(t('server.optionIdDescription')).setRequired(true))
    ),

  async execute(interaction) {
    const sub = interaction.options.getSubcommand();
    const id = interaction.options.getString('id');
    switch (sub) {
      case 'list':
        return admin.listServers(interaction);
      case 'add':
        return admin.openAddModal(interaction);
      case 'edit':
        return admin.openEditModal(interaction, id);
      case 'remove':
        return admin.promptRemove(interaction, id);
      default:
        return interaction.reply({ content: t('common.unknownSubcommand'), flags: 64 });
    }
  },
};
