<script setup>
import { computed, ref } from 'vue'
import { useLocalStorage, useMediaQuery } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import RowEditor from './RowEditor.vue'
import SwipeToDelete from './SwipeToDelete.vue'
import { createItem, deleteItem, updateItem } from '../api/items.js'

const props = defineProps({
  list: { type: Object, required: true }
})

// 'back' is only offered on narrow screens, where the list replaces the
// overview of all lists.
const emit = defineEmits(['item-added', 'item-updated', 'item-deleted', 'back'])

const { t } = useI18n()

const name = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)
// The item being renamed or deleted, if any. One at a time.
const editingId = ref(null)
// Matches the breakpoint in the styles below.
const narrow = useMediaQuery('(max-width: 640px)')

// Completed items are listed under the others, in a section that can be
// collapsed. Whether it is collapsed is remembered for every list.
const showCompleted = useLocalStorage('shoppinglist.show-completed', true)
const openItems = computed(() => props.list.items.filter(item => item.completed === null))
const completedItems = computed(() => props.list.items.filter(item => item.completed !== null))
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
    const item = await createItem(props.list.id, name.value)
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
      <h2>{{ list.name }}</h2>

      <p v-if="!list.items.length" class="empty">{{ t('lists.noItems') }}</p>
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
            <RowEditor
              v-if="editingId === item.id"
              :name="item.name"
              :save="newName => rename(item, newName)"
              :remove="() => remove(item)"
              @done="editingId = null"
            />
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
                  <span class="item-name">{{ item.name }}</span>
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

    <form class="add" @submit.prevent="submit">
      <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>
      <div class="row">
        <button
          type="button"
          class="back"
          :aria-label="t('lists.back')"
          :title="t('lists.back')"
          @click="emit('back')"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
        </button>
        <input
          v-model.trim="name"
          :aria-label="t('lists.itemName')"
          :placeholder="t('lists.itemName')"
          maxlength="512"
          required
        />
        <button type="submit" :disabled="busy || !name">{{ t('lists.addItem') }}</button>
      </div>
    </form>
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

.item-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
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
