const SESSION_ID_KEY = 'session-id'

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

async function postJson(path, body) {
  const response = await fetch(`/api${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })

  if (!response.ok) {
    throw await toApiError(response)
  }

  return response
}

// Logging in works before the email is verified; the session only becomes
// valid once it is.
export async function login(email, password) {
  const response = await postJson('/login_user', { email, password })
  const body = await response.json()
  return body[SESSION_ID_KEY]
}

export async function createUser(user) {
  // No username is sent: the backend uses the email as the username.
  const response = await postJson('/user', {
    firstname: user.firstname,
    lastname: user.lastname,
    birthdate: user.birthdate,
    gender: user.gender,
    password: user.password,
    email: user.email
  })
  return response.json()
}

// 'verified' | 'unverified' | 'invalid'. A session for a user whose email is
// not verified yet is rejected with its own error code, which is what tells
// the two apart.
export async function sessionStatus(sessionId) {
  const response = await fetch('/api/session_is_valid', {
    headers: { Authorization: `Bearer ${sessionId}` }
  })

  if (response.ok) {
    return 'verified'
  }

  const error = await toApiError(response)
  return error.code === 'USER_EMAIL_NOT_VERIFIED' ? 'unverified' : 'invalid'
}

// The code is the number from the verification email. The endpoint needs no
// session; the email identifies the user.
export async function verifyEmail(email, code) {
  await postJson('/verify_email', { email, key: Number(code) })
}
