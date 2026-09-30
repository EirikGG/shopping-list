<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { createUser, emailIsAvailable } from '../api/auth.js'

const emit = defineEmits(['created'])

const { t } = useI18n()

const CHECK_DELAY_MS = 400

// The backend reads every field unconditionally, so all of them are required.
// It derives the username from the email, so there is no username field.
const form = ref({
  firstname: '',
  lastname: '',
  birthdate: '',
  gender: 'MALE',
  password: '',
  email: '',
  countrycode: '+47',
  phonenumber: ''
})

const passwordRepeat = ref('')
// A message key, so the error follows a language change.
const error = ref('')
const busy = ref(false)

// 'idle' | 'checking' | 'available' | 'taken' | 'error'
const emailStatus = ref('idle')

let checkTimer = null
// Guards against a slow earlier check overwriting a newer one's result.
let latestCheck = 0

const passwordsMatch = computed(
  () => form.value.password === passwordRepeat.value
)

const showPasswordMismatch = computed(
  () => passwordRepeat.value !== '' && !passwordsMatch.value
)

const canSubmit = computed(
  () =>
    !busy.value &&
    passwordsMatch.value &&
    emailStatus.value !== 'taken' &&
    emailStatus.value !== 'checking'
)

watch(() => form.value.email, email => {
  clearTimeout(checkTimer)

  const trimmed = email.trim()
  if (trimmed === '') {
    emailStatus.value = 'idle'
    latestCheck++
    return
  }

  emailStatus.value = 'checking'
  checkTimer = setTimeout(() => checkEmail(trimmed), CHECK_DELAY_MS)
})

async function checkEmail(email) {
  const check = ++latestCheck

  try {
    const available = await emailIsAvailable(email)
    if (check === latestCheck) {
      emailStatus.value = available ? 'available' : 'taken'
    }
  } catch {
    if (check === latestCheck) {
      emailStatus.value = 'error'
    }
  }
}

onUnmounted(() => clearTimeout(checkTimer))

async function submit() {
  error.value = ''
  busy.value = true

  try {
    await createUser(form.value)
    emit('created', form.value.email)
  } catch (e) {
    if (e.code === 'DUPLICATE_USERNAME') {
      error.value = 'signup.duplicateEmail'
      emailStatus.value = 'taken'
    } else {
      error.value = 'signup.failed'
    }
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

    <div class="row phone">
      <div class="field">
        <label for="signup-countrycode">{{ t('signup.countrycode') }}</label>
        <input id="signup-countrycode" v-model="form.countrycode" required />
      </div>

      <div class="field">
        <label for="signup-phonenumber">{{ t('signup.phonenumber') }}</label>
        <input id="signup-phonenumber" v-model="form.phonenumber" required />
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
        :class="emailStatus"
        aria-live="polite"
      >
        <template v-if="emailStatus === 'checking'">{{ t('signup.emailChecking') }}</template>
        <template v-else-if="emailStatus === 'available'">{{ t('signup.emailAvailable') }}</template>
        <template v-else-if="emailStatus === 'taken'">{{ t('signup.emailTaken') }}</template>
        <template v-else-if="emailStatus === 'error'">{{ t('signup.emailCheckFailed') }}</template>
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

.row.phone .field:first-child {
  flex: 0 0 7rem;
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

.hint.available {
  color: var(--color-success);
}

.hint.taken,
.hint.error {
  color: var(--color-error);
}

.error {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
