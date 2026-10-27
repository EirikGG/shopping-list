import { request } from './request.js'

// Every list, as every Home Assistant user sees all of them, each with its
// items: [{ id, name, items: [{ id, name, created, completed, stores, links,
// attachments }] }]
// created is when the item was added; stores is [{ storeId, name }]; links is
// [{ id, url, title }]; attachments is [{ id, fileName, contentType, size }].
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
// Returns { id, name, created, completed, stores, links, attachments }, where
// completed is a timestamp or null.
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

// A link on an item. A url without a scheme is taken to be https; only http
// and https are accepted (400 with code INVALID_URL otherwise). title is
// optional. Returns { id, url, title }.
export async function addLink(itemId, url, title) {
  const response = await request('POST', '/item-link', { itemId, url, title })
  return response.json()
}

export async function deleteLink(id) {
  await request('DELETE', '/item-link', { id })
}

// The largest file the backend takes; Home Assistant's ingress allows little
// more. Checked here too, so a large photo is refused before it is sent.
export const MAX_FILE_BYTES = 15 * 1024 * 1024

// Uploads one file to an item. Returns { id, fileName, contentType, size }.
// Fails with 413 (code FILE_TOO_LARGE) if it is over MAX_FILE_BYTES.
export async function uploadAttachment(itemId, file) {
  const form = new FormData()
  form.append('itemId', itemId)
  form.append('file', file)
  const response = await request('POST', '/attachment', form)
  return response.json()
}

export async function deleteAttachment(id) {
  await request('DELETE', '/attachment', { id })
}

// Where a file is, relative like every other call, for links and images.
export function attachmentUrl(id, { download = false } = {}) {
  return `api/attachment/${encodeURIComponent(id)}${download ? '?download=true' : ''}`
}

// Images the backend shows in the page; anything else is only downloaded.
const INLINE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/avif']

export function isImage(attachment) {
  return INLINE_TYPES.includes(attachment.contentType)
}
