import { useRef, useState } from 'react'
import { useAppDispatch } from '../store/hooks'
import { sendMessage } from '../store/chatSlice'
import { loadSummary } from '../store/summarySlice'

interface DecodeResponse {
  upc: string
}

// `capture="environment"` opens the device's rear camera directly on mobile - no getUserMedia/
// live-video scanning UI needed, since the backend already does single-image server-side decode
// (ZXing) via the existing (previously unused) POST /api/barcode/decode endpoint.
export default function BarcodeScanButton() {
  const dispatch = useAppDispatch()
  const inputRef = useRef<HTMLInputElement>(null)
  const [status, setStatus] = useState<'idle' | 'decoding' | 'error'>('idle')

  const onFileSelected = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setStatus('decoding')
    try {
      const formData = new FormData()
      formData.append('image', file)
      const response = await fetch('/api/barcode/decode', { method: 'POST', body: formData })
      if (!response.ok) {
        setStatus('error')
        return
      }
      const { upc }: DecodeResponse = await response.json()
      setStatus('idle')
      // Round-trips through chat as the UPC digits, staying consistent with the model-drives-all-
      // writes architecture: this is exactly what typing/speaking the barcode already does.
      dispatch(sendMessage(upc)).finally(() => dispatch(loadSummary()))
    } catch {
      setStatus('error')
    }
  }

  return (
    <>
      <button
        type="button"
        className={`icon-toggle ${status === 'error' ? 'active' : ''}`}
        onClick={() => inputRef.current?.click()}
        disabled={status === 'decoding'}
        title={status === 'error' ? "Couldn't read a barcode from that photo - tap to try again" : 'Scan a barcode'}
        aria-label="Scan a barcode"
      >
        {status === 'decoding' ? '⏳' : '📷'}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={onFileSelected}
        style={{ display: 'none' }}
      />
    </>
  )
}
