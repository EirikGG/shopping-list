<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import LoginForm from './components/LoginForm.vue'
import SignupForm from './components/SignupForm.vue'
import VerifyEmail from './components/VerifyEmail.vue'
import { sessionIsValid } from './api/auth.js'
import { clearSession, sessionId } from './session.js'

const { t } = useI18n()

const tab = ref('login')
// Holds the created account's email rather than the finished sentence, so the
// notice follows a language change.
const createdEmail = ref('')

// The verification mail links back to the frontend, so that path renders the
// verification view instead of the login card. There is no router, so the path
// is read once on load.
const VERIFY_EMAIL_PATH = '/verify_email'
const isVerifyEmailRoute = window.location.pathname === VERIFY_EMAIL_PATH

// A stored session id can be expired or from a wiped database, so it is
// checked against the backend before the app treats it as logged in.
onMounted(async () => {
  if (isVerifyEmailRoute) {
    return
  }

  if (sessionId.value && !(await sessionIsValid(sessionId.value))) {
    clearSession()
  }
})

function onCreated(email) {
  createdEmail.value = email
  tab.value = 'login'
}

function showTab(name) {
  createdEmail.value = ''
  tab.value = name
}
</script>

<template>
  <main>
    <div class="card">
      <div class="card-header">
        <LanguageSwitcher />
      </div>

      <h1>TGG</h1>

      <VerifyEmail v-if="isVerifyEmailRoute" />

      <template v-else-if="sessionId">
        <p class="signed-in">{{ t('app.signedIn') }}</p>
        <button class="secondary" @click="clearSession">{{ t('app.logOut') }}</button>
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
</style>
