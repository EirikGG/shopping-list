<script setup>
import { onMounted, ref } from 'vue'
import LoginForm from './components/LoginForm.vue'
import SignupForm from './components/SignupForm.vue'
import { sessionIsValid } from './api/auth.js'
import { clearSession, sessionId } from './session.js'

const tab = ref('login')
const notice = ref('')

// A stored session id can be expired or from a wiped database, so it is
// checked against the backend before the app treats it as logged in.
onMounted(async () => {
  if (sessionId.value && !(await sessionIsValid(sessionId.value))) {
    clearSession()
  }
})

function onCreated(username) {
  notice.value = `Account "${username}" created. You can log in now.`
  tab.value = 'login'
}

function showTab(name) {
  notice.value = ''
  tab.value = name
}
</script>

<template>
  <main>
    <div class="card">
      <h1>TGG</h1>

      <template v-if="sessionId">
        <p class="signed-in">You are logged in.</p>
        <button class="secondary" @click="clearSession">Log out</button>
      </template>

      <template v-else>
        <div class="tabs">
          <button
            :class="{ secondary: tab !== 'login' }"
            @click="showTab('login')"
          >
            Log in
          </button>
          <button
            :class="{ secondary: tab !== 'signup' }"
            @click="showTab('signup')"
          >
            Create account
          </button>
        </div>

        <p v-if="notice" class="notice">{{ notice }}</p>

        <LoginForm v-if="tab === 'login'" />
        <SignupForm v-else @created="onCreated" />
      </template>
    </div>
  </main>
</template>

<style scoped>
main {
  display: flex;
  justify-content: center;
  padding: 3rem 1rem;
}

.card {
  width: 100%;
  max-width: 28rem;
  padding: 2rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: 0 1px 3px rgba(23, 50, 77, 0.08);
}

h1 {
  margin: 0 0 1.5rem;
  font-size: 1.5rem;
  text-align: center;
  color: var(--color-primary);
}

.tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.tabs button {
  flex: 1;
}

.notice {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--color-success);
}

.signed-in {
  margin: 0 0 1rem;
  color: var(--color-text-muted);
}
</style>
