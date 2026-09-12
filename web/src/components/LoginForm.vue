<script setup>
import { ref } from 'vue'
import { login } from '../api/auth.js'
import { setSessionId } from '../session.js'

const email = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true

  try {
    setSessionId(await login(email.value, password.value))
  } catch (e) {
    error.value = e.status === 401
      ? 'Wrong email or password.'
      : 'Could not log in. Try again.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form @submit.prevent="submit">
    <div class="field">
      <label for="login-email">Email</label>
      <input
        id="login-email"
        v-model="email"
        type="email"
        autocomplete="email"
        required
      />
    </div>

    <div class="field">
      <label for="login-password">Password</label>
      <input
        id="login-password"
        v-model="password"
        type="password"
        autocomplete="current-password"
        required
      />
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <button type="submit" :disabled="busy">
      {{ busy ? 'Logging in…' : 'Log in' }}
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
