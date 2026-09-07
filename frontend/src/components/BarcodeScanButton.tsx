import { useRef, useState } from 'react'
import { useAppDispatch } from '../store/hooks'
import { applyUpcResolution, resolveUpc } from '../store/barcodeQueueSlice'
import BarcodeIcon from './BarcodeIcon'
import BarcodeScannerOverlay from './BarcodeScannerOverlay'

// Chrome/Android (incl. this app's target Pixel 8) supports live client-side barcode detection;
// Safari and Firefox don't. Feature-detected once at click time rather than UA-sniffed, so the
// fallback path automatically covers every unsupported browser without needing to track which
// ones those are.
const hasLiveScanner = () => 'BarcodeDetector' in window

// `capture="environment"` opens the device's rear camera app directly - the fallback path for
// browsers without BarcodeDetector, or if the live scanner's own camera permission gets denied.
// The backend does single-image server-side decode (ZXing) and identity resolution (cache/Open
// Food Facts/FDC Branded) via POST /api/barcode/decode. When the live scanner does the decoding
// client-side instead, only GET /api/barcode/resolve (identity resolution alone) is needed - and
// that path, unlike this one, queues in IndexedDB on a network failure (see barcodeQueueSlice)
// rather than just erroring, since a bare UPC is cheap to hold onto and replay once back online.
export default function BarcodeScanButton() {
  const dispatch = useAppDispatch()
  const inputRef = useRef<HTMLInputElement>(null)
  const [status, setStatus] = useState<'idle' | 'decoding' | 'queued' | 'error'>('idle')
  const [scannerOpen, setScannerOpen] = useState(false)

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
      dispatch(applyUpcResolution(await response.json()))
    } catch {
      setStatus('error')
    }
  }

  const onLiveDetected = async (upc: string) => {
    setScannerOpen(false)
    setStatus('decoding')
    const result = await dispatch(resolveUpc(upc))
    setStatus(resolveUpc.rejected.match(result) ? (result.payload?.queued ? 'queued' : 'error') : 'idle')
  }

  const title = {
    idle: 'Scan a barcode',
    decoding: 'Looking it up…',
    queued: "You're offline - saved and will look it up once you're back online. Tap to scan another.",
    error: "Couldn't read that barcode - tap to try again",
  }[status]

  return (
    <>
      <button
        type="button"
        className={`icon-toggle ${status === 'error' || status === 'queued' ? 'active' : ''}`}
        onClick={() => (hasLiveScanner() ? setScannerOpen(true) : inputRef.current?.click())}
        disabled={status === 'decoding'}
        title={title}
        aria-label="Scan a barcode"
      >
        {status === 'decoding' ? '⏳' : status === 'queued' ? '📥' : <BarcodeIcon />}
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
