import { toApiError } from './auth.js'
import { clearSession, sessionId } from '../session.js'

async function request(method, path, body) {
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

// Every list of the logged-in user, each with its items:
// [{ id, name, items: [{ id, name, completed }] }]
export async function getItemLists() {
  const response = await request('GET', '/item-list')
  return response.json()
}

// Returns { id, name }.
export async function createItemList(name) {
  const response = await request('POST', '/item-list', { name })
  return response.json()
}

// Returns { id, name }.
export async function createItem(itemListId, name) {
  const response = await request('POST', '/item', { itemListId, name })
  return response.json()
}

// Returns { id, name, completed }.
export async function renameItem(id, newName) {
  const response = await request('PATCH', '/item', { id, newName })
  return response.json()
}

export async function deleteItem(id) {
  await request('DELETE', '/item', { id })
}

export async function renameItemList(itemListId, newName) {
  await request('PATCH', '/item-list', { itemListId, newName })
}

export async function deleteItemList(itemListId) {
  await request('DELETE', '/item-list', { itemListId })
}
