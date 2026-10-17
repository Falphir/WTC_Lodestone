'use strict';

const { ActionRowBuilder, MessageFlags, TextInputBuilder, TextInputStyle } = require('discord.js');
const config = require('../config');

const EPHEMERAL = { flags: MessageFlags.Ephemeral };

function isStaff(member) {
  if (!member || !member.roles) return false;
  return config.staffRoleIds.some((id) => member.roles.cache.has(id));
}

// One modal text input in the action row Discord requires around it. A `value` of 0 still
// prefills (unlike a truthy check), which is what makes it usable for numeric settings.
function inputRow(id, label, { value, max, required = true, placeholder } = {}) {
  const input = new TextInputBuilder()
    .setCustomId(id)
    .setLabel(label)
    .setStyle(TextInputStyle.Short)
    .setRequired(required);
  if (max) input.setMaxLength(max);
  if (value !== undefined && value !== null && value !== '') input.setValue(String(value));
  if (placeholder) input.setPlaceholder(placeholder);
  return new ActionRowBuilder().addComponents(input);
}

module.exports = { EPHEMERAL, isStaff, inputRow };
