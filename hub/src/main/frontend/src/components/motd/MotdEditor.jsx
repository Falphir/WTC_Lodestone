import { useEffect, useRef, useState } from 'react'
import { lineAt, parseMotd, PLAIN, pastedItems, replaceRange, serializeMotd, setAlign, styleAt, styleClass, styleFromClasses, styleRange } from '../../motd'
import { MotdToolbar } from './MotdToolbar'

// The DOM side of the editor. The box is drawn from the model by hand, not by React, which would fight the
// browser over a contentEditable's contents. Each run is a span with the class mc-run, and its mc-c-* and
// mc-l-style classes are both how it looks and which codes it stands for: reading the box back after
// typing goes by them. A run with no colour or format class is plain.

const RUN = 'mc-run'
const runClass = (style) => [RUN, styleClass(style)].filter(Boolean).join(' ')

function span(style, text, variable) {
  const el = document.createElement('span')
  el.className = variable ? `${runClass(style)} motd-var` : runClass(style)
  if (variable) {
    el.dataset.var = variable
    el.contentEditable = 'false'
  }
  el.textContent = text
  return el
}

/** Draws the model into the box: a div per line, a span per run of one style, a tag per variable. */
function draw(box, model) {
  box.replaceChildren()
  let line = null
  const newLine = (i) => {
    line = document.createElement('div')
    line.className = 'mc-line'
    line.dataset.align = model.aligns[i] ?? 'left'
    box.append(line)
  }
  newLine(0)
  let lineIndex = 0
  for (const item of model.items) {
    if (item.br) {
      if (!line.hasChildNodes()) line.append(document.createElement('br'))
      newLine(++lineIndex)
      continue
    }
    const last = line.lastChild
    if (item.v) line.append(span(item.s, item.v, item.v))
    else if (last && !last.dataset?.var && last.className === runClass(item.s)) last.textContent += item.t
    else line.append(span(item.s, item.t))
  }
  // an empty line needs something in it to have a height and take the caret
  if (!line.hasChildNodes()) line.append(document.createElement('br'))
}

/** Reads the box back after typing. Text typed outside any of our spans takes the style of what's before it. */
function read(box) {
  const aligns = []
  const items = []
  const walk = (node, style) => {
    if (node.nodeType === Node.TEXT_NODE) {
      // browsers keep typed spaces as no-break spaces so they don't collapse
      const own = style ?? (items.length && !items[items.length - 1].br ? items[items.length - 1].s : PLAIN)
      for (const t of node.textContent.replace(/\u00a0/g, ' ').replace(/\u200b/g, '')) items.push({ t, s: own })
      return
    }
    if (node.nodeType !== Node.ELEMENT_NODE || node.tagName === 'BR') return
    const run = node.classList.contains(RUN)
    if (node.dataset.var) {
      items.push({ v: node.dataset.var, s: run ? styleFromClasses(node.classList) : PLAIN })
      return
    }
    // anything else (a browser's own wrapper, say) changes nothing: what's inside keeps the run it's in
    const own = run ? styleFromClasses(node.classList) : style
    node.childNodes.forEach((child) => walk(child, own))
  }
  box.childNodes.forEach((node) => {
    const isLine = node.nodeType === Node.ELEMENT_NODE && node.classList.contains('mc-line')
    if (isLine || aligns.length === 0) {
      if (aligns.length > 0) items.push({ br: true })
      aligns.push(isLine ? node.dataset.align || 'left' : 'left')
    }
    if (isLine) node.childNodes.forEach((child) => walk(child, null))
    else walk(node, null)
  })
  if (aligns.length === 0) aligns.push('left')
  return { aligns, items }
}

/** How many model positions a piece of the box holds: a character or a tag each, and one per line break. */
function units(node) {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent.replace(/\u200b/g, '').length
  if (node.nodeType !== Node.ELEMENT_NODE && node.nodeType !== Node.DOCUMENT_FRAGMENT_NODE) return 0
  if (node.dataset?.var) return 1
  let total = 0
  let lines = 0
  node.childNodes.forEach((child) => {
    if (child.classList?.contains('mc-line')) lines++
    total += units(child)
  })
  return total + Math.max(0, lines - 1)
}

/** A point in the box (as a selection gives it) as a model offset. */
function offsetOf(box, node, offset) {
  const range = document.createRange()
  range.selectNodeContents(box)
  range.setEnd(node, offset)
  return units(range.cloneContents())
}

/** A model offset as a point in the box, for putting the selection back. Only for a box `draw` made. */
function pointAt(box, offset) {
  const lines = [...box.children]
  for (const line of lines) {
    const length = units(line)
    if (offset > length) {
      offset -= length + 1
      continue
    }
    for (let i = 0; i < line.childNodes.length; i++) {
      const child = line.childNodes[i]
      if (child.tagName === 'BR') break
      if (child.dataset?.var) {
        if (offset === 0) return [line, i]
        offset -= 1
        continue
      }
      const text = child.firstChild
      if (offset <= text.length) return [text, offset]
      offset -= text.length
    }
    return [line, line.childNodes.length && line.lastChild.tagName === 'BR' ? 0 : line.childNodes.length]
  }
  const last = lines[lines.length - 1]
  return [last, last.childNodes.length]
}

function select(box, start, end) {
  const range = document.createRange()
  range.setStart(...pointAt(box, start))
  range.setEnd(...pointAt(box, end))
  const selection = window.getSelection()
  selection.removeAllRanges()
  selection.addRange(range)
  box.focus()
}

/**
 * A MOTD you type into the way it will look: colours, styles and alignment show in place, variables are
 * tags, and codes never appear. What comes out (`onChange`) is the code text the hub and servers use.
 * "Edit as code" swaps in a plain text box for whoever wants the codes.
 */
export function MotdEditor({ value, onChange, labelledBy }) {
  const box = useRef(null)
  const model = useRef(parseMotd(value))
  // the text this editor last handed out, so its own changes aren't drawn over the caret again
  const emitted = useRef(null)
  const [asCode, setAsCode] = useState(false)
  const [caretLine, setCaretLine] = useState(0)

  useEffect(() => {
    if (asCode || !box.current) return
    if (value !== emitted.current || !box.current.hasChildNodes()) {
      model.current = parseMotd(value)
      draw(box.current, model.current)
      emitted.current = value
    }
  }, [value, asCode])

  // the toolbar shows how the caret's line is aligned
  useEffect(() => {
    const follow = () => {
      const range = selectionRange()
      if (range) setCaretLine(lineAt(model.current, range[0]))
    }
    document.addEventListener('selectionchange', follow)
    return () => document.removeEventListener('selectionchange', follow)
  })

  function emit(next) {
    model.current = next
    const text = serializeMotd(next)
    emitted.current = text
    onChange(text)
  }

  function selectionRange() {
    const selection = window.getSelection()
    if (!box.current || !selection.rangeCount || !box.current.contains(selection.anchorNode)) return null
    const range = selection.getRangeAt(0)
    return [offsetOf(box.current, range.startContainer, range.startOffset), offsetOf(box.current, range.endContainer, range.endOffset)]
  }

  /** A change from a button, a key or a paste: into the model, drawn, and the selection put back. */
  function change(make) {
    const range = selectionRange() ?? [model.current.items.length, model.current.items.length]
    const result = make(model.current, ...range)
    if (!result) return
    emit(result.model)
    draw(box.current, result.model)
    select(box.current, result.start, result.end)
  }

  const restyle = (how) => change((m, start, end) => start !== end && { model: styleRange(m, start, end, how), start, end })

  const insert = (items) => (m, start, end) => {
    const next = replaceRange(m, start, end, items)
    return next && { model: next, start: start + items.length, end: start + items.length }
  }

  function keyDown(event) {
    if (event.key === 'Enter') {
      event.preventDefault()
      change(insert([{ br: true }])) // refused on the last line: a MOTD has two
    }
  }

  function paste(event) {
    event.preventDefault()
    const text = event.clipboardData.getData('text/plain')
    change((m, start, end) => {
      const items = pastedItems(text, styleAt(m, start))
      // a paste with more lines than fit has its breaks become spaces
      return insert(items)(m, start, end) ?? insert(items.map((i) => (i.br ? { t: ' ', s: styleAt(m, start) } : i)))(m, start, end)
    })
  }

  return (
    <div className="motd-editor">
      {!asCode && (
        <MotdToolbar
          align={model.current.aligns[caretLine] ?? 'left'}
          onColor={(c) => restyle({ color: c })}
          onFormat={(f) => restyle({ format: f })}
          onReset={() => restyle({ reset: true })}
          onAlign={(a) => change((m, start, end) => ({ model: setAlign(m, start, a), start, end }))}
          onVariable={(v) => change((m, start, end) => insert([{ v, s: styleAt(m, start) }])(m, start, end))}
        />
      )}
      {asCode ? (
        <textarea
          className="mono"
          aria-labelledby={labelledBy}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          rows={3}
          maxLength={300}
          wrap="off"
        />
      ) : (
        <div
          ref={box}
          className="mc-text motd-editor-box"
          contentEditable
          suppressContentEditableWarning
          role="textbox"
          aria-multiline="true"
          aria-labelledby={labelledBy}
          spellCheck={false}
          onInput={() => emit(read(box.current))}
          onKeyDown={keyDown}
          onPaste={paste}
        />
      )}
      <label className="check-field motd-code-toggle">
        <input type="checkbox" checked={asCode} onChange={(e) => setAsCode(e.target.checked)} />
        <span>Edit as code</span>
      </label>
    </div>
  )
}
