import { createApp } from 'vue'
import './assets/theme.css'
import App from './App.vue'
import { i18n } from './i18n/index.js'
// Applies and keeps the colour theme up to date for the whole app.
import './theme/index.js'
// Keeps the app shell above the on-screen keyboard.
import './viewport.js'

createApp(App).use(i18n).mount('#app')
