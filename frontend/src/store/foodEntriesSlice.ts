import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface FoodEntry {
  id: number
  loggedAt: string
  rawUtterance: string
  totalCalories: number | null
  totalProteinG: number | null
  totalCarbsG: number | null
  totalFatG: number | null
  fiberG: number | null
  sugarG: number | null
  sodiumMg: number | null
  saturatedFatG: number | null
  cholesterolMg: number | null
  potassiumMg: number | null
  foodItemId: number | null
  amountGrams: number | null
}

interface FoodEntriesState {
  date: string
  items: FoodEntry[]
  status: 'idle' | 'loading' | 'error'
}

function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const initialState: FoodEntriesState = {
  date: todayIso(),
  items: [],
  status: 'idle',
}

export const loadFoodEntries = createAsyncThunk('foodEntries/load', async (date: string) => {
  const response = await fetch(`/api/food-entries?date=${date}`)
  if (!response.ok) throw new Error(`Food entries request failed: ${response.status}`)
  return (await response.json()) as FoodEntry[]
})

export const deleteFoodEntry = createAsyncThunk('foodEntries/delete', async (id: number) => {
  const response = await fetch(`/api/food-entries/${id}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Food entry delete failed: ${response.status}`)
  return id
})

const foodEntriesSlice = createSlice({
  name: 'foodEntries',
  initialState,
  reducers: {
    setFoodEntriesDate: (state, action: PayloadAction<string>) => {
      state.date = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadFoodEntries.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadFoodEntries.fulfilled, (state, action) => {
        state.status = 'idle'
        state.items = action.payload
      })
      .addCase(loadFoodEntries.rejected, (state) => {
        state.status = 'error'
      })
      .addCase(deleteFoodEntry.fulfilled, (state, action) => {
        state.items = state.items.filter((item) => item.id !== action.payload)
      })
  },
})

export const { setFoodEntriesDate } = foodEntriesSlice.actions
export default foodEntriesSlice.reducer
