<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import { removeItemListMember, shareItemList } from '../api/items.js'
import { isValidEmail } from '../validation.js'

// Who a list is shared with. The owner adds people by email and removes them;
// a member can leave the list.
const props = defineProps({
  list: { type: Object, required: true }
})

// 'members-changed' with the new members, 'left' once the user has left.
const emit = defineEmits(['members-changed', 'left'])

const { t } = useI18n()

const isOwner = computed(() => props.list.role === 'OWNER')
const me = computed(() => props.list.members.find(member => member.you))

const email = ref('')
const busy = ref(false)
// A message key, so the error follows a language change.
const error = ref('')

function fullName(member) {
  return `${member.firstName} ${member.lastName}`
}

function failed(e, key) {
  error.value = e.status === 429 ? 'errors.tooManyRequests' : key
}

async function share() {
  error.value = ''
  busy.value = true

  try {
    const member = await shareItemList(props.list.id, email.value)
    if (!props.list.members.some(other => other.personId === member.personId)) {
      emit('members-changed', [...props.list.members, member])
    }
    email.value = ''
  } catch (e) {
    failed(e, e.status === 404 ? 'share.notFound' : 'share.shareFailed')
  } finally {
    busy.value = false
  }
}

async function remove(member) {
  error.value = ''
  busy.value = true

  try {
    await removeItemListMember(props.list.id, member.personId)
    emit('members-changed', props.list.members.filter(other => other.personId !== member.personId))
  } catch (e) {
    failed(e, 'share.removeFailed')
  } finally {
    busy.value = false
  }
}

async function leave() {
  if (!window.confirm(t('share.confirmLeave', { name: props.list.name }))) {
    return
  }

  error.value = ''
  busy.value = true

  try {
    await removeItemListMember(props.list.id, me.value.personId)
    emit('left')
  } catch (e) {
    failed(e, 'share.leaveFailed')
    busy.value = false
  }
}
</script>

<template>
  <section class="sharing">
    <h3>{{ t('share.title') }}</h3>

    <ul class="members">
      <li v-for="member in list.members" :key="member.personId">
        <span class="who">
          <span class="name">
            {{ fullName(member) }}
            <span v-if="member.you" class="muted">({{ t('share.you') }})</span>
          </span>
          <span class="email">{{ member.email }}</span>
        </span>
        <span v-if="member.role === 'OWNER'" class="badge">{{ t('share.owner') }}</span>
        <button
          v-else-if="isOwner"
          type="button"
          class="remove"
          :aria-label="t('share.remove', { name: fullName(member) })"
          :title="t('share.remove', { name: fullName(member) })"
          :disabled="busy"
          @click="remove(member)"
        >
          <Icon name="x" />
        </button>
      </li>
    </ul>

    <form v-if="isOwner" class="add" @submit.prevent="share">
      <input
        v-model.trim="email"
        type="email"
        :aria-label="t('share.email')"
        :placeholder="t('share.email')"
        autocomplete="off"
        required
      />
      <button type="submit" :disabled="busy || !isValidEmail(email)">{{ t('share.add') }}</button>
    </form>

    <button v-else type="button" class="secondary leave" :disabled="busy" @click="leave">
      {{ t('share.leave') }}
    </button>

    <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>
  </section>
</template>

<style scoped>
.sharing {
  margin: 0 0 1.25rem;
  padding: 0.875rem 1rem 1rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: 0 1px 3px var(--color-shadow);
}

h3 {
  margin: 0 0 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-text-muted);
}

.members {
  margin: 0 0 0.75rem;
  padding: 0;
  list-style: none;
}

.members li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.75rem;
  padding: 0.25rem 0;
}

.members li + li {
  border-top: 1px solid var(--color-border);
}

.who {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.name,
.email {
  overflow-wrap: anywhere;
}

.email,
.muted {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.badge {
  flex-shrink: 0;
  padding: 0.125rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-primary);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.remove,
.remove:hover:not(:disabled) {
  display: flex;
  flex-shrink: 0;
  padding: 0.45rem;
  color: var(--color-text-muted);
  background: transparent;
}

.remove:hover:not(:disabled) {
  color: var(--color-error);
  background: var(--color-background);
}

.add {
  display: flex;
  gap: 0.5rem;
}

.add input {
  min-width: 0;
}

.add button {
  flex-shrink: 0;
}

.leave {
  width: 100%;
}

.error {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
