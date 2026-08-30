import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface ChatHistoryMessage {
  role: 'user' | 'assistant'
  text: string
  at: string
}

interface ChatHistoryState {
  date: string
  items: ChatHistoryMessage[]
  status: 'idle' | 'loading' | 'error'
  haikuCostUsd: number
  opusCostUsd: number
}

function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const initialState: ChatHistoryState = {
  date: todayIso(),
  items: [],
  status: 'idle',
  haikuCostUsd: 0,
  opusCostUsd: 0,
}

interface HistoryApiResponse {
  metabolicDate: string
  messages: ChatHistoryMessage[]
  haikuCostUsd: number
  opusCostUsd: number
}

// A separate read path from chat/loadHistory: this is a date-scoped lookup for the review page, not
// the live conversation window, so it keeps its own slice rather than reusing chatSlice's messages.
export const loadChatHistoryForDate = createAsyncThunk('chatHistory/load', async (date: string) => {
  const response = await fetch(`/api/chat/history?date=${date}`)
  if (!response.ok) throw new Error(`Chat history request failed: ${response.status}`)
  return (await response.json()) as HistoryApiResponse
})

const chatHistorySlice = createSlice({
  name: 'chatHistory',
  initialState,
  reducers: {
    setChatHistoryDate: (state, action: PayloadAction<string>) => {
      state.date = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadChatHistoryForDate.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadChatHistoryForDate.fulfilled, (state, action) => {
        state.status = 'idle'
        state.items = action.payload.messages
        state.haikuCostUsd = action.payload.haikuCostUsd
        state.opusCostUsd = action.payload.opusCostUsd
      })
      .addCase(loadChatHistoryForDate.rejected, (state) => {
        state.status = 'error'
      })
  },
})

export const { setChatHistoryDate } = chatHistorySlice.actions
export default chatHistorySlice.reducer
