'use strict';

const {
  ActionRowBuilder,
  ButtonBuilder,
  MessageFlags,
  TextInputBuilder,
  TextInputStyle,
} = require('discord.js');
const config = require('../config');

const EPHEMERAL = { flags: MessageFlags.Ephemeral };

function isStaff(member) {
  if (!member || !member.roles) return false;
  return config.staffRoleIds.some((id) => member.roles.cache.has(id));
}

function getField(embed, name) {
  const f = (embed.fields || []).find((x) => x.name === name);
  return f ? f.value : null;
}

// A single disabled button used to stamp a reviewed application (Approved/Denied).
function reviewedRow(label, style) {
  return new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('reviewed').setLabel(label).setStyle(style).setDisabled(true)
  );
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

module.exports = { EPHEMERAL, isStaff, getField, reviewedRow, inputRow };
