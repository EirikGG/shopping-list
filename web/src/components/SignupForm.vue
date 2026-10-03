<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { createUser, login } from '../api/auth.js'
import { setSession } from '../session.js'

const emit = defineEmits(['created'])

const { t } = useI18n()

// The backend reads every field unconditionally, so all of them are required.
// It derives the username from the email, so there is no username field.
const form = ref({
  firstname: '',
  lastname: '',
  birthdate: '',
  gender: 'MALE',
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

const passwordsMatch = computed(
  () => form.value.password === passwordRepeat.value
)

const showPasswordMismatch = computed(
  () => passwordRepeat.value !== '' && !passwordsMatch.value
)

const canSubmit = computed(
  () => !busy.value && passwordsMatch.value && !emailTaken.value
)

watch(() => form.value.email, () => {
  emailTaken.value = false
})

async function submit() {
  error.value = ''
  busy.value = true

  try {
    await createUser(form.value)
  } catch (e) {
    if (e.code === 'DUPLICATE_USERNAME') {
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

    <div class="row">
      <div class="field">
        <label for="signup-birthdate">{{ t('signup.birthdate') }}</label>
        <!-- type="date" submits yyyy-MM-dd, which is the format the backend parses. -->
        <input id="signup-birthdate" v-model="form.birthdate" type="date" required />
      </div>

      <div class="field">
        <label for="signup-gender">{{ t('signup.gender') }}</label>
        <select id="signup-gender" v-model="form.gender">
          <option value="MALE">{{ t('signup.genderMale') }}</option>
          <option value="FEMALE">{{ t('signup.genderFemale') }}</option>
          <option value="OTHER">{{ t('signup.genderOther') }}</option>
        </select>
      </div>
    </div>

    <div class="field">
      <label for="signup-email">{{ t('signup.email') }}</label>
      <input
        id="signup-email"
        v-model="form.email"
        type="email"
        autocomplete="email"
        aria-describedby="signup-email-status"
        required
      />
      <p
        id="signup-email-status"
        class="hint"
        :class="{ taken: emailTaken }"
        aria-live="polite"
      >
        {{ emailTaken ? t('signup.emailTaken') : t('signup.emailHint') }}
      </p>
    </div>

    <div class="field">
      <label for="signup-password">{{ t('signup.password') }}</label>
      <input
        id="signup-password"
        v-model="form.password"
        type="password"
        autocomplete="new-password"
        required
      />
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

.error {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
