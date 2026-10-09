'use strict';

const { Events } = require('discord.js');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const applications = require('../lib/applications');
const configPanel = require('../lib/configPanel');

module.exports = {
  name: Events.InteractionCreate,
  async execute(interaction) {
    try {
      if (interaction.isChatInputCommand()) {
        const command = interaction.client.commands.get(interaction.commandName);
        if (!command) return interaction.reply({ content: t('common.unknownCommand'), ...EPHEMERAL });
        return await command.execute(interaction);
      }

      if (interaction.isButton()) {
        const id = interaction.customId;
        if (id.startsWith('app_')) return await applications.onButton(interaction);
        if (id.startsWith('cfg:')) return await configPanel.onButton(interaction);
        return;
      }

      if (interaction.isAnySelectMenu()) {
        if (interaction.customId.startsWith('cfg:')) return await configPanel.onSelect(interaction);
        return;
      }

      if (interaction.isModalSubmit()) {
        const id = interaction.customId;
        if (id === 'apply_modal' || id.startsWith('deny_modal:')) {
          return await applications.onModal(interaction);
        }
        if (id.startsWith('cfg:')) return await configPanel.onModal(interaction);
        return;
      }
    } catch (err) {
      console.error('Interaction error:', err);
      const msg = { content: t('common.interactionError'), ...EPHEMERAL };
      try {
        if (interaction.deferred || interaction.replied) await interaction.followUp(msg);
        else await interaction.reply(msg);
      } catch {
        /* nothing else we can do */
      }
    }
  },
};
