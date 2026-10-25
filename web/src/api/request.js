import { toApiError } from './auth.js'
import { clearSession, sessionId } from '../session.js'

// Changes being saved, and a count of every change started. Polling uses them
// to drop a result that could undo a change shown before the backend had it.
let pendingWrites = 0
let writeGeneration = 0

export function writeState() {
  return { pending: pendingWrites, generation: writeGeneration }
}

// Calls the backend as the logged-in user.
export async function request(method, path, body) {
  const isWrite = method !== 'GET'
  if (isWrite) {
    pendingWrites++
    writeGeneration++
  }

  try {
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
  } finally {
    if (isWrite) {
      pendingWrites--
    }
  }
}
