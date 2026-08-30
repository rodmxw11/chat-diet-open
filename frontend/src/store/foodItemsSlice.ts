import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface FoodItem {
  id: number
  name: string
  upc: string | null
  per100gCalories: number | null
  per100gProtein: number | null
  per100gCarbs: number | null
  per100gFat: number | null
  per100gFiber: number | null
  per100gSugar: number | null
  per100gSodiumMg: number | null
  per100gSaturatedFat: number | null
  per100gCholesterolMg: number | null
  per100gPotassiumMg: number | null
  typicalServingG: number | null
  lookupSource: string
  useCount: number
  lastUsedAt: string | null
  deletedAt: string | null
}

export interface FoodItemUpsert {
  name: string
  upc: string | null
  per100gCalories: number | null
  per100gProtein: number | null
  per100gCarbs: number | null
  per100gFat: number | null
  per100gFiber: number | null
  per100gSugar: number | null
  per100gSodiumMg: number | null
  per100gSaturatedFat: number | null
  per100gCholesterolMg: number | null
  per100gPotassiumMg: number | null
  typicalServingG: number | null
  lookupSource: string
}

export interface NutritionLookupResult {
  name: string
  caloriesPer100g: number | null
  proteinPer100g: number | null
  carbsPer100g: number | null
  fatPer100g: number | null
  fiberPer100g: number | null
  sugarPer100g: number | null
  sodiumMgPer100g: number | null
  saturatedFatPer100g: number | null
  cholesterolMgPer100g: number | null
  potassiumMgPer100g: number | null
  typicalServingG: number | null
}

interface FoodItemsState {
  query: string
  includeDeleted: boolean
  items: FoodItem[]
  status: 'idle' | 'loading' | 'error'
}

const initialState: FoodItemsState = {
  query: '',
  includeDeleted: false,
  items: [],
  status: 'idle',
}

export const loadFoodItems = createAsyncThunk(
  'foodItems/load',
  async ({ query, includeDeleted }: { query: string; includeDeleted: boolean }) => {
    const params = new URLSearchParams({ q: query, includeDeleted: String(includeDeleted) })
    const response = await fetch(`/api/food-items?${params}`)
    if (!response.ok) throw new Error(`Food items request failed: ${response.status}`)
    return (await response.json()) as FoodItem[]
  },
)

export const createFoodItem = createAsyncThunk('foodItems/create', async (body: FoodItemUpsert) => {
  const response = await fetch('/api/food-items', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(`Food item create failed: ${response.status}`)
  return (await response.json()) as FoodItem
})

export const updateFoodItem = createAsyncThunk(
  'foodItems/update',
  async ({ id, body }: { id: number; body: FoodItemUpsert }) => {
    const response = await fetch(`/api/food-items/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!response.ok) throw new Error(`Food item update failed: ${response.status}`)
    return (await response.json()) as FoodItem
  },
)

export const deleteFoodItem = createAsyncThunk('foodItems/delete', async (id: number) => {
  const response = await fetch(`/api/food-items/${id}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Food item delete failed: ${response.status}`)
  return id
})

export const restoreFoodItem = createAsyncThunk('foodItems/restore', async (id: number) => {
  const response = await fetch(`/api/food-items/${id}/restore`, { method: 'POST' })
  if (!response.ok) throw new Error(`Food item restore failed: ${response.status}`)
  return (await response.json()) as FoodItem
})

export const lookupFoodItemNutrition = createAsyncThunk('foodItems/lookup', async (query: string) => {
  const response = await fetch(`/api/food-items/lookup?q=${encodeURIComponent(query)}`)
  if (!response.ok) throw new Error(`Lookup failed: ${response.status}`)
  return (await response.json()) as NutritionLookupResult[]
})

export const lookupFoodItemByUpc = createAsyncThunk('foodItems/lookupUpc', async (upc: string) => {
  const response = await fetch(`/api/food-items/lookup-upc?upc=${encodeURIComponent(upc)}`)
  if (!response.ok) throw new Error(`UPC lookup failed: ${response.status}`)
  return (await response.json()) as NutritionLookupResult | null
})

const foodItemsSlice = createSlice({
  name: 'foodItems',
  initialState,
  reducers: {
    setFoodItemsQuery: (state, action: PayloadAction<string>) => {
      state.query = action.payload
    },
    setFoodItemsIncludeDeleted: (state, action: PayloadAction<boolean>) => {
      state.includeDeleted = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadFoodItems.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadFoodItems.fulfilled, (state, action) => {
        state.status = 'idle'
        state.items = action.payload
      })
      .addCase(loadFoodItems.rejected, (state) => {
        state.status = 'error'
      })
      .addCase(createFoodItem.fulfilled, (state, action) => {
        state.items.push(action.payload)
      })
      .addCase(updateFoodItem.fulfilled, (state, action) => {
        const index = state.items.findIndex((item) => item.id === action.payload.id)
        if (index !== -1) state.items[index] = action.payload
      })
      .addCase(deleteFoodItem.fulfilled, (state, action) => {
        if (!state.includeDeleted) {
          state.items = state.items.filter((item) => item.id !== action.payload)
        } else {
          const item = state.items.find((i) => i.id === action.payload)
          if (item) item.deletedAt = new Date().toISOString()
        }
      })
      .addCase(restoreFoodItem.fulfilled, (state, action) => {
        const index = state.items.findIndex((item) => item.id === action.payload.id)
        if (index !== -1) state.items[index] = action.payload
      })
  },
})

export const { setFoodItemsQuery, setFoodItemsIncludeDeleted } = foodItemsSlice.actions
export default foodItemsSlice.reducer
