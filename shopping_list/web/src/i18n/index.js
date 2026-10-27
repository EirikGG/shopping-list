import { createI18n } from 'vue-i18n'

const STORAGE_KEY = 'shoppinglist.locale'
const FALLBACK_LOCALE = 'en'

// Every file in ./locales is a language, named by its locale code, so adding a
// language only takes a new file there.
const messages = Object.fromEntries(
  Object.entries(import.meta.glob('./locales/*.json', { eager: true })).map(
    ([path, module]) => [path.match(/([\w-]+)\.json$/)[1], module.default]
  )
)

export const availableLocales = Object.keys(messages)

// The flag for a locale is ./flags/<code>.svg. English uses the UK flag.
export const flags = Object.fromEntries(
  Object.entries(
    import.meta.glob('./flags/*.svg', { eager: true, import: 'default' })
  ).map(([path, url]) => [path.match(/([\w-]+)\.svg$/)[1], url])
)

// Browsers report Norwegian as 'nb', 'nn' or the older 'no', and a region can
// follow the language ('en-GB'), so codes are reduced to a known locale here.
const LOCALE_ALIASES = { no: 'nb', nn: 'nb' }

function resolveLocale(code) {
  if (!code) {
    return null
  }

  const language = code.toLowerCase().split('-')[0]
  const locale = LOCALE_ALIASES[language] ?? language
  return availableLocales.includes(locale) ? locale : null
}

function initialLocale() {
  const candidates = [localStorage.getItem(STORAGE_KEY), ...navigator.languages]
  return candidates.map(resolveLocale).find(Boolean) ?? FALLBACK_LOCALE
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: FALLBACK_LOCALE,
  messages
})

document.documentElement.lang = i18n.global.locale.value

export function setLocale(locale) {
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale
}
