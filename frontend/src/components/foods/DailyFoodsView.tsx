import { useEffect, useRef } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { deleteFoodEntry, loadFoodEntries, setFoodEntriesDate } from '../../store/foodEntriesSlice'
import { loadMacros } from '../../store/dashboardSlice'
import { loadSummary } from '../../store/summarySlice'

const TIME_FORMAT: Intl.DateTimeFormatOptions = { hour: 'numeric', minute: '2-digit' }

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, TIME_FORMAT)
}

function formatMacros(carbsG: number | null, fatG: number | null, proteinG: number | null): string {
  const c = Math.round(carbsG ?? 0)
  const f = Math.round(fatG ?? 0)
  const p = Math.round(proteinG ?? 0)
  return `${c}C ${f}F ${p}P`
}

// Parses a "YYYY-MM-DD" string as a local-time date and shifts it by `days`, avoiding the
// UTC-midnight parsing trap of `new Date(dateStr)` (which can land on the wrong local day near
// timezone boundaries).
function shiftDate(dateStr: string, days: number): string {
  const [year, month, day] = dateStr.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function localIsoDate(): string {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// Full-page view that replaces the chat screen entirely, same pattern as NotesView. Read-only:
// this is just for browsing what was logged on a given day, one at a time.
export default function DailyFoodsView() {
  const dispatch = useAppDispatch()
  const date = useAppSelector((state) => state.foodEntries.date)
  const items = useAppSelector((state) => state.foodEntries.items)
  const status = useAppSelector((state) => state.foodEntries.status)
  const metabolicDate = useAppSelector((state) => state.summary.data?.metabolicDate)
  const macroRange = useAppSelector((state) => state.dashboard.range)

  // Corrects the initial date to the server's current metabolic day (accounts for the
  // day-rollover hour) instead of the browser's raw local date - but only once, on first load.
  // A naive effect keyed on metabolicDate alone would snap back to "today" any time the summary
  // refetches while the user is looking at a past date.
  const hasAppliedMetabolicDate = useRef(false)
  useEffect(() => {
    if (!hasAppliedMetabolicDate.current && metabolicDate) {
      hasAppliedMetabolicDate.current = true
      dispatch(setFoodEntriesDate(metabolicDate))
    }
  }, [dispatch, metabolicDate])

  useEffect(() => {
    dispatch(loadFoodEntries(date))
  }, [dispatch, date])

  const changeDate = (newDate: string) => {
    dispatch(setFoodEntriesDate(newDate))
  }

  // Same fallback as the initial-load correction above: prefer the server's metabolic date
  // (accounts for the day-rollover hour) and only fall back to the browser's local date if the
  // summary hasn't loaded yet.
  const today = metabolicDate ?? localIsoDate()

  // The backend recomputes that day's macro cache as part of the delete itself; these refetches
  // just pull the corrected numbers into the header and sidebar chart so they don't show stale
  // totals until their next unrelated refresh.
  const handleDelete = (id: number) => {
    dispatch(deleteFoodEntry(id)).then(() => {
      dispatch(loadSummary())
      dispatch(loadMacros(macroRange))
    })
  }

  const totalCalories = items.reduce((sum, entry) => sum + (entry.totalCalories ?? 0), 0)
  const totalCarbs = items.reduce((sum, entry) => sum + (entry.totalCarbsG ?? 0), 0)
  const totalFat = items.reduce((sum, entry) => sum + (entry.totalFatG ?? 0), 0)
  const totalProtein = items.reduce((sum, entry) => sum + (entry.totalProteinG ?? 0), 0)

  return (
    <div className="shop-screen">
      <header className="app-header">
        <button
          type="button"
          className="back-button"
          onClick={() => dispatch(setScreen('chat'))}
          aria-label="Back to chat"
        >
          ←
        </button>
        <div className="shop-header-text">
          <span className="shop-title">Daily Foods</span>
          <span className="shop-progress">
            {items.length > 0
              ? `Calories: ${Math.round(totalCalories)} Macros: ${formatMacros(totalCarbs, totalFat, totalProtein)}`
              : `${items.length} logged`}
          </span>
        </div>
      </header>
      <div className="shop-list-wrapper">
        <div className="foods-date-row">
          <button
            type="button"
            className="date-nav-button"
            onClick={() => changeDate(shiftDate(date, -1))}
            aria-label="Previous day"
          >
            ‹
          </button>
          <input
            type="date"
            className="date-input"
            value={date}
            onChange={(event) => changeDate(event.target.value)}
          />
          <button
            type="button"
            className="date-nav-button"
            onClick={() => changeDate(shiftDate(date, 1))}
            aria-label="Next day"
          >
            ›
          </button>
          <button
            type="button"
            className="today-button"
            onClick={() => changeDate(today)}
            disabled={date === today}
          >
            Today
          </button>
        </div>

        {status === 'loading' && items.length === 0 && <p className="shop-empty">Loading…</p>}
        {status === 'error' && <p className="shop-empty">Couldn't load food entries.</p>}
        {status !== 'loading' && items.length === 0 && (
          <p className="shop-empty">Nothing logged on this day.</p>
        )}

        {items.length > 0 && (
          <div className="foods-table-wrapper">
            <table className="foods-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Food</th>
                  <th>Calories</th>
                  <th>Macros</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {items.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatTime(entry.loggedAt)}</td>
                    <td>{entry.rawUtterance}</td>
                    <td>{entry.totalCalories ?? 0}</td>
                    <td>{formatMacros(entry.totalCarbsG, entry.totalFatG, entry.totalProteinG)}</td>
                    <td className="foods-table-delete-cell">
                      <button
                        type="button"
                        className="foods-delete-button"
                        onClick={() => handleDelete(entry.id)}
                        aria-label={`Delete ${entry.rawUtterance}`}
                        title="Delete this entry"
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
