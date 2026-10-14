/** Every page's title row: heading block on the left, actions (a button, a status) on the right. */
export function PageHead({ children, actions }) {
  return (
    <header className="page-head">
      <div>{children}</div>
      {actions}
    </header>
  )
}
