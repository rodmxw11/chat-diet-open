import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

export type ShoppingStatus = 'PENDING' | 'PURCHASED'

export interface ShoppingItem {
  id: number
  description: string
  foodItemId: number | null
  suggestedStore: string | null
  status: ShoppingStatus
  addedAt: string
  purchasedAt: string | null
}

interface ShoppingState {
  items: ShoppingItem[]
  status: 'idle' | 'loading' | 'error'
}

const initialState: ShoppingState = {
  items: [],
  status: 'idle',
}

// Pending group first, purchased group at the bottom, each group chronological by when it entered
// that state - matches the Google-Keep-style behavior described in the design handoff.
function sortItems(items: ShoppingItem[]): ShoppingItem[] {
  return [...items].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'PENDING' ? -1 : 1
    if (a.status === 'PENDING') return a.addedAt.localeCompare(b.addedAt)
    return (a.purchasedAt ?? '').localeCompare(b.purchasedAt ?? '')
  })
}

export const loadShoppingItems = createAsyncThunk('shopping/load', async () => {
  const response = await fetch('/api/shopping-items')
  if (!response.ok) throw new Error(`Shopping list request failed: ${response.status}`)
  return (await response.json()) as ShoppingItem[]
})

export const toggleShoppingItem = createAsyncThunk('shopping/toggle', async (id: number) => {
  const response = await fetch(`/api/shopping-items/${id}/toggle`, { method: 'POST' })
  if (!response.ok) throw new Error(`Toggle request failed: ${response.status}`)
  return (await response.json()) as ShoppingItem
})

const shoppingSlice = createSlice({
  name: 'shopping',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadShoppingItems.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadShoppingItems.fulfilled, (state, action) => {
        state.status = 'idle'
        state.items = sortItems(action.payload)
      })
      .addCase(loadShoppingItems.rejected, (state) => {
        state.status = 'error'
      })
      .addCase(toggleShoppingItem.pending, (state, action) => {
        // Optimistic flip so the checkbox and reflow happen immediately; reconciled with the
        // server's copy in .fulfilled (or reverted in .rejected).
        const item = state.items.find((i) => i.id === action.meta.arg)
        if (item) {
          item.status = item.status === 'PENDING' ? 'PURCHASED' : 'PENDING'
          item.purchasedAt = item.status === 'PURCHASED' ? new Date().toISOString() : null
          state.items = sortItems(state.items)
        }
      })
      .addCase(toggleShoppingItem.fulfilled, (state, action) => {
        const index = state.items.findIndex((i) => i.id === action.payload.id)
        if (index !== -1) {
          state.items[index] = action.payload
          state.items = sortItems(state.items)
        }
      })
      .addCase(toggleShoppingItem.rejected, (state, action) => {
        // Revert the optimistic flip.
        const item = state.items.find((i) => i.id === action.meta.arg)
        if (item) {
          item.status = item.status === 'PENDING' ? 'PURCHASED' : 'PENDING'
          item.purchasedAt = item.status === 'PURCHASED' ? new Date().toISOString() : null
          state.items = sortItems(state.items)
        }
      })
  },
})

export default shoppingSlice.reducer
