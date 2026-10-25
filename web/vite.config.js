import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The backend has no CORS plugin, so the dev server proxies /api to it instead
// of the browser calling it cross-origin.
const apiTarget = process.env.API_TARGET || 'http://localhost:8080'

// Vite only answers requests for localhost and IP addresses unless other host
// names are listed, e.g. ALLOWED_HOSTS=list.eirikgg.no. Comma-separated.
const allowedHosts = (process.env.ALLOWED_HOSTS || '')
  .split(',')
  .map(host => host.trim())
  .filter(Boolean)

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: Number(process.env.WEB_PORT) || 5173,
    allowedHosts,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, ''),
        // The backend rate-limits by this address, as nginx sends it in prod.
        // Set here so a client cannot supply its own.
        configure: proxy => {
          proxy.on('proxyReq', (proxyReq, req) => {
            proxyReq.setHeader('X-Real-IP', req.socket.remoteAddress ?? '')
          })
        }
      }
    },
    watch: {
      // Needed for file change detection when the source is a bind mount.
      usePolling: true
    }
  }
})
