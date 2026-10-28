<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import RowEditor from './RowEditor.vue'
import SwipeToDelete from './SwipeToDelete.vue'
import {
  createLabel,
  deleteLabel,
  forgetLabel,
  loadLabels,
  renameLabel,
  labelsOf
} from '../api/labels.js'

// Picks the labels for an item, and adds, renames and deletes labels. The
// labels belong to the list, so everyone on it sees the same ones.
const props = defineProps({
  listId: { type: Number, required: true },
  // The ids of the chosen labels.
  modelValue: { type: Array, default: () => [] },
  // One row of chips that scrolls sideways, with New first, for picking and
  // adding only. For the add bar on phones, where the keyboard leaves room
  // for little else. Labels are renamed and deleted in the item editor.
  compact: { type: Boolean, default: false },
  // For an editor with room to spare: on phones it keeps the heading, and
  // shows every label rather than a box that scrolls.
  spacious: { type: Boolean, default: false }
})

// 'done' is sent when adding or editing a label has ended, so the parent can
// give the item field its focus back.
const emit = defineEmits(['update:modelValue', 'done'])

const { t } = useI18n()

const labels = computed(() => labelsOf(props.listId))

// Matches the breakpoint in the styles below.
const narrow = useMediaQuery('(max-width: 640px)')

// Message keys, so the errors follow a language change.
const loadError = ref('')
const error = ref('')

const adding = ref(false)
const newName = ref('')
const busy = ref(false)
const newInput = ref(null)

// The label being renamed or deleted, if any. One at a time.
const editingId = ref(null)

onMounted(load)

async function load() {
  loadError.value = ''
  try {
    await loadLabels(props.listId)
  } catch (e) {
    loadError.value = e.status === 429 ? 'errors.tooManyRequests' : 'labels.loadFailed'
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

function isChosen(label) {
  return props.modelValue.includes(label.id)
}

function choose(id) {
  if (!props.modelValue.includes(id)) {
    emit('update:modelValue', [...props.modelValue, id])
  }
}

function toggle(label) {
  if (isChosen(label)) {
    emit('update:modelValue', props.modelValue.filter(id => id !== label.id))
  } else {
    choose(label.id)
  }
}

function startEditing(label) {
  stopAdding(false)
  error.value = ''
  editingId.value = label.id
}

function stopEditing() {
  editingId.value = null
  emit('done')
}

// Deleting a label also unchooses it.
function forget(label) {
  if (isChosen(label)) {
    emit('update:modelValue', props.modelValue.filter(id => id !== label.id))
  }
  forgetLabel(props.listId, label.id)
}

// The editing ends before the row goes, so the focus is handed back to the
// item field while the editor still has it. Otherwise the picker closes.
async function remove(label) {
  await deleteLabel(label.id)
  stopEditing()
  forget(label)
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

// In the row of chips, scrolls a label just added or chosen into sight, as it
// may be far along the row.
const list = ref(null)

async function showChip(id) {
  if (!props.compact) {
    return
  }
  await nextTick()
  list.value
    ?.querySelector(`[data-label-id="${id}"]`)
    ?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
}

async function add() {
  if (!newName.value || busy.value) {
    return
  }

  // A label with that name already exists, so it is chosen instead.
  const existing = labels.value?.find(
    label => label.name.toLowerCase() === newName.value.toLowerCase()
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
    const label = await createLabel(props.listId, newName.value)
    choose(label.id)
    stopAdding()
    showChip(label.id)
  } catch (e) {
    error.value = e.status === 429 ? 'errors.tooManyRequests' : 'labels.createFailed'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="picker" :class="{ compact, spacious }" @mousedown="keepFocus">
    <p class="heading">{{ t('labels.labels') }}</p>

    <div v-if="loadError" class="status">
      <p class="error" aria-live="polite">{{ t(loadError) }}</p>
      <button type="button" class="secondary" @click="load">{{ t('app.retry') }}</button>
    </div>

    <p v-else-if="labels === null" class="status muted">{{ t('labels.loading') }}</p>

    <template v-else>
      <p v-if="!labels.length && !adding && !compact" class="status muted">{{ t('labels.none') }}</p>

      <ul ref="list" class="labels">
        <!-- First in the row of chips, so it is there without scrolling. -->
        <li v-if="compact && !adding">
          <div class="label-row add-label">
            <button type="button" class="label" @click="startAdding">
              <Icon name="plus" />
              <span class="name">{{ t('labels.new') }}</span>
            </button>
          </div>
        </li>

        <!-- In the row of chips, the field for a new label takes the whole row. -->
        <template v-if="!(compact && adding)">
          <li
            v-for="label in labels"
            :key="label.id"
            :data-label-id="label.id"
            :class="{ editing: editingId === label.id }"
          >
            <RowEditor
              v-if="editingId === label.id"
              :name="label.name"
              :save="newName => renameLabel(listId, label.id, newName)"
              :remove="() => remove(label)"
              @done="stopEditing"
            />
            <SwipeToDelete
              v-else
              :enabled="narrow && !compact"
              :remove="() => deleteLabel(label.id)"
              @removed="forget(label)"
              @failed="swipeFailed"
            >
              <div class="label-row" :class="{ selected: isChosen(label) }">
                <button
                  type="button"
                  class="label"
                  :aria-pressed="isChosen(label)"
                  @click="toggle(label)"
                >
                  <Icon :name="compact && isChosen(label) ? 'check' : 'label'" />
                  <span class="name">{{ label.name }}</span>
                  <Icon v-if="isChosen(label) && !compact" name="check" />
                </button>
                <button
                  v-if="!compact"
                  type="button"
                  class="edit"
                  :aria-label="t('edit.editItem', { name: label.name })"
                  :title="t('edit.edit')"
                  @click="startEditing(label)"
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
            :aria-label="t('labels.labelName')"
            :placeholder="t('labels.labelName')"
            maxlength="256"
            @keydown.enter.prevent="add"
            @keydown.esc.stop.prevent="stopAdding()"
          />
          <button
            type="button"
            class="icon"
            :aria-label="t('labels.create')"
            :title="t('labels.create')"
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
          <div class="label-row add-label">
            <button type="button" class="label" @click="startAdding">
              <Icon name="plus" />
              <span class="name">{{ t('labels.newLabel') }}</span>
            </button>
          </div>
        </li>
      </ul>

      <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>
    </template>
  </div>
</template>

<style scoped>
/* On wide screens the labels are chips that wrap onto more rows. */
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

.labels {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

/* The editor takes a whole line, as it is wider than a chip. */
.labels li.editing {
  flex-basis: 100%;
}

.label-row {
  display: flex;
  align-items: center;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.label-row:hover {
  border-color: var(--color-primary);
}

.label-row.selected {
  color: var(--color-on-primary);
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.label-row.add-label {
  color: var(--color-primary);
  border-style: dashed;
}

.label,
.label:hover:not(:disabled) {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  font-weight: 500;
  color: inherit;
  background: transparent;
  border: none;
}

.label-row:has(.edit) .label {
  padding-right: 0.25rem;
}

.label :deep(svg),
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
   the edges of the screen, as the label filter does. */
.picker.compact .labels {
  flex-wrap: nowrap;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  margin: 0 -1rem;
  padding: 0.125rem 1rem;
  scrollbar-width: none;
}

.picker.compact .labels::-webkit-scrollbar {
  display: none;
}

.picker.compact .labels li {
  flex-shrink: 0;
}

/* Large enough to tap with the keyboard open. */
.picker.compact .label,
.picker.compact .label:hover:not(:disabled) {
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

/* On narrow screens the labels are otherwise rows in a box about three and a
   half rows tall, so the bar stays low and the cut-off row shows that it
   scrolls. As with items, a row can be swiped left to delete it. */
@media (max-width: 640px) {
  .picker:not(.spacious) .heading {
    display: none;
  }

  .picker:not(.compact) .labels {
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
  .picker.spacious .labels {
    max-height: none;
  }

  .picker:not(.compact) .labels li {
    flex-shrink: 0;
  }

  .picker:not(.compact) .labels li + li {
    border-top: 1px solid var(--color-border);
  }

  .picker:not(.compact) .labels li.editing {
    flex-basis: auto;
    padding: 0.375rem 0.5rem 0.375rem 1rem;
  }

  .picker:not(.compact) .label-row,
  .picker:not(.compact) .label-row:hover {
    min-height: 2.75rem;
    border: none;
    border-radius: 0;
  }

  .picker:not(.compact) .label,
  .picker:not(.compact) .label:hover:not(:disabled) {
    flex: 1;
    gap: 0.75rem;
    align-self: stretch;
    padding: 0.375rem 1rem;
  }

  .picker:not(.compact) .label :deep(svg),
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
