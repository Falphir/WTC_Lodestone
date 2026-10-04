import { Icon } from './Icon'

/**
 * A labelled form field: wraps whatever <input>/<select> you pass as children with the shared
 * label/hint/icon chrome, since that part repeats everywhere but the input itself never does.
 * `hidden` keeps the label for screen readers only (visible placeholder text carries it instead).
 */
export function Field({ label, hidden, hint, inline, search, icon, children }) {
  const cls = ['field', inline && 'field-inline', search && 'field-search'].filter(Boolean).join(' ')
  return (
    <label className={cls}>
      <span className={hidden ? 'visually-hidden' : undefined}>{label}</span>
      {icon && <Icon name={icon} />}
      {children}
      {hint && <small>{hint}</small>}
    </label>
  )
}
