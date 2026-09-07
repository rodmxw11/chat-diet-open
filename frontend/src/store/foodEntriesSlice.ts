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
  /** Whether `date` has been deliberately set yet (by the metabolic-day correction, prev/next
      nav, the date picker, or jumping in from a chart bar) - guards applyMetabolicDate below. */
  dateInitialized: boolean
}

function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const initialState: FoodEntriesState = {
  date: todayIso(),
  items: [],
  status: 'idle',
  dateInitialized: false,
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
      state.dateInitialized = true
    },
    // The Daily Foods screen unmounts/remounts every time you navigate away and back (AppShell
    // conditionally renders it), so a component-local "have I corrected yet" ref resets on every
    // visit and would clobber a date the caller deliberately set (e.g. a macro-chart bar click)
    // right before switching screens. Tracking dateInitialized in the slice instead makes the
    // metabolic-day correction a true one-time-per-session default, not a per-mount reset.
    applyMetabolicDate: (state, action: PayloadAction<string>) => {
      if (state.dateInitialized) return
      state.date = action.payload
      state.dateInitialized = true
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

export const { setFoodEntriesDate, applyMetabolicDate } = foodEntriesSlice.actions
export default foodEntriesSlice.reducer
