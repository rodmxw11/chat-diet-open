import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export type Screen = 'chat' | 'shop' | 'notes' | 'foods' | 'chatHistory'
export type Overlay = null | 'chartsMacros' | 'chartsWeight' | 'queue'

interface UiState {
  screen: Screen
  overlay: Overlay
  menu: boolean
}

const initialState: UiState = {
  screen: 'chat',
  overlay: null,
  menu: false,
}

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    setScreen: (state, action: PayloadAction<Screen>) => {
      state.screen = action.payload
      state.menu = false
    },
    openOverlay: (state, action: PayloadAction<Exclude<Overlay, null>>) => {
      state.overlay = action.payload
      state.menu = false
    },
    closeOverlay: (state) => {
      state.overlay = null
    },
    toggleMenu: (state) => {
      state.menu = !state.menu
    },
    closeMenu: (state) => {
      state.menu = false
    },
  },
})

export const { setScreen, openOverlay, closeOverlay, toggleMenu, closeMenu } = uiSlice.actions
export default uiSlice.reducer
