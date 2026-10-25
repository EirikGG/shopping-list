<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'

// Renames or deletes one row — a list or an item. The parent does the actual
// work in save and remove, so this only handles the form, busy state and
// errors.
const props = defineProps({
  name: { type: String, required: true },
  save: { type: Function, required: true },
  remove: { type: Function, required: true },
  // A message key, asked before deleting. Without it the row is deleted
  // straight away.
  confirmDelete: { type: String, default: '' },
  // A message key for the remove button, such as leaving a shared list.
  removeLabel: { type: String, default: 'edit.delete' }
})

const emit = defineEmits(['done'])

const { t } = useI18n()

const newName = ref(props.name)
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)
const input = ref(null)

onMounted(() => {
  input.value.focus()
  input.value.select()
})

async function run(action, failedKey) {
  error.value = ''
  busy.value = true

  try {
    await action()
    emit('done')
  } catch (e) {
    error.value = e.status === 429 ? 'errors.tooManyRequests' : failedKey
  } finally {
    busy.value = false
  }
}

function submit() {
  if (newName.value === props.name) {
    emit('done')
    return
  }
  run(() => props.save(newName.value), 'edit.renameFailed')
}

function onDelete() {
  if (props.confirmDelete && !window.confirm(t(props.confirmDelete, { name: props.name }))) {
    return
  }
  run(props.remove, 'edit.deleteFailed')
}
</script>

<template>
  <form class="editor" @submit.prevent="submit" @keydown.esc.stop="emit('done')">
    <div class="row">
      <input
        ref="input"
        v-model.trim="newName"
        :aria-label="t('edit.name')"
        maxlength="512"
        required
      />
      <button
        type="submit"
        class="icon"
        :aria-label="t('edit.save')"
        :title="t('edit.save')"
        :disabled="busy || !newName"
      >
        <Icon name="check" />
      </button>
      <button
        type="button"
        class="icon danger"
        :aria-label="t(removeLabel)"
        :title="t(removeLabel)"
        :disabled="busy"
        @click="onDelete"
      >
        <Icon name="trash" />
      </button>
      <button
        type="button"
        class="icon secondary"
        :aria-label="t('edit.cancel')"
        :title="t('edit.cancel')"
        :disabled="busy"
        @click="emit('done')"
      >
        <Icon name="x" />
      </button>
    </div>
    <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>
  </form>
</template>

<style scoped>
.editor {
  width: 100%;
}

.row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.row input {
  min-width: 0;
  padding: 0.4rem 0.6rem;
}

.icon {
  display: flex;
  flex-shrink: 0;
  padding: 0.45rem;
}

.icon.danger,
.icon.danger:hover:not(:disabled) {
  color: var(--color-error);
  background: transparent;
  border-color: var(--color-border);
}

.icon.danger:hover:not(:disabled) {
  background: var(--color-background);
}

.error {
  margin: 0.375rem 0 0;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
