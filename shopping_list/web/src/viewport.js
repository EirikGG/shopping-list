// On phones the keyboard covers the bottom of the page, and iOS does not
// shrink 100dvh for it. The app shell takes its height from here instead, so
// the add item form and the label picker stay above the keyboard.
const viewport = window.visualViewport

function update() {
  // While zoomed in, the visual viewport is smaller than the page for a
  // different reason, so the shell keeps its normal height.
  if (viewport.scale > 1.01) {
    document.documentElement.style.removeProperty('--viewport-height')
    return
  }
  document.documentElement.style.setProperty('--viewport-height', `${viewport.height}px`)
  // iOS scrolls the page up to show a focused field. The logged-in shell is
  // exactly as tall as what is visible, so that scroll is undone there. Only
  // there: the login and signup forms are normal pages that must scroll to
  // the field being typed in, or it ends up hidden behind the keyboard.
  if (document.querySelector('.app-shell') && window.scrollY !== 0) {
    window.scrollTo(0, 0)
  }
}

if (viewport) {
  viewport.addEventListener('resize', update)
  update()
}
