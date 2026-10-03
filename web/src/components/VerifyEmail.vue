<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { verifyEmail } from '../api/auth.js'

const props = defineProps({
  email: { type: String, required: true }
})

const emit = defineEmits(['verified'])

const { t } = useI18n()

// The backend generates codes between 10000 and 99999.
const CODE_LENGTH = 5

const code = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)

const canSubmit = computed(
  () => !busy.value && new RegExp(`^\\d{${CODE_LENGTH}}$`).test(code.value)
)

async function submit() {
  error.value = ''
  busy.value = true

  try {
    await verifyEmail(props.email, code.value)
    emit('verified')
  } catch (e) {
    // Several cases share a status, so the code decides before the status
    // does. A plain 400 is a code the backend could not read.
    if (e.status === 429) {
      error.value = 'errors.tooManyRequests'
    } else if (e.code === 'EMAIL_ALREADY_VERIFIED_EXCEPTION') {
      emit('verified')
    } else if (e.code === 'NO_EMAIL_CONNECTED_TO_VERIFICATION_UUID') {
      error.value = 'verifyEmail.notRegistered'
    } else if (e.code === 'WRONG_EMAIL_VERIFICATION_CODE_EXCEPTION' || e.status === 400) {
      error.value = 'verifyEmail.wrongCode'
    } else {
      error.value = 'verifyEmail.failed'
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form @submit.prevent="submit">
    <p class="intro">{{ t('verifyEmail.intro', { email }) }}</p>

    <div class="field">
      <label for="verify-code">{{ t('verifyEmail.code') }}</label>
      <input
        id="verify-code"
        v-model.trim="code"
        inputmode="numeric"
        autocomplete="one-time-code"
        :maxlength="CODE_LENGTH"
        :pattern="`\\d{${CODE_LENGTH}}`"
        required
      />
    </div>

    <p v-if="error" class="error" aria-live="polite">{{ t(error) }}</p>

    <button type="submit" :disabled="!canSubmit">
      {{ busy ? t('verifyEmail.submitting') : t('verifyEmail.submit') }}
    </button>
  </form>
</template>

<style scoped>
.intro {
  margin: 0 0 1.5rem;
  font-size: 0.9375rem;
  color: var(--color-text-muted);
}

.field {
  margin-bottom: 1rem;
}

#verify-code {
  font-size: 1.25rem;
  letter-spacing: 0.25em;
  text-align: center;
}

button[type='submit'] {
  width: 100%;
}

.error {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
