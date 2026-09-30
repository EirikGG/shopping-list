import { createApp } from 'vue'
import './assets/theme.css'
import App from './App.vue'
import { i18n } from './i18n/index.js'

createApp(App).use(i18n).mount('#app')
