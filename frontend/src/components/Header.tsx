import { useAppDispatch, useAppSelector } from '../store/hooks'
import { startNewSession, toggleTts } from '../store/chatSlice'

export default function Header() {
  const dispatch = useAppDispatch()
  const ttsEnabled = useAppSelector((state) => state.chat.ttsEnabled)

  return (
    <header className="app-header">
      <span className="app-title">chat-diet</span>
      <div className="app-header-actions">
        <button
          type="button"
          className={`icon-toggle ${ttsEnabled ? 'active' : ''}`}
          onClick={() => dispatch(toggleTts())}
          aria-pressed={ttsEnabled}
          title={ttsEnabled ? 'Voice replies on' : 'Voice replies off'}
        >
          {ttsEnabled ? '🔊' : '🔇'}
        </button>
        <button
          type="button"
          className="new-session-button"
          onClick={() => {
            window.speechSynthesis?.cancel()
            dispatch(startNewSession())
          }}
        >
          New Session
        </button>
      </div>
    </header>
  )
}
