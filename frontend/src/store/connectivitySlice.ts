import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import type { AppDispatch, RootState } from './index'
import { drainScanQueue } from './barcodeQueueSlice'
import { drainEntryQueue, drainQueue } from './chatSlice'

export type ConnectionStatus = 'online' | 'retrying' | 'offline'

interface ConnectivityState {
  status: ConnectionStatus
  retryingSince: string | null
}

const initialState: ConnectivityState = {
  status: 'online',
  retryingSince: null,
}

const RETRY_GIVE_UP_MS = 30 * 60 * 1000

export const pingOnce = createAsyncThunk('connectivity/pingOnce', async () => {
  const response = await fetch('/api/ping')
  if (!response.ok) throw new Error(`Ping failed: ${response.status}`)
})

// Wraps a single ping with the reconnect side effect: draining the offline queue the moment we
// come back online, whether that ping was from the once-a-minute poll or a manual retry.
export const checkConnectivity = createAsyncThunk<void, void, { dispatch: AppDispatch; state: RootState }>(
  'connectivity/check',
  async (_, { dispatch, getState }) => {
    const before = getState().connectivity.status
    await dispatch(pingOnce())
    const after = getState().connectivity.status
    if (after === 'online' && before !== 'online') {
      dispatch(drainQueue())
      dispatch(drainEntryQueue())
      dispatch(drainScanQueue())
    }
  },
)

const connectivitySlice = createSlice({
  name: 'connectivity',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(pingOnce.fulfilled, (state) => {
        state.status = 'online'
        state.retryingSince = null
      })
      .addCase(pingOnce.rejected, (state) => {
        if (state.status === 'online') {
          state.status = 'retrying'
          state.retryingSince = new Date().toISOString()
          return
        }
        if (state.status === 'retrying' && state.retryingSince) {
          const elapsed = Date.now() - Date.parse(state.retryingSince)
          if (elapsed >= RETRY_GIVE_UP_MS) {
            state.status = 'offline'
          }
        }
        // Already offline: stays offline until a manual retry succeeds.
      })
  },
})

export default connectivitySlice.reducer

let monitorStarted = false

/**
 * Pings /api/ping once/minute. Per spec, once the connection has been down long enough to reach
 * `offline` (not just `retrying`), auto-polling stops entirely - only a manual retry (the status
 * light's "try reconnect" action) will ping again from then on. `window online/offline` events are
 * a fast-path hint to check sooner, not a replacement for the polling contract.
 */
export function startConnectivityMonitor(dispatch: AppDispatch, getState: () => RootState) {
  if (monitorStarted) return
  monitorStarted = true

  const tick = () => {
    if (getState().connectivity.status === 'offline') return
    dispatch(checkConnectivity())
  }

  tick()
  setInterval(tick, 60_000)

  window.addEventListener('online', () => dispatch(checkConnectivity()))
  window.addEventListener('offline', () => dispatch(checkConnectivity()))
}
