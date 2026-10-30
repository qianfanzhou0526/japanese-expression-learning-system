<script setup lang="ts"></script>

<!--
  アプリ全体の枠となるルートコンポーネント。
  全画面共通のヘッダー(システム名)を表示し、その下の <RouterView> に
  URL に対応する画面(S01～S04)が表示される。
-->
<template>
  <header class="app-header">
    <div class="app-container">日本語表現学習システム</div>
  </header>
  <main class="app-container app-main">
    <RouterView />
  </main>
</template>

<style scoped>
.app-header {
  padding: 12px 0;
  font-weight: bold;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

/* PCブラウザで読みやすい幅に制限し、中央に配置する */
.app-container {
  max-width: 880px;
  margin: 0 auto;
  padding: 0 24px;
}

.app-main {
  padding-top: 24px;
  padding-bottom: 40px;
}
</style>
