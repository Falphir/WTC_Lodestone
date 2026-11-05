/** Minecraft's sixteen colours, by their code. */
const COLORS = {
  0: '#000000', 1: '#0000aa', 2: '#00aa00', 3: '#00aaaa', 4: '#aa0000', 5: '#aa00aa', 6: '#ffaa00', 7: '#aaaaaa',
  8: '#555555', 9: '#5555ff', a: '#55ff55', b: '#55ffff', c: '#ff5555', d: '#ff55ff', e: '#ffff55', f: '#ffffff',
}

/** A server list's MOTD starts gray. */
const PLAIN = { color: COLORS[7], bold: false, italic: false, underline: false, strike: false }

/** Splits text with § or & formatting codes into lines of styled runs, the way the server list reads them. */
function parse(text) {
  const lines = [[]]
  let style = PLAIN
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    const code = text[i + 1]?.toLowerCase()
    if ((ch === '§' || ch === '&') && code && /[0-9a-fk-or]/.test(code)) {
      i++
      // a colour also clears bold and the rest, as in the game; r goes back to the start
      if (code in COLORS) style = { ...PLAIN, color: COLORS[code] }
      else if (code === 'r') style = PLAIN
      else if (code === 'l') style = { ...style, bold: true }
      else if (code === 'o') style = { ...style, italic: true }
      else if (code === 'n') style = { ...style, underline: true }
      else if (code === 'm') style = { ...style, strike: true }
      continue // k (obfuscated) is shown as the plain text it scrambles
    }
    if (ch === '\n') {
      lines.push([])
      continue
    }
    const line = lines[lines.length - 1]
    const last = line[line.length - 1]
    if (last && last.style === style) last.text += ch
    else line.push({ text: ch, style })
  }
  return lines
}

/**
 * Text with Minecraft formatting codes as the server list would show it, on its dark background. The
 * font is only close to Minecraft's, so spacing that centres a line in the game is near, not exact.
 */
export function MinecraftText({ text }) {
  return (
    <div className="mc-text">
      {parse(text ?? '').map((line, i) => (
        <div key={i} className="mc-line">
          {line.length === 0
            ? ' '
            : line.map((run, j) => (
                <span
                  key={j}
                  style={{
                    color: run.style.color,
                    fontWeight: run.style.bold ? 700 : undefined,
                    fontStyle: run.style.italic ? 'italic' : undefined,
                    textDecoration: [run.style.underline && 'underline', run.style.strike && 'line-through'].filter(Boolean).join(' ') || undefined,
                  }}
                >
                  {run.text}
                </span>
              ))}
        </div>
      ))}
    </div>
  )
}
