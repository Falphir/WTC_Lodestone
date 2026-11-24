# Network MOTDs

The network's MOTD templates: how a server picks one, how the hub renders and aligns it, how it reaches servers, and how the dashboard's code-free editor works.

## Templates

**Storage:** `motd_templates` (V19, `MotdTemplate`, `MotdService`, `/api/admin/motds`), a list of MOTDs managed on the dashboard's Configuration page (`#/config`, `MotdPanel`).

**Their text** has `{modpack}`, `{version}` and `{name}` variables, and holds real newlines: a `\n` typed as in `server.properties` is normalised on save.

**The default:** one MOTD can be the default, preselected in the New server dialog.

**How a server picks one:** through `servers.motd_template_id`.
- **Null** keeps the server's own MOTD.
- **Setting it:** `motdTemplateId` on `PATCH /api/admin/servers/{id}` (0 clears it), or on create.

**A MOTD in use can't be deleted** (409, naming the servers).

## Rendering and delivery

**`MotdService.renderFor(server)`** fills the variables from the server's listing (`modpack`, `modpackVersion`, name).

**It returns null when a variable the text uses is blank,** so a server never shows half a line and keeps its own MOTD instead.

**The render reaches a server two ways:**
- **Live:** it rides on the `hello` and heartbeat responses as `motd` (an explicit null means none).
  - The mod's `HubClient.readMessages` keeps it, and ignores an absent field from an older hub.
  - `HubClient.applyMotd`, called every tick on the server thread, applies a change with `server.setMotd`. It remembers the server's own MOTD first, and puts that back on null.
  - It's in memory only, never written to `server.properties`.
- **At creation:** `ServerProvisioningService` writes it into the template's `motd` setting, with newlines as `\n`, unless the form set one. That way `server.properties` is right before the mod's first hello.

## Alignment

**The markers:** a `{center}` or `{right}` at the start of a line.

**Why they're rendered per server:** alignment can't be baked into the text, because a line's width depends on each server's `{modpack}`/`{version}`. So `MotdService.render` strips the marker, drops the spaces typed around that line, and pads it with leading spaces for that server via `MinecraftFont`.

**Which spaces are dropped:** only invisible ones (`MinecraftFont.Lines.trimBlankSpaces`).
- A space with strikethrough or underline on is a visible stripe, and stays.
- Those formats carry over the line break, so the state follows from line to line.
- `parseMotd` on the dashboard applies the same rule.

**How exact the padding is:** `MinecraftFont` has the default font's ASCII glyph advances, codes at zero width, bold +1, against a ~270px MOTD area. It's close, not pixel-exact: non-ASCII is guessed, and resource packs or the Unicode font change widths.

**How the dashboard previews it:** `MinecraftText` aligns marked lines with CSS instead, so it shows the intent in any font. `ServerMotd` previews from the template with markers kept, not from the hub's padded `rendered`.

**The preview font is proportional, not monospace,** because Minecraft's space is 4px against 6px for most letters.

**Previews only mirror the hub.** The dashboard's `renderMotd`/`packVersion` in `format.js` copy the hub's rules for previews, and `MinecraftText` draws § and & codes. What servers get is always the hub's render.

## The editor

`MotdEditor` shows no codes at all: you type into the styled text, variables are tags, and alignment shows in place.

**It's a contentEditable box drawn by hand from a model in `motd.js`:** a flat list of characters, variables and line breaks, each with a style, plus an alignment per line. React doesn't draw the box, since React would fight the browser over a contentEditable's contents.

**`motd.js`** converts between saved code text and the model:
- `parseMotd`/`serializeMotd` (the latter writes the fewest codes).
- It also holds the edits: `styleRange`, `setAlign`, and `replaceRange` (which refuses a third line).
- Its tests are in `motd.test.js` (`npm test`, Node's built-in runner, no dependency).
- `MinecraftText` reads codes through the same `parseMotd`.

**Typing is left to the browser.** On `input`, the box is read back into the model through each run span's classes:
- A run is `mc-run`, plus a `mc-c-6`-style colour class and `mc-l`-style format classes (`styleClass`/`styleFromClasses`).
- Those classes are both its look and the codes it stands for. `App.css` is the one place the hex values live.
- No colour class means plain gray.

**Toolbar actions, Enter and paste** change the model, redraw the box, and put the selection back by offset. A paste with codes in it is parsed.

**"Edit as code"** swaps in a plain textarea.

**There's no `Field` around the editor,** because a `<label>` around the toolbar would pass a click on the label to the first swatch.
