import { useColorMode } from '@vueuse/core'

// Kept in sync with the inline script in index.html, which applies the theme
// before the app loads.
const STORAGE_KEY = 'shoppinglist.theme'

// The choices offered to the user. 'auto' follows the operating system.
export const themes = ['auto', 'light', 'dark']

// Sets data-theme="light" | "dark" on <html>, which theme.css styles against.
// Components only use the colour variables from theme.css, so they follow the
// theme without knowing about it.
const colorMode = useColorMode({
  attribute: 'data-theme',
  storageKey: STORAGE_KEY
})

// The user's choice, including 'auto'.
export const theme = colorMode.store

export function setTheme(value) {
  colorMode.store.value = value
}
