/** A confirmation that something worked. The success-side twin of ErrorNote. */
export function Note({ children }) {
  if (!children) return null
  return (
    <p className="note" role="status">
      {children}
    </p>
  )
}
