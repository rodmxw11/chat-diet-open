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
      {/* Always displayed rather than appearing with the first keystroke: a control that comes
          and goes shifts the input sideways and is hard to find when you want it. Disabled
          while empty so it reads as inert instead of merely doing nothing when pressed. */}
      <button
        type="button"
        className="clear-input-button"
        onClick={() => dispatch(setDraftText(''))}
        disabled={!text}
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
