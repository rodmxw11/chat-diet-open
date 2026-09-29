import { useEffect, useRef, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadAbout } from '../../store/aboutSlice'
import { dashboardApi } from '../../store/dashboardApi'

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

// Fetched and saved via a blob rather than a plain <a href download>: the service worker answers
// navigations with the cached app shell, and some browsers treat a download link as a navigation -
// which would save index.html instead of the database. A fetch() never counts as one.
async function downloadExport(): Promise<void> {
  const response = await fetch('/api/export')
  if (!response.ok) {
    throw new Error(`Export failed (${response.status})`)
  }
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const fileName = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'chat-diet-export.zip'
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

interface OmronUploadResult {
  readings: number
  newReadings: number
  from: string
  to: string
}

function formatDay(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

// Uploads one OMRON blood-pressure CSV export. The server upserts by reading time, so re-importing
// a report that overlaps an earlier one only adds the new readings; a file that isn't an OMRON
// export (or has a bad row) is rejected whole, with the reason in the response.
async function uploadOmronCsv(file: File): Promise<string> {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch('/api/omron/upload', { method: 'POST', body })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(data?.error ?? `Import failed (${response.status})`)
  }
  const result = data as OmronUploadResult
  const range = `${formatDay(result.from)} – ${formatDay(result.to)}`
  return `Imported ${result.readings} readings (${range}), ${result.newReadings} new.`
}

// Full-page view, same pattern as SchemaView. Uptime is computed live from startupTime (ticking
// every second) rather than displaying the uptimeSeconds snapshot from the last fetch, which
// would otherwise freeze the moment the page loaded.
export default function AboutView() {
  const dispatch = useAppDispatch()
  const info = useAppSelector((state) => state.about.info)
  const status = useAppSelector((state) => state.about.status)
  const [now, setNow] = useState(() => Date.now())
  const [exportStatus, setExportStatus] = useState<'idle' | 'exporting' | 'error'>('idle')
  const [importStatus, setImportStatus] = useState<'idle' | 'importing'>('idle')
  const [importMessage, setImportMessage] = useState<{ text: string; isError: boolean } | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const importBp = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // Cleared right away so picking the same file again (e.g. after fixing it) still fires onChange.
    event.target.value = ''
    if (!file) return
    setImportStatus('importing')
    setImportMessage(null)
    uploadOmronCsv(file)
      .then((text) => {
        setImportMessage({ text, isError: false })
        dispatch(dashboardApi.util.invalidateTags(['BloodPressure']))
      })
      .catch((error: Error) => setImportMessage({ text: error.message, isError: true }))
      .finally(() => setImportStatus('idle'))
  }

  const exportDb = () => {
    setExportStatus('exporting')
    downloadExport()
      .then(() => setExportStatus('idle'))
      .catch(() => setExportStatus('error'))
  }

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

        <div className="about-export">
          <button
            type="button"
            className="today-button"
            onClick={exportDb}
            disabled={exportStatus === 'exporting'}
            title="Download a zip with a consistent copy of the SQLite database"
          >
            {exportStatus === 'exporting' ? 'Exporting…' : 'Export DB'}
          </button>
          <button
            type="button"
            className="today-button"
            onClick={() => fileInputRef.current?.click()}
            disabled={importStatus === 'importing'}
            title="Upload an OMRON blood pressure CSV export and import its readings"
          >
            {importStatus === 'importing' ? 'Importing…' : 'Import BP'}
          </button>
          <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={importBp} />
          {exportStatus === 'error' && <span className="about-export-error">Export failed - try again.</span>}
        </div>
        {importMessage && (
          <p className={importMessage.isError ? 'about-import-message about-export-error' : 'about-import-message'}>
            {importMessage.text}
          </p>
        )}
      </div>
    </div>
  )
}
