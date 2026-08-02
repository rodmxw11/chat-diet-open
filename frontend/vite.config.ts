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
      workbox: {
        // Chat POSTs get queued (not cached) when offline and replayed in order once back
        // online - this is the actual offline write queue, not a GET response cache.
        runtimeCaching: [
          {
            urlPattern: /\/api\/chat$/,
            method: 'POST',
            handler: 'NetworkOnly',
            options: {
              backgroundSync: {
                name: 'chat-mutation-queue',
                options: {
                  maxRetentionTime: 24 * 60,
                },
              },
            },
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
