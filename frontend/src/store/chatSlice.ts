import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'

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
  role: 'user' | 'assistant'
  text: string
  chartSeries?: ChartSeries[]
  sqlAnswer?: SqlAnswer
  imageUrl?: string
}

interface ChatState {
  messages: ChatMessage[]
  status: 'idle' | 'loading' | 'error'
  ttsEnabled: boolean
  draftText: string
}

const initialState: ChatState = {
  messages: [],
  status: 'idle',
  ttsEnabled: false,
  draftText: '',
}

interface ChatApiResponse {
  reply: string
  chartSeries: ChartSeries[] | null
  sqlAnswer: SqlAnswer | null
}

export const sendMessage = createAsyncThunk(
  'chat/sendMessage',
  async (text: string, { rejectWithValue }) => {
    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text,
          clientSentAt: new Date().toISOString(),
        }),
      })
      if (!response.ok) {
        throw new Error(`Chat request failed: ${response.status}`)
      }
      const data: ChatApiResponse = await response.json()
      return data
    } catch (error) {
      // A service-worker background-sync queue replays this request later when back online -
      // this fetch rejecting with a TypeError (not an HTTP error status) means it never reached
      // the network at all, i.e. it's queued, not failed.
      if (error instanceof TypeError) {
        return rejectWithValue('offline')
      }
      throw error
    }
  },
)

interface SendPhotoArg {
  file: File
  previewUrl: string
}

export const sendPhoto = createAsyncThunk(
  'chat/sendPhoto',
  async ({ file }: SendPhotoArg, { rejectWithValue }) => {
    try {
      const formData = new FormData()
      formData.append('text', "Here's a photo of my food.")
      formData.append('clientSentAt', new Date().toISOString())
      formData.append('photo', file)
      const response = await fetch('/api/chat', {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) {
        throw new Error(`Chat request failed: ${response.status}`)
      }
      const data: ChatApiResponse = await response.json()
      return data
    } catch (error) {
      // Same offline/background-sync handling as sendMessage - see comment there.
      if (error instanceof TypeError) {
        return rejectWithValue('offline')
      }
      throw error
    }
  },
)

interface HistoryApiMessage {
  role: 'user' | 'assistant'
  text: string
  at: string
}

interface HistoryApiResponse {
  metabolicDate: string
  messages: HistoryApiMessage[]
}

// The conversation is scoped to the metabolic day, not to this tab, so a reload or a second
// device picks up the thread already in progress. Text only - charts, tables, and photos aren't
// persisted server-side, so they don't come back.
export const loadHistory = createAsyncThunk('chat/loadHistory', async () => {
  const response = await fetch('/api/chat/history')
  if (!response.ok) {
    throw new Error(`History request failed: ${response.status}`)
  }
  const data: HistoryApiResponse = await response.json()
  return data.messages.map((message) => ({ role: message.role, text: message.text }))
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
    appendDraftText: (state, action: PayloadAction<string>) => {
      state.draftText = state.draftText ? `${state.draftText} ${action.payload}` : action.payload
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
      .addCase(sendMessage.pending, (state, action) => {
        state.status = 'loading'
        state.messages.push({ role: 'user', text: action.meta.arg })
      })
      .addCase(sendMessage.fulfilled, (state, action) => {
        state.status = 'idle'
        state.messages.push({
          role: 'assistant',
          text: action.payload.reply,
          chartSeries: action.payload.chartSeries ?? undefined,
          sqlAnswer: action.payload.sqlAnswer ?? undefined,
        })
      })
      .addCase(sendMessage.rejected, (state, action) => {
        if (action.payload === 'offline') {
          state.status = 'idle'
          state.messages.push({
            role: 'assistant',
            text: "Offline - this message is queued and will send once you're back online.",
          })
          return
        }
        state.status = 'error'
        state.messages.push({
          role: 'assistant',
          text: 'Something went wrong sending that message.',
        })
      })
      .addCase(sendPhoto.pending, (state, action) => {
        state.status = 'loading'
        state.messages.push({
          role: 'user',
          text: 'Photo of my food',
          imageUrl: action.meta.arg.previewUrl,
        })
      })
      .addCase(sendPhoto.fulfilled, (state, action) => {
        state.status = 'idle'
        state.messages.push({
          role: 'assistant',
          text: action.payload.reply,
          chartSeries: action.payload.chartSeries ?? undefined,
          sqlAnswer: action.payload.sqlAnswer ?? undefined,
        })
      })
      .addCase(sendPhoto.rejected, (state, action) => {
        if (action.payload === 'offline') {
          state.status = 'idle'
          state.messages.push({
            role: 'assistant',
            text: "Offline - this photo is queued and will send once you're back online.",
          })
          return
        }
        state.status = 'error'
        state.messages.push({
          role: 'assistant',
          text: 'Something went wrong sending that photo.',
        })
      })
  },
})

export const { toggleTts, setDraftText, appendDraftText } = chatSlice.actions
export default chatSlice.reducer
