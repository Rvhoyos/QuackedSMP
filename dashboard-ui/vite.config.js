import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    // In dev mode, proxy both REST and WebSocket to the running Java server
    proxy: {
      '/api': 'http://localhost:8125',
      '/ws': {
        target: 'ws://localhost:8125',
        ws: true,
        rewriteWsOrigin: true,
      },
    },
  },
})
