import { request } from './client'

/** API-02 表現登録 / API-07 表現更新のリクエスト(学習内容4項目)。 */
export interface ExpressionContentRequest {
  /** 中国語例文(必須) */
  promptText: string
  /** 日本語表現(必須) */
  answerText: string
  /** 表現及び中国語意味(必須) */
  expressionMeaning: string
  /** 備考(任意) */
  note: string | null
}

/** API-02 表現登録のレスポンス。 */
export interface ExpressionCreateResponse {
  /** 採番された表現ID */
  expressionId: number
  /** 学習段階(登録直後は "NEW") */
  learningStage: string
  /** 初回復習予定日(YYYY-MM-DD) */
  nextReviewDate: string
}

/** API-05 表現ID検索 / API-07 表現更新のレスポンス(学習内容)。 */
export interface ExpressionDetail {
  expressionId: number
  promptText: string
  answerText: string
  expressionMeaning: string
  /** 備考(未入力の場合は null) */
  note: string | null
}

/** API-06 キーワード検索の結果1件。 */
export interface ExpressionSearchItem {
  expressionId: number
  answerText: string
  expressionMeaning: string
}

/** API-06 キーワード検索のレスポンス。 */
export interface ExpressionSearchResponse {
  /** 検索結果(表現IDの昇順) */
  items: ExpressionSearchItem[]
}

/** API-02 表現登録(POST /api/expressions)。 */
export function createExpression(
  body: ExpressionContentRequest,
): Promise<ExpressionCreateResponse> {
  return request<ExpressionCreateResponse>('POST', '/api/expressions', body)
}

/** API-05 表現ID検索・詳細取得(GET /api/expressions/{expressionId})。 */
export function getExpression(expressionId: number): Promise<ExpressionDetail> {
  return request<ExpressionDetail>('GET', `/api/expressions/${expressionId}`)
}

/**
 * API-06 キーワード検索(GET /api/expressions/search?keyword=...)。
 * キーワードは URLSearchParams でエンコードする(日本語・空白・% 等の記号をそのまま送るため)。
 */
export function searchExpressions(keyword: string): Promise<ExpressionSearchResponse> {
  const query = new URLSearchParams({ keyword })
  return request<ExpressionSearchResponse>('GET', `/api/expressions/search?${query}`)
}

/** API-07 表現更新(PUT /api/expressions/{expressionId})。 */
export function updateExpression(
  expressionId: number,
  body: ExpressionContentRequest,
): Promise<ExpressionDetail> {
  return request<ExpressionDetail>('PUT', `/api/expressions/${expressionId}`, body)
}

/** API-08 表現削除(DELETE /api/expressions/{expressionId})。成功時はレスポンスボディなし。 */
export function deleteExpression(expressionId: number): Promise<void> {
  return request<void>('DELETE', `/api/expressions/${expressionId}`)
}
