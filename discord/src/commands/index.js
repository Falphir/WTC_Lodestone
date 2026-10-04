'use strict';

// Auto-loads every command module in this folder. Each module exports
// { data: SlashCommandBuilder, execute(interaction) }.
//   - `commands` : Map<name, module>  (used by the interaction router)
//   - `data`     : JSON array          (used by deploy-commands.js)
const fs = require('node:fs');
const path = require('node:path');

const commands = new Map();
const data = [];

const files = fs
  .readdirSync(__dirname)
  .filter((f) => f.endsWith('.js') && f !== 'index.js');

for (const file of files) {
  const command = require(path.join(__dirname, file));
  if (!command.data || typeof command.execute !== 'function') {
    console.warn(`Skipping ${file}: missing "data" or "execute".`);
    continue;
  }
  commands.set(command.data.name, command);
  data.push(command.data.toJSON());
}

module.exports = { commands, data };
