import assert from 'node:assert/strict'
import { test } from 'node:test'
import { FORMATS, pastedItems, parseMotd, PLAIN, replaceRange, serializeMotd, setAlign, styleClass, styleFromClasses, styleRange } from './motd.js'

/** What a model looks like, ignoring how its codes happen to be written: one entry per character. */
const look = (model) =>
  model.items.map((i) => (i.br ? '\n' : `${i.v ? `{${i.v}}` : i.t}:${i.s.c ?? '-'}${['l', 'o', 'n', 'm', 'k'].filter((f) => i.s[f]).join('')}`))

const roundTrip = (text) => parseMotd(serializeMotd(parseMotd(text)))

test('the WTC MOTD comes back looking the same, markers and variables included', () => {
  const wtc = '§l   §r §8§m        §6 ✦ §e§lW T C  N E T W O R K §6✦ §8§m        §r §r\n{center}§2§l{modpack} §8| §7v{version}'
  const model = parseMotd(wtc)
  assert.deepEqual(look(roundTrip(wtc)), look(model))
  assert.deepEqual(model.aligns, ['left', 'center'])
  assert.ok(serializeMotd(model).includes('\n{center}'))
  assert.equal(model.items.filter((i) => i.v).map((i) => i.v).join(','), 'modpack,version')
})

test("a centred line's stripes stay; only the spaces that show nothing go", () => {
  // the General MOTD's first line, as saved
  const line = '{center}§8§m        §r§6 ✦ §e§lW T C  N E T W O R K §r§6✦ §8§m        '
  const model = parseMotd(line)
  const struck = (items) => items.filter((i) => i.t === ' ' && i.s.m).length
  assert.equal(struck(model.items), 16) // both stripes, 8 spaces each
  assert.equal(serializeMotd(model), line) // and it saves back exactly as it was
  assert.deepEqual(look(parseMotd('{center}   §6Hi   ')), ['H:6', 'i:6'])
})

test('codes are only written where the style changes', () => {
  assert.equal(serializeMotd(parseMotd('§6§6Go§6ld')), '§6Gold')
  assert.equal(serializeMotd(parseMotd('plain')), 'plain')
  // dropping bold needs a reset, then the colour again
  assert.equal(serializeMotd(parseMotd('§6§lA§r§6B')), '§6§lA§r§6B')
})

test('colouring the middle of a gold run leaves the rest gold, and keeps its bold', () => {
  const model = styleRange(parseMotd('§6§lABC'), 1, 2, { color: '3' })
  assert.deepEqual(look(model), ['A:6l', 'B:3l', 'C:6l'])
  assert.deepEqual(look(roundTrip(serializeMotd(model))), look(model))
})

test('a format turns on for the selection, or off when all of it has it already', () => {
  const on = styleRange(parseMotd('§6ABC'), 0, 2, { format: 'l' })
  assert.deepEqual(look(on), ['A:6l', 'B:6l', 'C:6'])
  const off = styleRange(on, 0, 2, { format: 'l' })
  assert.deepEqual(look(off), ['A:6', 'B:6', 'C:6'])
})

test('a reset makes the selection plain', () => {
  const model = styleRange(parseMotd('§c§nAB'), 0, 1, { reset: true })
  assert.deepEqual(look(model), ['A:-', 'B:cn'])
})

test('a third line is refused, and aligns follow the lines', () => {
  const one = parseMotd('first')
  const two = replaceRange(one, 5, 5, [{ br: true }])
  assert.equal(two.aligns.length, 2)
  assert.equal(replaceRange(two, 0, 0, [{ br: true }]), null)
  const centred = setAlign(two, 6, 'center')
  assert.deepEqual(centred.aligns, ['left', 'center'])
  // joining the lines back keeps the first one's alignment
  assert.deepEqual(replaceRange(centred, 5, 6, []).aligns, ['left'])
})

test("a span's classes give back the style they were made from, for every colour and format combination", () => {
  const classes = (s) => styleClass(s).split(' ').filter(Boolean)
  assert.deepEqual(classes(PLAIN), []) // plain is no class at all: the box's gray
  for (const c of [null, ...'0123456789abcdef']) {
    for (let mask = 0; mask < 1 << FORMATS.length; mask++) {
      const style = { ...PLAIN, c }
      FORMATS.forEach((f, i) => (style[f] = Boolean(mask & (1 << i))))
      assert.deepEqual(styleFromClasses(classes(style)), style)
    }
  }
  assert.equal(styleClass({ ...PLAIN, c: '6', l: true }), 'mc-c-6 mc-l')
})

test('a paste with codes in it arrives styled; one without takes the style it lands in', () => {
  assert.deepEqual(look({ items: pastedItems('§6Go §lB', PLAIN) }), ['G:6', 'o:6', ' :6', 'B:6l'])
  const gold = { ...PLAIN, c: '6' }
  assert.deepEqual(look({ items: pastedItems('hi', gold) }), ['h:6', 'i:6'])
  assert.deepEqual(look({ items: pastedItems('{modpack}', PLAIN) }), ['{modpack}:-'])
})
