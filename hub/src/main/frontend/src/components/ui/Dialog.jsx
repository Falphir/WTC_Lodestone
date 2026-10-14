/**
 * A centered modal <dialog> (showModal()) -- Escape-to-close, focus trap and the backdrop all come
 * free from the browser. Render it conditionally from the parent (it opens itself on mount); give
 * it the same `onClose` the parent would use to stop rendering it, and reuse that same prop for any
 * Cancel button inside -- closing is just "the parent stops rendering this", nothing to call back.
 */
export function Dialog({ onClose, className = '', children }) {
  return (
    <dialog
      ref={(node) => node && !node.open && node.showModal()}
      className={`dialog${className ? ` ${className}` : ''}`}
      onClose={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      {children}
    </dialog>
  )
}
