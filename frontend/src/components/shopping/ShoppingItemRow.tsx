import { useAppDispatch } from '../../store/hooks'
import { toggleShoppingItem, type ShoppingItem } from '../../store/shoppingSlice'

export default function ShoppingItemRow({ item }: { item: ShoppingItem }) {
  const dispatch = useAppDispatch()
  const purchased = item.status === 'PURCHASED'

  return (
    <li className={`shopping-item ${purchased ? 'purchased' : ''}`}>
      <button
        type="button"
        className="shopping-item-button"
        onClick={() => dispatch(toggleShoppingItem(item.id))}
        aria-pressed={purchased}
      >
        <span className="shopping-checkbox" aria-hidden="true">
          {purchased && '✓'}
        </span>
        <span className="shopping-item-label">{item.description}</span>
        {item.suggestedStore && <span className="shopping-item-store">{item.suggestedStore}</span>}
      </button>
    </li>
  )
}
