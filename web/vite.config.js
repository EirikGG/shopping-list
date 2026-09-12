import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The backend has no CORS plugin, so the dev server proxies /api to it instead
// of the browser calling it cross-origin.
const apiTarget = process.env.API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: Number(process.env.WEB_PORT) || 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, '')
      }
    },
    watch: {
      // Needed for file change detection when the source is a bind mount.
      usePolling: true
    }
  }
})
