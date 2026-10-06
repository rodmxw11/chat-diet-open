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

function queuedCount(state: RootState): number {
  return state.chat.queue.length + state.chat.entryQueue.length + state.barcodeQueue.queue.length
}

// One drain at a time: each drain replays its IndexedDB queue and only removes an item after it
// sends, so two overlapping drains would both send the same item - a duplicate log.
let draining = false

async function drainAll(dispatch: AppDispatch) {
  if (draining) return
  draining = true
  try {
    await Promise.all([dispatch(drainQueue()), dispatch(drainEntryQueue()), dispatch(drainScanQueue())])
  } finally {
    draining = false
  }
}

// Wraps a single ping with the replay side effect: draining the offline queues whenever the server
// is reachable and anything is waiting - not only on an offline -> online transition. A send that
// failed in a brief blip (Tailscale waking, a server restart) gets queued while the status never
// left `online`; with a transition-only drain it sat there until the next real outage ended.
export const checkConnectivity = createAsyncThunk<void, void, { dispatch: AppDispatch; state: RootState }>(
  'connectivity/check',
  async (_, { dispatch, getState }) => {
    const before = getState().connectivity.status
    await dispatch(pingOnce())
    const after = getState().connectivity.status
    if (after === 'online' && (before !== 'online' || queuedCount(getState()) > 0)) {
      await drainAll(dispatch)
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
        // Already offline: stays offline until a ping succeeds - polling continues while offline.
      })
  },
})

export default connectivitySlice.reducer

let monitorStarted = false

/**
 * Pings /api/ping once a minute, including while `offline`. Polling used to stop entirely once the
 * connection had been down long enough to reach `offline`, which left the app showing offline -
 * and its queue unsent - after the connection came back (e.g. a phone's Tailscale reconnecting)
 * until a reload or a manual "try reconnect". Also checks whenever the app becomes visible again,
 * the usual moment after a phone sat in a pocket. `window online/offline` events stay as a hint to
 * check sooner; they don't fire for VPN changes like Tailscale reconnecting.
 */
export function startConnectivityMonitor(dispatch: AppDispatch) {
  if (monitorStarted) return
  monitorStarted = true

  const check = () => dispatch(checkConnectivity())

  check()
  setInterval(check, 60_000)

  window.addEventListener('online', check)
  window.addEventListener('offline', check)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') check()
  })
}
