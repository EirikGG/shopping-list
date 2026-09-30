<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { verifyEmail } from '../api/auth.js'
import { sessionId } from '../session.js'

const { t } = useI18n()

// 'verifying' | 'verified' | 'already-verified' | 'invalid' | 'error'
const status = ref('verifying')

onMounted(async () => {
  const key = new URLSearchParams(window.location.search).get('key')

  if (!key) {
    status.value = 'invalid'
    return
  }

  try {
    await verifyEmail(key, sessionId.value)
    status.value = 'verified'
  } catch (e) {
    // Both known cases answer 400, so the code decides before the status does.
    if (e.code === 'EMAIL_ALREADY_VERIFIED_EXCEPTION') {
      status.value = 'already-verified'
    } else if (
      e.code === 'NO_EMAIL_CONNECTED_TO_VERIFICATION_UUID' ||
      e.status === 400
    ) {
      status.value = 'invalid'
    } else {
      status.value = 'error'
    }
  }
})

function goHome() {
  window.location.href = '/'
}
</script>

<template>
  <div class="verify">
    <template v-if="status === 'verifying'">
      <p class="muted" aria-live="polite">{{ t('verifyEmail.verifying') }}</p>
    </template>

    <template v-else-if="status === 'verified'">
      <p class="success" aria-live="polite">{{ t('verifyEmail.verified') }}</p>
      <button @click="goHome">{{ t('verifyEmail.continue') }}</button>
    </template>

    <template v-else-if="status === 'already-verified'">
      <p class="muted" aria-live="polite">{{ t('verifyEmail.alreadyVerified') }}</p>
      <button @click="goHome">{{ t('verifyEmail.continue') }}</button>
    </template>

    <template v-else-if="status === 'invalid'">
      <p class="error" aria-live="polite">{{ t('verifyEmail.invalid') }}</p>
      <button class="secondary" @click="goHome">{{ t('verifyEmail.backToLogin') }}</button>
    </template>

    <template v-else>
      <p class="error" aria-live="polite">{{ t('verifyEmail.failed') }}</p>
      <button class="secondary" @click="goHome">{{ t('verifyEmail.backToLogin') }}</button>
    </template>
  </div>
</template>

<style scoped>
.verify p {
  margin: 0 0 1.5rem;
  font-size: 0.9375rem;
}

.muted {
  color: var(--color-text-muted);
}

.success {
  color: var(--color-success);
}

.error {
  color: var(--color-error);
}

button {
  width: 100%;
}
</style>
