'use strict';

const { SlashCommandBuilder, PermissionFlagsBits } = require('discord.js');
const { t } = require('../i18n');
const panel = require('../lib/configPanel');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('config')
    .setDescription(t('config.commandDescription'))
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

  execute(interaction) {
    return panel.open(interaction);
  },
};
