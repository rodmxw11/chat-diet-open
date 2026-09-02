import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { enqueue, listQueued, removeQueued, type QueuedRequest } from '../lib/offlineQueue'
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

interface ChatState {
  messages: ChatMessage[]
  status: 'idle' | 'loading' | 'error'
  ttsEnabled: boolean
  draftText: string
  /** Mirrors the IndexedDB offline queue for the queue panel; kept in sync by queue-touching thunks. */
  queue: QueuedRequest[]
  /** True while the session's chat history is hidden from view (new replies still show). */
  hidden: boolean
  /** The last message's id at the moment hiding was turned on; only messages after it are shown
      while hidden. Null while not hidden, or if the chat was empty at that moment (so everything
      that follows counts as "new"). */
  hiddenSinceMessageId: string | null
  /** True after an external prefill (e.g. a barcode scan resolving a product name) sets draftText -
      consumed once by MessageInput to put the cursor at position 0 instead of the end, so the
      scale reading can be typed in front of the resolved name. */
  pendingCursorStart: boolean
}

const initialState: ChatState = {
  messages: [],
  status: 'idle',
  ttsEnabled: false,
  draftText: '',
  queue: [],
  hidden: false,
  hiddenSinceMessageId: null,
  pendingCursorStart: false,
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

// Re-fetches the day's transcript and merges in anything the server knows about that this tab
// doesn't - i.e. turns sent from another device while this one was showing a stale/hidden view.
// Matches server messages against existing local ones by (role, text) so already-displayed
// messages keep their rich attachments (charts, SQL tables) instead of being flattened to plain
// text; genuinely new turns are inserted in the server's order, and local-only entries (queued or
// failed sends the server doesn't know about yet) are kept, appended after the synced messages.
export const refreshHistory = createAsyncThunk('chat/refreshHistory', async () => fetchHistoryMessages())

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
    // A scan just resolved a product name - prefill "g {name}" with the cursor at the start so the
    // scale reading can be typed in front of it, instead of round-tripping the raw UPC through chat.
    setDraftTextWithCursorStart: (state, action: PayloadAction<string>) => {
      state.draftText = action.payload
      state.pendingCursorStart = true
    },
    clearPendingCursorStart: (state) => {
      state.pendingCursorStart = false
    },
    appendDraftText: (state, action: PayloadAction<string>) => {
      state.draftText = state.draftText ? `${state.draftText} ${action.payload}` : action.payload
    },
    hideChat: (state) => {
      state.hidden = true
      state.hiddenSinceMessageId =
        state.messages.length > 0 ? state.messages[state.messages.length - 1].id : null
    },
    showChat: (state) => {
      state.hidden = false
      state.hiddenSinceMessageId = null
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
      .addCase(refreshHistory.fulfilled, (state, action) => {
        // Claim each server message against the first unclaimed local message with the same
        // role+text, preserving that local message's id/attachments; anything left unclaimed on
        // the server side is new (from elsewhere) and gets inserted plainly, in server order.
        // Anything left unclaimed on the local side (queued/failed sends) is kept and appended
        // after, since the server doesn't know about it yet.
        const claimed = new Set<number>()
        const merged: ChatMessage[] = []
        for (let serverIndex = 0; serverIndex < action.payload.length; serverIndex++) {
          const serverMessage = action.payload[serverIndex]
          const localIndex = state.messages.findIndex(
            (m, i) => !claimed.has(i) && !m.queued && m.role === serverMessage.role && m.text === serverMessage.text,
          )
          if (localIndex !== -1) {
            claimed.add(localIndex)
            merged.push(state.messages[localIndex])
          } else {
            merged.push({
              id: `remote-${serverMessage.at}-${serverIndex}`,
              role: serverMessage.role,
              text: serverMessage.text,
            })
          }
        }
        const leftover = state.messages.filter((_, i) => !claimed.has(i))
        state.messages = [...merged, ...leftover]
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
  setDraftTextWithCursorStart,
  clearPendingCursorStart,
  appendDraftText,
  hideChat,
  showChat,
  queueItemSent,
} = chatSlice.actions
export default chatSlice.reducer
