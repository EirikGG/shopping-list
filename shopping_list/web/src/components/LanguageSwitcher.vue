<script setup>
import { useI18n } from 'vue-i18n'
import { availableLocales, flags, setLocale } from '../i18n/index.js'

const { locale, t } = useI18n()

// Each language is named in itself, so it is recognisable to someone who does
// not read the current one.
function nativeName(code) {
  return t('language.name', {}, { locale: code })
}
</script>

<template>
  <div class="flags" role="group" :aria-label="t('language.label')">
    <button
      v-for="code in availableLocales"
      :key="code"
      type="button"
      class="flag"
      :class="{ active: code === locale }"
      :title="nativeName(code)"
      :aria-label="nativeName(code)"
      :aria-pressed="code === locale"
      @click="setLocale(code)"
    >
      <img :src="flags[code]" alt="" />
    </button>
  </div>
</template>

<style scoped>
.flags {
  display: flex;
  gap: 0.375rem;
}

.flag,
.flag:hover:not(:disabled) {
  display: flex;
  padding: 2px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 3px;
  opacity: 0.5;
}

.flag:hover:not(:disabled),
.flag.active {
  opacity: 1;
}

.flag.active {
  border-color: var(--color-border);
}

.flag img {
  display: block;
  width: 1.5rem;
  height: 1rem;
  object-fit: cover;
  border-radius: 2px;
}
</style>
