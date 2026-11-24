# Audit log

What the admin audit log records, how it knows who acted (including through the Discord bot), and how it's shown and pruned.

`audit_log` (V21, `AuditEntry`, `AuditLog`) records every admin change:
- `at`: when;
- `actor`: the authenticated hub account, or `system` outside a request (e.g. `AdminUserSeeder` on first boot);
- `via`: the Discord staff member, for the bot's calls;
- `action`: a key like `whitelist.remove` or `server.edit`;
- `target`: a readable name;
- `detail`: more, or null.

**A new admin mutation records itself:** call `audit.record(action, target, detail)` in the service method, after the change and inside its transaction, so a refused or rolled-back change leaves no row. Nothing passes the admin's name in: `AuditLog` reads it from the `SecurityContext`, and `via` from the `X-Lodestone-On-Behalf-Of` header. Edits record field names, never values (`AuditLog.fieldsSet(patch)`), and never passwords.

**Sign-in lockouts** are the one row that isn't an admin's change: `login.lockout`, written by `LoginThrottle` when an IP or a username first gets locked out (`docs/hub-auth.md`). The actor is `system`, the target is `IP <addr>` or the username, and the detail names the other one.

**Not recorded:** what the mods report, the bot's own application bookkeeping (`pending`/`clear`), the nightly cleanups.

**Creating a server** registers it with `ServerService.register(…, false)` and records a single `server.create`, not a second `server.register`.

## The bot's header

The bot names who it's acting for without any handler passing it:
- **Where it's set:** `discord/src/events/interactionCreate.js` runs each interaction inside `hub.actingFor(interaction.user, …)`, an `AsyncLocalStorage` in `discord/src/hub.js`.
- **What's sent:** every request made meanwhile carries `X-Lodestone-On-Behalf-Of: name (id)`, reduced to printable ASCII since it's a header.
- **How the hub treats it:** taken as given, since only an admin can send it, and the row still names the bot as `actor`.

## Reading and pruning

**The API:** `GET /api/admin/audit?before=&limit=` (1–200, default 50) pages newest first.

**The History page** (`#/history`, `pages/History.jsx`) merges pages by id, so a live refresh (topic `audit`) never leaves a gap between the newest page and older ones.

**A new action key** also gets a label in `format.js`'s `auditAction`. Unknown keys show raw.

**Retention:** rows older than `lodestone.audit-retention-days` (180) are pruned nightly by `AuditRetentionTask`, and `PRIVACY.md` says so.
