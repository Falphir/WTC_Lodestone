/** The dashed placeholder shown instead of a table/list when there's nothing in it yet. */
export function EmptyState({ title, children }) {
  return (
    <div className="empty">
      <h2>{title}</h2>
      <p>{children}</p>
    </div>
  )
}
