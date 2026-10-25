import { request } from './request.js'

// Every list the logged-in user has access to, their own and those shared
// with them, each with its items:
// [{ id, name, role, members, items: [{ id, name, completed, stores }] }]
// role is 'OWNER' or 'MEMBER'. members is
// [{ personId, firstName, lastName, email, role, you }].
// stores is [{ storeId, name }].
export async function getItemLists() {
  const response = await request('GET', '/item-list')
  return response.json()
}

// Returns { id, name, role, members }.
export async function createItemList(name) {
  const response = await request('POST', '/item-list', { name })
  return response.json()
}

// Returns { id, name, stores: [{ storeId, name }] }.
export async function createItem(itemListId, name, storeIds = []) {
  const response = await request('POST', '/item', { itemListId, name, storeIds })
  return response.json()
}

// The backend sets both the name and the completed state on every call, so
// both are always sent. Completing sets the completed time to now.
// Returns { id, name, completed, stores }, where completed is a timestamp or
// null.
export async function updateItem(id, name, completed) {
  const response = await request('PATCH', '/item', { id, newName: name, completed })
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

// Shares the list with the user who logs in with the email. Only the owner
// can share. Returns the new member. Fails with 404 if no user has the email.
export async function shareItemList(itemListId, email) {
  const response = await request('POST', '/item-list-member', { itemListId, email })
  return response.json()
}

// The owner removes a member; a member passes their own personId to leave.
export async function removeItemListMember(itemListId, personId) {
  await request('DELETE', '/item-list-member', { itemListId, personId })
}
