import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'chat-diet',
        short_name: 'chat-diet',
        description: 'Conversational diet, weight, and vitals tracker',
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: 'chat-diet-icon-192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'chat-diet-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'chat-diet-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      // Offline queueing is now handled at the app level (src/lib/offlineQueue.ts, an IndexedDB
      // queue the offline-queue panel can list and delete from) instead of Workbox's opaque,
      // undeletable backgroundSync queue - VitePWA is kept only for manifest/installability and
      // its default precache of the build output (including the self-hosted font files).
    }),
  ],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
