import { useEffect, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadAbout } from '../../store/aboutSlice'

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

function formatUptime(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (days > 0 || hours > 0) parts.push(`${hours}h`)
  if (days > 0 || hours > 0 || minutes > 0) parts.push(`${minutes}m`)
  parts.push(`${secs}s`)
  return parts.join(' ')
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`
}

function formatHour(hour: number): string {
  return new Date(2000, 0, 1, hour).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

// Full-page view, same pattern as SchemaView. Uptime is computed live from startupTime (ticking
// every second) rather than displaying the uptimeSeconds snapshot from the last fetch, which
// would otherwise freeze the moment the page loaded.
export default function AboutView() {
  const dispatch = useAppDispatch()
  const info = useAppSelector((state) => state.about.info)
  const status = useAppSelector((state) => state.about.status)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    dispatch(loadAbout())
  }, [dispatch])

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [])

  const uptimeSeconds = info ? (now - Date.parse(info.startupTime)) / 1000 : 0

  return (
    <div className="shop-screen">
      <header className="app-header">
        <button type="button" className="back-button" onClick={() => dispatch(setScreen('chat'))} aria-label="Back to chat">
          ←
        </button>
        <div className="shop-header-text">
          <span className="shop-title">About</span>
          <span className="shop-progress">chat-diet</span>
        </div>
      </header>
      <div className="shop-list-wrapper">
        {status === 'loading' && !info && <p className="shop-empty">Loading…</p>}
        {status === 'error' && <p className="shop-empty">Couldn't load app info.</p>}

        {info && (
          <div className="dash-card">
            <div className="sql-table-wrapper">
              <table className="sql-table">
                <tbody>
                  <tr>
                    <td>Version</td>
                    <td>
                      {info.version ?? 'unknown'}
                      {info.gitCommit && ` (${info.gitCommit})`}
                    </td>
                  </tr>
                  <tr>
                    <td>Built</td>
                    <td>{info.buildTime ? formatDateTime(info.buildTime) : 'unknown'}</td>
                  </tr>
                  <tr>
                    <td>Started</td>
                    <td>{formatDateTime(info.startupTime)}</td>
                  </tr>
                  <tr>
                    <td>Uptime</td>
                    <td>{formatUptime(uptimeSeconds)}</td>
                  </tr>
                  <tr>
                    <td>Java runtime</td>
                    <td>{info.javaRuntime}</td>
                  </tr>
                  <tr>
                    <td>OS</td>
                    <td>{info.os}</td>
                  </tr>
                  <tr>
                    <td>Database size</td>
                    <td>{info.databaseSizeBytes !== null ? formatBytes(info.databaseSizeBytes) : 'unknown'}</td>
                  </tr>
                  <tr>
                    <td>Day rollover</td>
                    <td>{formatHour(info.dayRolloverHour)}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
