import type { SqlAnswer } from '../store/chatSlice'

function formatCell(value: unknown): string {
  if (value === null || value === undefined) return ''
  return String(value)
}

export default function SqlResultTable({ answer }: { answer: SqlAnswer }) {
  return (
    <div className="sql-result">
      <pre className="sql-text">{answer.sqlText}</pre>
      <div className="sql-table-wrapper">
        <table className="sql-table">
          <thead>
            <tr>
              {answer.columns.map((column) => (
                <th key={column}>{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {answer.rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td key={cellIndex}>{formatCell(cell)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="sql-result-footer">
        {answer.truncated && (
          <span>
            Showing first {answer.rows.length} of {answer.totalRows} rows.
          </span>
        )}
        <a href={`/api/sql-results/${answer.csvId}/csv`} download>
          Download CSV
        </a>
      </div>
    </div>
  )
}
