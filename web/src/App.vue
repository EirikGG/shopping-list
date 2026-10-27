<script setup>
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import ShoppingLists from './components/ShoppingLists.vue'
import ThemeSwitcher from './components/ThemeSwitcher.vue'
</script>

<!-- Home Assistant logs the user in before the app is shown, so there is no
     login here. -->
<template>
  <div class="app-shell">
    <header class="top-bar">
      <h1>Shopping list</h1>
      <div class="top-bar-actions">
        <ThemeSwitcher />
        <LanguageSwitcher />
      </div>
    </header>

    <ShoppingLists />
  </div>
</template>

<style scoped>
/* dvh follows the mobile browser's address bar; vh is the fallback.
   --viewport-height also leaves out the on-screen keyboard (src/viewport.js). */
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: var(--viewport-height, 100dvh);
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.5rem;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.top-bar h1 {
  margin: 0;
  font-size: 1.5rem;
  text-align: left;
  color: var(--color-primary);
}

.top-bar-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

@media (max-width: 640px) {
  .top-bar {
    padding: 0.5rem 1rem;
  }

  .top-bar h1 {
    font-size: 1.125rem;
    white-space: nowrap;
  }
}
</style>
