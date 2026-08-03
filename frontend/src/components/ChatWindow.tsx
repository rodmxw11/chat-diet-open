import { useEffect, useRef } from 'react'
import { useAppSelector } from '../store/hooks'
import ChartRenderer from './ChartRenderer'
import SqlResultTable from './SqlResultTable'

export default function ChatWindow() {
  const messages = useAppSelector((state) => state.chat.messages)
  const ttsEnabled = useAppSelector((state) => state.chat.ttsEnabled)
  const lastSpokenIndex = useRef(-1)

  useEffect(() => {
    if (!ttsEnabled || messages.length === 0) return
    const lastIndex = messages.length - 1
    const last = messages[lastIndex]
    if (last.role !== 'assistant' || lastIndex === lastSpokenIndex.current) return

    lastSpokenIndex.current = lastIndex
    window.speechSynthesis?.speak(new SpeechSynthesisUtterance(last.text))
  }, [messages, ttsEnabled])

  return (
    <div className="chat-window">
      {messages.map((message, index) => (
        <div key={index} className={`message ${message.role}`}>
          {message.imageUrl && (
            <img className="message-photo" src={message.imageUrl} alt="Attached food photo" />
          )}
          {message.text}
          {message.chartSeries && message.chartSeries.length > 0 && (
            <ChartRenderer series={message.chartSeries} />
          )}
          {message.sqlAnswer && <SqlResultTable answer={message.sqlAnswer} />}
        </div>
      ))}
    </div>
  )
}
