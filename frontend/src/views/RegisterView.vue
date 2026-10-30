<script setup lang="ts">
import { nextTick, reactive, ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { createExpression, type ExpressionCreateResponse } from '@/api/expressions'
import {
  noteForRequest,
  validateRequired,
  type ExpressionContentForm,
  type FieldErrors,
  type RequiredField,
} from '@/utils/expressionForm'

/*
 * S02 新規登録。
 * 入力された学習表現を API-02 で登録する。表現IDはバックエンドが採番するため入力しない。
 */

const router = useRouter()

/** 入力フォームの内容。v-model で各入力欄と双方向に連動する。 */
const form = reactive<ExpressionContentForm>({
  promptText: '',
  answerText: '',
  expressionMeaning: '',
  note: '',
})

/** 項目ごとの入力エラー。エラーがない項目はキー自体を持たない。 */
const fieldErrors = ref<FieldErrors>({})
/** 画面上部に表示するエラー(API通信失敗・APIのエラーレスポンス)。 */
const errorMessage = ref('')
/** 直前の登録結果(MSG-01 登録完了メッセージの表示に使用)。 */
const registered = ref<ExpressionCreateResponse | null>(null)
/** 登録処理中は登録ボタンを無効にし、二重登録を防ぐ。 */
const submitting = ref(false)

/** 1つ目の入力欄。登録完了後に続けて入力できるようフォーカスを戻すために参照する。 */
const promptTextInput = useTemplateRef<HTMLTextAreaElement>('promptTextInput')

/** 入力し直した項目のエラー表示を消す。 */
function clearFieldError(field: RequiredField) {
  delete fieldErrors.value[field]
}

async function submit() {
  errorMessage.value = ''
  registered.value = null
  fieldErrors.value = validateRequired(form)
  if (Object.keys(fieldErrors.value).length > 0) {
    return
  }

  submitting.value = true
  try {
    registered.value = await createExpression({
      promptText: form.promptText,
      answerText: form.answerText,
      expressionMeaning: form.expressionMeaning,
      note: noteForRequest(form.note),
    })
    // 続けて次の表現を登録できるよう、フォームを空に戻して1つ目の入力欄にフォーカスする
    form.promptText = ''
    form.answerText = ''
    form.expressionMeaning = ''
    form.note = ''
    await nextTick()
    promptTextInput.value?.focus()
  } catch (error) {
    errorMessage.value = toErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

function toErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '登録できませんでした。予期しないエラーが発生しました。'
  }
  // 400: 入力内容の誤り(バックエンドのメッセージに不正な項目が示される)
  if (error.status === 400) {
    return `登録できませんでした。入力内容を確認してください。(${error.message})`
  }
  return `登録できませんでした。${error.message}`
}

/** MSG-01 の OK: メッセージを閉じてホームへ遷移する(S02-ACT-03)。 */
function closeCompletionMessage() {
  router.push({ name: 'home' })
}
</script>

<template>
  <h1 class="page-title">新規登録</h1>

  <!-- MSG-01 登録完了メッセージ -->
  <div v-if="registered" class="message message-success message-with-action" role="status">
    <div>
      <p>
        登録が完了しました。(表現ID: {{ registered.expressionId }} / 初回復習日:
        {{ registered.nextReviewDate }})
      </p>
      <p class="completion-hint">続けて登録する場合は、そのまま次の表現を入力してください。</p>
    </div>
    <button type="button" class="btn" @click="closeCompletionMessage">OK</button>
  </div>

  <div v-if="errorMessage" class="message message-error" role="alert">
    <p>{{ errorMessage }}</p>
  </div>

  <!-- novalidate: ブラウザ標準の入力チェック表示は使わず、画面内のエラー表示に統一する -->
  <form class="card" novalidate @submit.prevent="submit">
    <p class="form-note"><span class="required-mark">*</span> は必須項目です。</p>

    <div class="form-field">
      <label for="promptText">中国語例文<span class="required-mark">*</span></label>
      <textarea
        id="promptText"
        ref="promptTextInput"
        v-model="form.promptText"
        rows="3"
        :aria-invalid="fieldErrors.promptText ? 'true' : 'false'"
        @input="clearFieldError('promptText')"
      ></textarea>
      <p v-if="fieldErrors.promptText" class="field-error">{{ fieldErrors.promptText }}</p>
    </div>

    <div class="form-field">
      <label for="answerText">日本語表現<span class="required-mark">*</span></label>
      <textarea
        id="answerText"
        v-model="form.answerText"
        rows="3"
        :aria-invalid="fieldErrors.answerText ? 'true' : 'false'"
        @input="clearFieldError('answerText')"
      ></textarea>
      <p v-if="fieldErrors.answerText" class="field-error">{{ fieldErrors.answerText }}</p>
    </div>

    <div class="form-field">
      <label for="expressionMeaning">表現及び中国語意味<span class="required-mark">*</span></label>
      <textarea
        id="expressionMeaning"
        v-model="form.expressionMeaning"
        rows="3"
        :aria-invalid="fieldErrors.expressionMeaning ? 'true' : 'false'"
        @input="clearFieldError('expressionMeaning')"
      ></textarea>
      <p v-if="fieldErrors.expressionMeaning" class="field-error">
        {{ fieldErrors.expressionMeaning }}
      </p>
    </div>

    <div class="form-field">
      <label for="note">備考</label>
      <textarea id="note" v-model="form.note" rows="3"></textarea>
    </div>

    <!-- 戻る操作を左端、主要操作(登録)を右端に配置する -->
    <div class="actions form-actions">
      <!-- 未保存の入力があっても確認ダイアログは表示しない(S02 補足仕様) -->
      <RouterLink :to="{ name: 'home' }" class="btn form-action">ホームへ戻る</RouterLink>
      <button type="submit" class="btn btn-primary form-action" :disabled="submitting">
        {{ submitting ? '登録中…' : '登録' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.completion-hint {
  font-size: 0.9rem;
}

/* ボタン行はフォームと同じ幅を使い、2つのボタンを左右の端に寄せる */
.form-actions {
  justify-content: space-between;
}

/* 2つのボタンの大きさを揃える(ホーム画面のボタンと同じ幅) */
.form-action {
  width: 160px;
}

/* 画面幅が狭い場合はボタンを縦に並べ、フォームと同じ幅に広げる(ホーム画面と同じ方針) */
@media (max-width: 600px) {
  .form-actions {
    flex-direction: column;
  }

  .form-action {
    width: 100%;
  }
}
</style>
