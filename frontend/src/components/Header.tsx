import { useRef, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { appendDraftText, hideChat, refreshHistory, showChat, toggleTts } from '../store/chatSlice'
import { openOverlay, setScreen, toggleMenu, closeMenu, toggleTheme } from '../store/uiSlice'
import StatusLight from './StatusLight'
import BarcodeScanButton from './BarcodeScanButton'

const SpeechRecognitionCtor = window.SpeechRecognition ?? window.webkitSpeechRecognition

// Plain emoji eyes (👁/🙈) render inconsistently across platforms - a hand-drawn eye vs. a monkey
// covering its face, at whatever weight the system emoji font happens to use. These are simple
// stroke-based eye / eye-with-slash icons instead, so both states read as the same glyph and the
// weight (strokeWidth) is ours to control.
function EyeIcon() {
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M1.5 12S5.5 5 12 5s10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12Z"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12" r="3.2" stroke="currentColor" strokeWidth="2.4" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M1.5 12S5.5 5 12 5s10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12Z"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12" r="3.2" stroke="currentColor" strokeWidth="2.4" />
      <line x1="2.5" y1="21.5" x2="21.5" y2="2.5" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  )
}

export default function Header() {
  const dispatch = useAppDispatch()
  const ttsEnabled = useAppSelector((state) => state.chat.ttsEnabled)
  const chatHidden = useAppSelector((state) => state.chat.hidden)
  const status = useAppSelector((state) => state.chat.status)
  const summary = useAppSelector((state) => state.summary.data)
  const menuOpen = useAppSelector((state) => state.ui.menu)
  const range = useAppSelector((state) => state.dashboard.range)
  const queueCount = useAppSelector((state) => state.chat.queue.length)
  const pendingShoppingCount = useAppSelector(
    (state) => state.shopping.items.filter((item) => item.status === 'PENDING').length,
  )
  const notesCount = useAppSelector((state) => state.notes.items.length)
  const theme = useAppSelector((state) => state.ui.theme)
  const recognitionRef = useRef<SpeechRecognition | null>(null)
  const [listening, setListening] = useState(false)

  const toggleChatHidden = () => {
    if (chatHidden) {
      // Un-hiding pulls in anything logged from another device while this one wasn't watching.
      dispatch(showChat())
      dispatch(refreshHistory())
    } else {
      dispatch(hideChat())
    }
  }

  const toggleListening = () => {
    if (!SpeechRecognitionCtor) return

    if (listening) {
      recognitionRef.current?.stop()
      return
    }

    const recognition = new SpeechRecognitionCtor()
    recognition.lang = navigator.language || 'en-US'
    recognition.interimResults = false
    recognition.continuous = false
    recognition.onresult = (event) => {
      const transcript = event.results[0][0].transcript.trim()
      if (transcript) dispatch(appendDraftText(transcript))
    }
    recognition.onerror = () => setListening(false)
    recognition.onend = () => setListening(false)

    recognitionRef.current = recognition
    setListening(true)
    recognition.start()
  }

  const goalHeadline =
    summary && summary.targetCalories !== null && summary.remainingCalories !== null
      ? `${summary.remainingCalories.toLocaleString()} left of ${summary.targetCalories.toLocaleString()}`
      : 'No goal set yet'
  const goalSub =
    summary && summary.targetCalories !== null
      ? `${summary.consumedCalories.toLocaleString()} eaten · ${summary.entryCount} ${summary.entryCount === 1 ? 'entry' : 'entries'}`
      : 'say "set my goal to 1800"'

  return (
    <header className="app-header">
      <span className="app-title">chatdiet</span>
      <div className="app-header-summary">
        <span className="goal-headline">{goalHeadline}</span>
        <span className="goal-sub">{goalSub}</span>
      </div>
      <StatusLight />
      <div className="app-header-menu-wrap">
        <button
          type="button"
          className="menu-button"
          onClick={() => dispatch(toggleMenu())}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-label="Menu"
        >
          ≡
        </button>
        {menuOpen && (
          <div className="menu-dropdown" role="menu">
            <button type="button" role="menuitem" onClick={() => dispatch(openOverlay('chartsMacros'))}>
              <span>Calories & macros</span>
              <span className="menu-hint">{range}d</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(openOverlay('chartsWeight'))}>
              <span>Weight trend</span>
              <span className="menu-hint">30d</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('shop'))}>
              <span>Shopping mode</span>
              <span className="menu-hint">{pendingShoppingCount} pending</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('notes'))}>
              <span>View notes</span>
              <span className="menu-hint">{notesCount}</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('foods'))}>
              <span>Daily foods</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('chatHistory'))}>
              <span>Chat history</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('micronutrients'))}>
              <span>Micronutrients</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(openOverlay('queue'))}>
              <span>Waiting to send</span>
              <span className="menu-hint">{queueCount}</span>
            </button>
            <button type="button" role="menuitem" onClick={() => dispatch(toggleTheme())}>
              <span>Dark mode</span>
              <span className="menu-hint">{theme === 'dark' ? 'On' : 'Off'}</span>
            </button>
          </div>
        )}
        {menuOpen && <div className="menu-backdrop" onClick={() => dispatch(closeMenu())} />}
      </div>
      <div className="app-header-actions">
        <button
          type="button"
          className={`icon-toggle ${chatHidden ? 'active' : ''}`}
          onClick={toggleChatHidden}
          aria-pressed={chatHidden}
          title={chatHidden ? 'Chat history hidden - tap to show it again' : 'Hide chat history'}
          aria-label={chatHidden ? 'Show chat history' : 'Hide chat history'}
        >
          {chatHidden ? <EyeOffIcon /> : <EyeIcon />}
        </button>
        {SpeechRecognitionCtor && (
          <button
            type="button"
            className={`icon-toggle ${listening ? 'active' : ''}`}
            onClick={toggleListening}
            disabled={status === 'loading'}
            aria-pressed={listening}
            title={listening ? 'Listening... tap to stop' : 'Speak a message'}
            aria-label={listening ? 'Listening... tap to stop' : 'Speak a message'}
          >
            {listening ? '🔴' : '🎤'}
          </button>
        )}
        <BarcodeScanButton />
        <button
          type="button"
          className={`icon-toggle ${ttsEnabled ? 'active' : ''}`}
          onClick={() => dispatch(toggleTts())}
          aria-pressed={ttsEnabled}
          title={ttsEnabled ? 'Voice replies on' : 'Voice replies off'}
        >
          {ttsEnabled ? '🔊' : '🔇'}
        </button>
      </div>
    </header>
  )
}
