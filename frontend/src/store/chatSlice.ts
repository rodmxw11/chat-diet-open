import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

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
}

interface ChatState {
  sessionId: string
  messages: ChatMessage[]
  status: 'idle' | 'loading' | 'error'
  ttsEnabled: boolean
}

function newSessionId(): string {
  return crypto.randomUUID()
}

const initialState: ChatState = {
  sessionId: newSessionId(),
  messages: [],
  status: 'idle',
  ttsEnabled: false,
}

interface ChatApiResponse {
  reply: string
  chartSeries: ChartSeries[] | null
  sqlAnswer: SqlAnswer | null
}

export const sendMessage = createAsyncThunk(
  'chat/sendMessage',
  async (text: string, { getState, rejectWithValue }) => {
    const { chat } = getState() as { chat: ChatState }
    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text,
          sessionId: chat.sessionId,
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

const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {
    startNewSession: (state) => {
      state.sessionId = newSessionId()
      state.messages = []
      state.status = 'idle'
    },
    toggleTts: (state) => {
      state.ttsEnabled = !state.ttsEnabled
    },
  },
  extraReducers: (builder) => {
    builder
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
  },
})

export const { startNewSession, toggleTts } = chatSlice.actions
export default chatSlice.reducer
