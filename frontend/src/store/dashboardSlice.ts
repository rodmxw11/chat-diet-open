import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface DailyMacros {
  date: string
  proteinG: number
  carbsG: number
  fatG: number
  calories: number
}

export interface WeighIn {
  date: string
  weightLbs: number
}

export interface TrendPoint {
  date: string
  value: number
}

export interface GoalLine {
  startDate: string
  startWeightLbs: number
  dailyRateLbs: number
}

export interface WeightTrendResponse {
  actual: WeighIn[]
  smoothed: TrendPoint[]
  goal: GoalLine | null
}

export interface TdeeStatus {
  estimatedCalories: number | null
  standardErrorCalories: number | null
  windowDays: number | null
  loggedDays: number | null
  weightChangeLbs: number | null
  caveat: string | null
  unavailableReason: string | null
}

export type MacroRange = 7 | 30

interface DashboardState {
  range: MacroRange
  macros: DailyMacros[]
  macrosStatus: 'idle' | 'loading' | 'error'
  weightTrend: WeightTrendResponse | null
  weightTrendStatus: 'idle' | 'loading' | 'error'
  tdee: TdeeStatus | null
  tdeeStatus: 'idle' | 'loading' | 'error'
}

const initialState: DashboardState = {
  range: 7,
  macros: [],
  macrosStatus: 'idle',
  weightTrend: null,
  weightTrendStatus: 'idle',
  tdee: null,
  tdeeStatus: 'idle',
}

export const loadMacros = createAsyncThunk('dashboard/loadMacros', async (days: MacroRange) => {
  const response = await fetch(`/api/dashboard/macros?days=${days}`)
  if (!response.ok) throw new Error(`Macro chart request failed: ${response.status}`)
  return (await response.json()) as DailyMacros[]
})

export const loadWeightTrend = createAsyncThunk('dashboard/loadWeightTrend', async () => {
  const response = await fetch('/api/dashboard/weight-trend')
  if (!response.ok) throw new Error(`Weight trend request failed: ${response.status}`)
  return (await response.json()) as WeightTrendResponse
})

export const loadTdeeEstimate = createAsyncThunk('dashboard/loadTdeeEstimate', async () => {
  const response = await fetch('/api/dashboard/tdee')
  if (!response.ok) throw new Error(`TDEE request failed: ${response.status}`)
  return (await response.json()) as TdeeStatus
})

const dashboardSlice = createSlice({
  name: 'dashboard',
  initialState,
  reducers: {
    setRange: (state, action: PayloadAction<MacroRange>) => {
      state.range = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadMacros.pending, (state) => {
        state.macrosStatus = 'loading'
      })
      .addCase(loadMacros.fulfilled, (state, action) => {
        state.macrosStatus = 'idle'
        state.macros = action.payload
      })
      .addCase(loadMacros.rejected, (state) => {
        state.macrosStatus = 'error'
      })
      .addCase(loadWeightTrend.pending, (state) => {
        state.weightTrendStatus = 'loading'
      })
      .addCase(loadWeightTrend.fulfilled, (state, action) => {
        state.weightTrendStatus = 'idle'
        state.weightTrend = action.payload
      })
      .addCase(loadWeightTrend.rejected, (state) => {
        state.weightTrendStatus = 'error'
      })
      .addCase(loadTdeeEstimate.pending, (state) => {
        state.tdeeStatus = 'loading'
      })
      .addCase(loadTdeeEstimate.fulfilled, (state, action) => {
        state.tdeeStatus = 'idle'
        state.tdee = action.payload
      })
      .addCase(loadTdeeEstimate.rejected, (state) => {
        state.tdeeStatus = 'error'
      })
  },
})

export const { setRange } = dashboardSlice.actions
export default dashboardSlice.reducer
