import { useRef, useState } from 'react'
import { useAppDispatch } from '../store/hooks'
import { setDraftTextWithCursorStart } from '../store/chatSlice'
import { openUpcPrebind } from '../store/foodItemsSlice'
import { setScreen } from '../store/uiSlice'

interface DecodeResponse {
  upc: string
  resolvedName: string | null
  needsManualEntry: boolean
}

// `capture="environment"` opens the device's rear camera directly on mobile - no getUserMedia/
// live-video scanning UI needed, since the backend already does single-image server-side decode
// (ZXing) and identity resolution (cache/Open Food Facts/FDC Branded) via POST /api/barcode/decode.
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
      const { upc, resolvedName, needsManualEntry }: DecodeResponse = await response.json()
      setStatus('idle')

      if (needsManualEntry || !resolvedName) {
        // Nothing resolved anywhere - the manual-entry modal on the Food Items page is the
        // terminus, prebound with this UPC so the label can be typed in directly.
        dispatch(openUpcPrebind(upc))
        dispatch(setScreen('foodItems'))
        return
      }

      // Identity resolved server-side and is guaranteed an exact alias hit - prefill the amount
      // field with the resolved name, cursor at the start, instead of round-tripping the raw UPC
      // digits through a chat turn.
      dispatch(setDraftTextWithCursorStart(`g ${resolvedName}`))
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
