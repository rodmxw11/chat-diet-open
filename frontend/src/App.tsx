import { useEffect } from 'react'
import ChatWindow from './components/ChatWindow'
import Header from './components/Header'
import MessageInput from './components/MessageInput'
import { useAppDispatch } from './store/hooks'
import { loadHistory } from './store/chatSlice'

function App() {
  const dispatch = useAppDispatch()

  // The day's conversation lives on the server, so restore it rather than opening a blank chat.
  useEffect(() => {
    dispatch(loadHistory())
  }, [dispatch])

  return (
    <div className="app">
      <Header />
      <ChatWindow />
      <MessageInput />
    </div>
  )
}

export default App
