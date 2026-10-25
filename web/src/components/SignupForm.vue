<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Icon from './Icon.vue'
import { createUser, login } from '../api/auth.js'
import { setSession } from '../session.js'
import { PASSWORD_RULES, isValidEmail, isValidPassword } from '../validation.js'

const emit = defineEmits(['created'])

const { t } = useI18n()

// The backend reads every field unconditionally, so all of them are required.
// It derives the username from the email, so there is no username field.
const form = ref({
  firstname: '',
  lastname: '',
  password: '',
  email: ''
})

const passwordRepeat = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)

// There is no availability check, so a taken email is only known once the
// backend rejects the signup. It stays marked until the email is changed.
const emailTaken = ref(false)

// Shown once the field has been left, so typing is not interrupted.
const emailTouched = ref(false)
const emailValid = computed(() => isValidEmail(form.value.email))
const showEmailInvalid = computed(() => emailTouched.value && form.value.email !== '' && !emailValid.value)

const passwordRules = computed(
  () => PASSWORD_RULES.map(rule => ({ key: rule.key, met: rule.test(form.value.password) }))
)
const passwordValid = computed(() => isValidPassword(form.value.password))

const passwordsMatch = computed(
  () => form.value.password === passwordRepeat.value
)

const showPasswordMismatch = computed(
  () => passwordRepeat.value !== '' && !passwordsMatch.value
)

const canSubmit = computed(
  () => !busy.value && emailValid.value && passwordValid.value && passwordsMatch.value && !emailTaken.value
)

watch(() => form.value.email, () => {
  emailTaken.value = false
})

async function submit() {
  error.value = ''
  busy.value = true

  // The backend trims the email as well; this keeps the login below in step.
  form.value.email = form.value.email.trim()

  try {
    await createUser(form.value)
  } catch (e) {
    if (e.status === 429) {
      error.value = 'errors.tooManyRequests'
    } else if (e.code === 'INVALID_EMAIL') {
      error.value = 'signup.emailInvalid'
    } else if (e.code === 'WEAK_PASSWORD') {
      error.value = 'signup.passwordWeak'
    } else if (e.code === 'DUPLICATE_USERNAME') {
      error.value = 'signup.duplicateEmail'
      emailTaken.value = true
    } else {
      error.value = 'signup.failed'
    }
    busy.value = false
    return
  }

  // Logging straight in takes the user to the verification code. If that
  // fails the account still exists, so the user is sent to log in instead.
  try {
    setSession(await login(form.value.email, form.value.password), form.value.email)
  } catch {
    emit('created', form.value.email)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form @submit.prevent="submit">
    <div class="row">
      <div class="field">
        <label for="signup-firstname">{{ t('signup.firstname') }}</label>
        <input id="signup-firstname" v-model="form.firstname" required />
      </div>

      <div class="field">
        <label for="signup-lastname">{{ t('signup.lastname') }}</label>
        <input id="signup-lastname" v-model="form.lastname" required />
      </div>
    </div>

    <div class="field">
      <label for="signup-email">{{ t('signup.email') }}</label>
      <input
        id="signup-email"
        v-model="form.email"
        type="email"
        autocomplete="email"
        maxlength="254"
        aria-describedby="signup-email-status"
        :aria-invalid="showEmailInvalid || emailTaken"
        required
        @blur="emailTouched = true"
      />
      <p
        id="signup-email-status"
        class="hint"
        :class="{ taken: emailTaken || showEmailInvalid }"
        aria-live="polite"
      >
        <template v-if="emailTaken">{{ t('signup.emailTaken') }}</template>
        <template v-else-if="showEmailInvalid">{{ t('signup.emailInvalid') }}</template>
        <template v-else>{{ t('signup.emailHint') }}</template>
      </p>
    </div>

    <div class="field">
      <label for="signup-password">{{ t('signup.password') }}</label>
      <input
        id="signup-password"
        v-model="form.password"
        type="password"
        autocomplete="new-password"
        maxlength="128"
        aria-describedby="signup-password-rules"
        required
      />
      <!-- Ticks off each rule while typing. -->
      <ul id="signup-password-rules" class="rules">
        <li v-for="rule in passwordRules" :key="rule.key" :class="{ met: rule.met }">
          <Icon :name="rule.met ? 'check' : 'x'" />
          <span>{{ t(rule.key) }}</span>
          <span class="visually-hidden">
            {{ rule.met ? t('signup.passwordRuleMet') : t('signup.passwordRuleNotMet') }}
          </span>
        </li>
      </ul>
    </div>

    <div class="field">
      <label for="signup-password-repeat">{{ t('signup.passwordRepeat') }}</label>
      <input
        id="signup-password-repeat"
        v-model="passwordRepeat"
        type="password"
        autocomplete="new-password"
        aria-describedby="signup-password-repeat-status"
        required
      />
      <p
        v-if="showPasswordMismatch"
        id="signup-password-repeat-status"
        class="hint taken"
        aria-live="polite"
      >
        {{ t('signup.passwordMismatch') }}
      </p>
    </div>

    <p v-if="error" class="error">{{ t(error) }}</p>

    <button type="submit" :disabled="!canSubmit">
      {{ busy ? t('signup.submitting') : t('signup.submit') }}
    </button>
  </form>
</template>

<style scoped>
.field {
  margin-bottom: 1rem;
}

.row {
  display: flex;
  gap: 0.75rem;
}

.row .field {
  flex: 1;
}

button[type='submit'] {
  width: 100%;
}

.hint {
  margin: 0.25rem 0 0;
  min-height: 1.1rem;
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.hint.taken {
  color: var(--color-error);
}

.rules {
  margin: 0.375rem 0 0;
  padding: 0;
  list-style: none;
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.rules li {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.rules li.met {
  color: var(--color-success);
}

.rules :deep(svg) {
  width: 0.875rem;
  height: 0.875rem;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.error {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
