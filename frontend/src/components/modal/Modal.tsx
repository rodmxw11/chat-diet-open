import { useEffect, useRef, type ReactNode } from 'react'

interface ModalProps {
  open: boolean
  onClose: () => void
  variant: 'modal' | 'sheet'
  title: string
  children: ReactNode
  footer?: ReactNode
}

// Wraps the native <dialog> element for its free focus-trap, Esc-to-close, and top-layer
// stacking. `variant` picks modal vs. sheet presentation, but a CSS media query forces sheet
// presentation under the mobile breakpoint regardless of the prop, so callers don't need to
// branch on viewport size themselves.
export default function Modal({ open, onClose, variant, title, children, footer }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) {
      try {
        dialog.showModal()
      } catch (error) {
        // Fall back to non-modal `show()` so content is at least reachable (no focus-trap/
        // backdrop in this path, but that's better than the dialog silently never appearing).
        console.error('dialog.showModal() failed, falling back to show():', error)
        dialog.show()
      }
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  return (
    <dialog
      ref={dialogRef}
      className={`app-modal app-modal--${variant}`}
      onClose={onClose}
      onCancel={onClose}
      onClick={(event) => {
        // Click on the ::backdrop area lands directly on the <dialog> element itself.
        if (event.target === dialogRef.current) onClose()
      }}
    >
      <div className="app-modal-header">
        <h2 className="app-modal-title">{title}</h2>
        <button type="button" className="app-modal-close" onClick={onClose} aria-label="Close">
          ✕
        </button>
      </div>
      <div className="app-modal-body">{children}</div>
      {footer && <div className="app-modal-footer">{footer}</div>}
    </dialog>
  )
}
