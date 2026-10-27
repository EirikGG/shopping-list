<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import StorePicker from './StorePicker.vue'
import {
  MAX_FILE_BYTES,
  addLink,
  attachmentUrl,
  deleteAttachment,
  deleteItem,
  deleteLink,
  isImage,
  updateItem,
  uploadAttachment
} from '../api/items.js'
import { formatTime, parseBackendTime } from '../time.js'

// Everything about one item: its name, stores, links and files. Stores, links
// and files are saved as soon as they change; the name when Done is pressed.
// Escape closes without saving a changed name. It opens with the focus on the
// close button rather than the name, so a phone does not bring up the
// keyboard until a field is tapped.
const props = defineProps({
  item: { type: Object, required: true },
  listId: { type: Number, required: true }
})

// 'item-updated' with the changed item, 'stores-changed' with the chosen store
// ids (the list saves those), 'item-deleted' with the id, and 'close'.
const emit = defineEmits(['item-updated', 'stores-changed', 'item-deleted', 'close'])

const { locale, t } = useI18n()

const dialog = ref(null)
const name = ref(props.item.name)
const busy = ref(false)
// A message key and its values, so the error follows a language change.
const error = ref(null)

const links = computed(() => props.item.links ?? [])
const attachments = computed(() => props.item.attachments ?? [])
const images = computed(() => attachments.value.filter(isImage))
const otherFiles = computed(() => attachments.value.filter(attachment => !isImage(attachment)))

onMounted(() => dialog.value.showModal())

function fail(e, key, values = {}) {
  error.value = e?.status === 429 ? { key: 'errors.tooManyRequests' } : { key, values }
}

function time(value) {
  return formatTime(parseBackendTime(value), locale.value)
}

// Saves a changed name, then closes. A failed save keeps the editor open.
async function done() {
  const newName = name.value.trim()
  if (newName && newName !== props.item.name) {
    busy.value = true
    error.value = null
    try {
      emit('item-updated', await updateItem(props.item.id, newName, props.item.completed !== null))
    } catch (e) {
      fail(e, 'edit.renameFailed')
      return
    } finally {
      busy.value = false
    }
  }
  dialog.value.close()
}

// A tap on the dimmed page around the editor is the same as Done.
function onDialogClick(event) {
  if (event.target === dialog.value) {
    done()
  }
}

async function removeItem() {
  if (!window.confirm(t('item.confirmDelete', { name: props.item.name }))) {
    return
  }
  busy.value = true
  error.value = null
  try {
    await deleteItem(props.item.id)
    emit('item-deleted', props.item.id)
    dialog.value.close()
  } catch (e) {
    fail(e, 'edit.deleteFailed')
  } finally {
    busy.value = false
  }
}


const newUrl = ref('')
const newTitle = ref('')
const addingLink = ref(false)

// The title if it has one, otherwise the address without https://.
function linkText(link) {
  return link.title || link.url.replace(/^https?:\/\//, '').replace(/\/$/, '')
}

async function submitLink() {
  addingLink.value = true
  error.value = null
  try {
    const link = await addLink(props.item.id, newUrl.value, newTitle.value)
    emit('item-updated', { ...props.item, links: [...links.value, link] })
    newUrl.value = ''
    newTitle.value = ''
  } catch (e) {
    fail(e, e?.code === 'INVALID_URL' ? 'item.invalidLink' : 'item.addLinkFailed')
  } finally {
    addingLink.value = false
  }
}

async function removeLink(link) {
  error.value = null
  try {
    await deleteLink(link.id)
    emit('item-updated', { ...props.item, links: links.value.filter(other => other.id !== link.id) })
  } catch (e) {
    fail(e, 'edit.deleteFailed')
  }
}


const fileInput = ref(null)
// The name of the file being uploaded, if any.
const uploading = ref('')

// One at a time, in the order chosen. A file over the limit is skipped with a
// message rather than sent, as the upload would only be refused.
async function uploadFiles(event) {
  const files = [...event.target.files]
  event.target.value = ''
  error.value = null

  for (const file of files) {
    if (file.size > MAX_FILE_BYTES) {
      fail(null, 'item.fileTooLarge', { name: file.name, max: formatSize(MAX_FILE_BYTES) })
      continue
    }
    uploading.value = file.name
    try {
      const attachment = await uploadAttachment(props.item.id, file)
      emit('item-updated', { ...props.item, attachments: [...attachments.value, attachment] })
    } catch (e) {
      const key = e?.code === 'FILE_TOO_LARGE' || e?.status === 413 ? 'item.fileTooLarge' : 'item.uploadFailed'
      fail(e, key, { name: file.name, max: formatSize(MAX_FILE_BYTES) })
    }
  }
  uploading.value = ''
}

async function removeAttachment(attachment) {
  if (!window.confirm(t('item.confirmDeleteFile', { name: attachment.fileName }))) {
    return
  }
  error.value = null
  try {
    await deleteAttachment(attachment.id)
    emit('item-updated', {
      ...props.item,
      attachments: attachments.value.filter(other => other.id !== attachment.id)
    })
  } catch (e) {
    fail(e, 'edit.deleteFailed')
  }
}

function formatSize(bytes) {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} kB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '')} MB`
}
</script>

<template>
  <dialog
    ref="dialog"
    class="item-editor"
    :aria-label="t('edit.editItem', { name: item.name })"
    @close="emit('close')"
    @click="onDialogClick"
  >
    <form class="header" @submit.prevent="done">
      <input
        v-model="name"
        class="name"
        :aria-label="t('edit.name')"
        maxlength="512"
        required
      />
      <button
        type="button"
        class="icon secondary"
        :aria-label="t('item.close')"
        :title="t('item.close')"
        autofocus
        @click="dialog.close()"
      >
        <Icon name="x" />
      </button>
    </form>

    <div class="body">
      <p class="times">
        {{ t('lists.addedAt', { time: time(item.created) }) }}
        <template v-if="item.completed">
          · {{ t('lists.completedAt', { time: time(item.completed) }) }}
        </template>
      </p>

      <StorePicker
        spacious
        :list-id="listId"
        :model-value="(item.stores ?? []).map(store => store.storeId)"
        @update:model-value="ids => emit('stores-changed', ids)"
      />

      <section>
        <h3 class="heading">{{ t('item.links') }}</h3>
        <ul v-if="links.length" class="links">
          <li v-for="link in links" :key="link.id">
            <Icon name="link" />
            <a :href="link.url" target="_blank" rel="noopener noreferrer">{{ linkText(link) }}</a>
            <button
              type="button"
              class="icon remove"
              :aria-label="t('item.removeLink', { name: linkText(link) })"
              :title="t('edit.delete')"
              @click="removeLink(link)"
            >
              <Icon name="trash" />
            </button>
          </li>
        </ul>
        <form class="add-link" @submit.prevent="submitLink">
          <input
            v-model.trim="newUrl"
            inputmode="url"
            autocomplete="off"
            :aria-label="t('item.linkUrl')"
            :placeholder="t('item.linkUrl')"
            maxlength="2048"
            required
          />
          <input
            v-model="newTitle"
            :aria-label="t('item.linkTitle')"
            :placeholder="t('item.linkTitle')"
            maxlength="256"
          />
          <button type="submit" class="secondary" :disabled="addingLink || !newUrl">
            {{ t('item.addLink') }}
          </button>
        </form>
      </section>

      <section>
        <h3 class="heading">{{ t('item.files') }}</h3>
        <ul v-if="images.length" class="images">
          <li v-for="image in images" :key="image.id">
            <a :href="attachmentUrl(image.id)" target="_blank" rel="noopener" :title="image.fileName">
              <img :src="attachmentUrl(image.id)" :alt="image.fileName" loading="lazy" />
            </a>
            <button
              type="button"
              class="icon remove on-image"
              :aria-label="t('item.removeFile', { name: image.fileName })"
              :title="t('edit.delete')"
              @click="removeAttachment(image)"
            >
              <Icon name="trash" />
            </button>
          </li>
        </ul>
        <ul v-if="otherFiles.length" class="files">
          <li v-for="file in otherFiles" :key="file.id">
            <Icon name="paperclip" />
            <a :href="attachmentUrl(file.id, { download: true })" :download="file.fileName">
              {{ file.fileName }}
            </a>
            <span class="size">{{ formatSize(file.size) }}</span>
            <button
              type="button"
              class="icon remove"
              :aria-label="t('item.removeFile', { name: file.fileName })"
              :title="t('edit.delete')"
              @click="removeAttachment(file)"
            >
              <Icon name="trash" />
            </button>
          </li>
        </ul>
        <input ref="fileInput" type="file" multiple class="file-input" @change="uploadFiles" />
        <button
          type="button"
          class="secondary upload"
          :disabled="!!uploading"
          @click="fileInput.click()"
        >
          <Icon name="paperclip" />
          {{ uploading ? t('item.uploading', { name: uploading }) : t('item.addFiles') }}
        </button>
      </section>
    </div>

    <div class="footer">
      <p v-if="error" class="error" aria-live="polite">{{ t(error.key, error.values ?? {}) }}</p>
      <div class="actions">
        <button type="button" class="secondary danger" :disabled="busy" @click="removeItem">
          <Icon name="trash" />
          {{ t('item.delete') }}
        </button>
        <button type="button" :disabled="busy || !name.trim()" @click="done">{{ t('item.done') }}</button>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
/* A box in the middle on wide screens; the whole screen on phones. */
.item-editor {
  display: flex;
  flex-direction: column;
  width: min(36rem, calc(100% - 2rem));
  max-height: calc(100% - 2rem);
  padding: 0;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: 0 8px 32px var(--color-shadow);
}

.item-editor:not([open]) {
  display: none;
}

.item-editor::backdrop {
  background: rgba(15, 29, 43, 0.45);
}

.header {
  display: flex;
  gap: 0.5rem;
  padding: 1rem 1rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.name {
  font-size: 1.125rem;
  font-weight: 600;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1rem;
  overflow-y: auto;
}

.times {
  margin: 0;
  font-size: 0.875rem;
  color: var(--color-text-muted);
}

/* Matches the heading of the store picker. */
.heading {
  margin: 0 0 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.links li,
.files li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.5rem;
}

.links li + li,
.files li + li {
  border-top: 1px solid var(--color-border);
}

.links a,
.files a {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-primary);
}

.size {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.add-link {
  display: grid;
  grid-template-columns: 2fr 1fr auto;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.add-link input {
  min-width: 0;
}

.images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(6.5rem, 1fr));
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.images li {
  position: relative;
  aspect-ratio: 1;
}

.images a,
.images img {
  display: block;
  width: 100%;
  height: 100%;
}

.images img {
  object-fit: cover;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-background);
}

.files {
  margin-bottom: 0.5rem;
}

/* The real file field is hidden behind the button, which looks like the
   others. */
.file-input {
  display: none;
}

.upload,
.danger {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.icon {
  display: flex;
  flex-shrink: 0;
  padding: 0.45rem;
}

.remove,
.remove:hover:not(:disabled) {
  color: var(--color-text-muted);
  background: transparent;
  border: none;
}

.remove:hover:not(:disabled) {
  color: var(--color-error);
}

.remove.on-image,
.remove.on-image:hover:not(:disabled) {
  position: absolute;
  top: 0.25rem;
  right: 0.25rem;
  padding: 0.35rem;
  color: #ffffff;
  background: rgba(15, 29, 43, 0.6);
  border-radius: var(--radius);
}

.footer {
  padding: 0.75rem 1rem 1rem;
  border-top: 1px solid var(--color-border);
}

.actions {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
}

.danger,
.danger:hover:not(:disabled) {
  color: var(--color-error);
}

.error {
  margin: 0 0 0.5rem;
  font-size: 0.875rem;
  color: var(--color-error);
}

@media (max-width: 640px) {
  .item-editor {
    width: 100%;
    max-width: 100%;
    height: var(--viewport-height, 100dvh);
    max-height: var(--viewport-height, 100dvh);
    margin: 0;
    border: none;
    border-radius: 0;
  }

  .body {
    flex: 1;
  }

  .add-link {
    grid-template-columns: 1fr auto;
  }

  .add-link input:first-child {
    grid-column: 1 / -1;
  }

  .footer {
    padding-bottom: calc(1rem + env(safe-area-inset-bottom));
  }
}
</style>
