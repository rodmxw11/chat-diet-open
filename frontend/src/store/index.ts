import { configureStore } from '@reduxjs/toolkit'
import chatReducer from './chatSlice'
import chatHistoryReducer from './chatHistorySlice'
import connectivityReducer from './connectivitySlice'
import dashboardReducer from './dashboardSlice'
import foodEntriesReducer from './foodEntriesSlice'
import notesReducer from './notesSlice'
import shoppingReducer from './shoppingSlice'
import summaryReducer from './summarySlice'
import uiReducer from './uiSlice'

export const store = configureStore({
  reducer: {
    chat: chatReducer,
    chatHistory: chatHistoryReducer,
    connectivity: connectivityReducer,
    dashboard: dashboardReducer,
    foodEntries: foodEntriesReducer,
    notes: notesReducer,
    shopping: shoppingReducer,
    summary: summaryReducer,
    ui: uiReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
