import { registerSW } from 'virtual:pwa-register'

const UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000

// The build only ever injects a bare navigator.serviceWorker.register() call unless this virtual
// module is actually imported and called - so without this, a new deploy's service worker (even
// though it's built to self-activate via skipWaiting/clientsClaim) never gets *checked for* on
// any predictable schedule, and the already-open app never reloads to pick it up once it does.
// Chrome's own background check for a changed sw.js is throttled to roughly once/24h, which is
// why "force close and reopen" alone can keep serving a days-old bundle indefinitely. Polling
// registration.update() here - on an interval and whenever the app regains focus - closes that
// gap; registerType: 'autoUpdate' then reloads the page automatically once the new worker takes
// over (see this module's own activated-event listener, wired up by calling registerSW below).
export function registerServiceWorkerUpdates(): void {
  registerSW({
    immediate: true,
    onRegisteredSW(_swUrl, registration) {
      if (!registration) return
      setInterval(() => void registration.update(), UPDATE_CHECK_INTERVAL_MS)
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') void registration.update()
      })
    },
  })
}
