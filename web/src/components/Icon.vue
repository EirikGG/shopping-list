<script setup>
// Outline icons from Lucide (lucide.dev). They take the colour of the text
// around them.
const PATHS = {
  pencil: [
    'M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z',
    'm15 5 4 4'
  ],
  trash: [
    'M3 6h18',
    'M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6',
    'M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2'
  ],
  check: ['M20 6 9 17l-5-5'],
  chevron: ['m9 18 6-6-6-6'],
  x: ['M18 6 6 18', 'm6 6 12 12']
}

defineProps({
  name: { type: String, required: true }
})
</script>

<template>
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path v-for="d in PATHS[name]" :key="d" :d="d" />
  </svg>
</template>

<style scoped>
svg {
  display: block;
  width: 1.125rem;
  height: 1.125rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
