import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadNotes } from '../../store/notesSlice'

function formatLoggedAt(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

// Full-page view that replaces the chat screen entirely, same pattern as ShoppingModeView. Read-only:
// notes are logged and corrected via chat, this screen is just for browsing the full history.
export default function NotesView() {
  const dispatch = useAppDispatch()
  const notes = useAppSelector((state) => state.notes.items)
  const status = useAppSelector((state) => state.notes.status)

  useEffect(() => {
    dispatch(loadNotes())
  }, [dispatch])

  return (
    <div className="shop-screen">
      <header className="app-header">
        <button
          type="button"
          className="back-button"
          onClick={() => dispatch(setScreen('chat'))}
          aria-label="Back to chat"
        >
          ←
        </button>
        <div className="shop-header-text">
          <span className="shop-title">Notes</span>
          <span className="shop-progress">{notes.length} total</span>
        </div>
      </header>
      <div className="shop-list-wrapper">
        {status === 'loading' && notes.length === 0 && <p className="shop-empty">Loading…</p>}
        {status === 'error' && <p className="shop-empty">Couldn't load notes.</p>}
        {status !== 'loading' && notes.length === 0 && (
          <p className="shop-empty">No notes yet.</p>
        )}
        <ul className="notes-list">
          {notes.map((note) => (
            <li key={note.id} className="note-card">
              <p className="note-text">{note.text}</p>
              <span className="note-timestamp">{formatLoggedAt(note.loggedAt)}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
