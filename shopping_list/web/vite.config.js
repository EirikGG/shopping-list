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

// In Home Assistant, its ingress proxy tells the backend who the logged-in
// user is. Here the dev server does that, always as this user.
const devUserId = process.env.DEV_HA_USER_ID || 'dev-user'
const devUserName = process.env.DEV_HA_USER_NAME || 'Dev User'

export default defineConfig({
  plugins: [vue()],
  // Relative links in the built app, as Home Assistant serves it under
  // /api/hassio_ingress/<token>/ rather than at the root.
  base: './',
  server: {
    host: '0.0.0.0',
    port: Number(process.env.WEB_PORT) || 5173,
    allowedHosts,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        // Set here, replacing any the browser sent, as HA's proxy does.
        configure: proxy => {
          proxy.on('proxyReq', proxyReq => {
            proxyReq.setHeader('X-Remote-User-Id', devUserId)
            proxyReq.setHeader('X-Remote-User-Display-Name', devUserName)
            proxyReq.removeHeader('X-Remote-User-Name')
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
