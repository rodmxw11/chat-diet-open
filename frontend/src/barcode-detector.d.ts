// Shape Detection API's BarcodeDetector - not part of TypeScript's standard DOM lib, and only
// implemented in Chromium browsers (no Safari/Firefox), which is exactly why BarcodeScannerOverlay
// feature-detects it at call time rather than assuming it exists.
interface Point2D {
  x: number
  y: number
}

interface DetectedBarcode {
  rawValue: string
  boundingBox: DOMRectReadOnly
  cornerPoints: Point2D[]
}

interface BarcodeDetectorOptions {
  formats?: string[]
}

interface BarcodeDetector {
  detect(image: CanvasImageSource): Promise<DetectedBarcode[]>
}

interface Window {
  BarcodeDetector?: {
    new (options?: BarcodeDetectorOptions): BarcodeDetector
    getSupportedFormats?(): Promise<string[]>
  }
}
