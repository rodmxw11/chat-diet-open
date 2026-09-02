import { useEffect, useRef } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadChatHistoryForDate, setChatHistoryDate } from '../../store/chatHistorySlice'

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

// Full-page view that replaces the chat screen entirely, same pattern as DailyFoodsView. Read-only:
// this is just for browsing a past day's conversation, one day at a time.
export default function ChatHistoryView() {
  const dispatch = useAppDispatch()
  const date = useAppSelector((state) => state.chatHistory.date)
  const items = useAppSelector((state) => state.chatHistory.items)
  const status = useAppSelector((state) => state.chatHistory.status)
  const haikuCostUsd = useAppSelector((state) => state.chatHistory.haikuCostUsd)
  const opusCostUsd = useAppSelector((state) => state.chatHistory.opusCostUsd)
  const metabolicDate = useAppSelector((state) => state.summary.data?.metabolicDate)

  // Corrects the initial date to the server's current metabolic day (accounts for the
  // day-rollover hour) instead of the browser's raw local date - but only once, on first load.
  // A naive effect keyed on metabolicDate alone would snap back to "today" any time the summary
  // refetches while the user is looking at a past date.
  const hasAppliedMetabolicDate = useRef(false)
  useEffect(() => {
    if (!hasAppliedMetabolicDate.current && metabolicDate) {
      hasAppliedMetabolicDate.current = true
      dispatch(setChatHistoryDate(metabolicDate))
    }
  }, [dispatch, metabolicDate])

  useEffect(() => {
    dispatch(loadChatHistoryForDate(date))
  }, [dispatch, date])

  const changeDate = (newDate: string) => {
    dispatch(setChatHistoryDate(newDate))
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
            <span className="shop-title">Chat History</span>
            <span className="shop-progress">{items.length} messages</span>
            <span className="shop-progress">
              Haiku ${haikuCostUsd.toFixed(2)} · Opus ${opusCostUsd.toFixed(2)}
            </span>
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
        {status === 'error' && <p className="shop-empty">Couldn't load chat history.</p>}
        {status !== 'loading' && items.length === 0 && (
          <p className="shop-empty">Nothing said on this day.</p>
        )}

        {items.length > 0 && (
          <div className="chat-window chat-history-window">
            {items.map((message, index) => (
              <div key={index} className={`message ${message.role}`}>
                {message.text}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
