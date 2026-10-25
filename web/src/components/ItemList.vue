<script setup>
import { computed, ref } from 'vue'
import { useLocalStorage, useMediaQuery } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import ListSharing from './ListSharing.vue'
import RowEditor from './RowEditor.vue'
import StorePicker from './StorePicker.vue'
import SwipeToDelete from './SwipeToDelete.vue'
import { createItem, deleteItem, updateItem } from '../api/items.js'
import { addItemStore, removeItemStore, storeName, stores } from '../api/stores.js'
import { formatTime, parseBackendTime } from '../time.js'

const props = defineProps({
  list: { type: Object, required: true }
})

// 'back' is only offered on narrow screens, where the list replaces the
// overview of all lists.
// 'members-changed' and 'left' come from sharing the list.
const emit = defineEmits([
  'item-added',
  'item-updated',
  'item-deleted',
  'back',
  'members-changed',
  'left'
])

const { locale, t } = useI18n()

// Whether the panel showing who the list is shared with is open.
const showSharing = ref(false)
const shared = computed(() => props.list.members.length > 1)

const name = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)
// The item being renamed or deleted, if any. One at a time.
const editingId = ref(null)
// Matches the breakpoint in the styles below.
const narrow = useMediaQuery('(max-width: 640px)')

const addBar = ref(null)
const nameInput = ref(null)
// The stores the next item is added to. Kept after adding, so several items
// for the same stores can be added in a row.
const storeIds = ref([])
// Leaves out stores deleted since they were chosen.
const selectedStores = computed(
  () => stores.value?.filter(store => storeIds.value.includes(store.id)) ?? []
)

// The store picker opens when the item field gets focus, and stays open while
// anything in the add bar has it.
const picking = ref(false)

function onFocusOut(event) {
  if (!addBar.value.contains(event.relatedTarget)) {
    picking.value = false
  }
}

function closePicker() {
  picking.value = false
  nameInput.value.blur()
}

function storeNames(item) {
  return (item.stores ?? []).map(storeName).filter(Boolean).join(', ')
}

// The back button closes the picker first, if it is open.
function onBack() {
  if (picking.value) {
    closePicker()
  } else {
    emit('back')
  }
}

// Completed items are listed under the others, in a section that can be
// collapsed. Whether it is collapsed is remembered for every list.
const showCompleted = useLocalStorage('shoppinglist.show-completed', true)

// The list can be narrowed to the items of some stores. The choice is kept
// across lists and visits, as it follows the store you are in.
const storeFilter = useLocalStorage('shoppinglist.store-filter', [])

// The stores used by items in this list, sorted by name, leaving out deleted
// ones. In a shared list this includes the stores of the others.
const listStores = computed(() => {
  const byId = new Map()
  for (const item of props.list.items) {
    for (const itemStore of item.stores ?? []) {
      const name = storeName(itemStore)
      if (name) {
        byId.set(itemStore.storeId, { id: itemStore.storeId, name })
      }
    }
  }
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id)
})

// Stores filtered on that this list does not use are ignored.
const activeFilter = computed(
  () => storeFilter.value.filter(id => listStores.value.some(store => store.id === id))
)

function toggleFilter(id) {
  storeFilter.value = activeFilter.value.includes(id)
    ? activeFilter.value.filter(other => other !== id)
    : [...activeFilter.value, id]
}

// An item is shown if it has any of the filtered stores.
const visibleItems = computed(() => {
  if (!activeFilter.value.length) {
    return props.list.items
  }
  return props.list.items.filter(
    item => item.stores?.some(store => activeFilter.value.includes(store.storeId))
  )
})

const openItems = computed(() => visibleItems.value.filter(item => item.completed === null))
const completedItems = computed(() => visibleItems.value.filter(item => item.completed !== null))
const sections = computed(() => [
  { key: 'open', items: openItems.value },
  ...(completedItems.value.length ? [{ key: 'completed', items: completedItems.value }] : [])
])

// Items with a completion still being saved, so a double tap is not sent twice.
const toggling = ref(new Set())

async function rename(item, newName) {
  emit('item-updated', await updateItem(item.id, newName, item.completed !== null))
}

// Shown straight away, and put back if saving fails. The timestamp is a
// stand-in until the backend returns its own.
async function toggleCompleted(item) {
  if (toggling.value.has(item.id)) {
    return
  }

  const previous = item.completed
  const completed = previous === null
  error.value = ''
  toggling.value.add(item.id)
  emit('item-updated', { ...item, completed: completed ? new Date().toISOString() : null })

  try {
    emit('item-updated', await updateItem(item.id, item.name, completed))
  } catch (e) {
    emit('item-updated', { ...item, completed: previous })
    error.value = e.status === 429 ? 'errors.tooManyRequests' : 'lists.completeFailed'
  } finally {
    toggling.value.delete(item.id)
  }
}

// Saved as soon as a store is tapped, apart from the name, and shown straight
// away. Put back if saving fails.
async function changeStores(item, ids) {
  const previous = item.stores ?? []
  const previousIds = previous.map(store => store.storeId)
  const added = ids.filter(id => !previousIds.includes(id))
  const removed = previousIds.filter(id => !ids.includes(id))

  error.value = ''
  // Stores of others in a shared list are not in the picker, so they are kept
  // as they were.
  const own = (stores.value ?? [])
    .filter(store => ids.includes(store.id))
    .map(store => ({ storeId: store.id, name: store.name }))
  const others = previous.filter(
    itemStore => ids.includes(itemStore.storeId) && !own.some(o => o.storeId === itemStore.storeId)
  )
  emit('item-updated', {
    ...item,
    stores: [...own, ...others].sort((a, b) => a.name.localeCompare(b.name))
  })

  try {
    await Promise.all([
      ...added.map(id => addItemStore(item.id, id)),
      ...removed.map(id => removeItemStore(item.id, id))
    ])
  } catch (e) {
    emit('item-updated', { ...item, stores: previous })
    error.value = e.status === 429 ? 'errors.tooManyRequests' : 'lists.completeFailed'
  }
}

async function remove(item) {
  await deleteItem(item.id)
  emit('item-deleted', item.id)
}

function swipeFailed(e) {
  error.value = e.status === 429 ? 'errors.tooManyRequests' : 'edit.deleteFailed'
}

async function submit() {
  error.value = ''
  busy.value = true

  try {
    const item = await createItem(
      props.list.id,
      name.value,
      selectedStores.value.map(store => store.id)
    )
    emit('item-added', { ...item, completed: null })
    name.value = ''
  } catch (e) {
    error.value = e.status === 429 ? 'errors.tooManyRequests' : 'lists.addItemFailed'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="list">
    <div class="scroll">
      <div class="heading">
        <h2>{{ list.name }}</h2>
        <button
          type="button"
          class="share"
          :class="{ active: showSharing, shared }"
          :aria-expanded="showSharing"
          :aria-label="t('share.shareList', { name: list.name })"
          :title="t('share.shareList', { name: list.name })"
          @click="showSharing = !showSharing"
        >
          <Icon name="users" />
          <span v-if="shared" class="member-count">{{ list.members.length }}</span>
        </button>
      </div>

      <ListSharing
        v-if="showSharing"
        :list="list"
        @members-changed="members => emit('members-changed', members)"
        @left="emit('left')"
      />

      <div v-if="listStores.length" class="filter" role="group" :aria-label="t('stores.filter')">
        <button
          v-for="store in listStores"
          :key="store.id"
          type="button"
          class="filter-chip"
          :class="{ active: activeFilter.includes(store.id) }"
          :aria-pressed="activeFilter.includes(store.id)"
          @click="toggleFilter(store.id)"
        >
          <Icon name="store" />
          {{ store.name }}
        </button>
        <button
          v-if="activeFilter.length"
          type="button"
          class="filter-chip show-all"
          @click="storeFilter = []"
        >
          {{ t('stores.showAll') }}
        </button>
      </div>

      <p v-if="!list.items.length" class="empty">{{ t('lists.noItems') }}</p>
      <p v-else-if="!visibleItems.length" class="empty">{{ t('lists.noFilteredItems') }}</p>
      <p v-else-if="!openItems.length" class="empty">{{ t('lists.allCompleted') }}</p>

      <template v-for="section in sections" :key="section.key">
        <button
          v-if="section.key === 'completed'"
          type="button"
          class="section-toggle"
          :aria-expanded="showCompleted"
          aria-controls="completed-items"
          @click="showCompleted = !showCompleted"
        >
          <Icon name="chevron" class="chevron" :class="{ open: showCompleted }" />
          {{ t('lists.completed', { count: section.items.length }) }}
        </button>

        <ul
          v-if="section.items.length && (section.key === 'open' || showCompleted)"
          :id="section.key === 'completed' ? 'completed-items' : undefined"
          class="items"
        >
          <li
            v-for="item in section.items"
            :key="item.id"
            :class="{ completed: item.completed, editing: editingId === item.id }"
          >
            <template v-if="editingId === item.id">
              <RowEditor
                :name="item.name"
                :save="newName => rename(item, newName)"
                :remove="() => remove(item)"
                @done="editingId = null"
              />
              <StorePicker
                :model-value="(item.stores ?? []).map(store => store.storeId)"
                @update:model-value="ids => changeStores(item, ids)"
              />
            </template>
            <SwipeToDelete
              v-else
              :enabled="narrow"
              :remove="() => deleteItem(item.id)"
              @removed="emit('item-deleted', item.id)"
              @failed="swipeFailed"
            >
              <div class="item-row">
                <button
                  type="button"
                  class="complete"
                  :class="{ done: item.completed }"
                  role="checkbox"
                  :aria-checked="item.completed !== null"
                  @click="toggleCompleted(item)"
                >
                  <span class="circle">
                    <Icon name="check" />
                  </span>
                  <span class="text">
                    <span class="item-name">{{ item.name }}</span>
                    <span v-if="storeNames(item)" class="item-store">
                      <Icon name="store" />
                      {{ storeNames(item) }}
                    </span>
                    <time
                      v-if="item.completed"
                      class="completed-at"
                      :datetime="parseBackendTime(item.completed).toISOString()"
                    >
                      {{ t('lists.completedAt', { time: formatTime(parseBackendTime(item.completed), locale) }) }}
                    </time>
                  </span>
                </button>
                <button
                  type="button"
                  class="edit"
                  :aria-label="t('edit.editItem', { name: item.name })"
                  :title="t('edit.edit')"
                  @click="editingId = item.id"
                >
                  <Icon name="pencil" />
                </button>
              </div>
            </SwipeToDelete>
          </li>
        </ul>
      </template>
    </div>

    <!-- The picker sits beside the form rather than in it, as editing a store
         uses a form of its own. -->
    <div
      ref="addBar"
      class="add"
      @focusout="onFocusOut"
      @keydown.esc="closePicker"
    >
      <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>
      <ul v-if="selectedStores.length && !picking" class="chosen-stores">
        <li v-for="store in selectedStores" :key="store.id">
          <button type="button" class="chosen" @mousedown.prevent @click="nameInput.focus()">
            <Icon name="store" />
            {{ store.name }}
          </button>
          <button
            type="button"
            class="clear"
            :aria-label="t('stores.clear', { name: store.name })"
            :title="t('stores.clear', { name: store.name })"
            @mousedown.prevent
            @click="storeIds = storeIds.filter(id => id !== store.id)"
          >
            <Icon name="x" />
          </button>
        </li>
      </ul>
      <form class="row" @submit.prevent="submit">
        <!-- mousedown is cancelled so the field keeps its focus until the
             click decides between closing the picker and going back. -->
        <button
          type="button"
          class="back"
          :aria-label="picking ? t('stores.close') : t('lists.back')"
          :title="picking ? t('stores.close') : t('lists.back')"
          @mousedown.prevent
          @click="onBack"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
        </button>
        <input
          ref="nameInput"
          v-model.trim="name"
          :aria-label="t('lists.itemName')"
          :placeholder="t('lists.itemName')"
          maxlength="512"
          required
          @focus="picking = true"
        />
        <!-- Keeps the keyboard open on phones, for adding the next item. -->
        <button type="submit" :disabled="busy || !name" @mousedown.prevent>
          {{ t('lists.addItem') }}
        </button>
      </form>
      <StorePicker v-if="picking" v-model="storeIds" @done="nameInput.focus()" />
    </div>
  </section>
</template>

<style scoped>
/* On wide screens the add field sits above the items; on narrow screens it is
   a bar at the bottom, as the overview of lists has. */
.list {
  display: flex;
  flex-direction: column;
  max-width: 40rem;
  margin: 0 auto;
}

.add {
  order: -1;
  margin-bottom: 1.25rem;
}

.heading {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  margin: 0 0 1.25rem;
}

.heading h2 {
  flex: 1;
  min-width: 0;
  margin: 0;
}

.share,
.share:hover:not(:disabled) {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.25rem;
  padding: 0.45rem;
  font-size: 0.875rem;
  color: var(--color-text-muted);
  background: transparent;
}

.share:hover:not(:disabled),
.share.active {
  color: var(--color-text);
  background: var(--color-surface);
}

.share.shared {
  color: var(--color-primary);
}

h2 {
  margin: 0 0 1.25rem;
  font-size: 1.5rem;
  overflow-wrap: anywhere;
}

.row {
  display: flex;
  gap: 0.5rem;
}

.row input {
  min-width: 0;
}

.row button {
  flex-shrink: 0;
}

.back {
  display: none;
}

.items {
  margin: 0;
  padding: 0;
  list-style: none;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: 0 1px 3px var(--color-shadow);
  /* Keeps a swiped row's red background inside the rounded corners. */
  overflow: hidden;
}

/* The padding is on the row inside the li, so a swipe moves all of it. */
.item-row,
.items li.editing {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 3rem;
  padding: 0.375rem 0.5rem 0.375rem 1rem;
}

.item-row {
  padding-left: 0.25rem;
}

/* The store picker goes under the name field while an item is edited. */
.items li.editing {
  flex-direction: column;
  align-items: stretch;
  padding-bottom: 0.75rem;
}

/* The circle and the name are one button, so tapping anywhere on the item
   but the pencil completes it. */
.complete,
.complete:hover:not(:disabled) {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  min-width: 0;
  min-height: 2.75rem;
  padding: 0 0 0 0.75rem;
  font-weight: inherit;
  text-align: left;
  color: inherit;
  background: transparent;
  border: none;
}

.circle {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-shrink: 0;
  width: 1.375rem;
  height: 1.375rem;
  color: transparent;
  border: 2px solid var(--color-text-muted);
  border-radius: 50%;
  transition: background-color 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}

.complete:hover .circle {
  border-color: var(--color-primary);
}

.complete:active .circle {
  transform: scale(0.9);
}

.complete.done .circle {
  color: var(--color-on-primary);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.circle :deep(svg) {
  width: 0.875rem;
  height: 0.875rem;
  stroke-width: 3.5;
}

.section-toggle,
.section-toggle:hover:not(:disabled) {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin: 1.25rem 0 0.625rem;
  padding: 0.375rem 0.5rem 0.375rem 0.25rem;
  font-size: 0.9375rem;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
}

.section-toggle:hover:not(:disabled) {
  color: var(--color-text);
}

.chevron {
  transition: transform 0.15s ease;
}

.chevron.open {
  transform: rotate(90deg);
}

.text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  padding: 0.25rem 0;
}

.item-name {
  overflow-wrap: anywhere;
}

.completed-at,
.item-store {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.item-store {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.item-store :deep(svg) {
  width: 0.875rem;
  height: 0.875rem;
  flex-shrink: 0;
}

.picker {
  margin-top: 0.75rem;
}

.chosen-stores {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.75rem;
  margin: 0 0 0.5rem;
  padding: 0;
  list-style: none;
}

.chosen-stores li {
  display: flex;
  align-items: center;
}

.chosen,
.chosen:hover:not(:disabled),
.clear,
.clear:hover:not(:disabled) {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.375rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-primary);
  background: transparent;
}

.chosen :deep(svg),
.clear :deep(svg) {
  width: 1rem;
  height: 1rem;
}

.clear,
.clear:hover:not(:disabled) {
  color: var(--color-text-muted);
}

.chosen:hover:not(:disabled),
.clear:hover:not(:disabled) {
  background: var(--color-background);
}

.edit,
.edit:hover:not(:disabled) {
  display: flex;
  flex-shrink: 0;
  padding: 0.45rem;
  color: var(--color-text-muted);
  background: transparent;
}

.edit:hover:not(:disabled) {
  color: var(--color-text);
  background: var(--color-background);
}

.items li + li {
  border-top: 1px solid var(--color-border);
}

.items li.completed .item-name {
  text-decoration: line-through;
  color: var(--color-text-muted);
}

/* The stores used in the list, as chips that narrow the list down. */
.filter {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: -0.5rem 0 1rem;
}

.filter-chip,
.filter-chip:hover:not(:disabled) {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.375rem;
  padding: 0.3rem 0.75rem;
  font-size: 0.875rem;
  font-weight: 500;
  white-space: nowrap;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.filter-chip:hover:not(:disabled) {
  border-color: var(--color-primary);
}

.filter-chip.active,
.filter-chip.active:hover:not(:disabled) {
  color: var(--color-on-primary);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.filter-chip.show-all,
.filter-chip.show-all:hover:not(:disabled) {
  color: var(--color-primary);
  background: transparent;
  border-color: transparent;
}

.filter-chip :deep(svg) {
  width: 0.9375rem;
  height: 0.9375rem;
}

.empty {
  margin: 0;
  color: var(--color-text-muted);
}

.error {
  margin: 0 0 0.5rem;
  font-size: 0.875rem;
  color: var(--color-error);
}

@media (max-width: 640px) {
  .list {
    flex: 1;
    min-height: 0;
    width: 100%;
    max-width: none;
  }

  .scroll {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 1.25rem 1rem;
  }

  .add {
    order: 0;
    margin: 0;
    padding: 0.625rem 1rem calc(0.625rem + env(safe-area-inset-bottom));
    background: var(--color-surface);
    border-top: 1px solid var(--color-border);
  }

  .picker {
    margin-top: 0.5rem;
  }

  /* One row that scrolls sideways, running to the edges of the screen. */
  .filter {
    flex-wrap: nowrap;
    overflow-x: auto;
    margin: -0.5rem -1rem 1rem;
    padding: 0 1rem;
    scrollbar-width: none;
  }

  .filter::-webkit-scrollbar {
    display: none;
  }

  .back,
  .back:hover:not(:disabled) {
    display: flex;
    align-items: center;
    padding: 0.4rem;
    color: var(--color-text);
    background: transparent;
  }

  .back svg {
    width: 1.5rem;
    height: 1.5rem;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
}
</style>
