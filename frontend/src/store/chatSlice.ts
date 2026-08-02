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

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  chartSeries?: ChartSeries[]
}

interface ChatState {
  messages: ChatMessage[]
  status: 'idle' | 'loading' | 'error'
}

const initialState: ChatState = {
  messages: [],
  status: 'idle',
}

interface ChatApiResponse {
  reply: string
  chartSeries: ChartSeries[] | null
}

export const sendMessage = createAsyncThunk(
  'chat/sendMessage',
  async (text: string) => {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    })
    if (!response.ok) {
      throw new Error(`Chat request failed: ${response.status}`)
    }
    const data: ChatApiResponse = await response.json()
    return data
  },
)

const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {},
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
        })
      })
      .addCase(sendMessage.rejected, (state) => {
        state.status = 'error'
        state.messages.push({
          role: 'assistant',
          text: 'Something went wrong sending that message.',
        })
      })
  },
})

export default chatSlice.reducer
