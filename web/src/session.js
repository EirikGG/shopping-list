import { ref } from 'vue'

const SESSION_ID_KEY = 'shoppinglist.session-id'
const EMAIL_KEY = 'shoppinglist.email'

export const sessionId = ref(localStorage.getItem(SESSION_ID_KEY))
// The backend has no endpoint for the logged-in user, so the email entered at
// login is kept here. Verifying the email address needs it.
export const sessionEmail = ref(localStorage.getItem(EMAIL_KEY))

export function setSession(id, email) {
  sessionId.value = id
  sessionEmail.value = email
  localStorage.setItem(SESSION_ID_KEY, id)
  localStorage.setItem(EMAIL_KEY, email)
}

export function clearSession() {
  sessionId.value = null
  sessionEmail.value = null
  localStorage.removeItem(SESSION_ID_KEY)
  localStorage.removeItem(EMAIL_KEY)
}
