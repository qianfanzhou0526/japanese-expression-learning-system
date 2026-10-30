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
    // ブラウザからの /api で始まるリクエストを Spring Boot(127.0.0.1:8080)へ転送する。
    // ブラウザから見ると同じオリジン(localhost:5173)への通信になるため、backend側のCORS設定が不要になる。
    // フロントエンドのコードでは backend のホスト・ポートを書かず、/api/... の相対URLのみを使用する。
    // 転送先を localhost ではなく 127.0.0.1 としているのは、backend が 127.0.0.1(IPv4)のみで待ち受けており、
    // Node.js が localhost を IPv6(::1)として解決した場合に接続できなくなることを避けるため。
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
})
