import { useAppSelector } from '../store/hooks'

export default function ChatWindow() {
  const messages = useAppSelector((state) => state.chat.messages)

  return (
    <div className="chat-window">
      {messages.map((message, index) => (
        <div key={index} className={`message ${message.role}`}>
          {message.text}
        </div>
      ))}
    </div>
  )
}
