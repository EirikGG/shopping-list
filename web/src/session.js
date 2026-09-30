import { ref } from 'vue'

const STORAGE_KEY = 'shoppinglist.session-id'

export const sessionId = ref(localStorage.getItem(STORAGE_KEY))

export function setSessionId(id) {
  sessionId.value = id
  localStorage.setItem(STORAGE_KEY, id)
}

export function clearSession() {
  sessionId.value = null
  localStorage.removeItem(STORAGE_KEY)
}
