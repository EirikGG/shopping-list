<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import Icon from './Icon.vue'

// Swipe a row to the left to delete it, as rows are swiped in Spotify: the
// row follows the finger and uncovers the action behind it, which lights up
// once letting go will trigger it. Letting go before then puts the row back.
// The pencil button stays the way to delete for mouse, keyboard and screen
// readers, so the swipe is touch-only and hidden from assistive technology.
const props = defineProps({
  enabled: { type: Boolean, default: true },
  // Does the deleting. If it fails, the row comes back.
  remove: { type: Function, required: true }
})

// 'removed' once the row has slid out and collapsed, so the parent can drop
// it. 'failed' with the error if remove threw.
const emit = defineEmits(['removed', 'failed'])

// How far, as a share of the row's width, the row has to be dragged.
const THRESHOLD = 0.35
// Movement before deciding between a swipe and a vertical scroll.
const SLOP = 8
const ANIMATION_MS = 200

const root = ref(null)
const offset = ref(0)
const width = ref(0)
// 'idle' | 'dragging' | 'removing' | 'collapsing'
const state = ref('idle')
const collapseFrom = ref(null)

let pointerId = null
let startX = 0
let startY = 0
// null until the gesture is known to be a swipe ('x') or not ('none').
let direction = null
// Set when a swipe ends, so the click that follows does not hit a button.
let swiped = false

const armed = computed(() => width.value > 0 && -offset.value >= width.value * THRESHOLD)

// A short buzz when the action arms, where the browser supports it.
watch(armed, isArmed => {
  if (isArmed && state.value === 'dragging') {
    navigator.vibrate?.(10)
  }
})

const rootStyle = computed(() => {
  if (state.value === 'collapsing') {
    return { height: '0px' }
  }
  return collapseFrom.value === null ? {} : { height: `${collapseFrom.value}px` }
})

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

function onPointerDown(e) {
  if (!props.enabled || state.value !== 'idle' || pointerId !== null) {
    return
  }
  if (e.pointerType === 'mouse') {
    return
  }

  pointerId = e.pointerId
  startX = e.clientX
  startY = e.clientY
  direction = null
  swiped = false
  width.value = root.value.offsetWidth
}

function onPointerMove(e) {
  if (e.pointerId !== pointerId) {
    return
  }

  const dx = e.clientX - startX
  const dy = e.clientY - startY

  if (direction === null) {
    if (Math.abs(dx) < SLOP && Math.abs(dy) < SLOP) {
      return
    }
    direction = dx < 0 && Math.abs(dx) > Math.abs(dy) ? 'x' : 'none'
    if (direction === 'x') {
      root.value.setPointerCapture(pointerId)
      state.value = 'dragging'
    } else {
      pointerId = null
      return
    }
  }

  offset.value = Math.min(0, dx)
}

function onPointerUp(e) {
  if (e.pointerId !== pointerId) {
    return
  }
  pointerId = null

  if (direction !== 'x') {
    return
  }
  swiped = true

  if (armed.value) {
    slideOut()
  } else {
    springBack()
  }
}

function springBack() {
  pointerId = null
  state.value = 'idle'
  offset.value = 0
}

async function slideOut() {
  state.value = 'removing'
  offset.value = -width.value

  try {
    await Promise.all([props.remove(), wait(ANIMATION_MS)])
  } catch (e) {
    springBack()
    emit('failed', e)
    return
  }

  // Height only animates between fixed values, so the current one is set
  // first and the change to 0 waits for the next frame.
  collapseFrom.value = root.value.offsetHeight
  await nextTick()
  requestAnimationFrame(() => {
    state.value = 'collapsing'
  })
  await wait(ANIMATION_MS + 20)
  emit('removed')
}

function onClickCapture(e) {
  if (swiped) {
    swiped = false
    e.stopPropagation()
    e.preventDefault()
  }
}
</script>

<template>
  <div
    ref="root"
    class="swipe"
    :class="{ enabled }"
    :style="rootStyle"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="springBack"
    @click.capture="onClickCapture"
  >
    <div v-if="enabled" class="action" :class="{ armed }" aria-hidden="true">
      <Icon name="trash" />
    </div>
    <div
      class="content"
      :class="{ animate: state !== 'dragging' }"
      :style="offset ? { transform: `translateX(${offset}px)` } : {}"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.swipe {
  position: relative;
  transition: height 0.2s ease;
}

/* Vertical scrolling stays with the browser; sideways movement comes here. */
.swipe.enabled {
  overflow: hidden;
  touch-action: pan-y;
}

/* Grey while letting go would do nothing, red once it would delete. */
.action {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-right: 1.25rem;
  color: var(--color-surface);
  background: var(--color-text-muted);
  transition: background-color 0.15s ease;
}

.action.armed {
  background: var(--color-error);
}

.action :deep(svg) {
  width: 1.375rem;
  height: 1.375rem;
  transition: transform 0.15s ease;
}

.action.armed :deep(svg) {
  transform: scale(1.25);
}

.content {
  position: relative;
  background: var(--color-surface);
}

.content.animate {
  transition: transform 0.2s ease;
}
</style>
