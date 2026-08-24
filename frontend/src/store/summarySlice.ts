import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import type { AppDispatch } from './index'

export interface TodaySummary {
  metabolicDate: string
  targetCalories: number | null
  consumedCalories: number
  remainingCalories: number | null
  entryCount: number
}

interface SummaryState {
  data: TodaySummary | null
  status: 'idle' | 'loading' | 'error'
}

const initialState: SummaryState = {
  data: null,
  status: 'idle',
}

export const loadSummary = createAsyncThunk('summary/load', async () => {
  const response = await fetch('/api/summary/today')
  if (!response.ok) throw new Error(`Summary request failed: ${response.status}`)
  return (await response.json()) as TodaySummary
})

const summarySlice = createSlice({
  name: 'summary',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadSummary.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadSummary.fulfilled, (state, action) => {
        state.status = 'idle'
        state.data = action.payload
      })
      .addCase(loadSummary.rejected, (state) => {
        state.status = 'error'
      })
  },
})

export default summarySlice.reducer

let pollStarted = false

// Polled independently of the chat send/receive cycle so the header stays current even from a
// tool-driven mutation (e.g. logging food via a chat turn started elsewhere) without the whole
// app needing to know when that happened.
export function startSummaryPolling(dispatch: AppDispatch) {
  if (pollStarted) return
  pollStarted = true
  dispatch(loadSummary())
  setInterval(() => dispatch(loadSummary()), 60_000)
}
