import { useRef, useState } from 'react'
import { useAppDispatch } from '../../store/hooks'
import { dashboardApi } from '../../store/dashboardApi'

interface OmronUploadResult {
  readings: number
  newReadings: number
  from: string
  to: string
}

export interface ImportMessage {
  text: string
  isError: boolean
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

// The blood pressure page's header button: picks an OMRON CSV, uploads it, reports the outcome to
// the page, and refreshes the cached readings (chart, table, latest-reading date) on success.
export default function ImportBpButton({ onResult }: { onResult: (message: ImportMessage | null) => void }) {
  const dispatch = useAppDispatch()
  const [importing, setImporting] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const importFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // Cleared right away so picking the same file again (e.g. after fixing it) still fires onChange.
    event.target.value = ''
    if (!file) return
    setImporting(true)
    onResult(null)
    uploadOmronCsv(file)
      .then((text) => {
        onResult({ text, isError: false })
        dispatch(dashboardApi.util.invalidateTags(['BloodPressure']))
      })
      .catch((error: Error) => onResult({ text: error.message, isError: true }))
      .finally(() => setImporting(false))
  }

  return (
    <>
      <button
        type="button"
        className="today-button"
        onClick={() => fileInputRef.current?.click()}
        disabled={importing}
        title="Upload an OMRON blood pressure CSV export and import its readings"
      >
        {importing ? 'Importing…' : 'Import BP'}
      </button>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={importFile} />
    </>
  )
}
