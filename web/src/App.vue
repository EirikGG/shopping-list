<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import LoginForm from './components/LoginForm.vue'
import ShoppingLists from './components/ShoppingLists.vue'
import SignupForm from './components/SignupForm.vue'
import ThemeSwitcher from './components/ThemeSwitcher.vue'
import VerifyEmail from './components/VerifyEmail.vue'
import { logout, sessionStatus } from './api/auth.js'
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

// The session is forgotten here first, so logging out works even when the
// backend cannot be reached or the session has already expired. Ending it on
// the backend is then a best effort.
function logOut() {
  const id = sessionId.value
  createdEmail.value = ''
  clearSession()

  if (id) {
    logout(id).catch(() => {})
  }
}
</script>

<template>
  <div v-if="status === 'verified'" class="app-shell">
    <header class="top-bar">
      <h1>Shopping list</h1>
      <div class="top-bar-actions">
        <ThemeSwitcher />
        <LanguageSwitcher />
        <button class="secondary" @click="logOut">{{ t('app.logOut') }}</button>
      </div>
    </header>

    <ShoppingLists />
  </div>

  <main v-else>
    <div class="card">
      <div class="card-header">
        <ThemeSwitcher />
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
/* dvh follows the mobile browser's address bar; vh is the fallback.
   --viewport-height also leaves out the on-screen keyboard (src/viewport.js). */
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: var(--viewport-height, 100dvh);
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.5rem;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.top-bar h1 {
  margin: 0;
  text-align: left;
}

.top-bar-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (max-width: 640px) {
  .top-bar {
    padding: 0.5rem 1rem;
  }

  .top-bar h1 {
    font-size: 1.125rem;
    white-space: nowrap;
  }

  .top-bar-actions button.secondary {
    padding: 0.4rem 0.6rem;
    font-size: 0.875rem;
  }
}

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
  box-shadow: 0 1px 3px var(--color-shadow);
}

.card-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.5rem;
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
