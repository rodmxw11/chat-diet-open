import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { enqueueScan, listQueuedScans, removeQueuedScan, type QueuedScan } from '../lib/offlineQueue'
import { setDraftTextWithCursorStart } from './chatSlice'
import { openUpcPrebind } from './foodItemsSlice'
import { setScreen } from './uiSlice'

interface ResolveResponse {
  upc: string
  resolvedName: string | null
  needsManualEntry: boolean
}

interface BarcodeQueueState {
  queue: QueuedScan[]
}

const initialState: BarcodeQueueState = {
  queue: [],
}

// Same downstream handling for a resolved UPC everywhere it can happen: a live scan resolved
// immediately, the snap-a-photo fallback, or a previously-queued scan resolved after reconnecting -
// one place so all three stay in sync instead of three copies of the same if/else.
export const applyUpcResolution = createAsyncThunk(
  'barcodeQueue/applyResolution',
  async (response: ResolveResponse, { dispatch }) => {
    if (response.needsManualEntry || !response.resolvedName) {
      // Nothing resolved anywhere - the manual-entry modal on the Food Items page is the
      // terminus, prebound with this UPC so the label can be typed in directly.
      dispatch(openUpcPrebind(response.upc))
      dispatch(setScreen('foodItems'))
      return
    }

    // Identity resolved server-side and is guaranteed an exact alias hit - prefill the amount
    // field with the resolved name, cursor at the start, instead of round-tripping the raw UPC
    // digits through a chat turn.
    dispatch(setDraftTextWithCursorStart(`g ${response.resolvedName}`))
  },
)

interface QueuedRejection {
  queued: true
  record: QueuedScan
}

// Resolves a UPC the live scanner (or its snap-a-photo fallback) already decoded. A TypeError here
// means the fetch never reached the network at all (offline/DNS/etc.), as opposed to the server
// responding with an HTTP error status - queue it in IndexedDB, same handling as a chat message
// sent while offline, so the scan isn't just lost.
export const resolveUpc = createAsyncThunk<void, string, { rejectValue: QueuedRejection }>(
  'barcodeQueue/resolveUpc',
  async (upc, { dispatch, rejectWithValue }) => {
    let response: Response
    try {
      response = await fetch(`/api/barcode/resolve?upc=${encodeURIComponent(upc)}`)
    } catch (error) {
      if (error instanceof TypeError) {
        const record = await enqueueScan(upc)
        return rejectWithValue({ queued: true, record })
      }
      throw error
    }
    if (!response.ok) {
      throw new Error(`Barcode resolve failed: ${response.status}`)
    }
    await dispatch(applyUpcResolution(await response.json()))
  },
)

// Replays every queued scan in order, stopping at the first failure so the rest stay queued - the
// last one to resolve is the one left sitting in the chat draft, same as scanning it live just now
// would; any earlier ones in the batch still got upserted into food_item along the way, just not
// left in the (single) draft box.
export const drainScanQueue = createAsyncThunk('barcodeQueue/drainQueue', async (_, { dispatch }) => {
  const items = await listQueuedScans()
  for (const item of items) {
    try {
      const response = await fetch(`/api/barcode/resolve?upc=${encodeURIComponent(item.upc)}`)
      if (!response.ok) throw new Error(`Barcode resolve failed: ${response.status}`)
      await removeQueuedScan(item.id)
      dispatch(scanDequeued(item.id))
      await dispatch(applyUpcResolution(await response.json()))
    } catch {
      break
    }
  }
})

export const loadScanQueue = createAsyncThunk('barcodeQueue/loadQueue', async () => listQueuedScans())

export const deleteQueuedScan = createAsyncThunk('barcodeQueue/deleteQueued', async (id: string) => {
  await removeQueuedScan(id)
  return id
})

const barcodeQueueSlice = createSlice({
  name: 'barcodeQueue',
  initialState,
  reducers: {
    scanDequeued: (state, action) => {
      state.queue = state.queue.filter((item) => item.id !== action.payload)
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadScanQueue.fulfilled, (state, action) => {
        state.queue = action.payload
      })
      .addCase(resolveUpc.rejected, (state, action) => {
        if (action.payload?.queued) {
          state.queue.push(action.payload.record)
        }
      })
      .addCase(deleteQueuedScan.fulfilled, (state, action) => {
        state.queue = state.queue.filter((item) => item.id !== action.payload)
      })
  },
})

export const { scanDequeued } = barcodeQueueSlice.actions
export default barcodeQueueSlice.reducer
