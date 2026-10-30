<script setup lang="ts">
import { reactive, ref } from 'vue'

import { ApiError } from '@/api/client'
import {
  deleteExpression,
  getExpression,
  searchExpressions,
  updateExpression,
  type ExpressionDetail,
  type ExpressionSearchItem,
} from '@/api/expressions'
import {
  noteForRequest,
  validateRequired,
  type ExpressionContentForm,
  type FieldErrors,
  type RequiredField,
} from '@/utils/expressionForm'

/*
 * S04 表現検索。
 * 表現ID検索(API-05)またはキーワード検索(API-06)の結果から1件を選び、
 * 内容の編集(API-07)・削除(API-08)を行う。
 * 検索条件・並び順・特殊文字の扱いはバックエンドの仕様をそのまま利用し、画面側では絞り込み等を行わない。
 */

// ---------------------------------------------------------------------
// 検索
// ---------------------------------------------------------------------

/** 表現ID検索の入力値(S04-TXT-01)。 */
const idInput = ref('')
/** キーワード検索の入力値(S04-TXT-02)。 */
const keyword = ref('')
/** 検索結果一覧(S04-TBL-01)。null は「まだ検索していない」状態。 */
const results = ref<ExpressionSearchItem[] | null>(null)
const searchErrorMessage = ref('')
const searching = ref(false)

/** 検索を始める前に、前回の結果・選択・メッセージをすべて消す。 */
function resetForNewSearch() {
  searchErrorMessage.value = ''
  results.value = null
  clearSelection()
  editMessage.value = null
}

/** 表現ID検索(S04-ACT-01)。入力されたIDと完全一致する表現を検索結果一覧に表示する。 */
async function searchById() {
  resetForNewSearch()
  const text = idInput.value.trim()
  if (text === '') {
    searchErrorMessage.value = '表現IDを入力してください。'
    return
  }
  // 表現IDは正整数のみ入力可能(S04-TXT-01)
  if (!/^[1-9][0-9]*$/.test(text)) {
    searchErrorMessage.value = '表現IDは1以上の整数で入力してください。'
    return
  }

  searching.value = true
  try {
    const detail = await getExpression(Number(text))
    results.value = [toSearchItem(detail)]
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      // 対象なしはエラーではなく「検索結果0件」として表示する
      results.value = []
    } else {
      searchErrorMessage.value = `検索できませんでした。${messageOf(error)}`
    }
  } finally {
    searching.value = false
  }
}

/** キーワード検索(S04-ACT-02)。中国語例文・日本語表現・表現及び中国語意味の部分一致(備考は対象外)。 */
async function searchByKeyword() {
  resetForNewSearch()
  // 未入力・空白のみのキーワードは不正入力として扱う(API設計書 6章)
  if (keyword.value.trim() === '') {
    searchErrorMessage.value = '検索キーワードを入力してください。'
    return
  }

  searching.value = true
  try {
    const response = await searchExpressions(keyword.value)
    results.value = response.items
  } catch (error) {
    searchErrorMessage.value = `検索できませんでした。${messageOf(error)}`
  } finally {
    searching.value = false
  }
}

function toSearchItem(detail: ExpressionDetail): ExpressionSearchItem {
  return {
    expressionId: detail.expressionId,
    answerText: detail.answerText,
    expressionMeaning: detail.expressionMeaning,
  }
}

// ---------------------------------------------------------------------
// 選択・編集・削除
// ---------------------------------------------------------------------

/** 検索結果一覧で選択中の表現ID(S04-RDO-01)。 */
const selectedId = ref<number | null>(null)
/** 編集領域に表示している表現ID(S04-LBL-01)。表示のみで編集はできない。 */
const editingId = ref<number | null>(null)
/** 編集領域の入力値(S04-TXT-03～06)。 */
const form = reactive<ExpressionContentForm>({
  promptText: '',
  answerText: '',
  expressionMeaning: '',
  note: '',
})
const fieldErrors = ref<FieldErrors>({})
/** 編集領域の上部に表示するメッセージ(MSG-02 更新完了・削除完了・エラー)。 */
const editMessage = ref<{ type: 'success' | 'error'; text: string; withOk?: boolean } | null>(null)
const loadingDetail = ref(false)
const processing = ref(false)

function clearSelection() {
  selectedId.value = null
  editingId.value = null
  fieldErrors.value = {}
}

/**
 * 検索結果から1件を選択したとき(S04-ACT-03)。
 * 一覧には一部の項目しかないため、API-05 で最新の登録内容を取得して編集領域に表示する。
 */
async function select(expressionId: number) {
  selectedId.value = expressionId
  editingId.value = null
  fieldErrors.value = {}
  editMessage.value = null
  loadingDetail.value = true
  try {
    const detail = await getExpression(expressionId)
    form.promptText = detail.promptText
    form.answerText = detail.answerText
    form.expressionMeaning = detail.expressionMeaning
    form.note = detail.note ?? ''
    editingId.value = detail.expressionId
  } catch (error) {
    handleEditError(error, expressionId, '取得')
  } finally {
    loadingDetail.value = false
  }
}

function clearFieldError(field: RequiredField) {
  delete fieldErrors.value[field]
}

/** 保存(S04-ACT-04)。学習内容4項目のみ更新する。学習進捗はバックエンド側でも変更されない。 */
async function save() {
  const id = editingId.value
  if (id === null) return
  editMessage.value = null
  fieldErrors.value = validateRequired(form)
  if (Object.keys(fieldErrors.value).length > 0) {
    return
  }

  processing.value = true
  try {
    const updated = await updateExpression(id, {
      promptText: form.promptText,
      answerText: form.answerText,
      expressionMeaning: form.expressionMeaning,
      note: noteForRequest(form.note),
    })
    // 検索結果一覧の表示内容も更新後の値に合わせる
    replaceResult(toSearchItem(updated))
    editMessage.value = { type: 'success', text: '更新が完了しました。', withOk: true }
  } catch (error) {
    handleEditError(error, id, '更新')
  } finally {
    processing.value = false
  }
}

/** 削除(S04-ACT-05 / 07)。確認後に削除し、検索結果一覧から除いて編集領域を閉じる。 */
async function remove() {
  const id = editingId.value
  if (id === null) return
  // DLG-01 削除確認ダイアログ。V1ではブラウザ標準の確認ダイアログを使用する
  if (!window.confirm('この表現を削除しますか？')) return

  editMessage.value = null
  processing.value = true
  try {
    await deleteExpression(id)
    removeResult(id)
    editMessage.value = { type: 'success', text: `表現ID ${id} を削除しました。` }
  } catch (error) {
    handleEditError(error, id, '削除')
  } finally {
    processing.value = false
  }
}

/**
 * 取得・更新・削除の失敗時の処理。
 * 404 の場合は別の操作で削除済みのため、検索結果一覧から除いて編集領域を閉じる。
 */
function handleEditError(error: unknown, expressionId: number, action: string) {
  if (error instanceof ApiError && error.status === 404) {
    removeResult(expressionId)
    editMessage.value = {
      type: 'error',
      text: `表現ID ${expressionId} は存在しません。すでに削除された可能性があります。`,
    }
    return
  }
  const prefix = `${action}できませんでした。`
  // 400: 入力内容の誤り(バックエンドのメッセージに不正な項目が示される)
  const detail =
    error instanceof ApiError && error.status === 400
      ? `入力内容を確認してください。(${error.message})`
      : messageOf(error)
  editMessage.value = { type: 'error', text: prefix + detail }
}

function replaceResult(item: ExpressionSearchItem) {
  if (!results.value) return
  results.value = results.value.map((r) => (r.expressionId === item.expressionId ? item : r))
}

function removeResult(expressionId: number) {
  if (results.value) {
    results.value = results.value.filter((r) => r.expressionId !== expressionId)
  }
  clearSelection()
}

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '予期しないエラーが発生しました。'
}
</script>

<template>
  <h1 class="page-title">表現検索</h1>

  <!-- 検索条件 -->
  <section class="card">
    <form class="form-field" novalidate @submit.prevent="searchById">
      <label for="searchId">表現ID</label>
      <div class="inline-field">
        <input
          id="searchId"
          v-model="idInput"
          type="text"
          inputmode="numeric"
          placeholder="例：125"
        />
        <button type="submit" class="btn" :disabled="searching">ID検索</button>
      </div>
    </form>
    <form class="form-field search-keyword" novalidate @submit.prevent="searchByKeyword">
      <label for="searchKeyword">検索キーワード</label>
      <div class="inline-field">
        <input
          id="searchKeyword"
          v-model="keyword"
          type="text"
          placeholder="中国語例文・日本語表現・表現及び中国語意味から検索"
        />
        <button type="submit" class="btn" :disabled="searching">検索</button>
      </div>
    </form>
  </section>

  <div v-if="searchErrorMessage" class="message message-error section-gap" role="alert">
    <p>{{ searchErrorMessage }}</p>
  </div>

  <!-- 検索結果一覧(S04-TBL-01) -->
  <section v-if="results" class="card section-gap">
    <h2 class="section-title">検索結果</h2>
    <p v-if="results.length === 0" class="message message-info">該当する表現はありません。</p>
    <table v-else class="result-table">
      <thead>
        <tr>
          <th class="col-select">選択</th>
          <th class="col-id">表現ID</th>
          <th>日本語表現</th>
          <th>表現及び中国語意味</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in results"
          :key="item.expressionId"
          :class="{ selected: item.expressionId === selectedId }"
          @click="select(item.expressionId)"
        >
          <td class="col-select">
            <input
              type="radio"
              name="selectedExpression"
              :value="item.expressionId"
              :checked="item.expressionId === selectedId"
              :aria-label="`表現ID ${item.expressionId} を選択`"
              @click.stop="select(item.expressionId)"
            />
          </td>
          <td class="col-id">{{ item.expressionId }}</td>
          <td>{{ item.answerText }}</td>
          <td>{{ item.expressionMeaning }}</td>
        </tr>
      </tbody>
    </table>
  </section>

  <!-- 編集領域のメッセージ(MSG-02 更新完了・削除完了・エラー) -->
  <div
    v-if="editMessage"
    class="message section-gap"
    :class="[
      editMessage.type === 'success' ? 'message-success' : 'message-error',
      { 'message-with-action': editMessage.withOk },
    ]"
    :role="editMessage.type === 'success' ? 'status' : 'alert'"
  >
    <p>{{ editMessage.text }}</p>
    <!-- MSG-02 の OK: メッセージを閉じて表現検索画面に戻る(S04-ACT-06) -->
    <button v-if="editMessage.withOk" type="button" class="btn" @click="editMessage = null">
      OK
    </button>
  </div>

  <p v-if="loadingDetail" class="card section-gap">読み込み中…</p>

  <!-- 選択データ編集 -->
  <form v-else-if="editingId !== null" class="card section-gap" novalidate @submit.prevent="save">
    <h2 class="section-title">選択データ編集</h2>
    <p class="form-note"><span class="required-mark">*</span> は必須項目です。</p>

    <div class="form-field">
      <span class="field-label">表現ID</span>
      <!-- S04-LBL-01 表現ID(編集不可) -->
      <p class="readonly-value">{{ editingId }}</p>
    </div>

    <div class="form-field">
      <label for="editPromptText">中国語例文<span class="required-mark">*</span></label>
      <textarea
        id="editPromptText"
        v-model="form.promptText"
        rows="3"
        :aria-invalid="fieldErrors.promptText ? 'true' : 'false'"
        @input="clearFieldError('promptText')"
      ></textarea>
      <p v-if="fieldErrors.promptText" class="field-error">{{ fieldErrors.promptText }}</p>
    </div>

    <div class="form-field">
      <label for="editAnswerText">日本語表現<span class="required-mark">*</span></label>
      <textarea
        id="editAnswerText"
        v-model="form.answerText"
        rows="3"
        :aria-invalid="fieldErrors.answerText ? 'true' : 'false'"
        @input="clearFieldError('answerText')"
      ></textarea>
      <p v-if="fieldErrors.answerText" class="field-error">{{ fieldErrors.answerText }}</p>
    </div>

    <div class="form-field">
      <label for="editExpressionMeaning"
        >表現及び中国語意味<span class="required-mark">*</span></label
      >
      <textarea
        id="editExpressionMeaning"
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
      <label for="editNote">備考</label>
      <textarea id="editNote" v-model="form.note" rows="3"></textarea>
    </div>

    <div class="actions">
      <button type="button" class="btn btn-danger" :disabled="processing" @click="remove">
        削除
      </button>
      <button type="submit" class="btn btn-primary" :disabled="processing">
        {{ processing ? '処理中…' : '保存' }}
      </button>
    </div>
  </form>

  <div class="actions">
    <RouterLink :to="{ name: 'home' }" class="btn">ホームへ戻る</RouterLink>
  </div>
</template>

<style scoped>
.section-gap {
  margin-top: 20px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 1.1rem;
}

.search-keyword {
  margin-bottom: 0;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
}

.result-table th,
.result-table td {
  padding: 8px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--color-border);
}

.result-table tbody tr {
  cursor: pointer;
}

.result-table tbody tr:hover,
.result-table tbody tr.selected {
  background: #eef1f5;
}

.col-select {
  width: 56px;
  text-align: center;
}

.col-id {
  width: 80px;
}

.field-label {
  display: block;
  margin-bottom: 4px;
  font-weight: bold;
}

.readonly-value {
  margin: 0;
  padding: 8px;
  color: var(--color-muted);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 4px;
}
</style>
