import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadSchema } from '../../store/schemaSlice'

// Full-page, read-only view of the live SQLite schema - not date-scoped, so it uses the plain
// (non-stacked) header like FoodItemsView rather than the date-nav header the browsing pages use.
export default function SchemaView() {
  const dispatch = useAppDispatch()
  const tables = useAppSelector((state) => state.schema.tables)
  const status = useAppSelector((state) => state.schema.status)

  useEffect(() => {
    dispatch(loadSchema())
  }, [dispatch])

  return (
    <div className="shop-screen">
      <header className="app-header">
        <button type="button" className="back-button" onClick={() => dispatch(setScreen('chat'))} aria-label="Back to chat">
          ←
        </button>
        <div className="shop-header-text">
          <span className="shop-title">Database Schema</span>
          <span className="shop-progress">{tables.length} tables</span>
        </div>
      </header>
      <div className="shop-list-wrapper">
        {status === 'loading' && tables.length === 0 && <p className="shop-empty">Loading…</p>}
        {status === 'error' && <p className="shop-empty">Couldn't load the schema.</p>}

        {tables.map((table) => (
          <div className="dash-card schema-table-card" key={table.tableName}>
            <div className="dash-card-header">
              <span className="dash-card-title">{table.tableName}</span>
              <span className="dash-card-subtitle">{table.columns.length} columns</span>
            </div>
            <div className="foods-table-wrapper">
              <table className="foods-table">
                <thead>
                  <tr>
                    <th>Column</th>
                    <th>Type</th>
                  </tr>
                </thead>
                <tbody>
                  {table.columns.map((column) => (
                    <tr key={column.name}>
                      <td>{column.name}</td>
                      <td>{column.type || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
