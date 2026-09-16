import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadBloodPressure, setBpRange, type MacroRange } from '../../store/dashboardSlice'
import BloodPressureChart from './BloodPressureChart'

function formatDate(iso: string): string {
  const date = new Date(iso)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatTime(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

// Full-page view, same pattern as MicronutrientsView, reached from the header menu. The chart
// stays fixed at the top; only the readings table below it scrolls, so a 30-day range with many
// readings doesn't push the chart out of view.
export default function BloodPressureView() {
  const dispatch = useAppDispatch()
  const range = useAppSelector((state) => state.dashboard.bpRange)
  const readings = useAppSelector((state) => state.dashboard.bloodPressure)
  const status = useAppSelector((state) => state.dashboard.bloodPressureStatus)

  useEffect(() => {
    dispatch(loadBloodPressure(range))
  }, [dispatch, range])

  const latest = readings.length > 0 ? readings[readings.length - 1] : null

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
            <span className="shop-title">Blood pressure</span>
            <span className="shop-progress">
              {latest ? `Last: ${latest.systolic}/${latest.diastolic}${latest.bpm !== null ? ` · ${latest.bpm} bpm` : ''}` : 'No readings yet'}
            </span>
          </div>
        </div>
      </header>
      <div className="shop-list-wrapper">
        <div className="dash-card">
          <div className="dash-card-header">
            <span className="dash-card-title">Blood pressure & heart rate</span>
            <div className="range-toggle">
              {([7, 30] as MacroRange[]).map((option) => (
                <button
                  key={option}
                  type="button"
                  className={`range-toggle-segment ${range === option ? 'active' : ''}`}
                  onClick={() => dispatch(setBpRange(option))}
                >
                  {option}d
                </button>
              ))}
            </div>
          </div>

          {status === 'loading' && readings.length === 0 && <p className="shop-empty">Loading…</p>}
          {status === 'error' && <p className="shop-empty">Couldn't load blood pressure data.</p>}
          {status !== 'loading' && readings.length === 0 && (
            <p className="shop-empty">No readings in this range.</p>
          )}

          {readings.length > 0 && (
            <>
              <BloodPressureChart readings={readings} />
              <div className="weight-chart-legend">
                <span>
                  <span className="legend-swatch legend-swatch--line" style={{ background: 'var(--accent)' }} /> SBP
                </span>
                <span>
                  <span className="legend-swatch legend-swatch--line" style={{ background: 'var(--status-offline)' }} /> DBP
                </span>
                <span>
                  <span className="legend-swatch legend-swatch--line" style={{ background: 'var(--macro-carbs)' }} /> BPM
                </span>
              </div>
              <div className="sql-table-wrapper bp-table-wrapper">
                <table className="sql-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Time</th>
                      <th>SBP</th>
                      <th>DBP</th>
                      <th>BPM</th>
                    </tr>
                  </thead>
                  <tbody>
                    {readings
                      .slice()
                      .reverse()
                      .map((r) => (
                        <tr key={r.timestamp}>
                          <td>{formatDate(r.timestamp)}</td>
                          <td>{formatTime(r.timestamp)}</td>
                          <td>{r.systolic}</td>
                          <td>{r.diastolic}</td>
                          <td>{r.bpm ?? '—'}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
