import { request } from './client'

/** API-03 今日の復習対象の1件。 */
export interface ReviewTarget {
  expressionId: number
  /** 中国語例文 */
  promptText: string
  /** 日本語表現(回答後に表示) */
  answerText: string
  /** 表現及び中国語意味(回答後に表示) */
  expressionMeaning: string
  /** 備考(回答後に表示。未入力の場合は null) */
  note: string | null
}

/** API-03 今日の復習対象取得のレスポンス。 */
export interface ReviewTargetsResponse {
  /** 復習対象(バックエンドが決めた優先順) */
  items: ReviewTarget[]
}

/** 自己採点の結果。 */
export type ReviewResult = 'CORRECT' | 'INCORRECT'

/** API-04 復習結果更新のレスポンス(採点後の学習状態)。 */
export interface ReviewResultResponse {
  expressionId: number
  learningStage: string
  consecutiveCorrectCount: number
  totalCorrectCount: number
  totalWrongCount: number
  /** 次回復習予定日(YYYY-MM-DD)。MASTERED へ遷移した場合は null */
  nextReviewDate: string | null
  /** 最終復習日(YYYY-MM-DD) */
  lastReviewDate: string
}

/** API-03 今日の復習対象取得(GET /api/reviews/today)。 */
export function getTodayReviewTargets(): Promise<ReviewTargetsResponse> {
  return request<ReviewTargetsResponse>('GET', '/api/reviews/today')
}

/** API-04 復習結果更新(POST /api/reviews/{expressionId}/result)。 */
export function recordReviewResult(
  expressionId: number,
  result: ReviewResult,
): Promise<ReviewResultResponse> {
  return request<ReviewResultResponse>('POST', `/api/reviews/${expressionId}/result`, { result })
}
