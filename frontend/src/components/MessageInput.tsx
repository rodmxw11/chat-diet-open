import { useEffect, useRef } from 'react'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { sendMessage, setDraftText } from '../store/chatSlice'
import { loadSummary } from '../store/summarySlice'

export default function MessageInput() {
  const dispatch = useAppDispatch()
  const text = useAppSelector((state) => state.chat.draftText)
  const status = useAppSelector((state) => state.chat.status)
  const inputRef = useRef<HTMLInputElement>(null)
  const wasLoading = useRef(false)

  // The input is disabled while a request is in flight, which blurs it; once the reply lands and
  // it's re-enabled, focus doesn't come back on its own, so bring it back here - but only on
  // devices with a real keyboard. On a touchscreen, focusing a text input reopens the on-screen
  // keyboard, which is exactly what the user just dismissed by hitting enter/send.
  useEffect(() => {
    const hasCoarsePointer = window.matchMedia('(pointer: coarse)').matches
    if (wasLoading.current && status !== 'loading' && !hasCoarsePointer) {
      inputRef.current?.focus()
    }
    wasLoading.current = status === 'loading'
  }, [status])

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || status === 'loading') return
    // The header's calorie/entry counts are polled independently on a minute-long interval, which
    // reads as "stuck" right after logging something - refresh it the moment this turn settles
    // instead of waiting for the next poll tick.
    dispatch(sendMessage(trimmed)).finally(() => dispatch(loadSummary()))
    dispatch(setDraftText(''))
  }

  return (
    <form className="message-input" onSubmit={submit}>
      <div className="input-pill">
        <input
          ref={inputRef}
          value={text}
          onChange={(event) => dispatch(setDraftText(event.target.value))}
          placeholder="Say something..."
          disabled={status === 'loading'}
        />
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
      </div>
      <button type="submit" className="send-button" disabled={status === 'loading'}>
        Send
      </button>
    </form>
  )
}
