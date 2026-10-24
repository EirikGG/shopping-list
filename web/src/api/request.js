import { toApiError } from './auth.js'
import { clearSession, sessionId } from '../session.js'

// Calls the backend as the logged-in user.
export async function request(method, path, body) {
  const response = await fetch(`/api${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${sessionId.value}`,
      'Content-Type': 'application/json'
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })

  if (!response.ok) {
    // A 401 here means the session has expired since it was checked, so the
    // user is sent back to the login form.
    if (response.status === 401) {
      clearSession()
    }
    throw await toApiError(response)
  }

  return response
}
