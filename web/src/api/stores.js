import { ref, watch } from 'vue'
import { request } from './request.js'
import { sessionId } from '../session.js'

// The stores of the logged-in user, shared by every list: [{ id, name }],
// sorted by name. null until loaded.
export const stores = ref(null)

let loading = null

// Another login can be another user, so the stores are fetched again.
watch(sessionId, () => {
  stores.value = null
  deletedStoreIds.value = new Set()
  loading = null
})

function sortByName(list) {
  return [...list].sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id)
}

// Loads the stores once. Later calls reuse the result, unless the first load
// failed.
export function loadStores() {
  if (!loading) {
    const id = sessionId.value
    loading = request('GET', '/store-list')
      .then(response => response.json())
      .then(list => {
        // Dropped if the session changed while loading.
        if (sessionId.value === id) {
          stores.value = list.map(store => ({ id: store.storeId, name: store.name }))
        }
      })
      .catch(e => {
        loading = null
        throw e
      })
  }
  return loading
}

// Returns the new store as { id, name }.
export async function createStore(name) {
  const response = await request('POST', '/store', { name })
  const { storeId } = await response.json()
  const store = { id: storeId, name }
  stores.value = sortByName([...(stores.value ?? []), store])
  return store
}

export async function renameStore(id, newName) {
  await request('PATCH', '/store', { storeId: id, newName })
  stores.value = sortByName(
    stores.value.map(store => (store.id === id ? { ...store, name: newName } : store))
  )
}

// Only deletes on the backend. forgetStore drops it from the list, which a
// swipe does once its animation has finished.
export async function deleteStore(id) {
  await request('DELETE', '/store', { storeId: id })
}

export function forgetStore(id) {
  stores.value = stores.value.filter(store => store.id !== id)
  deletedStoreIds.value = new Set([...deletedStoreIds.value, id])
}

// Stores deleted since the lists were loaded.
export const deletedStoreIds = ref(new Set())

// The name to show for a store on an item, or null if it has been deleted.
// The user's own stores give the latest name; stores of others in a shared
// list keep the name they had when loaded.
export function storeName(itemStore) {
  if (deletedStoreIds.value.has(itemStore.storeId)) {
    return null
  }
  return stores.value?.find(store => store.id === itemStore.storeId)?.name ?? itemStore.name
}

// An item can have any number of stores.
export async function addItemStore(itemId, storeId) {
  await request('POST', '/store-item-mapping', { itemId, storeId })
}

export async function removeItemStore(itemId, storeId) {
  await request('DELETE', '/store-item-mapping', { itemId, storeId })
}
