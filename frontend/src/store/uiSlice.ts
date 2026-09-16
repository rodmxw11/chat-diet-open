import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export type Screen =
  | 'chat'
  | 'notes'
  | 'foods'
  | 'chatHistory'
  | 'micronutrients'
  | 'foodItems'
  | 'schema'
  | 'bloodPressure'
  | 'about'
export type Overlay = null | 'chartsMacros' | 'chartsWeight' | 'queue'
export type Theme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'chatdiet-theme'

// Until the user picks explicitly, this follows the OS/browser preference (the app's original,
// toggle-free behavior) - so nobody sees a change just from this feature shipping. Once toggled,
// the choice is remembered and no longer tracks the system setting.
function initialTheme(): Theme {
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  if (stored === 'light' || stored === 'dark') return stored
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

interface UiState {
  screen: Screen
  overlay: Overlay
  menu: boolean
  theme: Theme
}

const initialState: UiState = {
  screen: 'chat',
  overlay: null,
  menu: false,
  theme: initialTheme(),
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
    toggleTheme: (state) => {
      state.theme = state.theme === 'dark' ? 'light' : 'dark'
      localStorage.setItem(THEME_STORAGE_KEY, state.theme)
    },
  },
})

export const { setScreen, openOverlay, closeOverlay, toggleMenu, closeMenu, toggleTheme } = uiSlice.actions
export default uiSlice.reducer
