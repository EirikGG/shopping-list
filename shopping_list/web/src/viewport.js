// On phones the keyboard covers the bottom of the page, and iOS does not
// shrink 100dvh for it. The app shell takes its height from here instead, so
// the add item form and the label picker stay above the keyboard.
const viewport = window.visualViewport

// Smaller than any real screen. iOS can report a height near 0 for a moment
// while Home Assistant lays out the frame the app is in; the shell keeps its
// normal height then rather than collapse.
const MIN_HEIGHT = 120

function update() {
  // While zoomed in, the visual viewport is smaller than the page for a
  // different reason, so the shell keeps its normal height.
  if (viewport.scale > 1.01 || viewport.height < MIN_HEIGHT) {
    document.documentElement.style.removeProperty('--viewport-height')
  } else {
    document.documentElement.style.setProperty('--viewport-height', `${viewport.height}px`)
  }
  // iOS scrolls the page up to show a focused field. The shell is exactly as
  // tall as what is visible, so that scroll is undone.
  if (window.scrollY !== 0) {
    window.scrollTo(0, 0)
  }
  repaint()
}

// The Home Assistant app on iPhone shows the app in a frame, and its browser
// engine sometimes stops drawing the frame after the page has changed: the
// screen goes black, though taps still work. Moving the page to a layer of its
// own for one frame makes it draw everything again.
let repaintQueued = false

export function repaint() {
  if (repaintQueued) {
    return
  }
  repaintQueued = true
  const root = document.documentElement
  root.style.transform = 'translateZ(0)'
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      root.style.removeProperty('transform')
      repaintQueued = false
    })
  })
}

if (viewport) {
  viewport.addEventListener('resize', update)
  update()
}

// Coming back to the app, and the moments after Home Assistant has shown the
// frame, when the black screen has been seen.
window.addEventListener('pageshow', repaint)
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    repaint()
  }
})
window.addEventListener('load', () => {
  for (const ms of [250, 1000, 2500]) {
    setTimeout(repaint, ms)
  }
})
