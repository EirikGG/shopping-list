<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ItemList from './ItemList.vue'
import { createItemList, getItemLists } from '../api/items.js'

const { t } = useI18n()

// null until the first load has finished.
const lists = ref(null)
const selectedId = ref(null)
// Message keys, so the errors follow a language change.
const loadError = ref('')
const createError = ref('')

const newName = ref('')
const creating = ref(false)

// Narrow screens show either the overview of all lists or one open list.
// Wide screens show both, so there this has no effect.
const showingList = ref(false)

function open(id) {
  selectedId.value = id
  showingList.value = true
}

const selectedList = computed(
  () => lists.value?.find(list => list.id === selectedId.value) ?? null
)

onMounted(load)

async function load() {
  loadError.value = ''

  try {
    lists.value = await getItemLists()
    selectedId.value = lists.value[0]?.id ?? null
  } catch (e) {
    loadError.value = e.status === 429 ? 'errors.tooManyRequests' : 'lists.loadFailed'
  }
}

async function create() {
  createError.value = ''
  creating.value = true

  try {
    const list = await createItemList(newName.value)
    lists.value.push({ ...list, items: [] })
    open(list.id)
    newName.value = ''
  } catch (e) {
    createError.value = e.status === 429 ? 'errors.tooManyRequests' : 'lists.createFailed'
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div v-if="loadError" class="status">
    <p class="error" aria-live="polite">{{ t(loadError) }}</p>
    <button @click="load">{{ t('app.retry') }}</button>
  </div>

  <p v-else-if="lists === null" class="status muted">{{ t('lists.loading') }}</p>

  <div v-else class="layout" :class="{ 'showing-list': showingList }">
    <aside class="sidebar">
      <nav v-if="lists.length">
        <button
          v-for="list in lists"
          :key="list.id"
          class="list-link"
          :class="{ active: list.id === selectedId }"
          :aria-current="list.id === selectedId ? 'page' : undefined"
          @click="open(list.id)"
        >
          <span class="list-name">{{ list.name }}</span>
          <span class="count">{{ list.items.length }}</span>
        </button>
      </nav>
      <p v-else class="muted no-lists">{{ t('lists.noLists') }}</p>

      <form class="create" @submit.prevent="create">
        <div class="row">
          <input
            v-model.trim="newName"
            :aria-label="t('lists.newList')"
            :placeholder="t('lists.newList')"
            maxlength="512"
            required
          />
          <button
            type="submit"
            class="add-list"
            :aria-label="t('lists.create')"
            :title="t('lists.create')"
            :disabled="creating || !newName"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </button>
        </div>
        <p v-if="createError" class="error" aria-live="polite">{{ t(createError) }}</p>
      </form>
    </aside>

    <main class="content">
      <ItemList
        v-if="selectedList"
        :key="selectedList.id"
        :list="selectedList"
        @item-added="item => selectedList.items.push(item)"
        @back="showingList = false"
      />
      <p v-else class="muted">{{ t('lists.selectOrCreate') }}</p>
    </main>
  </div>
</template>

<style scoped>
/* The page is exactly one screen tall, so the lists and the items each scroll
   on their own while the menu stays in place. */
.layout {
  flex: 1;
  display: flex;
  min-height: 0;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 18rem;
  flex-shrink: 0;
  padding: 1.25rem 1rem;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-height: 0;
  overflow-y: auto;
}

.list-link {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
  width: 100%;
  padding: 0.5rem 0.75rem;
  font-weight: 500;
  text-align: left;
  color: var(--color-text);
  background: transparent;
}

.list-link:hover:not(:disabled) {
  background: var(--color-background);
}

.list-link.active,
.list-link.active:hover:not(:disabled) {
  color: var(--color-on-primary);
  background: var(--color-primary);
}

.list-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.count {
  font-size: 0.8125rem;
  opacity: 0.75;
}

.create {
  flex-shrink: 0;
  padding-top: 1rem;
  border-top: 1px solid var(--color-border);
}

.row {
  display: flex;
  gap: 0.5rem;
}

.row input {
  min-width: 0;
}

.add-list {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding: 0.5rem 0.6rem;
}

svg {
  width: 1.25rem;
  height: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
}

.content {
  flex: 1;
  min-width: 0;
  padding: 2rem;
  overflow-y: auto;
}

.status {
  padding: 2rem;
}

.muted {
  margin: 0;
  color: var(--color-text-muted);
}

.error {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: var(--color-error);
}

/* On narrow screens there is room for one thing at a time: the overview of
   all lists, or the open list. Both have their field in a bar at the bottom. */
@media (max-width: 640px) {
  .layout {
    flex-direction: column;
  }

  .layout.showing-list .sidebar,
  .layout:not(.showing-list) .content {
    display: none;
  }

  .sidebar {
    flex: 1;
    min-height: 0;
    gap: 0;
    width: auto;
    padding: 0;
    background: transparent;
    border-right: none;
  }

  nav {
    flex: 1;
    gap: 0;
    padding: 1.25rem 1rem;
  }

  /* The rows form one card, like the items of a list. */
  .list-link,
  .list-link.active,
  .list-link:hover:not(:disabled),
  .list-link.active:hover:not(:disabled) {
    padding: 0.75rem 1rem;
    color: var(--color-text);
    background: var(--color-surface);
    border-color: var(--color-border);
    border-radius: 0;
  }

  .list-link + .list-link {
    margin-top: -1px;
  }

  .list-link:first-child {
    border-radius: var(--radius) var(--radius) 0 0;
  }

  .list-link:last-child {
    border-radius: 0 0 var(--radius) var(--radius);
  }

  .list-link:only-child {
    border-radius: var(--radius);
  }

  .no-lists {
    flex: 1;
    padding: 1.25rem 1rem;
  }

  .create {
    padding: 0.625rem 1rem calc(0.625rem + env(safe-area-inset-bottom));
    background: var(--color-surface);
    border-top: 1px solid var(--color-border);
  }

  .content {
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: 0;
    overflow: hidden;
  }
}
</style>
