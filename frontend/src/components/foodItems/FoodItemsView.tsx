import { useEffect, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { openQuantityPrompt } from '../../store/chatSlice'
import { setScreen } from '../../store/uiSlice'
import {
  addFoodAlias,
  clearAliasError,
  clearUpcPrebind,
  createFoodItem,
  deleteFoodItem,
  loadFoodAliases,
  loadFoodItems,
  loadPortionUnits,
  lookupFoodItemByUpc,
  lookupFoodItemNutrition,
  removeFoodAlias,
  removePortionUnit,
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
  const prebindUpc = useAppSelector((state) => state.foodItems.prebindUpc)
  const aliases = useAppSelector((state) => state.foodItems.aliases)
  const portionUnits = useAppSelector((state) => state.foodItems.portionUnits)
  const aliasError = useAppSelector((state) => state.foodItems.aliasError)

  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<FoodItemUpsert>(BLANK_FORM)
  const [modalOpen, setModalOpen] = useState(false)
  const [entryGrams, setEntryGrams] = useState(100)
  const [lookupStatus, setLookupStatus] = useState<'idle' | 'loading' | 'notfound'>('idle')
  const [candidates, setCandidates] = useState<NutritionLookupResult[]>([])
  const [upcLookupStatus, setUpcLookupStatus] = useState<'idle' | 'loading' | 'notfound'>('idle')
  const [fromPrebind, setFromPrebind] = useState(false)
  const [portionUnitName, setPortionUnitName] = useState('')
  const [portionUnitGrams, setPortionUnitGrams] = useState('')
  const [newAlias, setNewAlias] = useState('')
  /** Aliases typed before Save; flushed to the server on Save, failures kept here and shown. */
  const [pendingAliases, setPendingAliases] = useState<string[]>([])

  useEffect(() => {
    dispatch(loadFoodItems({ query, includeDeleted }))
  }, [dispatch, query, includeDeleted])

  // A barcode scan that missed everywhere routes here, UPC prebound, instead of stranding the
  // user on this page mid-sandwich - the modal opens automatically with the scanned code filled in.
  useEffect(() => {
    if (prebindUpc === null) return
    setEditingId(null)
    setForm({ ...BLANK_FORM, upc: prebindUpc })
    setEntryGrams(100)
    setLookupStatus('idle')
    setCandidates([])
    setUpcLookupStatus('idle')
    setFromPrebind(true)
    setPortionUnitName('')
    setPortionUnitGrams('')
    setNewAlias('')
    setPendingAliases([])
    dispatch(clearAliasError())
    setModalOpen(true)
    dispatch(clearUpcPrebind())
  }, [dispatch, prebindUpc])

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
    setFromPrebind(false)
    setPortionUnitName('')
    setPortionUnitGrams('')
    setNewAlias('')
    setPendingAliases([])
    dispatch(clearAliasError())
    setModalOpen(true)
  }

  const openEdit = (item: FoodItem) => {
    setEditingId(item.id)
    setForm(toFormState(item))
    setEntryGrams(100)
    setLookupStatus('idle')
    setCandidates([])
    setUpcLookupStatus('idle')
    setFromPrebind(false)
    setPortionUnitName('')
    setPortionUnitGrams('')
    setNewAlias('')
    setPendingAliases([])
    dispatch(clearAliasError())
    dispatch(loadFoodAliases(item.id))
    dispatch(loadPortionUnits(item.id))
    setModalOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) return
    const payload = scaledForSave(form, entryGrams)
    const wasPrebind = fromPrebind

    let savedItem: FoodItem
    if (editingId === null) {
      savedItem = await dispatch(createFoodItem(payload)).unwrap()
    } else {
      savedItem = await dispatch(updateFoodItem({ id: editingId, body: payload })).unwrap()
    }

    const unitGrams = Number(portionUnitGrams)
    if (portionUnitName.trim() && unitGrams > 0) {
      await fetch(`/api/food-items/${savedItem.id}/portion-units`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ unitName: portionUnitName.trim(), grams: unitGrams }),
      })
      setPortionUnitName('')
      setPortionUnitGrams('')
    }

    // Flush queued aliases now that the item has an id. A collision (409) is a visible failure,
    // not fake success: the item saved, the alias didn't - stay open in edit mode showing which.
    const failed: string[] = []
    for (const alias of pendingAliases) {
      try {
        await dispatch(addFoodAlias({ foodItemId: savedItem.id, alias })).unwrap()
      } catch {
        failed.push(alias)
      }
    }
    if (failed.length > 0) {
      setPendingAliases(failed)
      setEditingId(savedItem.id)
      dispatch(loadFoodAliases(savedItem.id))
      dispatch(loadPortionUnits(savedItem.id))
      return
    }
    setPendingAliases([])

    setModalOpen(false)

    if (wasPrebind) {
      // Don't strand the user here mid-sandwich - hand back to chat with the quantity prompt
      // open for the just-saved item, same as a normal scan resolution.
      dispatch(
        openQuantityPrompt({
          foodItemId: savedItem.id,
          name: savedItem.name,
          typicalServingG: savedItem.typicalServingG,
        }),
      )
      dispatch(setScreen('chat'))
    }
  }

  // Aliases queue locally and are written on Save (a new item has no id to attach them to yet;
  // queueing in both modes keeps one mental model).
  const handleAddAlias = () => {
    const trimmed = newAlias.trim()
    if (!trimmed) return
    dispatch(clearAliasError())
    if (!pendingAliases.includes(trimmed)) {
      setPendingAliases([...pendingAliases, trimmed])
    }
    setNewAlias('')
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
            placeholder="Search by name or alias…"
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

          {editingId !== null && portionUnits.length > 0 && (
            <div className="food-item-field food-item-field--wide">
              <span>Household measures</span>
              <ul className="food-item-lookup-candidates">
                {portionUnits.map((unit) => (
                  <li key={unit.id}>
                    {unit.unitName} = {unit.grams} g ({unit.source})
                    <button
                      type="button"
                      className="foods-delete-button"
                      onClick={() => dispatch(removePortionUnit(unit.id))}
                      aria-label={`Remove measure ${unit.unitName}`}
                      title="Remove this measure"
                    >
                      ✕
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <label className="food-item-field food-item-field--wide">
            <span>Household measure (optional) - e.g. "slice"</span>
            <input
              type="text"
              value={portionUnitName}
              onChange={(event) => setPortionUnitName(event.target.value)}
              placeholder="slice"
            />
          </label>
          <label className="food-item-field">
            <span>...weighs how many grams?</span>
            <input
              type="number"
              min="0"
              step="any"
              value={portionUnitGrams}
              onChange={(event) => setPortionUnitGrams(event.target.value)}
            />
          </label>

          <div className="food-item-field food-item-field--wide">
            <span>Aliases (other names this food resolves from)</span>
            <ul className="food-item-lookup-candidates">
              {editingId !== null &&
                aliases.map((alias) => (
                  <li key={alias.id}>
                    {alias.aliasNormalized} ({alias.source})
                    <button
                      type="button"
                      className="foods-delete-button"
                      onClick={() => dispatch(removeFoodAlias(alias.id))}
                      aria-label={`Remove alias ${alias.aliasNormalized}`}
                      title="Remove this alias"
                    >
                      ✕
                    </button>
                  </li>
                ))}
              {pendingAliases.map((alias) => (
                <li key={`pending-${alias}`}>
                  {alias} <em>(saved with the item)</em>
                  <button
                    type="button"
                    className="foods-delete-button"
                    onClick={() => setPendingAliases(pendingAliases.filter((a) => a !== alias))}
                    aria-label={`Remove pending alias ${alias}`}
                    title="Remove this alias"
                  >
                    ✕
                  </button>
                </li>
              ))}
              {(editingId === null || aliases.length === 0) && pendingAliases.length === 0 && (
                <li>No aliases yet.</li>
              )}
            </ul>
            <div className="food-item-lookup-row">
              <input
                type="text"
                value={newAlias}
                onChange={(event) => setNewAlias(event.target.value)}
                placeholder="Add an alias…"
              />
              <button type="button" className="today-button" onClick={handleAddAlias} disabled={!newAlias.trim()}>
                Add
              </button>
            </div>
            {aliasError && <span className="food-item-lookup-status">{aliasError}</span>}
          </div>
        </div>
      </Modal>
    </div>
  )
}
