<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import RowEditor from './RowEditor.vue'
import SwipeToDelete from './SwipeToDelete.vue'
import {
  createStore,
  deleteStore,
  forgetStore,
  loadStores,
  renameStore,
  storesOf
} from '../api/stores.js'

// Picks the stores for an item, and adds, renames and deletes stores. The
// stores belong to the list, so everyone on it sees the same ones.
const props = defineProps({
  listId: { type: Number, required: true },
  // The ids of the chosen stores.
  modelValue: { type: Array, default: () => [] },
  // One row of chips that scrolls sideways, with New first, for picking and
  // adding only. For the add bar on phones, where the keyboard leaves room
  // for little else. Stores are renamed and deleted in the item editor.
  compact: { type: Boolean, default: false },
  // For an editor with room to spare: on phones it keeps the heading, and
  // shows every store rather than a box that scrolls.
  spacious: { type: Boolean, default: false }
})

// 'done' is sent when adding or editing a store has ended, so the parent can
// give the item field its focus back.
const emit = defineEmits(['update:modelValue', 'done'])

const { t } = useI18n()

const stores = computed(() => storesOf(props.listId))

// Matches the breakpoint in the styles below.
const narrow = useMediaQuery('(max-width: 640px)')

// Message keys, so the errors follow a language change.
const loadError = ref('')
const error = ref('')

const adding = ref(false)
const newName = ref('')
const busy = ref(false)
const newInput = ref(null)

// The store being renamed or deleted, if any. One at a time.
const editingId = ref(null)

onMounted(load)

async function load() {
  loadError.value = ''
  try {
    await loadStores(props.listId)
  } catch (e) {
    loadError.value = e.status === 429 ? 'errors.tooManyRequests' : 'stores.loadFailed'
  }
}

// Tapping the picker must not take the focus from the item field, or the
// keyboard closes on phones and the picker with it. Its own fields are the
// exception.
function keepFocus(event) {
  if (!event.target.closest('input')) {
    event.preventDefault()
  }
}

function isChosen(store) {
  return props.modelValue.includes(store.id)
}

function choose(id) {
  if (!props.modelValue.includes(id)) {
    emit('update:modelValue', [...props.modelValue, id])
  }
}

function toggle(store) {
  if (isChosen(store)) {
    emit('update:modelValue', props.modelValue.filter(id => id !== store.id))
  } else {
    choose(store.id)
  }
}

function startEditing(store) {
  stopAdding(false)
  error.value = ''
  editingId.value = store.id
}

function stopEditing() {
  editingId.value = null
  emit('done')
}

// Deleting a store also unchooses it.
function forget(store) {
  if (isChosen(store)) {
    emit('update:modelValue', props.modelValue.filter(id => id !== store.id))
  }
  forgetStore(props.listId, store.id)
}

// The editing ends before the row goes, so the focus is handed back to the
// item field while the editor still has it. Otherwise the picker closes.
async function remove(store) {
  await deleteStore(store.id)
  stopEditing()
  forget(store)
}

function swipeFailed(e) {
  error.value = e.status === 429 ? 'errors.tooManyRequests' : 'edit.deleteFailed'
}

async function startAdding() {
  editingId.value = null
  error.value = ''
  adding.value = true
  await nextTick()
  newInput.value.focus()
}

function stopAdding(refocus = true) {
  if (!adding.value) {
    return
  }
  adding.value = false
  newName.value = ''
  error.value = ''
  if (refocus) {
    emit('done')
  }
}

// In the row of chips, scrolls a store just added or chosen into sight, as it
// may be far along the row.
const list = ref(null)

async function showChip(id) {
  if (!props.compact) {
    return
  }
  await nextTick()
  list.value
    ?.querySelector(`[data-store-id="${id}"]`)
    ?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
}

async function add() {
  if (!newName.value || busy.value) {
    return
  }

  // A store with that name already exists, so it is chosen instead.
  const existing = stores.value?.find(
    store => store.name.toLowerCase() === newName.value.toLowerCase()
  )
  if (existing) {
    choose(existing.id)
    stopAdding()
    showChip(existing.id)
    return
  }

  error.value = ''
  busy.value = true

  try {
    const store = await createStore(props.listId, newName.value)
    choose(store.id)
    stopAdding()
    showChip(store.id)
  } catch (e) {
    error.value = e.status === 429 ? 'errors.tooManyRequests' : 'stores.createFailed'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="picker" :class="{ compact, spacious }" @mousedown="keepFocus">
    <p class="heading">{{ t('stores.stores') }}</p>

    <div v-if="loadError" class="status">
      <p class="error" aria-live="polite">{{ t(loadError) }}</p>
      <button type="button" class="secondary" @click="load">{{ t('app.retry') }}</button>
    </div>

    <p v-else-if="stores === null" class="status muted">{{ t('stores.loading') }}</p>

    <template v-else>
      <p v-if="!stores.length && !adding && !compact" class="status muted">{{ t('stores.none') }}</p>

      <ul ref="list" class="stores">
        <!-- First in the row of chips, so it is there without scrolling. -->
        <li v-if="compact && !adding">
          <div class="store-row add-store">
            <button type="button" class="store" @click="startAdding">
              <Icon name="plus" />
              <span class="name">{{ t('stores.new') }}</span>
            </button>
          </div>
        </li>

        <!-- In the row of chips, the field for a new store takes the whole row. -->
        <template v-if="!(compact && adding)">
          <li
            v-for="store in stores"
            :key="store.id"
            :data-store-id="store.id"
            :class="{ editing: editingId === store.id }"
          >
            <RowEditor
              v-if="editingId === store.id"
              :name="store.name"
              :save="newName => renameStore(listId, store.id, newName)"
              :remove="() => remove(store)"
              @done="stopEditing"
            />
            <SwipeToDelete
              v-else
              :enabled="narrow && !compact"
              :remove="() => deleteStore(store.id)"
              @removed="forget(store)"
              @failed="swipeFailed"
            >
              <div class="store-row" :class="{ selected: isChosen(store) }">
                <button
                  type="button"
                  class="store"
                  :aria-pressed="isChosen(store)"
                  @click="toggle(store)"
                >
                  <Icon :name="compact && isChosen(store) ? 'check' : 'store'" />
                  <span class="name">{{ store.name }}</span>
                  <Icon v-if="isChosen(store) && !compact" name="check" />
                </button>
                <button
                  v-if="!compact"
                  type="button"
                  class="edit"
                  :aria-label="t('edit.editItem', { name: store.name })"
                  :title="t('edit.edit')"
                  @click="startEditing(store)"
                >
                  <Icon name="pencil" />
                </button>
              </div>
            </SwipeToDelete>
          </li>
        </template>

        <li v-if="adding" class="new">
          <input
            ref="newInput"
            v-model.trim="newName"
            :aria-label="t('stores.storeName')"
            :placeholder="t('stores.storeName')"
            maxlength="256"
            @keydown.enter.prevent="add"
            @keydown.esc.stop.prevent="stopAdding()"
          />
          <button
            type="button"
            class="icon"
            :aria-label="t('stores.create')"
            :title="t('stores.create')"
            :disabled="busy || !newName"
            @click="add"
          >
            <Icon name="check" />
          </button>
          <button
            type="button"
            class="icon secondary"
            :aria-label="t('edit.cancel')"
            :title="t('edit.cancel')"
            :disabled="busy"
            @click="stopAdding()"
          >
            <Icon name="x" />
          </button>
        </li>
        <li v-else-if="!compact">
          <div class="store-row add-store">
            <button type="button" class="store" @click="startAdding">
              <Icon name="plus" />
              <span class="name">{{ t('stores.newStore') }}</span>
            </button>
          </div>
        </li>
      </ul>

      <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>
    </template>
  </div>
</template>

<style scoped>
/* On wide screens the stores are chips that wrap onto more rows. */
.heading {
  margin: 0 0 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

.status {
  margin: 0 0 0.5rem;
  font-size: 0.875rem;
}

.status .error {
  margin-bottom: 0.5rem;
}

.muted {
  color: var(--color-text-muted);
}

.stores {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* The editor takes a whole line, as it is wider than a chip. */
.stores li.editing {
  flex-basis: 100%;
}

.store-row {
  display: flex;
  align-items: center;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.store-row:hover {
  border-color: var(--color-primary);
}

.store-row.selected {
  color: var(--color-on-primary);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.store-row.add-store {
  color: var(--color-primary);
  border-style: dashed;
}

.store,
.store:hover:not(:disabled) {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  font-weight: 500;
  color: inherit;
  background: transparent;
  border: none;
}

.store-row:has(.edit) .store {
  padding-right: 0.25rem;
}

.store :deep(svg),
.edit :deep(svg) {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
}

.edit,
.edit:hover:not(:disabled) {
  display: flex;
  flex-shrink: 0;
  padding: 0.375rem 0.625rem 0.375rem 0.375rem;
  color: inherit;
  background: transparent;
  border: none;
  opacity: 0.7;
}

.edit:hover:not(:disabled) {
  opacity: 1;
}

.name {
  overflow-wrap: anywhere;
  text-align: left;
}

.new {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.new input {
  min-width: 0;
  width: 12rem;
  padding: 0.35rem 0.6rem;
}

.icon {
  display: flex;
  flex-shrink: 0;
  padding: 0.4rem;
}

.error {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: var(--color-error);
}

/* The add bar on phones: one row of chips that scrolls sideways, running to
   the edges of the screen, as the store filter does. */
.picker.compact .stores {
  flex-wrap: nowrap;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  margin: 0 -1rem;
  padding: 0.125rem 1rem;
  scrollbar-width: none;
}

.picker.compact .stores::-webkit-scrollbar {
  display: none;
}

.picker.compact .stores li {
  flex-shrink: 0;
}

/* Large enough to tap with the keyboard open. */
.picker.compact .store,
.picker.compact .store:hover:not(:disabled) {
  min-height: 2.5rem;
  padding: 0.375rem 0.875rem;
}

.picker.compact .name {
  white-space: nowrap;
}

.picker.compact .new {
  flex: 1;
}

.picker.compact .new input {
  flex: 1;
  width: auto;
  min-height: 2.5rem;
}

/* On narrow screens the stores are otherwise rows in a box about three and a
   half rows tall, so the bar stays low and the cut-off row shows that it
   scrolls. As with items, a row can be swiped left to delete it. */
@media (max-width: 640px) {
  .picker:not(.spacious) .heading {
    display: none;
  }

  .picker:not(.compact) .stores {
    flex-direction: column;
    flex-wrap: nowrap;
    gap: 0;
    max-height: 9.75rem;
    overflow-y: auto;
    overscroll-behavior: contain;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
  }

  /* An editor has the room to show them all. */
  .picker.spacious .stores {
    max-height: none;
  }

  .picker:not(.compact) .stores li {
    flex-shrink: 0;
  }

  .picker:not(.compact) .stores li + li {
    border-top: 1px solid var(--color-border);
  }

  .picker:not(.compact) .stores li.editing {
    flex-basis: auto;
    padding: 0.375rem 0.5rem 0.375rem 1rem;
  }

  .picker:not(.compact) .store-row,
  .picker:not(.compact) .store-row:hover {
    min-height: 2.75rem;
    border: none;
    border-radius: 0;
  }

  .picker:not(.compact) .store,
  .picker:not(.compact) .store:hover:not(:disabled) {
    flex: 1;
    gap: 0.75rem;
    align-self: stretch;
    padding: 0.375rem 1rem;
  }

  .picker:not(.compact) .store :deep(svg),
  .picker:not(.compact) .edit :deep(svg) {
    width: 1.125rem;
    height: 1.125rem;
  }

  .picker:not(.compact) .edit,
  .picker:not(.compact) .edit:hover:not(:disabled) {
    padding: 0.45rem 0.75rem;
  }

  .picker:not(.compact) .name {
    flex: 1;
  }

  .picker:not(.compact) .new {
    min-height: 2.75rem;
    padding: 0.25rem 0.5rem 0.25rem 1rem;
  }

  .picker:not(.compact) .new input {
    flex: 1;
    width: auto;
  }
}
</style>
