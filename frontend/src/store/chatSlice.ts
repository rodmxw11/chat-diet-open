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
  sessionId: string
  messages: ChatMessage[]
  status: 'idle' | 'loading' | 'error'
  ttsEnabled: boolean
  draftText: string
}

function newSessionId(): string {
  return crypto.randomUUID()
}

const initialState: ChatState = {
  sessionId: newSessionId(),
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

interface SendPhotoArg {
  file: File
  previewUrl: string
}

export const sendPhoto = createAsyncThunk(
  'chat/sendPhoto',
  async ({ file }: SendPhotoArg, { getState, rejectWithValue }) => {
    const { chat } = getState() as { chat: ChatState }
    try {
      const formData = new FormData()
      formData.append('text', "Here's a photo of my food.")
      formData.append('sessionId', chat.sessionId)
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
    setDraftText: (state, action: PayloadAction<string>) => {
      state.draftText = action.payload
    },
    appendDraftText: (state, action: PayloadAction<string>) => {
      state.draftText = state.draftText ? `${state.draftText} ${action.payload}` : action.payload
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

export const { startNewSession, toggleTts, setDraftText, appendDraftText } = chatSlice.actions
export default chatSlice.reducer
