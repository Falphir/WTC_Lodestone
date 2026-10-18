'use strict';

const { randomUUID } = require('node:crypto');
const { Events } = require('discord.js');
const { EPHEMERAL } = require('../lib/util');
const { t } = require('../i18n');
const applications = require('../lib/applications');
const configPanel = require('../lib/configPanel');

/** What failed, for the log: the command name or the component's custom id. */
function describe(interaction) {
  if (interaction.isChatInputCommand()) return `/${interaction.commandName}`;
  if (interaction.isAutocomplete()) return `/${interaction.commandName} (autocomplete)`;
  return interaction.customId || `interaction type ${interaction.type}`;
}

module.exports = {
  name: Events.InteractionCreate,
  async execute(interaction) {
    try {
      if (interaction.isChatInputCommand()) {
        const command = interaction.client.commands.get(interaction.commandName);
        if (!command) return interaction.reply({ content: t('common.unknownCommand'), ...EPHEMERAL });
        return await command.execute(interaction);
      }

      if (interaction.isAutocomplete()) {
        const command = interaction.client.commands.get(interaction.commandName);
        // An autocomplete interaction has no reply, only choices; an empty list is the way to
        // say "nothing to suggest", which is also all the catch below can do for it.
        if (!command?.autocomplete) return interaction.respond([]);
        return await command.autocomplete(interaction);
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
      // The same short reference goes in the log line and in what the user is told, so a
      // "it broke for me" report can be grepped straight to the stack trace that caused it.
      const ref = randomUUID().slice(0, 8);
      console.error(`Interaction error [${ref}] ${describe(interaction)} by ${interaction.user?.id}:`, err);

      // Autocomplete can't be replied to -- the hub being slow just means no suggestions.
      if (interaction.isAutocomplete()) {
        return interaction.respond([]).catch(() => {});
      }
      const msg = { content: t('common.interactionError', { ref }), ...EPHEMERAL };
      try {
        if (interaction.deferred || interaction.replied) await interaction.followUp(msg);
        else await interaction.reply(msg);
      } catch {
        /* nothing else we can do */
      }
    }
  },
};
