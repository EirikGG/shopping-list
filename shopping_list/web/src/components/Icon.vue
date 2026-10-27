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
  plus: ['M5 12h14', 'M12 5v14'],
  store: [
    'm2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7',
    'M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8',
    'M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4',
    'M2 7h20',
    'M22 7v3a2 2 0 0 1-2 2a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 16 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 12 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 8 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 4 12a2 2 0 0 1-2-2V7'
  ],
  chevron: ['m9 18 6-6-6-6'],
  link: [
    'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71',
    'M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71'
  ],
  paperclip: [
    'm21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48'
  ],
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
