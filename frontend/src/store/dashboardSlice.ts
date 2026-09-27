import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

// UI state only - the day ranges the macro chart and blood pressure page are showing. The data
// itself lives in dashboardApi's cache.
export type MacroRange = 7 | 30

interface DashboardState {
  range: MacroRange
  bpRange: MacroRange
}

const initialState: DashboardState = {
  range: 7,
  bpRange: 7,
}

const dashboardSlice = createSlice({
  name: 'dashboard',
  initialState,
  reducers: {
    setRange: (state, action: PayloadAction<MacroRange>) => {
      state.range = action.payload
    },
    setBpRange: (state, action: PayloadAction<MacroRange>) => {
      state.bpRange = action.payload
    },
  },
})

export const { setRange, setBpRange } = dashboardSlice.actions
export default dashboardSlice.reducer
