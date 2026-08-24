import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setScreen } from '../../store/uiSlice'
import { loadShoppingItems } from '../../store/shoppingSlice'
import ShoppingItemRow from './ShoppingItemRow'

// Full-page view that replaces the chat screen entirely (not an overlay/sheet), reached via the
// header menu or the food-item picker's confirm button.
export default function ShoppingModeView() {
  const dispatch = useAppDispatch()
  const items = useAppSelector((state) => state.shopping.items)
  const status = useAppSelector((state) => state.shopping.status)

  useEffect(() => {
    dispatch(loadShoppingItems())
  }, [dispatch])

  const doneCount = items.filter((item) => item.status === 'PURCHASED').length

  return (
    <div className="shop-screen">
      <header className="app-header">
        <button
          type="button"
          className="back-button"
          onClick={() => dispatch(setScreen('chat'))}
          aria-label="Back to chat"
        >
          ←
        </button>
        <div className="shop-header-text">
          <span className="shop-title">Shopping</span>
          <span className="shop-progress">
            {doneCount} of {items.length} done
          </span>
        </div>
      </header>
      <div className="shop-list-wrapper">
        {status === 'loading' && items.length === 0 && <p className="shop-empty">Loading…</p>}
        {status !== 'loading' && items.length === 0 && <p className="shop-empty">Nothing on your shopping list yet.</p>}
        <ul className="shopping-list">
          {items.map((item) => (
            <ShoppingItemRow key={item.id} item={item} />
          ))}
        </ul>
      </div>
    </div>
  )
}
