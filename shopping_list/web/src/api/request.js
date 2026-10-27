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
// ingress proxy tells the backend who they are. body is sent as JSON, or as
// it is if it is FormData, as for an upload.
export async function request(method, path, body) {
  const isWrite = method !== 'GET'
  if (isWrite) {
    pendingWrites++
    writeGeneration++
  }

  try {
    // Relative, without a leading slash: Home Assistant serves the app under
    // /api/hassio_ingress/<token>/, and the API is below that.
    // The browser sets the type of FormData itself, with its boundary.
    const form = body instanceof FormData
    const response = await fetch(`api${path}`, {
      method,
      headers: form ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined || form ? body : JSON.stringify(body)
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
