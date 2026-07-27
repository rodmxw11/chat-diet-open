import { useState } from 'react'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { sendMessage } from '../store/chatSlice'

export default function MessageInput() {
  const [text, setText] = useState('')
  const dispatch = useAppDispatch()
  const status = useAppSelector((state) => state.chat.status)

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || status === 'loading') return
    dispatch(sendMessage(trimmed))
    setText('')
  }

  return (
    <form className="message-input" onSubmit={submit}>
      <input
        value={text}
        onChange={(event) => setText(event.target.value)}
        placeholder="Say something..."
        disabled={status === 'loading'}
      />
      <button type="submit" disabled={status === 'loading'}>
        Send
      </button>
    </form>
  )
}
