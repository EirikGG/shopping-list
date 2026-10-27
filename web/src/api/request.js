export class ApiError extends Error {
  constructor(message, status, code) {
    super(message)
    this.status = status
    this.code = code
  }
}

// The backend returns a PublicError body for known cases and an empty body for
// the rest, so the parse has to be optional.
async function toApiError(response) {
  const error = await response.json().catch(() => null)
  return new ApiError(response.statusText, response.status, error?.error)
}

// Changes being saved, and a count of every change started. Polling uses them
// to drop a result that could undo a change shown before the backend had it.
let pendingWrites = 0
let writeGeneration = 0

export function writeState() {
  return { pending: pendingWrites, generation: writeGeneration }
}

// Calls the backend. Home Assistant has already logged the user in, and its
// ingress proxy tells the backend who they are.
export async function request(method, path, body) {
  const isWrite = method !== 'GET'
  if (isWrite) {
    pendingWrites++
    writeGeneration++
  }

  try {
    const response = await fetch(`/api${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    })

    if (!response.ok) {
      throw await toApiError(response)
    }

    return response
  } finally {
    if (isWrite) {
      pendingWrites--
    }
  }
}
