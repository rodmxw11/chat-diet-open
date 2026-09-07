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

export interface FoodAlias {
  id: number
  aliasNormalized: string
  foodItemId: number
  source: string
  createdAt: string
}

export interface PortionUnit {
  id: number
  foodItemId: number
  unitName: string
  grams: number
  source: string
}

interface FoodItemsState {
  query: string
  includeDeleted: boolean
  items: FoodItem[]
  status: 'idle' | 'loading' | 'error'
  /** Set when a barcode scan couldn't resolve identity anywhere - opens the "new item, UPC
      prebound" modal instead of the barcode round-tripping through chat as raw digits. */
  prebindUpc: string | null
  aliases: FoodAlias[]
  portionUnits: PortionUnit[]
  /** A visible alias-add failure (usually a 409: the phrase already belongs to another item). */
  aliasError: string | null
}

const initialState: FoodItemsState = {
  query: '',
  includeDeleted: false,
  items: [],
  status: 'idle',
  prebindUpc: null,
  aliases: [],
  portionUnits: [],
  aliasError: null,
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

export const loadFoodAliases = createAsyncThunk('foodItems/loadAliases', async (foodItemId: number) => {
  const response = await fetch(`/api/food-items/${foodItemId}/aliases`)
  if (!response.ok) throw new Error(`Alias list failed: ${response.status}`)
  return (await response.json()) as FoodAlias[]
})

interface AliasConflictBody {
  alias: string
  owningItemId: number
  owningItemName: string
}

export const addFoodAlias = createAsyncThunk<
  FoodAlias,
  { foodItemId: number; alias: string },
  { rejectValue: string }
>('foodItems/addAlias', async ({ foodItemId, alias }, { rejectWithValue }) => {
  const response = await fetch(`/api/food-items/${foodItemId}/aliases`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ alias }),
  })
  if (response.status === 409) {
    const conflict = (await response.json()) as AliasConflictBody
    return rejectWithValue(`"${conflict.alias}" is already an alias of "${conflict.owningItemName}"`)
  }
  if (!response.ok) throw new Error(`Alias add failed: ${response.status}`)
  return (await response.json()) as FoodAlias
})

export const removeFoodAlias = createAsyncThunk('foodItems/removeAlias', async (aliasId: number) => {
  const response = await fetch(`/api/food-items/aliases/${aliasId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Alias remove failed: ${response.status}`)
  return aliasId
})

export const loadPortionUnits = createAsyncThunk('foodItems/loadPortionUnits', async (foodItemId: number) => {
  const response = await fetch(`/api/food-items/${foodItemId}/portion-units`)
  if (!response.ok) throw new Error(`Portion unit list failed: ${response.status}`)
  return (await response.json()) as PortionUnit[]
})

export const removePortionUnit = createAsyncThunk('foodItems/removePortionUnit', async (portionUnitId: number) => {
  const response = await fetch(`/api/food-items/portion-units/${portionUnitId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Portion unit remove failed: ${response.status}`)
  return portionUnitId
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
    openUpcPrebind: (state, action: PayloadAction<string>) => {
      state.prebindUpc = action.payload
    },
    clearUpcPrebind: (state) => {
      state.prebindUpc = null
    },
    clearAliasError: (state) => {
      state.aliasError = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadFoodAliases.fulfilled, (state, action) => {
        state.aliases = action.payload
      })
      .addCase(addFoodAlias.fulfilled, (state, action) => {
        state.aliasError = null
        // The idempotent re-add case returns an alias that may already be in the list.
        if (!state.aliases.some((a) => a.id === action.payload.id)) {
          state.aliases.push(action.payload)
        }
      })
      .addCase(addFoodAlias.rejected, (state, action) => {
        state.aliasError = action.payload ?? 'Adding that alias failed.'
      })
      .addCase(removeFoodAlias.fulfilled, (state, action) => {
        state.aliases = state.aliases.filter((a) => a.id !== action.payload)
      })
      .addCase(loadPortionUnits.fulfilled, (state, action) => {
        state.portionUnits = action.payload
      })
      .addCase(removePortionUnit.fulfilled, (state, action) => {
        state.portionUnits = state.portionUnits.filter((p) => p.id !== action.payload)
      })
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

export const { setFoodItemsQuery, setFoodItemsIncludeDeleted, openUpcPrebind, clearUpcPrebind, clearAliasError } =
  foodItemsSlice.actions
export default foodItemsSlice.reducer
