import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // forwards API calls to the hub during `npm run dev`; in production the
    // dashboard is served by the hub itself, so this only matters locally
    proxy: {
      // ws: the live-updates WebSocket at /api/admin/live goes through the same proxy
      '/api': { target: 'http://localhost:8080', ws: true },
    },
  },
})
