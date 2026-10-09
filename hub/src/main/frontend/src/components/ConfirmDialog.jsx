import { Dialog } from './Dialog'
import { ErrorNote } from './ErrorNote'

/** A styled stand-in for window.confirm, for actions worth getting the dashboard's own look on. */
export function ConfirmDialog({ title, children, confirmLabel = 'Confirm', danger, busy, error, onConfirm, onCancel }) {
  return (
    <Dialog onClose={onCancel}>
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault()
          onConfirm()
        }}
      >
        <h2>{title}</h2>
        <p className="muted">{children}</p>
        <ErrorNote>{error}</ErrorNote>
        <div className="form-actions">
          <button type="submit" className={`button${danger ? ' button-danger' : ' button-primary'}`} disabled={busy} autoFocus={!danger}>
            {confirmLabel}
          </button>
          <button type="button" className="button" onClick={onCancel} disabled={busy} autoFocus={danger}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  )
}
