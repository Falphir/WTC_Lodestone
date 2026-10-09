'use strict';

// Runtime server management: add / edit / remove / list. These servers are display-only
// metadata for /serverinfo -- the bot no longer talks to them directly (see hub.js).
const { ActionRowBuilder, ButtonBuilder, ButtonStyle, ModalBuilder } = require('discord.js');

const config = require('../config');
const { t } = require('../i18n');
const { withIcon } = require('../icons');
const { EPHEMERAL, inputRow } = require('./util');
const { brandEmbed } = require('./embeds');

// ---------- modal openers ----------

async function openAddModal(interaction) {
  const modal = new ModalBuilder().setCustomId('server_add').setTitle(t('server.addModalTitle'));
  modal.addComponents(
    inputRow('id', t('server.labelId'), { max: 32, placeholder: t('server.placeholderId') }),
    inputRow('name', t('server.labelName'), { max: 100, placeholder: t('server.placeholderName') }),
    inputRow('publicAddress', t('server.labelPublicAddress'), { max: 100, required: false }),
    inputRow('modpack', t('server.labelModpack'), { max: 100, required: false }),
    inputRow('installUrl', t('server.labelInstallUrl'), { max: 300, required: false })
  );
  await interaction.showModal(modal);
}

async function openEditModal(interaction, id) {
  const s = config.serverById(id);
  if (!s) return interaction.reply({ content: t('server.noSuchServer', { id }), ...EPHEMERAL });
  const modal = new ModalBuilder()
    .setCustomId(`server_edit:${id}`)
    .setTitle(t('server.editModalTitle', { id }).slice(0, 45));
  modal.addComponents(
    inputRow('name', t('server.labelName'), { max: 100, value: s.name }),
    inputRow('publicAddress', t('server.labelPublicAddress'), { max: 100, value: s.publicAddress, required: false }),
    inputRow('modpack', t('server.labelModpack'), { max: 100, value: s.modpack, required: false }),
    inputRow('version', t('server.labelVersion'), { max: 32, value: s.version, required: false }),
    inputRow('installUrl', t('server.labelInstallUrl'), { max: 300, value: s.installUrl, required: false })
  );
  await interaction.showModal(modal);
}

// ---------- modal submit ----------

async function onModal(interaction) {
  const id = interaction.customId;
  if (id === 'server_add') return submitAdd(interaction);
  if (id.startsWith('server_edit:')) return submitEdit(interaction, id.split(':')[1]);
}

async function submitAdd(interaction) {
  const sid = interaction.fields.getTextInputValue('id').trim().toLowerCase();
  const name = interaction.fields.getTextInputValue('name').trim();

  if (!/^[a-z0-9_-]+$/.test(sid)) {
    return interaction.reply({ content: t('server.idInvalid'), ...EPHEMERAL });
  }
  if (config.serverById(sid)) {
    return interaction.reply({ content: t('server.idExists', { id: sid }), ...EPHEMERAL });
  }

  await config.addServer({
    id: sid,
    name,
    publicAddress: interaction.fields.getTextInputValue('publicAddress').trim(),
    modpack: interaction.fields.getTextInputValue('modpack').trim(),
    version: '',
    installUrl: interaction.fields.getTextInputValue('installUrl').trim(),
  });
  await interaction.reply({
    content: t('server.added', { id: sid }),
    ...EPHEMERAL,
  });
}

async function submitEdit(interaction, sid) {
  if (!config.serverById(sid)) {
    return interaction.reply({ content: t('server.noSuchServer', { id: sid }), ...EPHEMERAL });
  }
  await config.updateServer(sid, {
    name: interaction.fields.getTextInputValue('name').trim(),
    publicAddress: interaction.fields.getTextInputValue('publicAddress').trim(),
    modpack: interaction.fields.getTextInputValue('modpack').trim(),
    version: interaction.fields.getTextInputValue('version').trim(),
    installUrl: interaction.fields.getTextInputValue('installUrl').trim(),
  });
  await interaction.reply({ content: t('server.updated', { id: sid }), ...EPHEMERAL });
}

// ---------- remove (with confirm button) ----------

async function promptRemove(interaction, id) {
  if (!config.serverById(id)) {
    return interaction.reply({ content: t('server.noSuchServer', { id }), ...EPHEMERAL });
  }
  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId(`srvdel:${id}`)
      .setLabel(t('server.removeButton', { id }))
      .setStyle(ButtonStyle.Danger)
  );
  await interaction.reply({
    content: t('server.removePrompt', { id }),
    components: [row],
    ...EPHEMERAL,
  });
}

async function onButton(interaction) {
  const [action, id] = interaction.customId.split(':');
  if (action === 'srvdel') {
    const ok = await config.removeServer(id);
    return interaction.update({
      content: ok ? t('server.removed', { id }) : t('server.noSuchServer', { id }),
      components: [],
    });
  }
}

// ---------- list & test ----------

async function listServers(interaction) {
  const servers = config.servers;
  if (!servers.length) {
    return interaction.reply({ content: t('server.listEmpty'), ...EPHEMERAL });
  }
  const embed = brandEmbed().setTitle(withIcon('servers', t('server.listTitle')));
  for (const s of servers) {
    embed.addFields({
      name: t('server.listItemName', { id: s.id, name: s.name || t('server.listItemNoName') }),
      value: t('server.listItemValue', {
        address: s.publicAddress || t('config.empty'),
        modpack: s.modpack || t('config.empty'),
        version: s.version || t('config.empty'),
      }),
    });
  }
  await interaction.reply({ embeds: [embed], ...EPHEMERAL });
}

module.exports = {
  openAddModal,
  openEditModal,
  promptRemove,
  listServers,
  onModal,
  onButton,
};
