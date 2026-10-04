/** The card container used everywhere: tables, forms, notices. `as` picks the tag (div/form/section)
 * since the same look wraps all three; `pad` adds the inner padding forms/notices need but tables don't. */
export function Panel({ as: As = 'div', pad, className = '', children, ...rest }) {
  const cls = ['panel', pad && 'panel-pad', className].filter(Boolean).join(' ')
  return (
    <As className={cls} {...rest}>
      {children}
    </As>
  )
}
