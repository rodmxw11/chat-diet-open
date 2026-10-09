import { useAppDispatch } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { useGetMeasurementsQuery, type MeasurementView } from '../../store/dashboardApi'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function inches(value: number | null): string {
  return value === null ? '—' : value.toFixed(1)
}

function signed(value: number): string {
  return `${value > 0 ? '+' : value < 0 ? '−' : '±'}${Math.abs(value).toFixed(1)}`
}

// "Body fat 28.4% · waist 52.5 in (−2.0 since Sep 1)" - newest estimate and waist change overall.
function summary(measurements: MeasurementView[]): string | null {
  const latestFat = measurements.find((m) => m.bodyFatPct !== null)
  const waists = measurements.filter((m) => m.waistIn !== null)
  const parts: string[] = []
  if (latestFat?.bodyFatPct != null) parts.push(`Body fat ${latestFat.bodyFatPct.toFixed(1)}%`)
  if (waists.length > 0) {
    const newest = waists[0]
    const oldest = waists[waists.length - 1]
    const change = waists.length > 1 ? ` (${signed(newest.waistIn! - oldest.waistIn!)} since ${formatDate(oldest.measuredAt)})` : ''
    parts.push(`waist ${newest.waistIn!.toFixed(1)} in${change}`)
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

// Tape-measurement history with the US Navy body-fat estimate for each session. Measurements are
// logged by chat ("I measure 45.5 waist 19.2 neck"); this page is read-only, like the other
// history pages reached from the header menu.
export default function MeasurementsView() {
  const dispatch = useAppDispatch()
  const { data, isLoading, isError } = useGetMeasurementsQuery()
  const measurements = data?.measurements ?? []
  const showHip = measurements.some((m) => m.hipIn !== null)
  const anyCarried = measurements.some((m) => m.neckCarried || m.hipCarried)
  const headline = summary(measurements)

  return (
    <div className="shop-screen">
      <header className="app-header app-header--stacked">
        <div className="app-header-top-row">
          <button
            type="button"
            className="back-button"
            onClick={() => dispatch(setScreen('chat'))}
            aria-label="Back to chat"
          >
            ←
          </button>
          <div className="shop-header-text">
            <span className="shop-title">Measurements</span>
            <span className="shop-progress">
              {measurements.length} {measurements.length === 1 ? 'session' : 'sessions'}
            </span>
          </div>
        </div>
        {headline && (
          <div className="bp-header-stats">
            <span className="shop-progress">{headline}</span>
          </div>
        )}
      </header>
      <div className="shop-list-wrapper">
        {isLoading && <p className="shop-empty">Loading…</p>}
        {isError && <p className="shop-empty">Couldn't load measurements.</p>}
        {data && measurements.length === 0 && (
          <p className="shop-empty">No measurements yet. Log them in chat, e.g. "I measure 45.5 waist 19.2 neck".</p>
        )}
        {data && !data.profileComplete && measurements.length > 0 && (
          <p className="shop-empty">
            No body-fat estimate: set sex and height-in under chat-diet.profile in the app's config.
          </p>
        )}

        {measurements.length > 0 && (
          <div className="dash-card">
            <div className="sql-table-wrapper">
              <table className="sql-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Waist</th>
                    <th>Neck</th>
                    {showHip && <th>Hip</th>}
                    <th>Body fat</th>
                  </tr>
                </thead>
                <tbody>
                  {measurements.map((m) => (
                    <tr key={m.id}>
                      <td>{formatDate(m.measuredAt)}</td>
                      <td>{inches(m.waistIn)}</td>
                      <td>{inches(m.neckIn)}</td>
                      {showHip && <td>{inches(m.hipIn)}</td>}
                      <td>
                        {m.bodyFatPct === null ? '—' : `${m.bodyFatPct.toFixed(1)}%`}
                        {(m.neckCarried || m.hipCarried) && '*'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="measurements-footnote">
              Inches. Body fat is the US Navy circumference estimate - typically within a few points of a
              DEXA scan; watch the trend.
              {anyCarried && ' * Uses your most recent earlier neck (or hip) measurement.'}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
