<script setup>
import { ref } from 'vue'
import { createUser } from '../api/auth.js'

const emit = defineEmits(['created'])

// The backend reads every field unconditionally, so all of them are required.
const form = ref({
  firstname: '',
  lastname: '',
  birthdate: '',
  gender: 'MALE',
  username: '',
  password: '',
  email: '',
  countrycode: '+47',
  phonenumber: ''
})

const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true

  try {
    await createUser(form.value)
    emit('created', form.value.username)
  } catch (e) {
    error.value = e.code === 'DUPLICATE_USERNAME'
      ? 'That username is already taken.'
      : 'Could not create the account. Try again.'
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

    <div class="field">
      <label for="signup-email">Email</label>
      <input id="signup-email" v-model="form.email" type="email" required />
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
      <label for="signup-username">Username</label>
      <input
        id="signup-username"
        v-model="form.username"
        autocomplete="username"
        required
      />
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

    <p v-if="error" class="error">{{ error }}</p>

    <button type="submit" :disabled="busy">
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

.error {
  margin: 0 0 1rem;
  font-size: 0.875rem;
  color: var(--color-error);
}
</style>
