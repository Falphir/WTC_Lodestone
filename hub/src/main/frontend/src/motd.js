// A MOTD as the editor works on it, and the code text it's saved as. The text is what the hub, the mod
// and servers use (§ codes, {center}/{right} line markers, {modpack}-style variables); the model is what
// lets the editor show none of that. No React here, so `node --test` can check it.
//
// model = { aligns: ['left' | 'center' | 'right', ...one per line], items: [...] }
//   item = { t: 'a', s: style } a character, { v: 'modpack', s: style } a variable, or { br: true }
//   style = { c: colour code or null (the server list's gray), l, o, n, m, k: bold, italic, underline,
//             strikethrough, obfuscated }
// An offset is an index into `items`, so a line break and a variable each count one, as in the editor.

export const MAX_LINES = 2
export const VARIABLES = ['modpack', 'version', 'name']
export const FORMATS = ['l', 'o', 'n', 'm', 'k']

export const PLAIN = Object.freeze({ c: null, l: false, o: false, n: false, m: false, k: false })

/** Minecraft's colours by code, with the names its docs use. What each looks like is the mc-c-* classes in App.css. */
export const COLORS = {
  0: 'Black',
  1: 'Dark blue',
  2: 'Dark green',
  3: 'Dark aqua',
  4: 'Dark red',
  5: 'Dark purple',
  6: 'Gold',
  7: 'Gray',
  8: 'Dark gray',
  9: 'Blue',
  a: 'Green',
  b: 'Aqua',
  c: 'Red',
  d: 'Light purple',
  e: 'Yellow',
  f: 'White',
}

const MARKER = /^\{(center|right)\}/
const VARIABLE = /^\{(modpack|version|name)\}/

export const sameStyle = (a, b) => a.c === b.c && FORMATS.every((f) => a[f] === b[f])

/**
 * A style as classes: mc-c-6 for gold, mc-l for bold and so on, one per code it stands for, so a span's
 * classes are both how it looks (App.css) and which codes it means. No colour class is the plain gray.
 */
export const styleClass = (s) => [s.c !== null && `mc-c-${s.c}`, ...FORMATS.filter((f) => s[f]).map((f) => `mc-${f}`)].filter(Boolean).join(' ')

/** The style a span's classes stand for; the other way from {@link styleClass}. */
export function styleFromClasses(classList) {
  const s = { ...PLAIN }
  for (const name of classList) {
    const color = name.match(/^mc-c-([0-9a-f])$/)
    if (color) s.c = color[1]
    else if (/^mc-[lonmk]$/.test(name)) s[name[3]] = true
  }
  return s
}
/**
 * Saved text → model. § and & codes both count, as the mod reads both; a colour clears the formats and
 * §r clears everything, as in the game. The style carries on across the line break, also as in the game.
 */
export function parseMotd(text) {
  const aligns = []
  const items = []
  let style = PLAIN
  const lines = (text ?? '').replace(/\r\n/g, '\n').replace(/\\n/g, '\n').split('\n')
  lines.forEach((raw, index) => {
    if (index > 0) items.push({ br: true })
    const marker = raw.match(MARKER)
    aligns.push(marker ? marker[1] : 'left')
    const line = marker ? raw.slice(marker[0].length) : raw
    const lineStart = items.length
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      const code = line[i + 1]?.toLowerCase()
      if ((ch === '§' || ch === '&') && code && /[0-9a-fk-or]/.test(code)) {
        i++
        if (code in COLORS) style = { ...PLAIN, c: code }
        else if (code === 'r') style = PLAIN
        else style = { ...style, [code]: true }
        continue
      }
      const variable = ch === '{' && line.slice(i).match(VARIABLE)
      if (variable) {
        items.push({ v: variable[1], s: style })
        i += variable[0].length - 1
      } else {
        items.push({ t: ch, s: style })
      }
    }
    // an aligned line is lined up by the hub, which drops the spaces typed around it to do it by hand; so
    // does the editor. Only the ones that show nothing, though: a struck-through or underlined space is a stripe
    if (marker) {
      const blank = (item) => item?.t === ' ' && !item.s.m && !item.s.n
      while (blank(items[items.length - 1]) && items.length > lineStart) items.pop()
      let lead = lineStart
      while (lead < items.length && blank(items[lead])) lead++
      items.splice(lineStart, lead - lineStart)
    }
  })
  return { aligns, items }
}

/** The codes that take the text from one style to the next, as few as the game allows. */
function transition(from, to) {
  const dropsSomething = (from.c !== null && to.c === null) || FORMATS.some((f) => from[f] && !to[f])
  const formats = (s) => FORMATS.filter((f) => s[f]).map((f) => `§${f}`).join('')
  // a colour code clears the formats too, so they go back on after it
  if (dropsSomething) return '§r' + (to.c ? `§${to.c}` : '') + formats(to)
  if (from.c !== to.c) return `§${to.c}` + formats(to)
  return FORMATS.filter((f) => to[f] && !from[f]).map((f) => `§${f}`).join('')
}

/** Model → saved text. */
export function serializeMotd(model) {
  let out = ''
  let style = PLAIN
  let line = 0
  const marker = (i) => (model.aligns[i] && model.aligns[i] !== 'left' ? `{${model.aligns[i]}}` : '')
  out += marker(0)
  for (const item of model.items) {
    if (item.br) {
      out += '\n' + marker(++line)
      continue
    }
    if (!sameStyle(style, item.s)) {
      out += transition(style, item.s)
      style = item.s
    }
    out += item.v ? `{${item.v}}` : item.t
  }
  return out
}

/** Which line an offset is on. */
export const lineAt = (model, offset) => model.items.slice(0, offset).filter((i) => i.br).length

/** The style typing at `offset` would get: that of what's just before it on the same line. */
export function styleAt(model, offset) {
  const before = model.items[offset - 1]
  return before && !before.br ? before.s : (model.items[offset]?.s ?? PLAIN)
}

/**
 * Restyles [start, end): `{ color: '3' }` recolours and keeps bold and the like; `{ format: 'l' }` turns
 * it on for the lot, or off if all of it already has it; `{ reset: true }` makes it plain.
 */
export function styleRange(model, start, end, change) {
  const inRange = (i) => i >= start && i < end && !model.items[i].br
  let format = null
  if (change.format) {
    const all = model.items.every((item, i) => !inRange(i) || item.s[change.format])
    format = { [change.format]: !all }
  }
  const items = model.items.map((item, i) => {
    if (!inRange(i)) return item
    if (change.reset) return { ...item, s: PLAIN }
    if (change.color) return { ...item, s: { ...item.s, c: change.color } }
    return { ...item, s: { ...item.s, ...format } }
  })
  return { ...model, items }
}

/** Lines up the line `offset` is on. */
export function setAlign(model, offset, align) {
  const aligns = [...model.aligns]
  aligns[lineAt(model, offset)] = align
  return { ...model, aligns }
}

/**
 * Puts `inserted` (items, line breaks allowed) in place of [start, end). Null when that would make more
 * than MAX_LINES lines, so the caller can refuse it. A new line starts left-aligned.
 */
export function replaceRange(model, start, end, inserted) {
  const items = [...model.items.slice(0, start), ...inserted, ...model.items.slice(end)]
  const lines = items.filter((i) => i.br).length + 1
  if (lines > MAX_LINES) return null
  // the lines a replacement removed or added change which alignment belongs to which line
  const before = lineAt(model, start)
  const removed = model.items.slice(start, end).filter((i) => i.br).length
  const added = inserted.filter((i) => i.br).length
  const aligns = [
    ...model.aligns.slice(0, before + 1),
    ...Array(added).fill('left'),
    ...model.aligns.slice(before + 1 + removed),
  ].slice(0, lines)
  while (aligns.length < lines) aligns.push('left')
  return { aligns, items }
}

/** Plain characters in `style`, for typing or a paste without codes in it. */
export const textItems = (text, style) => [...text].map((t) => (t === '\n' ? { br: true } : { t, s: style }))

/** A paste: with codes or variables in it, they're read as such; otherwise it takes the style it lands in. */
export function pastedItems(text, style) {
  const clean = text.replace(/\r\n/g, '\n')
  return /[§&][0-9a-fk-or]|\{(modpack|version|name)\}/i.test(clean) ? parseMotd(clean).items : textItems(clean, style)
}
