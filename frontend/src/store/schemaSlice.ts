import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

export interface ColumnSchema {
  name: string
  type: string
}

export interface TableSchema {
  tableName: string
  columns: ColumnSchema[]
}

interface SchemaState {
  tables: TableSchema[]
  status: 'idle' | 'loading' | 'error'
}

const initialState: SchemaState = {
  tables: [],
  status: 'idle',
}

export const loadSchema = createAsyncThunk('schema/load', async () => {
  const response = await fetch('/api/schema')
  if (!response.ok) throw new Error(`Schema request failed: ${response.status}`)
  return (await response.json()) as TableSchema[]
})

const schemaSlice = createSlice({
  name: 'schema',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadSchema.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(loadSchema.fulfilled, (state, action) => {
        state.status = 'idle'
        state.tables = action.payload
      })
      .addCase(loadSchema.rejected, (state) => {
        state.status = 'error'
      })
  },
})

export default schemaSlice.reducer
