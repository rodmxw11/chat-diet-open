import { useRef, useState } from 'react'
import { useAppDispatch } from '../store/hooks'
import { setDraftTextWithCursorStart } from '../store/chatSlice'
import { openUpcPrebind } from '../store/foodItemsSlice'
import { setScreen } from '../store/uiSlice'
import BarcodeIcon from './BarcodeIcon'
import BarcodeScannerOverlay from './BarcodeScannerOverlay'

interface ResolveResponse {
  upc: string
  resolvedName: string | null
  needsManualEntry: boolean
}

// Chrome/Android (incl. this app's target Pixel 8) supports live client-side barcode detection;
// Safari and Firefox don't. Feature-detected once at click time rather than UA-sniffed, so the
// fallback path automatically covers every unsupported browser without needing to track which
// ones those are.
const hasLiveScanner = () => 'BarcodeDetector' in window

// `capture="environment"` opens the device's rear camera app directly - the fallback path for
// browsers without BarcodeDetector, or if the live scanner's own camera permission gets denied.
// The backend does single-image server-side decode (ZXing) and identity resolution (cache/Open
// Food Facts/FDC Branded) via POST /api/barcode/decode. When the live scanner does the decoding
// client-side instead, only GET /api/barcode/resolve (identity resolution alone) is needed.
export default function BarcodeScanButton() {
  const dispatch = useAppDispatch()
  const inputRef = useRef<HTMLInputElement>(null)
  const [status, setStatus] = useState<'idle' | 'decoding' | 'error'>('idle')
  const [scannerOpen, setScannerOpen] = useState(false)

  const handleResolution = ({ upc, resolvedName, needsManualEntry }: ResolveResponse) => {
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
  }

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
      setStatus('idle')
      handleResolution(await response.json())
    } catch {
      setStatus('error')
    }
  }

  const onLiveDetected = async (upc: string) => {
    setScannerOpen(false)
    setStatus('decoding')
    try {
      const response = await fetch(`/api/barcode/resolve?upc=${encodeURIComponent(upc)}`)
      if (!response.ok) {
        setStatus('error')
        return
      }
      setStatus('idle')
      handleResolution(await response.json())
    } catch {
      setStatus('error')
    }
  }

  return (
    <>
      <button
        type="button"
        className={`icon-toggle ${status === 'error' ? 'active' : ''}`}
        onClick={() => (hasLiveScanner() ? setScannerOpen(true) : inputRef.current?.click())}
        disabled={status === 'decoding'}
        title={status === 'error' ? "Couldn't read a barcode from that photo - tap to try again" : 'Scan a barcode'}
        aria-label="Scan a barcode"
      >
        {status === 'decoding' ? '⏳' : <BarcodeIcon />}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={onFileSelected}
        style={{ display: 'none' }}
      />
      <BarcodeScannerOverlay
        open={scannerOpen}
        onClose={() => setScannerOpen(false)}
        onDetected={onLiveDetected}
        onFallbackToPhoto={() => {
          setScannerOpen(false)
          inputRef.current?.click()
        }}
      />
    </>
  )
}
