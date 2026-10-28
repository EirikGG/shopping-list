import { ref } from 'vue'
import { request } from './request.js'

// Labels belong to a list. Loaded labels, per list id:
// { [listId]: [{ id, name }] }, sorted by name.
const labelsByList = ref({})

// Labels deleted since the lists were loaded. Label ids are unique across
// lists, so one set does for all.
export const deletedLabelIds = ref(new Set())

function sortByName(list) {
  return [...list].sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id)
}

function setLabels(listId, labels) {
  labelsByList.value = { ...labelsByList.value, [listId]: sortByName(labels) }
}

// The loaded labels of a list, or null if not loaded yet.
export function labelsOf(listId) {
  return labelsByList.value[listId] ?? null
}

// Fetched every time, as others may have added labels. What was loaded
// before is shown in the meantime.
export async function loadLabels(listId) {
  const response = await request('GET', `/label-list?itemListId=${encodeURIComponent(listId)}`)
  const list = await response.json()
  setLabels(listId, list.map(label => ({ id: label.labelId, name: label.name })))
}

// Returns the new label as { id, name }.
export async function createLabel(listId, name) {
  const response = await request('POST', '/label', { itemListId: listId, name })
  const { labelId } = await response.json()
  const label = { id: labelId, name }
  setLabels(listId, [...(labelsOf(listId) ?? []), label])
  return label
}

export async function renameLabel(listId, id, newName) {
  await request('PATCH', '/label', { labelId: id, newName })
  setLabels(listId, (labelsOf(listId) ?? []).map(label => (label.id === id ? { ...label, name: newName } : label)))
}

// Only deletes on the backend. forgetLabel drops it from the list, which a
// swipe does once its animation has finished.
export async function deleteLabel(id) {
  await request('DELETE', '/label', { labelId: id })
}

export function forgetLabel(listId, id) {
  setLabels(listId, (labelsOf(listId) ?? []).filter(label => label.id !== id))
  deletedLabelIds.value = new Set([...deletedLabelIds.value, id])
}

// The name to show for a label on an item, or null if it has been deleted.
// Loaded labels give the latest name; otherwise the name the item came with.
export function labelName(listId, itemLabel) {
  if (deletedLabelIds.value.has(itemLabel.labelId)) {
    return null
  }
  return labelsOf(listId)?.find(label => label.id === itemLabel.labelId)?.name ?? itemLabel.name
}

// An item can have any number of labels.
export async function addItemLabel(itemId, labelId) {
  await request('POST', '/label-item-mapping', { itemId, labelId })
}

export async function removeItemLabel(itemId, labelId) {
  await request('DELETE', '/label-item-mapping', { itemId, labelId })
}
