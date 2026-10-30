<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { deleteExpression } from '@/api/expressions'
import {
  getTodayReviewTargets,
  recordReviewResult,
  type ReviewResult,
  type ReviewTarget,
} from '@/api/reviews'

/*
 * S03 復習。
 * API-03 で取得した今日の復習対象を、バックエンドが返した順序のまま1件ずつ表示する。
 * 利用者は中国語例文から日本語表現を思い出し、「答えを見る」で正答を確認して自己採点する(API-04)。
 *
 * 学習段階・次回復習日などの更新はすべてバックエンドが行い、この画面では採点結果を送信するだけとする。
 */

const router = useRouter()

/** 画面表示時に取得した復習対象。順序はバックエンドが決めた優先順のまま使用する(並び替えない)。 */
const targets = ref<ReviewTarget[]>([])
/** 現在表示している復習対象の位置(0始まり)。targets.length に達したら復習完了。 */
const currentIndex = ref(0)
/** 「答えを見る」を押した後(回答後状態)かどうか。 */
const answerShown = ref(false)

const loading = ref(true)
/** 採点・削除の送信中。送信中は操作ボタンを無効にし、同じ表現の二重送信を防ぐ。 */
const processing = ref(false)
/** 復習対象を取得できなかった場合のエラー。 */
const loadErrorMessage = ref('')
/** 採点・削除の失敗や、採点できなかった理由の表示。 */
const actionMessage = ref('')

const current = computed<ReviewTarget | undefined>(() => targets.value[currentIndex.value])
/** MSG-03 復習対象なし: 取得に成功し、対象が0件の場合。 */
const noTargets = computed(
  () => !loading.value && !loadErrorMessage.value && targets.value.length === 0,
)
/** MSG-04 復習完了: 対象が1件以上あり、すべて処理し終えた場合。 */
const completed = computed(
  () => targets.value.length > 0 && currentIndex.value >= targets.value.length,
)

onMounted(async () => {
  try {
    const response = await getTodayReviewTargets()
    targets.value = response.items
  } catch (error) {
    loadErrorMessage.value = `復習対象を取得できませんでした。${messageOf(error)}`
  } finally {
    loading.value = false
  }
})

/** 次の復習対象へ進む(回答前状態に戻す)。 */
function goNext() {
  currentIndex.value++
  answerShown.value = false
}

/** 正解・不正解を送信し、成功したら次の対象へ進む(S03-ACT-03 / 04)。 */
async function submitResult(result: ReviewResult) {
  const target = current.value
  if (!target) return
  actionMessage.value = ''
  processing.value = true
  try {
    await recordReviewResult(target.expressionId, result)
    goNext()
  } catch (error) {
    handleActionError(error, target, '採点')
  } finally {
    processing.value = false
  }
}

/** 削除確認の後、表現を削除して次の対象へ進む(S03-ACT-05 / 06)。削除は採点として扱わない。 */
async function removeCurrent() {
  const target = current.value
  if (!target) return
  // DLG-01 削除確認ダイアログ。V1ではブラウザ標準の確認ダイアログを使用する
  if (!window.confirm('この表現を削除しますか？')) return

  actionMessage.value = ''
  processing.value = true
  try {
    await deleteExpression(target.expressionId)
    goNext()
  } catch (error) {
    handleActionError(error, target, '削除')
  } finally {
    processing.value = false
  }
}

/**
 * 採点・削除の失敗時の処理。
 *
 * 404(すでに削除されている)・409(当日採点済み・予定日前・採点対象外)は、その表現をこれ以上
 * 採点・削除できない状態であるため、理由を表示して次の対象へ進む。
 * 通信エラー・サーバーエラー等の場合は、同じ表現のまま再度操作できるようにする。
 */
function handleActionError(error: unknown, target: ReviewTarget, action: string) {
  const message = messageOf(error)
  if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
    actionMessage.value = `表現ID ${target.expressionId} を${action}できなかったため、次の表現へ進みました。(${message})`
    goNext()
  } else {
    actionMessage.value = `${action}できませんでした。${message}`
  }
}

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '予期しないエラーが発生しました。'
}

/** MSG-03 / MSG-04 の OK: ホームへ遷移する(S03-ACT-08 / 09)。 */
function goHome() {
  router.push({ name: 'home' })
}
</script>

<template>
  <h1 class="page-title">復習</h1>

  <p v-if="loading" class="card">読み込み中…</p>

  <div v-else-if="loadErrorMessage" class="message message-error" role="alert">
    <p>{{ loadErrorMessage }}</p>
  </div>

  <!-- MSG-03 復習対象なしメッセージ -->
  <div v-else-if="noTargets" class="message message-success message-with-action" role="status">
    <p>本日の復習対象はありません。</p>
    <button type="button" class="btn" @click="goHome">OK</button>
  </div>

  <template v-else>
    <div v-if="actionMessage" class="message message-error" role="alert">
      <p>{{ actionMessage }}</p>
    </div>

    <!-- MSG-04 復習完了メッセージ -->
    <div v-if="completed" class="message message-success message-with-action" role="status">
      <p>本日の復習が完了しました。</p>
      <button type="button" class="btn" @click="goHome">OK</button>
    </div>

    <section v-else-if="current" class="card">
      <div class="review-header">
        <!-- S03-LBL-02 表現ID(表示のみ) -->
        <span>表現ID：{{ current.expressionId }}</span>
        <!-- S03-LBL-01 復習進捗(現在件数／当日対象件数) -->
        <span class="progress">{{ currentIndex + 1 }} / {{ targets.length }}</span>
      </div>

      <h2 class="item-label">中国語例文</h2>
      <p class="item-text prompt">{{ current.promptText }}</p>

      <div v-if="!answerShown" class="actions">
        <button type="button" class="btn btn-primary" @click="answerShown = true">
          答えを見る
        </button>
      </div>

      <template v-else>
        <h2 class="item-label">日本語表現</h2>
        <p class="item-text answer">{{ current.answerText }}</p>

        <h2 class="item-label">表現及び中国語意味</h2>
        <p class="item-text">{{ current.expressionMeaning }}</p>

        <h2 class="item-label">備考</h2>
        <p class="item-text">{{ current.note ?? '' }}</p>

        <div class="actions">
          <button
            type="button"
            class="btn"
            :disabled="processing"
            @click="submitResult('INCORRECT')"
          >
            不正解
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="processing"
            @click="submitResult('CORRECT')"
          >
            正解
          </button>
          <button
            type="button"
            class="btn btn-danger"
            :disabled="processing"
            @click="removeCurrent"
          >
            削除
          </button>
        </div>
      </template>
    </section>
  </template>

  <div class="actions">
    <RouterLink :to="{ name: 'home' }" class="btn">ホームへ戻る</RouterLink>
  </div>
</template>

<style scoped>
.review-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  color: var(--color-muted);
}

.item-label {
  margin: 16px 0 4px;
  color: var(--color-muted);
  font-size: 0.9rem;
}

/* 改行を含む入力もそのまま表示する */
.item-text {
  margin: 0;
  white-space: pre-wrap;
}

.prompt,
.answer {
  font-size: 1.3rem;
}
</style>
