<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { login } from '../api/auth.js'
import { setSession } from '../session.js'

const { t } = useI18n()

const email = ref('')
const password = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true

  try {
    setSession(await login(email.value, password.value), email.value)
  } catch (e) {
    if (e.status === 429) {
      error.value = 'errors.tooManyRequests'
    } else {
      error.value = e.status === 401 ? 'login.wrongCredentials' : 'login.failed'
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form @submit.prevent="submit">
    <div class="field">
      <label for="login-email">{{ t('login.email') }}</label>
      <input
        id="login-email"
        v-model="email"
        type="email"
        autocomplete="email"
        required
      />
    </div>

    <div class="field">
      <label for="login-password">{{ t('login.password') }}</label>
      <input
        id="login-password"
        v-model="password"
        type="password"
        autocomplete="current-password"
        required
      />
    </div>

    <p v-if="error" class="error">{{ t(error) }}</p>

    <button type="submit" :disabled="busy">
      {{ busy ? t('login.submitting') : t('login.submit') }}
    </button>
  </form>
</template>

<style scoped>
.field {
  margin-bottom: 1rem;
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
