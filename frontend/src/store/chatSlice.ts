import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import {
  enqueue,
  enqueueEntry,
  listQueued,
  listQueuedEntries,
  removeQueued,
  removeQueuedEntry,
  type QueuedEntry,
  type QueuedRequest,
} from '../lib/offlineQueue'
import { loadMacros, loadTdeeEstimate, loadWeightTrend } from './dashboardSlice'
import { loadSummary } from './summarySlice'
import type { RootState } from './index'

export interface SeriesPoint {
  at: string
  value: number
  target: number | null
}

export interface ChartSeries {
  label: string
  points: SeriesPoint[]
}

export interface SqlAnswer {
  sqlText: string
  columns: string[]
  rows: unknown[][]
  truncated: boolean
  totalRows: number
  csvId: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  chartSeries?: ChartSeries[]
  sqlAnswer?: SqlAnswer
  /** True while this message is sitting in the offline queue, not yet delivered to the server. */
  queued?: boolean
  queuedAt?: string
}

/** A barcode scan resolved this item - the quantity prompt bar asks "how much?" and logs by id. */
export interface QuantityPrompt {
  foodItemId: number
  name: string
  typicalServingG: number | null
}

export type QuantityUnit = 'g' | 'cal' | 'servings'

interface ChatState {
  messages: ChatMessage[]
  status: 'idle' | 'loading' | 'error'
  ttsEnabled: boolean
  draftText: string
  /** Mirrors the IndexedDB offline queue for the queue panel; kept in sync by queue-touching thunks. */
  queue: QueuedRequest[]
  /** Offline-queued quantity-prompt submits, mirrored from IndexedDB like {@link queue}. */
  entryQueue: QueuedEntry[]
  quantityPrompt: QuantityPrompt | null
}

const initialState: ChatState = {
  messages: [],
  status: 'idle',
  ttsEnabled: false,
  draftText: '',
  queue: [],
  entryQueue: [],
  quantityPrompt: null,
}

interface ChatApiResponse {
  reply: string
  chartSeries: ChartSeries[] | null
  sqlAnswer: SqlAnswer | null
}

async function postChat(text: string, clientSentAt: string): Promise<ChatApiResponse> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, clientSentAt }),
  })
  if (!response.ok) {
    throw new Error(`Chat request failed: ${response.status}`)
  }
  return (await response.json()) as ChatApiResponse
}

interface QueuedRejection {
  queued: true
  record: QueuedRequest
}

export const sendMessage = createAsyncThunk<ChatApiResponse, string, { rejectValue: QueuedRejection }>(
  'chat/sendMessage',
  async (text, { rejectWithValue }) => {
    const clientSentAt = new Date().toISOString()
    try {
      return await postChat(text, clientSentAt)
    } catch (error) {
      // A TypeError here means the fetch never reached the network at all (offline/DNS/etc.), as
      // opposed to the server responding with an HTTP error status. Queue it in our own IndexedDB
      // queue - explicit and inspectable, unlike the old invisible Workbox background-sync queue.
      if (error instanceof TypeError) {
        const record = await enqueue(text, clientSentAt)
        return rejectWithValue({ queued: true, record })
      }
      throw error
    }
  },
)

// Replays every queued request in order, stopping at the first failure so the rest stay queued -
// order matters because ChatController.replyAt depends on clientSentAt ordering.
export const drainQueue = createAsyncThunk('chat/drainQueue', async (_, { dispatch, getState }) => {
  const items = await listQueued()
  let sentAny = false
  for (const item of items) {
    try {
      const data = await postChat(item.text, item.clientSentAt)
      await removeQueued(item.id)
      dispatch(queueItemSent({ id: item.id, response: data }))
      sentAny = true
    } catch {
      break
    }
  }
  // Replayed turns can log food or weight same as any other - refresh the header and dashboard
  // charts rather than leaving them stuck at whatever they showed before reconnecting.
  if (sentAny) {
    dispatch(loadSummary())
    dispatch(loadWeightTrend())
    dispatch(loadTdeeEstimate())
    dispatch(loadMacros((getState() as RootState).dashboard.range))
  }
})

// Hydrates queued-but-unsent messages back into the chat window on app boot, since redux state
// doesn't survive a reload but the IndexedDB queue does.
export const loadQueue = createAsyncThunk('chat/loadQueue', async () => listQueued())

export const deleteQueuedMessage = createAsyncThunk('chat/deleteQueuedMessage', async (id: string) => {
  await removeQueued(id)
  return id
})

function formatAmount(amount: number, unit: QuantityUnit): string {
  if (unit === 'g') return `${amount}g`
  if (unit === 'cal') return `${amount} cal`
  return amount === 1 ? '1 serving' : `${amount} servings`
}

/** The user half of a quantity-prompt exchange - matches what the server persists to history. */
export function scannedEntryText(name: string, amount: number, unit: QuantityUnit): string {
  return `Scanned ${name}: ${formatAmount(amount, unit)}`
}

interface FoodEntryApiResponse {
  reply: string
}

async function postFoodEntry(
  foodItemId: number,
  amount: number,
  unit: QuantityUnit,
  clientSentAt: string,
): Promise<FoodEntryApiResponse> {
  const response = await fetch('/api/food-entries', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      foodItemId,
      clientSentAt,
      grams: unit === 'g' ? amount : null,
      kcal: unit === 'cal' ? amount : null,
      servings: unit === 'servings' ? amount : null,
    }),
  })
  if (!response.ok) {
    throw new Error(`Food entry failed: ${response.status}`)
  }
  return (await response.json()) as FoodEntryApiResponse
}

interface EntryQueuedRejection {
  queued: true
  record: QueuedEntry
}

export interface LogScannedFoodArgs {
  foodItemId: number
  name: string
  amount: number
  unit: QuantityUnit
}

// The quantity prompt's submit: logs directly by food_item id via POST /api/food-entries - no
// chat round trip, no alias lookup - and queues offline with its original clientSentAt so a
// replayed entry still backdates to when it was actually eaten.
export const logScannedFood = createAsyncThunk<
  FoodEntryApiResponse,
  LogScannedFoodArgs,
  { rejectValue: EntryQueuedRejection }
>('chat/logScannedFood', async ({ foodItemId, name, amount, unit }, { rejectWithValue }) => {
  const clientSentAt = new Date().toISOString()
  try {
    return await postFoodEntry(foodItemId, amount, unit, clientSentAt)
  } catch (error) {
    if (error instanceof TypeError) {
      const record = await enqueueEntry({ foodItemId, name, amount, unit, clientSentAt })
      return rejectWithValue({ queued: true, record })
    }
    throw error
  }
})

// Replays offline-queued quantity-prompt entries in order, stopping at the first failure.
export const drainEntryQueue = createAsyncThunk('chat/drainEntryQueue', async (_, { dispatch, getState }) => {
  const items = await listQueuedEntries()
  let sentAny = false
  for (const item of items) {
    try {
      const data = await postFoodEntry(item.foodItemId, item.amount, item.unit, item.clientSentAt)
      await removeQueuedEntry(item.id)
      dispatch(entryQueueItemSent({ id: item.id, reply: data.reply }))
      sentAny = true
    } catch {
      break
    }
  }
  if (sentAny) {
    dispatch(loadSummary())
    dispatch(loadWeightTrend())
    dispatch(loadTdeeEstimate())
    dispatch(loadMacros((getState() as RootState).dashboard.range))
  }
})

export const loadEntryQueue = createAsyncThunk('chat/loadEntryQueue', async () => listQueuedEntries())

export const deleteQueuedEntry = createAsyncThunk('chat/deleteQueuedEntry', async (id: string) => {
  await removeQueuedEntry(id)
  return id
})

interface HistoryApiMessage {
  role: 'user' | 'assistant'
  text: string
  at: string
}

interface HistoryApiResponse {
  metabolicDate: string
  messages: HistoryApiMessage[]
}

async function fetchHistoryMessages(): Promise<HistoryApiMessage[]> {
  const response = await fetch('/api/chat/history')
  if (!response.ok) {
    throw new Error(`History request failed: ${response.status}`)
  }
  const data: HistoryApiResponse = await response.json()
  return data.messages
}

// The conversation is scoped to the metabolic day, not to this tab, so a reload or a second
// device picks up the thread already in progress. Text only - charts and tables aren't persisted
// server-side, so they don't come back.
export const loadHistory = createAsyncThunk('chat/loadHistory', async () => {
  const messages = await fetchHistoryMessages()
  return messages.map((message, index) => ({
    id: `history-${index}`,
    role: message.role,
    text: message.text,
  }))
})

const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {
    toggleTts: (state) => {
      state.ttsEnabled = !state.ttsEnabled
    },
    setDraftText: (state, action: PayloadAction<string>) => {
      state.draftText = action.payload
    },
    // A scan just resolved an item - open the "How much of X?" bar above the input. A newer scan
    // replaces an older unanswered prompt, same as the old draft prefill's last-one-wins.
    openQuantityPrompt: (state, action: PayloadAction<QuantityPrompt>) => {
      state.quantityPrompt = action.payload
    },
    dismissQuantityPrompt: (state) => {
      state.quantityPrompt = null
    },
    appendDraftText: (state, action: PayloadAction<string>) => {
      state.draftText = state.draftText ? `${state.draftText} ${action.payload}` : action.payload
    },
    // Logs a barcode scan as a local-only turn (not sent through /api/chat or persisted server-
    // side, so it won't survive a reload or show up on the separate Chat History page) so the live
    // chat window shows what got scanned, same as if it had been typed - without spending a model
    // turn on something that's already fully resolved client-side.
    logScanResult: (state, action: PayloadAction<{ upc: string; reply: string }>) => {
      state.messages.push({ id: crypto.randomUUID(), role: 'user', text: `Snapped UPC ${action.payload.upc}` })
      state.messages.push({ id: crypto.randomUUID(), role: 'assistant', text: action.payload.reply })
    },
    queueItemSent: (state, action: PayloadAction<{ id: string; response: ChatApiResponse }>) => {
      state.queue = state.queue.filter((item) => item.id !== action.payload.id)
      const message = state.messages.find((m) => m.id === action.payload.id)
      if (message) {
        message.queued = false
      }
      state.messages.push({
        id: crypto.randomUUID(),
        role: 'assistant',
        text: action.payload.response.reply,
        chartSeries: action.payload.response.chartSeries ?? undefined,
        sqlAnswer: action.payload.response.sqlAnswer ?? undefined,
      })
    },
    entryQueueItemSent: (state, action: PayloadAction<{ id: string; reply: string }>) => {
      state.entryQueue = state.entryQueue.filter((item) => item.id !== action.payload.id)
      const message = state.messages.find((m) => m.id === action.payload.id)
      if (message) {
        message.queued = false
      }
      state.messages.push({ id: crypto.randomUUID(), role: 'assistant', text: action.payload.reply })
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadHistory.fulfilled, (state, action) => {
        // Only seed an empty window: someone who started typing before this landed shouldn't
        // have their in-flight message replaced by the server's older view of the day.
        if (state.messages.length === 0) {
          state.messages = action.payload
        }
      })
      // A failed history load is left silent on purpose - offline cold start should open an
      // empty chat, not an error.
      .addCase(loadQueue.fulfilled, (state, action) => {
        state.queue = action.payload
        for (const item of action.payload) {
          if (state.messages.some((m) => m.id === item.id)) continue
          state.messages.push({
            id: item.id,
            role: 'user',
            text: item.text,
            queued: true,
            queuedAt: item.createdAt,
          })
        }
      })
      .addCase(deleteQueuedMessage.fulfilled, (state, action) => {
        state.queue = state.queue.filter((item) => item.id !== action.payload)
        state.messages = state.messages.filter((m) => m.id !== action.payload)
      })
      .addCase(loadEntryQueue.fulfilled, (state, action) => {
        state.entryQueue = action.payload
        for (const item of action.payload) {
          if (state.messages.some((m) => m.id === item.id)) continue
          state.messages.push({
            id: item.id,
            role: 'user',
            text: scannedEntryText(item.name, item.amount, item.unit),
            queued: true,
            queuedAt: item.createdAt,
          })
        }
      })
      .addCase(deleteQueuedEntry.fulfilled, (state, action) => {
        state.entryQueue = state.entryQueue.filter((item) => item.id !== action.payload)
        state.messages = state.messages.filter((m) => m.id !== action.payload)
      })
      .addCase(logScannedFood.pending, (state, action) => {
        const { name, amount, unit } = action.meta.arg
        state.messages.push({
          id: action.meta.requestId,
          role: 'user',
          text: scannedEntryText(name, amount, unit),
        })
        state.quantityPrompt = null
      })
      .addCase(logScannedFood.fulfilled, (state, action) => {
        state.messages.push({ id: crypto.randomUUID(), role: 'assistant', text: action.payload.reply })
      })
      .addCase(logScannedFood.rejected, (state, action) => {
        if (action.payload?.queued) {
          const { record } = action.payload
          const message = state.messages.find((m) => m.id === action.meta.requestId)
          if (message) {
            message.id = record.id
            message.queued = true
            message.queuedAt = record.createdAt
          }
          state.entryQueue.push(record)
          return
        }
        state.messages.push({
          id: crypto.randomUUID(),
          role: 'assistant',
          text: 'Something went wrong logging that scan.',
        })
      })
      .addCase(sendMessage.pending, (state, action) => {
        state.status = 'loading'
        state.messages.push({ id: action.meta.requestId, role: 'user', text: action.meta.arg })
      })
      .addCase(sendMessage.fulfilled, (state, action) => {
        state.status = 'idle'
        state.messages.push({
          id: crypto.randomUUID(),
          role: 'assistant',
          text: action.payload.reply,
          chartSeries: action.payload.chartSeries ?? undefined,
          sqlAnswer: action.payload.sqlAnswer ?? undefined,
        })
      })
      .addCase(sendMessage.rejected, (state, action) => {
        state.status = 'idle'
        if (action.payload?.queued) {
          const { record } = action.payload
          const message = state.messages.find((m) => m.id === action.meta.requestId)
          if (message) {
            message.id = record.id
            message.queued = true
            message.queuedAt = record.createdAt
          }
          state.queue.push(record)
          return
        }
        state.status = 'error'
        state.messages.push({
          id: crypto.randomUUID(),
          role: 'assistant',
          text: 'Something went wrong sending that message.',
        })
      })
  },
})

export const {
  toggleTts,
  setDraftText,
  openQuantityPrompt,
  dismissQuantityPrompt,
  appendDraftText,
  logScanResult,
  queueItemSent,
  entryQueueItemSent,
} = chatSlice.actions
export default chatSlice.reducer
