import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

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
