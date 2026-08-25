import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

export interface Note {
  id: number
  loggedAt: string
  text: string
}

interface NotesState {
  items: Note[]
  status: 'idle' | 'loading' | 'error'
}

const initialState: NotesState = {
  items: [],
  status: 'idle',
}

export const loadNotes = createAsyncThunk('notes/load', async () => {
  const response = await fetch('/api/notes')
  if (!response.ok) throw new Error(`Notes request failed: ${response.status}`)
  return (await response.json()) as Note[]
})

const notesSlice = createSlice({
  name: 'notes',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadNotes.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadNotes.fulfilled, (state, action) => {
        state.status = 'idle'
        state.items = action.payload
      })
      .addCase(loadNotes.rejected, (state) => {
        state.status = 'error'
      })
  },
})

export default notesSlice.reducer
