<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import LoginForm from './components/LoginForm.vue'
import SignupForm from './components/SignupForm.vue'
import VerifyEmail from './components/VerifyEmail.vue'
import { sessionStatus } from './api/auth.js'
import { clearSession, sessionEmail, sessionId } from './session.js'

const { t } = useI18n()

const tab = ref('login')
// Holds the created account's email rather than the finished sentence, so the
// notice follows a language change.
const createdEmail = ref('')

// null while logged out, otherwise 'checking' | 'verified' | 'unverified' |
// 'unavailable'. 'unavailable' means the check itself failed, so the session
// is kept and the check can be retried.
// Logging in works before the email is verified, so a session is only fully
// usable once the backend reports it as verified.
const status = ref(null)
// A message key for why the check failed, so it follows a language change.
const checkError = ref('')

// A stored session id can be expired or from a wiped database, so every
// session — stored or new — is checked against the backend.
watch(sessionId, refreshStatus, { immediate: true })

async function refreshStatus(id) {
  if (!id) {
    status.value = null
    return
  }

  status.value = 'checking'

  let result
  try {
    result = await sessionStatus(id)
  } catch (e) {
    result = 'unavailable'
    checkError.value = e.status === 429 ? 'errors.tooManyRequests' : 'app.checkFailed'
  }

  // A later login or logout wins over a check that was still in flight.
  if (sessionId.value !== id) {
    return
  }

  // Sessions stored before the email was kept alongside them cannot be
  // verified, so they are logged out as well.
  if (result === 'invalid' || (result === 'unverified' && !sessionEmail.value)) {
    clearSession()
  } else {
    status.value = result
  }
}

function onCreated(email) {
  createdEmail.value = email
  tab.value = 'login'
}

function showTab(name) {
  createdEmail.value = ''
  tab.value = name
}

function logOut() {
  createdEmail.value = ''
  clearSession()
}
</script>

<template>
  <main>
    <div class="card">
      <div class="card-header">
        <LanguageSwitcher />
      </div>

      <h1>Shopping list</h1>

      <p v-if="status === 'checking'" class="signed-in">{{ t('app.checkingSession') }}</p>

      <template v-else-if="status === 'unavailable'">
        <p class="check-error" aria-live="polite">{{ t(checkError) }}</p>
        <button @click="refreshStatus(sessionId)">{{ t('app.retry') }}</button>
        <button class="secondary log-out" @click="logOut">{{ t('app.logOut') }}</button>
      </template>

      <template v-else-if="status === 'unverified'">
        <VerifyEmail :email="sessionEmail" @verified="status = 'verified'" />
        <button class="secondary log-out" @click="logOut">{{ t('app.logOut') }}</button>
      </template>

      <template v-else-if="status === 'verified'">
        <p class="signed-in">{{ t('app.signedIn') }}</p>
        <button class="secondary" @click="logOut">{{ t('app.logOut') }}</button>
      </template>

      <template v-else>
        <div class="tabs">
          <button
            :class="{ secondary: tab !== 'login' }"
            @click="showTab('login')"
          >
            {{ t('app.logInTab') }}
          </button>
          <button
            :class="{ secondary: tab !== 'signup' }"
            @click="showTab('signup')"
          >
            {{ t('app.signupTab') }}
          </button>
        </div>

        <p v-if="createdEmail" class="notice">
          {{ t('app.accountCreated', { email: createdEmail }) }}
        </p>

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

.card-header {
  display: flex;
  justify-content: flex-end;
  margin: -1rem -1rem 0 0;
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

.check-error {
  margin: 0 0 1rem;
  color: var(--color-error);
}

.check-error + button {
  width: 100%;
}

.log-out {
  width: 100%;
  margin-top: 0.75rem;
}
</style>
