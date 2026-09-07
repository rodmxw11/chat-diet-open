import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import '@fontsource/ibm-plex-sans/400.css'
import '@fontsource/ibm-plex-sans/500.css'
import '@fontsource/ibm-plex-sans/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import { store } from './store'
import { startConnectivityMonitor } from './store/connectivitySlice'
import { startSummaryPolling } from './store/summarySlice'
import { registerServiceWorkerUpdates } from './lib/registerServiceWorker'
import './index.css'
import App from './App.tsx'

registerServiceWorkerUpdates()
startConnectivityMonitor(store.dispatch, store.getState)
startSummaryPolling(store.dispatch)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <App />
    </Provider>
  </StrictMode>,
)
