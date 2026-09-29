import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

export interface MemoryStats {
  heapUsedBytes: number
  heapCommittedBytes: number
  heapMaxBytes: number
  /** Headroom before the heap limit: max minus used. */
  heapFreeBytes: number
  nonHeapUsedBytes: number
  systemTotalBytes: number | null
  systemFreeBytes: number | null
  swapTotalBytes: number | null
  swapFreeBytes: number | null
}

export interface AboutInfo {
  appName: string
  version: string | null
  gitCommit: string | null
  buildTime: string | null
  startupTime: string
  uptimeSeconds: number
  javaRuntime: string
  os: string
  databaseSizeBytes: number | null
  dayRolloverHour: number
  memory: MemoryStats
}

interface AboutState {
  info: AboutInfo | null
  status: 'idle' | 'loading' | 'error'
}

const initialState: AboutState = {
  info: null,
  status: 'idle',
}

export const loadAbout = createAsyncThunk('about/load', async () => {
  const response = await fetch('/api/about')
  if (!response.ok) throw new Error(`About request failed: ${response.status}`)
  return (await response.json()) as AboutInfo
})

const aboutSlice = createSlice({
  name: 'about',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadAbout.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadAbout.fulfilled, (state, action) => {
        state.status = 'idle'
        state.info = action.payload
      })
      .addCase(loadAbout.rejected, (state) => {
        state.status = 'error'
      })
  },
})

export default aboutSlice.reducer
