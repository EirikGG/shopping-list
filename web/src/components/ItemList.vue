<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createItem } from '../api/items.js'

const props = defineProps({
  list: { type: Object, required: true }
})

// 'back' is only offered on narrow screens, where the list replaces the
// overview of all lists.
const emit = defineEmits(['item-added', 'back'])

const { t } = useI18n()

const name = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)

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

      <ul v-if="list.items.length" class="items">
        <li v-for="item in list.items" :key="item.id" :class="{ completed: item.completed }">
          {{ item.name }}
        </li>
      </ul>
      <p v-else class="empty">{{ t('lists.noItems') }}</p>
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
}

.items li {
  padding: 0.75rem 1rem;
  overflow-wrap: anywhere;
}

.items li + li {
  border-top: 1px solid var(--color-border);
}

.items li.completed {
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
