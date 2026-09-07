// Viewfinder-style barcode icon (corner brackets around a set of bars) instead of a plain camera
// glyph, since this button opens the live barcode scanner, not a general photo capture. Stroke-
// based like the app's other custom icons, so it inherits the button's currentColor and works in
// both themes without needing separate light/dark art.
const BAR_WIDTHS = [0.7, 0.7, 1.1, 0.7, 0.7, 1.1, 0.7, 1.6, 0.7, 0.7, 1.1]
const BAR_GAP = 0.4
const BAR_START_X = 6.2
const BAR_TOP = 7
const BAR_HEIGHT = 10

function barRects() {
  let x = BAR_START_X
  return BAR_WIDTHS.map((width, i) => {
    const rect = <rect key={i} x={x} y={BAR_TOP} width={width} height={BAR_HEIGHT} fill="currentColor" />
    x += width + BAR_GAP
    return rect
  })
}

export default function BarcodeIcon() {
  return (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <g stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 7.5V3a1 1 0 0 1 1-1h4.5" />
        <path d="M22 7.5V3a1 1 0 0 0-1-1h-4.5" />
        <path d="M2 16.5V21a1 1 0 0 0 1 1h4.5" />
        <path d="M22 16.5V21a1 1 0 0 1-1 1h-4.5" />
      </g>
      {barRects()}
    </svg>
  )
}
