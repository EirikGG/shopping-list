import { ref, watch } from 'vue'
import { request } from './request.js'
import { sessionId } from '../session.js'

// Stores belong to a list and are shared by everyone on it. Loaded stores,
// per list id: { [listId]: [{ id, name }] }, sorted by name.
const storesByList = ref({})

// Stores deleted since the lists were loaded. Store ids are unique across
// lists, so one set does for all.
export const deletedStoreIds = ref(new Set())

// Another login can be another user, so everything is fetched again.
watch(sessionId, () => {
  storesByList.value = {}
  deletedStoreIds.value = new Set()
})

function sortByName(list) {
  return [...list].sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id)
}

function setStores(listId, stores) {
  storesByList.value = { ...storesByList.value, [listId]: sortByName(stores) }
}

// The loaded stores of a list, or null if not loaded yet.
export function storesOf(listId) {
  return storesByList.value[listId] ?? null
}

// Fetched every time, as others on the list may have added stores. What was
// loaded before is shown in the meantime.
export async function loadStores(listId) {
  const session = sessionId.value
  const response = await request('GET', `/store-list?itemListId=${encodeURIComponent(listId)}`)
  const list = await response.json()
  // Dropped if the session changed while loading.
  if (sessionId.value === session) {
    setStores(listId, list.map(store => ({ id: store.storeId, name: store.name })))
  }
}

// Returns the new store as { id, name }.
export async function createStore(listId, name) {
  const response = await request('POST', '/store', { itemListId: listId, name })
  const { storeId } = await response.json()
  const store = { id: storeId, name }
  setStores(listId, [...(storesOf(listId) ?? []), store])
  return store
}

export async function renameStore(listId, id, newName) {
  await request('PATCH', '/store', { storeId: id, newName })
  setStores(listId, (storesOf(listId) ?? []).map(store => (store.id === id ? { ...store, name: newName } : store)))
}

// Only deletes on the backend. forgetStore drops it from the list, which a
// swipe does once its animation has finished.
export async function deleteStore(id) {
  await request('DELETE', '/store', { storeId: id })
}

export function forgetStore(listId, id) {
  setStores(listId, (storesOf(listId) ?? []).filter(store => store.id !== id))
  deletedStoreIds.value = new Set([...deletedStoreIds.value, id])
}

// The name to show for a store on an item, or null if it has been deleted.
// Loaded stores give the latest name; otherwise the name the item came with.
export function storeName(listId, itemStore) {
  if (deletedStoreIds.value.has(itemStore.storeId)) {
    return null
  }
  return storesOf(listId)?.find(store => store.id === itemStore.storeId)?.name ?? itemStore.name
}

// An item can have any number of stores.
export async function addItemStore(itemId, storeId) {
  await request('POST', '/store-item-mapping', { itemId, storeId })
}

export async function removeItemStore(itemId, storeId) {
  await request('DELETE', '/store-item-mapping', { itemId, storeId })
}
