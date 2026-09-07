import { useEffect, useState } from 'react'
import { useAppDispatch, useAppSelector } from '../store/hooks'
import { dismissQuantityPrompt, logScannedFood, type QuantityUnit } from '../store/chatSlice'
import { loadMacros, loadTdeeEstimate, loadWeightTrend } from '../store/dashboardSlice'
import { loadSummary } from '../store/summarySlice'

const UNITS: { value: QuantityUnit; label: string }[] = [
  { value: 'g', label: 'g' },
  { value: 'cal', label: 'cal' },
  { value: 'servings', label: 'servings' },
]

// The terminus of a barcode scan: "How much of <name>?" - type a number, tap a unit, done. Logs
// directly by food_item id (POST /api/food-entries), so no chat draft to finish and no alias
// round trip. Replaces the old "g <name>" draft prefill, which real history shows got sent
// without the grams ever typed.
export default function QuantityPromptBar() {
  const dispatch = useAppDispatch()
  const prompt = useAppSelector((state) => state.chat.quantityPrompt)
  const macroRange = useAppSelector((state) => state.dashboard.range)
  const [amount, setAmount] = useState('')
  const [unit, setUnit] = useState<QuantityUnit>('g')

  // A new scan replaces an unanswered prompt - clear the previous scan's half-typed amount.
  useEffect(() => {
    setAmount('')
    setUnit('g')
  }, [prompt?.foodItemId])

  if (!prompt) return null

  const value = Number(amount)
  const canLog = Number.isFinite(value) && value > 0

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!canLog) return
    dispatch(
      logScannedFood({ foodItemId: prompt.foodItemId, name: prompt.name, amount: value, unit }),
    ).finally(() => {
      dispatch(loadSummary())
      dispatch(loadWeightTrend())
      dispatch(loadTdeeEstimate())
      dispatch(loadMacros(macroRange))
    })
  }

  return (
    <form className="quantity-prompt-bar" onSubmit={submit}>
      <div className="quantity-prompt-header">
        <span className="quantity-prompt-question">How much of {prompt.name}?</span>
        <button
          type="button"
          className="clear-input-button"
          onClick={() => dispatch(dismissQuantityPrompt())}
          title="Dismiss"
          aria-label="Dismiss quantity prompt"
        >
          ✕
        </button>
      </div>
      <div className="quantity-prompt-controls">
        <div className="input-pill quantity-prompt-pill">
          <input
            type="number"
            min="0"
            step="any"
            inputMode="decimal"
            autoFocus
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            placeholder="Amount"
            aria-label={`Amount of ${prompt.name}`}
          />
        </div>
        {UNITS.map(({ value: unitValue, label }) => (
          <button
            key={unitValue}
            type="button"
            className={`quick-entry-button ${unit === unitValue ? 'quantity-unit-active' : ''}`}
            onClick={() => setUnit(unitValue)}
          >
            {label}
          </button>
        ))}
        <button type="submit" className="send-button" disabled={!canLog}>
          Log
        </button>
      </div>
      {unit === 'servings' && prompt.typicalServingG !== null && (
        <span className="quantity-prompt-hint">1 serving = {prompt.typicalServingG} g</span>
      )}
    </form>
  )
}