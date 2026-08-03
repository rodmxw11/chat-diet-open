import { useRef, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { appendDraftText, sendPhoto, startNewSession, toggleTts } from '../store/chatSlice'

const SpeechRecognitionCtor = window.SpeechRecognition ?? window.webkitSpeechRecognition

export default function Header() {
  const dispatch = useAppDispatch()
  const ttsEnabled = useAppSelector((state) => state.chat.ttsEnabled)
  const status = useAppSelector((state) => state.chat.status)
  const photoInputRef = useRef<HTMLInputElement>(null)
  const recognitionRef = useRef<SpeechRecognition | null>(null)
  const [listening, setListening] = useState(false)

  const handlePhotoSelected = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    dispatch(sendPhoto({ file, previewUrl: URL.createObjectURL(file) }))
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

  return (
    <header className="app-header">
      <div className="app-header-left">
        <span className="app-title">chat-diet</span>
        <button
          type="button"
          className="icon-toggle"
          onClick={() => photoInputRef.current?.click()}
          disabled={status === 'loading'}
          title="Take a photo of food"
          aria-label="Take a photo of food"
        >
          📷
        </button>
        <input
          ref={photoInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          className="visually-hidden"
          onChange={handlePhotoSelected}
        />
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
      </div>
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
