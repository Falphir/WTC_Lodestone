'use strict';

// Registers the guild slash commands. Run once after adding/changing a command:
//   npm run deploy
const { REST, Routes } = require('discord.js');
const config = require('./config');
const { data } = require('./commands');

(async () => {
  const rest = new REST({ version: '10' }).setToken(config.token);
  console.log(`Registering ${data.length} guild commands to ${config.guildId}...`);
  await rest.put(Routes.applicationGuildCommands(config.clientId, config.guildId), { body: data });
  console.log('Done. Commands should appear in your server within a few seconds.');
})().catch((err) => {
  console.error('Failed to register commands:', err);
  process.exit(1);
});
