export function ErrorNote({ children }) {
  if (!children) return null
  return (
    <p className="note note-error" role="alert">
      {children}
    </p>
  )
}
