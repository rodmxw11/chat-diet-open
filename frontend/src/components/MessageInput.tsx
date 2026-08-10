import { useAppDispatch, useAppSelector } from '../store/hooks'
import { sendMessage, setDraftText } from '../store/chatSlice'

export default function MessageInput() {
  const dispatch = useAppDispatch()
  const text = useAppSelector((state) => state.chat.draftText)
  const status = useAppSelector((state) => state.chat.status)

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || status === 'loading') return
    dispatch(sendMessage(trimmed))
    dispatch(setDraftText(''))
  }

  return (
    <form className="message-input" onSubmit={submit}>
      {/* Kept mounted but hidden when empty so the input doesn't shift sideways on the first
          keystroke; visibility:hidden also keeps it out of the tab order. */}
      <button
        type="button"
        className={`clear-input-button ${text ? '' : 'is-hidden'}`}
        onClick={() => dispatch(setDraftText(''))}
        title="Clear text"
        aria-label="Clear text"
      >
        ✕
      </button>
      <input
        value={text}
        onChange={(event) => dispatch(setDraftText(event.target.value))}
        placeholder="Say something..."
        disabled={status === 'loading'}
      />
      <button type="submit" disabled={status === 'loading'}>
        Send
      </button>
    </form>
  )
}
