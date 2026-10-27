import { request } from './request.js'

// Every list, as every Home Assistant user sees all of them, each with its
// items: [{ id, name, items: [{ id, name, created, completed, stores }] }]
// created is when the item was added; stores is [{ storeId, name }].
export async function getItemLists() {
  const response = await request('GET', '/item-list')
  return response.json()
}

// Returns { id, name }.
export async function createItemList(name) {
  const response = await request('POST', '/item-list', { name })
  return response.json()
}

// Returns { id, name, created, stores: [{ storeId, name }] }.
export async function createItem(itemListId, name, storeIds = []) {
  const response = await request('POST', '/item', { itemListId, name, storeIds })
  return response.json()
}

// The backend sets both the name and the completed state on every call, so
// both are always sent. Completing sets the completed time to now.
// Returns { id, name, created, completed, stores }, where completed is a
// timestamp or null.
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
