<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { createUser, emailIsAvailable } from '../api/auth.js'

const emit = defineEmits(['created'])

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
      error.value = 'An account with that email already exists.'
      emailStatus.value = 'taken'
    } else {
      error.value = 'Could not create the account. Try again.'
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
        <label for="signup-firstname">First name</label>
        <input id="signup-firstname" v-model="form.firstname" required />
      </div>

      <div class="field">
        <label for="signup-lastname">Last name</label>
        <input id="signup-lastname" v-model="form.lastname" required />
      </div>
    </div>

    <div class="row">
      <div class="field">
        <label for="signup-birthdate">Birthdate</label>
        <!-- type="date" submits yyyy-MM-dd, which is the format the backend parses. -->
        <input id="signup-birthdate" v-model="form.birthdate" type="date" required />
      </div>

      <div class="field">
        <label for="signup-gender">Gender</label>
        <select id="signup-gender" v-model="form.gender">
          <option value="MALE">Male</option>
          <option value="FEMALE">Female</option>
          <option value="OTHER">Other</option>
        </select>
      </div>
    </div>

    <div class="row phone">
      <div class="field">
        <label for="signup-countrycode">Country code</label>
        <input id="signup-countrycode" v-model="form.countrycode" required />
      </div>

      <div class="field">
        <label for="signup-phonenumber">Phone number</label>
        <input id="signup-phonenumber" v-model="form.phonenumber" required />
      </div>
    </div>

    <div class="field">
      <label for="signup-email">Email</label>
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
        <template v-if="emailStatus === 'checking'">Checking availability…</template>
        <template v-else-if="emailStatus === 'available'">Email is available.</template>
        <template v-else-if="emailStatus === 'taken'">Email is already registered.</template>
        <template v-else-if="emailStatus === 'error'">Could not check availability.</template>
        <template v-else>You will log in with this email.</template>
      </p>
    </div>

    <div class="field">
      <label for="signup-password">Password</label>
      <input
        id="signup-password"
        v-model="form.password"
        type="password"
        autocomplete="new-password"
        required
      />
    </div>

    <div class="field">
      <label for="signup-password-repeat">Repeat password</label>
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
        Passwords do not match.
      </p>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <button type="submit" :disabled="!canSubmit">
      {{ busy ? 'Creating account…' : 'Create account' }}
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
