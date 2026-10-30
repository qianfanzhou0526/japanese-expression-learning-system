import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 開発サーバー(npm run dev)のプロキシ設定。
    // ブラウザからの /api で始まるリクエストを Spring Boot(localhost:8080)へ転送する。
    // ブラウザから見ると同じオリジン(localhost:5173)への通信になるため、backend側のCORS設定が不要になる。
    // フロントエンドのコードでは backend のホスト・ポートを書かず、/api/... の相対URLのみを使用する。
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
