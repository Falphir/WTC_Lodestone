import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // forwards API calls to the hub during `npm run dev`; in production the
    // dashboard is served by the hub itself, so this only matters locally
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
