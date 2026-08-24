import { useState } from 'react'
import { useAppDispatch } from '../../store/hooks'
import { sendMessage, type FoodItemOption } from '../../store/chatSlice'
import { setScreen } from '../../store/uiSlice'

// Renders ChatMessage.foodItemOptions inline in the assistant bubble, following the same pattern
// as ChartRenderer/SqlResultTable for optional structured chat-response fields.
export default function FoodItemPicker({ options }: { options: FoodItemOption[] }) {
  const dispatch = useAppDispatch()
  const [checked, setChecked] = useState<Set<number>>(new Set())

  const toggle = (id: number) => {
    setChecked((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const selected = options.filter((option) => checked.has(option.id))

  const confirm = () => {
    if (selected.length === 0) return
    const names = selected.map((option) => option.name).join(', ')
    // Round-trips through chat as synthesized natural-language text, staying consistent with the
    // model-drives-all-writes architecture instead of adding a parallel non-chat mutation path.
    dispatch(sendMessage(`Add these to my shopping list: ${names}`))
    dispatch(setScreen('shop'))
  }

  return (
    <div className="food-item-picker">
      <ul className="food-item-picker-list">
        {options.map((option) => (
          <li key={option.id}>
            <button
              type="button"
              className="food-item-picker-row"
              onClick={() => toggle(option.id)}
              aria-pressed={checked.has(option.id)}
            >
              <span className="food-item-picker-checkbox" aria-hidden="true">
                {checked.has(option.id) && '✓'}
              </span>
              <span className="food-item-picker-label">{option.name}</span>
              {option.typicalServingG !== null && (
                <span className="food-item-picker-qty">{Math.round(option.typicalServingG)}g</span>
              )}
            </button>
          </li>
        ))}
      </ul>
      <button
        type="button"
        className="food-item-picker-confirm"
        onClick={confirm}
        disabled={selected.length === 0}
      >
        Add {selected.length} to shopping list
      </button>
    </div>
  )
}
