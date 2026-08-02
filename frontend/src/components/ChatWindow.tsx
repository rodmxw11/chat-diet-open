import { useAppSelector } from '../store/hooks'
import ChartRenderer from './ChartRenderer'
import SqlResultTable from './SqlResultTable'

export default function ChatWindow() {
  const messages = useAppSelector((state) => state.chat.messages)

  return (
    <div className="chat-window">
      {messages.map((message, index) => (
        <div key={index} className={`message ${message.role}`}>
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
