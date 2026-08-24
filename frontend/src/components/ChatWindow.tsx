import { useEffect, useRef } from 'react'
import { useAppSelector } from '../store/hooks'
import ChartRenderer from './ChartRenderer'
import SqlResultTable from './SqlResultTable'
import FoodItemPicker from './shopping/FoodItemPicker'

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
      {messages.map((message) => (
        <div key={message.id} className={`message ${message.role} ${message.queued ? 'queued' : ''}`}>
          {message.text}
          {message.chartSeries && message.chartSeries.length > 0 && <ChartRenderer series={message.chartSeries} />}
          {message.sqlAnswer && <SqlResultTable answer={message.sqlAnswer} />}
          {message.foodItemOptions && message.foodItemOptions.length > 0 && (
            <FoodItemPicker options={message.foodItemOptions} />
          )}
          {message.queued && <div className="queued-caption">queued · waiting for network</div>}
        </div>
      ))}
    </div>
  )
}
