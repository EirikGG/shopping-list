// On phones the keyboard covers the bottom of the page, and iOS does not
// shrink 100dvh for it. The app shell takes its height from here instead, so
// the add item form and the store picker stay above the keyboard.
const viewport = window.visualViewport

function update() {
  // While zoomed in, the visual viewport is smaller than the page for a
  // different reason, so the shell keeps its normal height.
  if (viewport.scale > 1.01) {
    document.documentElement.style.removeProperty('--viewport-height')
    return
  }
  document.documentElement.style.setProperty('--viewport-height', `${viewport.height}px`)
  // iOS scrolls the page up to show a focused field; the shell already fits.
  window.scrollTo(0, 0)
}

if (viewport) {
  viewport.addEventListener('resize', update)
  update()
}
