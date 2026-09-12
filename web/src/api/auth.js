const SESSION_ID_KEY = 'session-id'

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function postJson(path, body) {
  const response = await fetch(`/api${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })

  if (!response.ok) {
    // The backend returns a PublicError body for known cases and an empty
    // body for the rest, so the parse has to be optional.
    const error = await response.json().catch(() => null)
    throw new ApiError(response.statusText, response.status, error?.error)
  }

  return response.json()
}

// The backend stores the email as the username, so the email is what identifies
// a user on login.
export async function login(email, password) {
  const body = await postJson('/login_user', { username: email, password })
  return body[SESSION_ID_KEY]
}

export function createUser(user) {
  // No username is sent: the backend derives it from the email field.
  return postJson('/user', {
    firstname: user.firstname,
    lastname: user.lastname,
    birthdate: user.birthdate,
    gender: user.gender,
    password: user.password,
    email: user.email,
    countrycode: user.countrycode,
    phonenumber: user.phonenumber
  })
}

export async function emailIsAvailable(email) {
  // The endpoint checks the username column — which holds the email — and takes
  // the value as a header, answering 406 when it is taken.
  const response = await fetch('/api/is_username_available', {
    headers: { username: email }
  })

  if (response.status === 406) {
    return false
  }

  if (!response.ok) {
    throw new ApiError(response.statusText, response.status)
  }

  return true
}

export async function sessionIsValid(sessionId) {
  const response = await fetch('/api/session_is_valid', {
    headers: { Authorization: `Bearer ${sessionId}` }
  })
  return response.ok
}
