<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { ApiError } from '@/api/client'
import { getDashboard, type DashboardResponse } from '@/api/dashboard'

/*
 * S01 ホーム。
 * 画面表示時に API-01 で学習状況(今日の復習件数・登録表現数)を取得して表示し、各画面への入口を提供する。
 */

/** API-01 の取得結果。取得前・取得失敗時は null。 */
const dashboard = ref<DashboardResponse | null>(null)
const loading = ref(true)
const errorMessage = ref('')

// onMounted: 画面(コンポーネント)が表示された直後に1回実行される
onMounted(async () => {
  try {
    dashboard.value = await getDashboard()
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError
        ? `学習状況を取得できませんでした。${error.message}`
        : '学習状況を取得できませんでした。'
  } finally {
    loading.value = false
  }
})

/** 件数の表示文字列。取得中・取得失敗時は「-」とする。 */
function countText(count: number | undefined): string {
  return count === undefined ? '-' : String(count)
}
</script>

<template>
  <h1 class="page-title">ホーム</h1>

  <div v-if="errorMessage" class="message message-error" role="alert">
    <p>{{ errorMessage }}</p>
  </div>

  <section class="card">
    <h2 class="section-title">学習状況</h2>
    <p v-if="loading" class="loading">読み込み中…</p>
    <div class="stats">
      <div class="stat">
        <span class="stat-label">本日の復習対象</span>
        <span class="stat-value">{{ countText(dashboard?.todayReviewCount) }} 件</span>
      </div>
      <div class="stat">
        <span class="stat-label">登録表現数</span>
        <span class="stat-value">{{ countText(dashboard?.registeredExpressionCount) }} 件</span>
      </div>
    </div>
  </section>

  <!-- 復習対象が0件、または件数を取得できなかった場合も、各画面への遷移は常に可能とする(S01 補足仕様) -->
  <nav class="home-actions">
    <RouterLink :to="{ name: 'register' }" class="btn home-action home-action-start">
      新規登録
    </RouterLink>
    <RouterLink :to="{ name: 'review' }" class="btn btn-primary home-action home-action-center">
      復習を開始
    </RouterLink>
    <RouterLink :to="{ name: 'expressions' }" class="btn home-action home-action-end">
      表現検索
    </RouterLink>
  </nav>
</template>

<style scoped>
.section-title {
  margin: 0 0 16px;
  font-size: 1.1rem;
}

.loading {
  margin: 0 0 12px;
  color: var(--color-muted);
}

.stats {
  display: flex;
  gap: 16px;
}

.stat {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: bold;
}

/*
 * 画面下部の操作ボタン。上の「学習状況」カードと同じ幅の中で、左・中央・右に配置する。
 * 1fr / auto / 1fr の3列グリッドは左右の列が常に同じ幅になるため、
 * 中央のボタンの中心がカード(=ボタン列全体)の中心と一致する。
 */
.home-actions {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16px;
  align-items: center;
  margin-top: 24px;
}

/* 3つのボタンの大きさを揃える */
.home-action {
  width: 160px;
}

.home-action-start {
  justify-self: start;
}

.home-action-center {
  justify-self: center;
}

.home-action-end {
  justify-self: end;
}

/* 画面幅が狭い場合は横一列をやめ、ボタンを縦に並べてカードと同じ幅に広げる */
@media (max-width: 600px) {
  .home-actions {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .home-action {
    width: 100%;
    justify-self: stretch;
  }
}
</style>
