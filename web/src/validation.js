// The rules for a new account's email and password. The backend checks the
// same (rest/.../service/CredentialValidator.java), so keep the two in step.

const ATOM = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
const LABEL = '[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?'
const EMAIL = new RegExp(`^${ATOM}(?:\\.${ATOM})*@(?:${LABEL}\\.)+[A-Za-z]{2,63}$`)

export function isValidEmail(email) {
  const trimmed = email.trim()
  const at = trimmed.lastIndexOf('@')
  return trimmed.length <= 254 && at > 0 && at <= 64 && EMAIL.test(trimmed)
}

// Each rule with a message key, so the form can show which are met.
export const PASSWORD_RULES = [
  { key: 'signup.passwordRuleLength', test: password => password.length >= 8 && password.length <= 128 },
  { key: 'signup.passwordRuleLowercase', test: password => /\p{Ll}/u.test(password) },
  { key: 'signup.passwordRuleUppercase', test: password => /\p{Lu}/u.test(password) },
  { key: 'signup.passwordRuleDigit', test: password => /\p{Nd}/u.test(password) }
]

export function isValidPassword(password) {
  return PASSWORD_RULES.every(rule => rule.test(password))
}
