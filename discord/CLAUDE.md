# Discord bot (discord/)

The rules for changing bot code. Commands are in the root `CLAUDE.md`; why the bot is built this way (auth, state, applications, `/review`) is in `docs/discord-bot.md`.

- **The bot keeps no durable state.** Everything it needs lives on the hub, reached through `src/hub.js`, the one module that talks to it.
  - What it holds in memory are caches it can lose: the config (`src/config.js`, 60s), `/review`'s suggestion list (15s), and half-filled `/apply` answers (15 min).
  - Never add a local file or database.
- **It's an ordinary hub admin, not a special account.** It signs in with the `discord-bot` account at `POST /api/auth/token`. It never calls game servers, and it calls Mojang only through the hub (`GET /api/admin/mojang/{name}`; `src/mojang.js` is a pass-through).
  - The only things it calls directly are Discord and the cosmetic skin renders (Visage, then mc-heads, in `src/lib/embeds.js`).
- **Every hub call names who it's for,** for the audit log. `src/events/interactionCreate.js` runs each interaction inside `hub.actingFor(interaction.user, …)`, and `hub.js` adds the `X-Lodestone-On-Behalf-Of` header.
  - A new command needs nothing extra.
  - A test that swaps in a stub hub has to give it an `actingFor: (_user, fn) => fn()`.
- **No user-facing string is written inline.** Every reply, embed, modal label and command description comes from `locales/en.json` through `i18n.js`'s `t('dot.path', { placeholder })`.
  - `BOT_LANG` picks the file, and a missing key returns the key itself rather than throwing.
  - New bot copy means a new key there.
- **Changing a command or option description** also needs `npm run deploy` to re-register it with Discord.
- **Autocomplete can only answer with choices,** never a reply, and Discord fires it on every keystroke. Build a choice list at most once per cache period, filter it in memory, and answer errors with `[]`.
- **The hub's application row is the record,** not the posted embed. Handlers read `status` and `messageId` from the row, and the hub refuses a decision unless the row is still `pending` (`setApplicationDecision` returns `null` then).
  - What a decision does in Discord lives in one place, `applyDecision`, used by both `/review` and `syncDashboardDecisions` (the 30s poll for decisions made on the dashboard). See `docs/discord-bot.md`.
- **The `/apply` form needs a recent discord.js.** It's built from `LabelBuilder` components, so its declared version can't drop below the range that has `addLabelComponents`/`setDescription`/selects in modals.
- **Tests** (`test/*.test.js`, `node:test`) stub `fetch`, or replace `src/hub.js` in `require.cache`. They never reach a real hub or Discord.
