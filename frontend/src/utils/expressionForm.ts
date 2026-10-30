/**
 * 学習内容(中国語例文・日本語表現・表現及び中国語意味・備考)の入力フォームに関する共通処理。
 * S02 新規登録 と S04 表現検索(編集)で同じ入力チェックを使うため、ここにまとめている。
 */

/** 学習内容の入力値。画面の入力欄では、備考も未入力時は空文字として扱う。 */
export interface ExpressionContentForm {
  promptText: string
  answerText: string
  expressionMeaning: string
  note: string
}

export type RequiredField = 'promptText' | 'answerText' | 'expressionMeaning'

/** 必須項目と画面上の項目名(API設計書で必須とされている3項目)。 */
export const REQUIRED_FIELDS: Record<RequiredField, string> = {
  promptText: '中国語例文',
  answerText: '日本語表現',
  expressionMeaning: '表現及び中国語意味',
}

/** 項目ごとの入力エラーメッセージ。エラーがない項目はキーを持たない。 */
export type FieldErrors = Partial<Record<RequiredField, string>>

/**
 * 必須項目が未入力・空白のみでないかを確認し、項目ごとのエラーを返す。
 * バックエンド(API-02 / API-07)と同じ必須条件であり、送信前に分かる誤りを利用者へ早く知らせるためのもの。
 */
export function validateRequired(form: ExpressionContentForm): FieldErrors {
  const errors: FieldErrors = {}
  for (const field of Object.keys(REQUIRED_FIELDS) as RequiredField[]) {
    if (form[field].trim() === '') {
      errors[field] = `${REQUIRED_FIELDS[field]}は必須です。`
    }
  }
  return errors
}

/** 送信用の備考。空欄の場合は「未入力」として null とする。 */
export function noteForRequest(note: string): string | null {
  return note === '' ? null : note
}
