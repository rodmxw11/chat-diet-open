import { useEffect, useRef } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadFoodEntries, setFoodEntriesDate, type FoodEntry } from '../../store/foodEntriesSlice'

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

// Same local-time parsing as shiftDate, to avoid the day-off-by-one trap from parsing "YYYY-MM-DD"
// as UTC midnight.
function weekdayLabel(dateStr: string): string {
  const [year, month, day] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { weekday: 'short' })
}

// The "Core 6" the user chose to track (skipping vitamins/minerals - already covered by a daily
// multivitamin). DV = FDA adult daily value, 2000-kcal reference; sugar has no official %DV on
// the nutrition label, so 50g (the added-sugar guideline) is used as the closest common reference.
interface NutrientDef {
  key: keyof Pick<FoodEntry, 'fiberG' | 'sugarG' | 'sodiumMg' | 'saturatedFatG' | 'cholesterolMg' | 'potassiumMg'>
  label: string
  unit: 'g' | 'mg'
  dv: number
}

const NUTRIENTS: NutrientDef[] = [
  { key: 'fiberG', label: 'Fiber', unit: 'g', dv: 28 },
  { key: 'sugarG', label: 'Sugar', unit: 'g', dv: 50 },
  { key: 'saturatedFatG', label: 'Saturated fat', unit: 'g', dv: 20 },
  { key: 'sodiumMg', label: 'Sodium', unit: 'mg', dv: 2300 },
  { key: 'cholesterolMg', label: 'Cholesterol', unit: 'mg', dv: 300 },
  { key: 'potassiumMg', label: 'Potassium', unit: 'mg', dv: 4700 },
]

// Full-page view that replaces the chat screen entirely, same pattern as DailyFoodsView - in fact
// it reads the exact same `foodEntries` slice/date rather than fetching independently, since this
// is just a different summary of the same day's rows. That also keeps this page and Daily Foods
// on the same date when switching between them.
export default function MicronutrientsView() {
  const dispatch = useAppDispatch()
  const date = useAppSelector((state) => state.foodEntries.date)
  const items = useAppSelector((state) => state.foodEntries.items)
  const status = useAppSelector((state) => state.foodEntries.status)
  const metabolicDate = useAppSelector((state) => state.summary.data?.metabolicDate)

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

  const today = metabolicDate ?? localIsoDate()

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
            <span className="shop-title">Micronutrients</span>
            <span className="shop-progress">{items.length} items logged</span>
          </div>
        </div>
        <div className="foods-date-row">
          <span className="date-weekday">{weekdayLabel(date)}</span>
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
      </header>
      <div className="shop-list-wrapper">
        {status === 'loading' && items.length === 0 && <p className="shop-empty">Loading…</p>}
        {status === 'error' && <p className="shop-empty">Couldn't load micronutrient data.</p>}
        {status !== 'loading' && items.length === 0 && (
          <p className="shop-empty">Nothing logged on this day.</p>
        )}

        {items.length > 0 && (
          <div className="nutrient-list">
            {NUTRIENTS.map(({ key, label, unit, dv }) => {
              const total = items.reduce((sum, entry) => sum + (entry[key] ?? 0), 0)
              const pct = Math.round((total / dv) * 100)
              const barPct = Math.min(100, pct)
              return (
                <div className="nutrient-row" key={key}>
                  <div className="nutrient-row-header">
                    <span className="nutrient-label">{label}</span>
                    <span className="nutrient-amount">
                      {Math.round(total)}
                      {unit} · {pct}% DV
                    </span>
                  </div>
                  <div className="nutrient-dv-bar">
                    <div className="nutrient-dv-bar-fill" style={{ width: `${barPct}%` }} />
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
