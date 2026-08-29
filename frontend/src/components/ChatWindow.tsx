import { useEffect, useRef } from 'react'
import { useAppSelector } from '../store/hooks'
import ChartRenderer from './ChartRenderer'
import SqlResultTable from './SqlResultTable'

export default function ChatWindow() {
  const messages = useAppSelector((state) => state.chat.messages)
  const ttsEnabled = useAppSelector((state) => state.chat.ttsEnabled)
  const hidden = useAppSelector((state) => state.chat.hidden)
  const hiddenSinceMessageId = useAppSelector((state) => state.chat.hiddenSinceMessageId)
  const lastSpokenIndex = useRef(-1)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!ttsEnabled || messages.length === 0) return
    const lastIndex = messages.length - 1
    const last = messages[lastIndex]
    if (last.role !== 'assistant' || lastIndex === lastSpokenIndex.current) return

    lastSpokenIndex.current = lastIndex
    window.speechSynthesis?.speak(new SpeechSynthesisUtterance(last.text))
  }, [messages, ttsEnabled])

  let visibleMessages = messages
  if (hidden) {
    const cutoffIndex = hiddenSinceMessageId ? messages.findIndex((m) => m.id === hiddenSinceMessageId) : -1
    visibleMessages = cutoffIndex === -1 ? [] : messages.slice(cutoffIndex + 1)
  }

  // Runs after every render that could change what's on screen, not just message count, so
  // toggling history back on (same message count, wildly different scrollHeight) also jumps down.
  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [visibleMessages.length, hidden, messages.length])

  return (
    <div className="chat-window" ref={scrollRef}>
      {hidden && visibleMessages.length === 0 && (
        <p className="chat-hidden-notice">Chat history is hidden. New replies will appear here.</p>
      )}
      {visibleMessages.map((message) => (
        <div key={message.id} className={`message ${message.role} ${message.queued ? 'queued' : ''}`}>
          {message.text}
          {message.chartSeries && message.chartSeries.length > 0 && <ChartRenderer series={message.chartSeries} />}
          {message.sqlAnswer && <SqlResultTable answer={message.sqlAnswer} />}
          {message.queued && <div className="queued-caption">queued · waiting for network</div>}
        </div>
      ))}
    </div>
  )
}
