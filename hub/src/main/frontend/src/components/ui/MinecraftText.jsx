import { parseMotd, sameStyle, styleClass } from '../../motd'

/** A model's lines, each as runs of one style; a variable still in the text shows as {name}. */
function linesOf(model) {
  const lines = [[]]
  for (const item of model.items) {
    if (item.br) {
      lines.push([])
      continue
    }
    const text = item.v ? `{${item.v}}` : item.t
    const line = lines[lines.length - 1]
    const last = line[line.length - 1]
    if (last && sameStyle(last.s, item.s)) last.text += text
    else line.push({ text, s: item.s })
  }
  return lines
}

/**
 * Text with Minecraft formatting codes as the server list would show it, on its dark background, in a box
 * about as wide as the server list's MOTD. A line starting {center} or {right} is lined up with CSS: what
 * it's meant to look like, whatever this font, while servers get the hub's spaces that do the same in theirs.
 */
export function MinecraftText({ text }) {
  const model = parseMotd(text ?? '')
  return (
    <div className="mc-text">
      {linesOf(model).map((line, i) => (
        <div key={i} className="mc-line" data-align={model.aligns[i]}>
          {line.length === 0
            ? ' '
            : line.map((run, j) => (
                <span key={j} className={styleClass(run.s) || undefined}>
                  {run.text}
                </span>
              ))}
        </div>
      ))}
    </div>
  )
}
