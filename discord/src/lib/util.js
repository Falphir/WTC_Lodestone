'use strict';

const { ActionRowBuilder, ButtonBuilder, MessageFlags } = require('discord.js');
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

module.exports = { EPHEMERAL, isStaff, getField, reviewedRow };
