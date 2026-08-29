import { configureStore } from '@reduxjs/toolkit'
import chatReducer from './chatSlice'
import chatHistoryReducer from './chatHistorySlice'
import connectivityReducer from './connectivitySlice'
import dashboardReducer from './dashboardSlice'
import foodEntriesReducer from './foodEntriesSlice'
import foodItemsReducer from './foodItemsSlice'
import notesReducer from './notesSlice'
import summaryReducer from './summarySlice'
import uiReducer from './uiSlice'

export const store = configureStore({
  reducer: {
    chat: chatReducer,
    chatHistory: chatHistoryReducer,
    connectivity: connectivityReducer,
    dashboard: dashboardReducer,
    foodEntries: foodEntriesReducer,
    foodItems: foodItemsReducer,
    notes: notesReducer,
    summary: summaryReducer,
    ui: uiReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
