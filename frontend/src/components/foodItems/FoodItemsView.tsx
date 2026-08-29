import { useEffect, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import {
  createFoodItem,
  deleteFoodItem,
  loadFoodItems,
  restoreFoodItem,
  setFoodItemsIncludeDeleted,
  setFoodItemsQuery,
  updateFoodItem,
  type FoodItem,
  type FoodItemUpsert,
} from '../../store/foodItemsSlice'
import Modal from '../modal/Modal'

const LOOKUP_SOURCES = ['MANUAL', 'OFF', 'FDC', 'MODEL_ESTIMATE', 'LABEL_OCR']

const BLANK_FORM: FoodItemUpsert = {
  name: '',
  upc: null,
  per100gCalories: null,
  per100gProtein: null,
  per100gCarbs: null,
  per100gFat: null,
  per100gFiber: null,
  per100gSugar: null,
  per100gSodiumMg: null,
  per100gSaturatedFat: null,
  per100gCholesterolMg: null,
  per100gPotassiumMg: null,
  typicalServingG: null,
  lookupSource: 'MANUAL',
}

function toFormState(item: FoodItem): FoodItemUpsert {
  return {
    name: item.name,
    upc: item.upc,
    per100gCalories: item.per100gCalories,
    per100gProtein: item.per100gProtein,
    per100gCarbs: item.per100gCarbs,
    per100gFat: item.per100gFat,
    per100gFiber: item.per100gFiber,
    per100gSugar: item.per100gSugar,
    per100gSodiumMg: item.per100gSodiumMg,
    per100gSaturatedFat: item.per100gSaturatedFat,
    per100gCholesterolMg: item.per100gCholesterolMg,
    per100gPotassiumMg: item.per100gPotassiumMg,
    typicalServingG: item.typicalServingG,
    lookupSource: item.lookupSource,
  }
}

function round(value: number | null): string {
  return value === null ? '—' : String(Math.round(value * 10) / 10)
}

// Full-page CRUD screen, a deliberate exception to the chat-first/no-forms pattern the rest of
// the app follows - fixing a bad cached lookup (a UPC scan missing sodium, a stale model
// estimate) had no way to be corrected directly before this existed.
export default function FoodItemsView() {
  const dispatch = useAppDispatch()
  const items = useAppSelector((state) => state.foodItems.items)
  const status = useAppSelector((state) => state.foodItems.status)
  const query = useAppSelector((state) => state.foodItems.query)
  const includeDeleted = useAppSelector((state) => state.foodItems.includeDeleted)

  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<FoodItemUpsert>(BLANK_FORM)
  const [modalOpen, setModalOpen] = useState(false)

  useEffect(() => {
    dispatch(loadFoodItems({ query, includeDeleted }))
  }, [dispatch, query, includeDeleted])

  const visibleItems = includeDeleted
    ? items.filter((item) => item.deletedAt !== null)
    : items.filter((item) => item.deletedAt === null)

  const openCreate = () => {
    setEditingId(null)
    setForm(BLANK_FORM)
    setModalOpen(true)
  }

  const openEdit = (item: FoodItem) => {
    setEditingId(item.id)
    setForm(toFormState(item))
    setModalOpen(true)
  }

  const handleSave = () => {
    if (!form.name.trim()) return
    if (editingId === null) {
      dispatch(createFoodItem(form))
    } else {
      dispatch(updateFoodItem({ id: editingId, body: form }))
    }
    setModalOpen(false)
  }

  const numberField = (key: keyof FoodItemUpsert, label: string) => (
    <label className="food-item-field">
      <span>{label}</span>
      <input
        type="number"
        step="any"
        value={form[key] === null || form[key] === undefined ? '' : (form[key] as number)}
        onChange={(event) =>
          setForm({ ...form, [key]: event.target.value === '' ? null : Number(event.target.value) })
        }
      />
    </label>
  )

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
            <span className="shop-title">Food Items</span>
            <span className="shop-progress">{visibleItems.length} shown</span>
          </div>
          <button type="button" className="today-button" onClick={openCreate}>
            + New
          </button>
        </div>
        <div className="food-items-controls">
          <input
            type="search"
            className="date-input food-items-search"
            placeholder="Search by name…"
            value={query}
            onChange={(event) => dispatch(setFoodItemsQuery(event.target.value))}
          />
          <div className="food-items-tabs">
            <button
              type="button"
              className={`food-items-tab ${!includeDeleted ? 'active' : ''}`}
              onClick={() => dispatch(setFoodItemsIncludeDeleted(false))}
            >
              Active
            </button>
            <button
              type="button"
              className={`food-items-tab ${includeDeleted ? 'active' : ''}`}
              onClick={() => dispatch(setFoodItemsIncludeDeleted(true))}
            >
              Deleted
            </button>
          </div>
        </div>
      </header>
      <div className="shop-list-wrapper">
        {status === 'loading' && visibleItems.length === 0 && <p className="shop-empty">Loading…</p>}
        {status === 'error' && <p className="shop-empty">Couldn't load food items.</p>}
        {status !== 'loading' && visibleItems.length === 0 && (
          <p className="shop-empty">{includeDeleted ? 'Nothing deleted.' : 'No food items cached yet.'}</p>
        )}

        {visibleItems.length > 0 && (
          <div className="foods-table-wrapper">
            <table className="foods-table food-items-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Source</th>
                  <th>Cal</th>
                  <th>P</th>
                  <th>C</th>
                  <th>F</th>
                  <th>Uses</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <button type="button" className="food-items-name-button" onClick={() => openEdit(item)}>
                        {item.name}
                      </button>
                    </td>
                    <td>
                      <span className="food-items-badge">{item.lookupSource}</span>
                    </td>
                    <td>{round(item.per100gCalories)}</td>
                    <td>{round(item.per100gProtein)}</td>
                    <td>{round(item.per100gCarbs)}</td>
                    <td>{round(item.per100gFat)}</td>
                    <td>{item.useCount}</td>
                    <td className="foods-table-delete-cell">
                      {item.deletedAt === null ? (
                        <button
                          type="button"
                          className="foods-delete-button"
                          onClick={() => dispatch(deleteFoodItem(item.id))}
                          aria-label={`Delete ${item.name}`}
                          title="Delete this item"
                        >
                          ✕
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="foods-delete-button"
                          onClick={() => dispatch(restoreFoodItem(item.id))}
                          aria-label={`Restore ${item.name}`}
                          title="Restore this item"
                        >
                          ↺
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        variant="modal"
        title={editingId === null ? 'New food item' : 'Edit food item'}
        footer={
          <button type="button" className="today-button" onClick={handleSave}>
            Save
          </button>
        }
      >
        <div className="food-item-form">
          <label className="food-item-field food-item-field--wide">
            <span>Name</span>
            <input
              type="text"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
            />
          </label>
          <label className="food-item-field food-item-field--wide">
            <span>UPC</span>
            <input
              type="text"
              value={form.upc ?? ''}
              onChange={(event) => setForm({ ...form, upc: event.target.value === '' ? null : event.target.value })}
            />
          </label>
          <label className="food-item-field">
            <span>Source</span>
            <select
              value={form.lookupSource}
              onChange={(event) => setForm({ ...form, lookupSource: event.target.value })}
            >
              {LOOKUP_SOURCES.map((source) => (
                <option key={source} value={source}>
                  {source}
                </option>
              ))}
            </select>
          </label>
          {numberField('typicalServingG', 'Typical serving (g)')}
          {numberField('per100gCalories', 'Calories / 100g')}
          {numberField('per100gProtein', 'Protein (g)')}
          {numberField('per100gCarbs', 'Carbs (g)')}
          {numberField('per100gFat', 'Fat (g)')}
          {numberField('per100gFiber', 'Fiber (g)')}
          {numberField('per100gSugar', 'Sugar (g)')}
          {numberField('per100gSodiumMg', 'Sodium (mg)')}
          {numberField('per100gSaturatedFat', 'Saturated fat (g)')}
          {numberField('per100gCholesterolMg', 'Cholesterol (mg)')}
          {numberField('per100gPotassiumMg', 'Potassium (mg)')}
        </div>
      </Modal>
    </div>
  )
}
