<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setTheme, theme, themes } from '../theme/index.js'

const { t } = useI18n()

// One button that steps through the choices, so the header stays small.
function next() {
  setTheme(themes[(themes.indexOf(theme.value) + 1) % themes.length])
}

const label = computed(() =>
  t('theme.current', { theme: t(`theme.${theme.value}`) })
)
</script>

<template>
  <button
    type="button"
    class="theme"
    :title="label"
    :aria-label="label"
    @click="next"
  >
    <!-- Monitor for 'auto', sun for light, moon for dark. -->
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <template v-if="theme === 'light'">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </template>
      <path v-else-if="theme === 'dark'" d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      <template v-else>
        <rect x="3" y="4" width="18" height="12" rx="2" />
        <path d="M8 20h8M12 16v4" />
      </template>
    </svg>
  </button>
</template>

<style scoped>
.theme,
.theme:hover:not(:disabled) {
  display: flex;
  padding: 2px;
  color: var(--color-text-muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 3px;
}

.theme:hover:not(:disabled) {
  color: var(--color-text);
}

svg {
  width: 1.25rem;
  height: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
