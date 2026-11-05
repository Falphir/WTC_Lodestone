import { COLORS, VARIABLES } from '../../motd'
import { Icon } from '../ui/Icon'

const FORMATS = [
  ['l', 'B', 'Bold', { fontWeight: 700 }],
  ['o', 'I', 'Italic', { fontStyle: 'italic' }],
  ['n', 'U', 'Underline', { textDecoration: 'underline' }],
  ['m', 'S', 'Strikethrough', { textDecoration: 'line-through' }],
]

const ALIGNS = [
  ['left', 'alignLeft', 'Align line left'],
  ['center', 'alignCenter', 'Centre line'],
  ['right', 'alignRight', 'Align line right'],
]

/**
 * The MOTD editor's buttons: colours and styles for the selected text, alignment for the caret's line
 * (the current one shown pressed), and the variables. None of them take focus, so the editor's
 * selection is still there when the click lands.
 */
export function MotdToolbar({ align, onColor, onFormat, onReset, onAlign, onVariable }) {
  // mousedown would move focus off the editor and lose its selection before the click lands
  const keepSelection = (e) => e.preventDefault()

  return (
    <div className="motd-toolbar" role="toolbar" aria-label="MOTD formatting">
      <div className="motd-swatches">
        {Object.entries(COLORS).map(([code, name]) => (
          <button
            key={code}
            type="button"
            className={`motd-swatch mc-c-${code}`}
            title={name}
            aria-label={name}
            onMouseDown={keepSelection}
            onClick={() => onColor(code)}
          />
        ))}
      </div>
      <div className="motd-tools">
        {FORMATS.map(([code, letter, name, look]) => (
          <button key={code} type="button" className="icon-button" title={name} aria-label={name} onMouseDown={keepSelection} onClick={() => onFormat(code)}>
            <span style={look}>{letter}</span>
          </button>
        ))}
        <button type="button" className="icon-button" title="Plain text" aria-label="Plain text" onMouseDown={keepSelection} onClick={onReset}>
          <span>R</span>
        </button>
        <span className="motd-tools-gap" />
        {ALIGNS.map(([a, icon, name]) => (
          <button
            key={a}
            type="button"
            className="icon-button"
            title={name}
            aria-label={name}
            aria-pressed={align === a}
            onMouseDown={keepSelection}
            onClick={() => onAlign(a)}
          >
            <Icon name={icon} />
          </button>
        ))}
        <span className="motd-tools-gap" />
        {VARIABLES.map((variable) => (
          <button
            key={variable}
            type="button"
            className="button button-small"
            title={`Insert the server's ${variable}`}
            onMouseDown={keepSelection}
            onClick={() => onVariable(variable)}
          >
            + {variable}
          </button>
        ))}
      </div>
    </div>
  )
}
