import { useRef, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { appendDraftText, toggleTts } from '../store/chatSlice'
import { openOverlay, setScreen, toggleMenu, closeMenu, toggleTheme } from '../store/uiSlice'
import StatusLight from './StatusLight'
import BarcodeScanButton from './BarcodeScanButton'

const SpeechRecognitionCtor = window.SpeechRecognition ?? window.webkitSpeechRecognition

export default function Header() {
  const dispatch = useAppDispatch()
  const ttsEnabled = useAppSelector((state) => state.chat.ttsEnabled)
  const status = useAppSelector((state) => state.chat.status)
  const summary = useAppSelector((state) => state.summary.data)
  const menuOpen = useAppSelector((state) => state.ui.menu)
  const range = useAppSelector((state) => state.dashboard.range)
  const queueCount = useAppSelector((state) => state.chat.queue.length + state.barcodeQueue.queue.length)
  const notesCount = useAppSelector((state) => state.notes.items.length)
  const theme = useAppSelector((state) => state.ui.theme)
  const recognitionRef = useRef<SpeechRecognition | null>(null)
  const [listening, setListening] = useState(false)

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
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('foodItems'))}>
              <span>Manage food items</span>
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
            <button type="button" role="menuitem" onClick={() => dispatch(setScreen('schema'))}>
              <span>Database schema</span>
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
