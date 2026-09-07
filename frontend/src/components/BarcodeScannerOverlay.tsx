import { useEffect, useRef, useState } from 'react'

interface BarcodeScannerOverlayProps {
  open: boolean
  onClose: () => void
  onDetected: (upc: string) => void
  onFallbackToPhoto: () => void
}

const BARCODE_FORMATS = ['upc_a', 'upc_e', 'ean_13', 'ean_8']

// How often detect() actually runs, in ms - the canvas still redraws every animation frame, but
// re-running detection on every single frame (~60/s) burns battery for no benefit over a barcode,
// which isn't moving fast enough to need that cadence.
const DETECT_INTERVAL_MS = 100

// How long the green "captured" bracket stays on screen before handing off, so the snap feels
// deliberate rather than instant/jarring - roughly matches the Sam's Club-style pause being
// duplicated here.
const CAPTURE_HOLD_MS = 350

type ScanState = 'requesting' | 'scanning' | 'captured' | 'error'

// Live in-page barcode scanner: opens the rear camera directly (no OS camera app round-trip),
// runs a throttled BarcodeDetector loop, and draws a live bracket around whatever it finds. Only
// ever rendered when BarcodeScanButton has already feature-detected `BarcodeDetector` support -
// this component assumes it exists. Falls back to nothing itself on getUserMedia failure beyond
// showing the error and the "snap a photo instead" escape hatch; the actual fallback flow (file
// input + server-side decode) lives in the parent, same as before this component existed.
export default function BarcodeScannerOverlay({ open, onClose, onDetected, onFallbackToPhoto }: BarcodeScannerOverlayProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const rafRef = useRef<number | null>(null)
  const lastDetectAtRef = useRef(0)
  const detectingRef = useRef(false)
  const stoppedRef = useRef(false)

  const [state, setState] = useState<ScanState>('requesting')
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) {
      try {
        dialog.showModal()
      } catch (error) {
        console.error('dialog.showModal() failed, falling back to show():', error)
        dialog.show()
      }
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  useEffect(() => {
    if (!open) return

    stoppedRef.current = false
    setState('requesting')
    setErrorMessage('')

    const detector = new window.BarcodeDetector!({ formats: BARCODE_FORMATS })

    const stopStream = () => {
      streamRef.current?.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }

    const resizeCanvas = () => {
      const canvas = canvasRef.current
      if (!canvas) return
      canvas.width = canvas.clientWidth
      canvas.height = canvas.clientHeight
    }

    const loop = async (timestamp: number) => {
      if (stoppedRef.current) return
      const video = videoRef.current
      const canvas = canvasRef.current
      if (!video || !canvas) {
        rafRef.current = requestAnimationFrame(loop)
        return
      }

      if (
        !detectingRef.current &&
        video.readyState >= video.HAVE_ENOUGH_DATA &&
        timestamp - lastDetectAtRef.current >= DETECT_INTERVAL_MS
      ) {
        lastDetectAtRef.current = timestamp
        detectingRef.current = true
        try {
          const barcodes = await detector.detect(video)
          if (!stoppedRef.current && barcodes.length > 0) {
            drawBracket(canvas, video, barcodes[0].cornerPoints)
            capture(barcodes[0].rawValue)
            detectingRef.current = false
            return
          }
          if (!stoppedRef.current) clearCanvas(canvas)
        } catch (error) {
          console.error('Barcode detection failed on this frame:', error)
        }
        detectingRef.current = false
      }

      if (!stoppedRef.current) rafRef.current = requestAnimationFrame(loop)
    }

    const capture = (upc: string) => {
      stoppedRef.current = true
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current)
      navigator.vibrate?.(60)
      setState('captured')
      stopStream()
      window.setTimeout(() => onDetected(upc), CAPTURE_HOLD_MS)
    }

    async function start() {
      try {
        const constraints: MediaStreamConstraints = {
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        }
        const stream = await navigator.mediaDevices.getUserMedia(constraints)
        if (stoppedRef.current) {
          stream.getTracks().forEach((track) => track.stop())
          return
        }
        streamRef.current = stream

        // Not all devices/browsers support this constraint - continuous autofocus is what makes
        // close-up barcode text sharp, but its absence shouldn't block scanning entirely.
        const [track] = stream.getVideoTracks()
        try {
          await track.applyConstraints({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] })
        } catch (error) {
          console.warn('Continuous autofocus not supported on this camera:', error)
        }

        const video = videoRef.current
        if (!video) return
        video.srcObject = stream
        await video.play()

        resizeCanvas()
        window.addEventListener('resize', resizeCanvas)

        setState('scanning')
        rafRef.current = requestAnimationFrame(loop)
      } catch (error) {
        if (stoppedRef.current) return
        console.error('Camera access failed:', error)
        setState('error')
        setErrorMessage(
          error instanceof DOMException && error.name === 'NotAllowedError'
            ? 'Camera access was denied. Allow camera access for this site in your browser settings, then try again.'
            : "Couldn't access the camera on this device.",
        )
      }
    }

    start()

    return () => {
      stoppedRef.current = true
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current)
      stopStream()
      window.removeEventListener('resize', resizeCanvas)
    }
  }, [open, onDetected])

  return (
    <dialog
      ref={dialogRef}
      className="barcode-scanner-dialog"
      onClose={onClose}
      onCancel={onClose}
    >
      <div className="barcode-scanner-viewport">
        <video ref={videoRef} playsInline muted className="barcode-scanner-video" />
        <canvas ref={canvasRef} className="barcode-scanner-canvas" />
        <button type="button" className="barcode-scanner-close" onClick={onClose} aria-label="Close scanner">
          ✕
        </button>
        <div className="barcode-scanner-footer">
          {state === 'requesting' && <span className="barcode-scanner-badge">Starting camera…</span>}
          {state === 'scanning' && <span className="barcode-scanner-badge">Point the camera at a barcode</span>}
          {state === 'captured' && <span className="barcode-scanner-badge barcode-scanner-badge--success">Captured</span>}
          {state === 'error' && <span className="barcode-scanner-badge barcode-scanner-badge--error">{errorMessage}</span>}
          <button type="button" className="barcode-scanner-fallback" onClick={onFallbackToPhoto}>
            Can't find it? Snap a photo instead
          </button>
        </div>
      </div>
    </dialog>
  )
}

function clearCanvas(canvas: HTMLCanvasElement) {
  canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height)
}

// Maps the video's native frame coordinates to the canvas's CSS pixel coordinates, accounting for
// the `object-fit: cover` crop (the video is scaled up to fill the viewport, not letterboxed).
function drawBracket(canvas: HTMLCanvasElement, video: HTMLVideoElement, cornerPoints: { x: number; y: number }[]) {
  const displayWidth = canvas.clientWidth
  const displayHeight = canvas.clientHeight
  if (canvas.width !== displayWidth || canvas.height !== displayHeight) {
    canvas.width = displayWidth
    canvas.height = displayHeight
  }

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const scale = Math.max(canvas.width / video.videoWidth, canvas.height / video.videoHeight)
  const offsetX = (canvas.width - video.videoWidth * scale) / 2
  const offsetY = (canvas.height - video.videoHeight * scale) / 2

  const points = cornerPoints.map((point) => ({
    x: point.x * scale + offsetX,
    y: point.y * scale + offsetY,
  }))
  if (points.length === 0) return

  ctx.strokeStyle = '#00e676'
  ctx.lineWidth = 4
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(points[0].x, points[0].y)
  for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y)
  ctx.closePath()
  ctx.stroke()
  ctx.fillStyle = 'rgba(0, 230, 118, 0.18)'
  ctx.fill()
}
