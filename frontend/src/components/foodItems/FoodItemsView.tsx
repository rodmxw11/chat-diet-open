import { useEffect, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import {
  createFoodItem,
  deleteFoodItem,
  loadFoodItems,
  lookupFoodItemByUpc,
  lookupFoodItemNutrition,
  restoreFoodItem,
  setFoodItemsIncludeDeleted,
  setFoodItemsQuery,
  updateFoodItem,
  type NutritionLookupResult,
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

const NUTRIENT_KEYS = [
  'per100gCalories',
  'per100gProtein',
  'per100gCarbs',
  'per100gFat',
  'per100gFiber',
  'per100gSugar',
  'per100gSodiumMg',
  'per100gSaturatedFat',
  'per100gCholesterolMg',
  'per100gPotassiumMg',
] as const

// Nutrition labels are usually printed per-serving, not per-100g - this lets someone type the
// numbers exactly as read off a label (at whatever gram amount) and scales to per-100g here,
// instead of requiring that math be done by hand before typing.
function scaledForSave(form: FoodItemUpsert, entryGrams: number): FoodItemUpsert {
  if (entryGrams === 100) return form
  const factor = 100 / entryGrams
  const scaled = { ...form }
  for (const key of NUTRIENT_KEYS) {
    const value = form[key]
    scaled[key] = value === null ? null : value * factor
  }
  return scaled
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
  const [entryGrams, setEntryGrams] = useState(100)
  const [lookupStatus, setLookupStatus] = useState<'idle' | 'loading' | 'notfound'>('idle')
  const [candidates, setCandidates] = useState<NutritionLookupResult[]>([])
  const [upcLookupStatus, setUpcLookupStatus] = useState<'idle' | 'loading' | 'notfound'>('idle')

  useEffect(() => {
    dispatch(loadFoodItems({ query, includeDeleted }))
  }, [dispatch, query, includeDeleted])

  const visibleItems = includeDeleted
    ? items.filter((item) => item.deletedAt !== null)
    : items.filter((item) => item.deletedAt === null)

  const openCreate = () => {
    setEditingId(null)
    setForm(BLANK_FORM)
    setEntryGrams(100)
    setLookupStatus('idle')
    setCandidates([])
    setUpcLookupStatus('idle')
    setModalOpen(true)
  }

  const openEdit = (item: FoodItem) => {
    setEditingId(item.id)
    setForm(toFormState(item))
    setEntryGrams(100)
    setLookupStatus('idle')
    setCandidates([])
    setUpcLookupStatus('idle')
    setModalOpen(true)
  }

  const handleSave = () => {
    if (!form.name.trim()) return
    const payload = scaledForSave(form, entryGrams)
    if (editingId === null) {
      dispatch(createFoodItem(payload))
    } else {
      dispatch(updateFoodItem({ id: editingId, body: payload }))
    }
    setModalOpen(false)
  }

  const applyLookupResult = (result: NutritionLookupResult, source: string) => {
    setForm({
      ...form,
      per100gCalories: result.caloriesPer100g,
      per100gProtein: result.proteinPer100g,
      per100gCarbs: result.carbsPer100g,
      per100gFat: result.fatPer100g,
      per100gFiber: result.fiberPer100g,
      per100gSugar: result.sugarPer100g,
      per100gSodiumMg: result.sodiumMgPer100g,
      per100gSaturatedFat: result.saturatedFatPer100g,
      per100gCholesterolMg: result.cholesterolMgPer100g,
      per100gPotassiumMg: result.potassiumMgPer100g,
      typicalServingG: result.typicalServingG,
      lookupSource: source,
    })
    setEntryGrams(100)
    setCandidates([])
  }

  const handleLookup = async () => {
    setLookupStatus('loading')
    setCandidates([])
    try {
      const results = await dispatch(lookupFoodItemNutrition(form.name)).unwrap()
      if (results.length === 0) {
        setLookupStatus('notfound')
      } else if (results.length === 1) {
        applyLookupResult(results[0], 'FDC')
        setLookupStatus('idle')
      } else {
        setCandidates(results)
        setLookupStatus('idle')
      }
    } catch {
      setLookupStatus('notfound')
    }
  }

  const handleUpcLookup = async () => {
    if (!form.upc) return
    setUpcLookupStatus('loading')
    try {
      const result = await dispatch(lookupFoodItemByUpc(form.upc)).unwrap()
      if (result === null) {
        setUpcLookupStatus('notfound')
      } else {
        applyLookupResult(result, 'OFF')
        setUpcLookupStatus('idle')
      }
    } catch {
      setUpcLookupStatus('notfound')
    }
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
          <div className="food-item-field food-item-field--wide food-item-lookup-row">
            <button
              type="button"
              className="today-button"
              onClick={handleLookup}
              disabled={!form.name.trim() || lookupStatus === 'loading'}
            >
              Look up nutrition (USDA)
            </button>
            {lookupStatus === 'loading' && <span className="food-item-lookup-status">Looking up…</span>}
            {lookupStatus === 'notfound' && (
              <span className="food-item-lookup-status">
                No USDA match found — check the name or fill in manually below.
              </span>
            )}
          </div>
          {candidates.length > 0 && (
            <ul className="food-item-lookup-candidates food-item-field--wide">
              {candidates.map((candidate, index) => (
                <li key={index}>
                  <button type="button" onClick={() => applyLookupResult(candidate, 'FDC')}>
                    {candidate.name}
                    {candidate.caloriesPer100g !== null && ` — ${Math.round(candidate.caloriesPer100g)} cal/100g`}
                  </button>
                </li>
              ))}
            </ul>
          )}
          <label className="food-item-field food-item-field--wide">
            <span>UPC</span>
            <input
              type="text"
              value={form.upc ?? ''}
              onChange={(event) => setForm({ ...form, upc: event.target.value === '' ? null : event.target.value })}
            />
          </label>
          <div className="food-item-field food-item-field--wide food-item-lookup-row">
            <button
              type="button"
              className="today-button"
              onClick={handleUpcLookup}
              disabled={!form.upc || upcLookupStatus === 'loading'}
            >
              Look up by UPC (Open Food Facts)
            </button>
            {upcLookupStatus === 'loading' && <span className="food-item-lookup-status">Looking up…</span>}
            {upcLookupStatus === 'notfound' && (
              <span className="food-item-lookup-status">
                No Open Food Facts match for that UPC — fill in manually below.
              </span>
            )}
          </div>
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
          <label className="food-item-field food-item-field--wide">
            <span>Values entered for how many grams?</span>
            <input
              type="number"
              min="1"
              step="any"
              value={entryGrams}
              onChange={(event) => setEntryGrams(Number(event.target.value) || 100)}
            />
          </label>
          {numberField('typicalServingG', 'Typical serving (g)')}
          {numberField('per100gCalories', `Calories (per ${entryGrams}g)`)}
          {numberField('per100gProtein', `Protein (g, per ${entryGrams}g)`)}
          {numberField('per100gCarbs', `Carbs (g, per ${entryGrams}g)`)}
          {numberField('per100gFat', `Fat (g, per ${entryGrams}g)`)}
          {numberField('per100gFiber', `Fiber (g, per ${entryGrams}g)`)}
          {numberField('per100gSugar', `Sugar (g, per ${entryGrams}g)`)}
          {numberField('per100gSodiumMg', `Sodium (mg, per ${entryGrams}g)`)}
          {numberField('per100gSaturatedFat', `Saturated fat (g, per ${entryGrams}g)`)}
          {numberField('per100gCholesterolMg', `Cholesterol (mg, per ${entryGrams}g)`)}
          {numberField('per100gPotassiumMg', `Potassium (mg, per ${entryGrams}g)`)}
        </div>
      </Modal>
    </div>
  )
}
