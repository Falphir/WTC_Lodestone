'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { Client, GatewayIntentBits } = require('discord.js');

const config = require('./config');
const { commands } = require('./commands');

const client = new Client({
  intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers],
});

// Make the command map available to the interaction router.
client.commands = commands;

// The net, not the fix: every handler that can throw should catch its own (see the member
// events). This is here so the one that forgets logs instead of taking the process down --
// Node exits on an unhandled rejection, and systemd would restart us having lost the work.
process.on('unhandledRejection', (err) => console.error('Unhandled rejection:', err));
client.on('error', (err) => console.error('Client error:', err));

// Auto-register every event module in ./events.
const eventsDir = path.join(__dirname, 'events');
for (const file of fs.readdirSync(eventsDir).filter((f) => f.endsWith('.js'))) {
  const event = require(path.join(eventsDir, file));
  if (event.once) client.once(event.name, (...args) => event.execute(...args));
  else client.on(event.name, (...args) => event.execute(...args));
}

async function main() {
  await config.init(); // the bot's config lives on the hub -- nothing works without it
  await client.login(config.token);
}

main().catch((err) => {
  console.error('Fatal startup error:', err);
  process.exit(1);
});
