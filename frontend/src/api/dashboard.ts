import { request } from './client'

/** API-01 ホーム情報取得のレスポンス。 */
export interface DashboardResponse {
  /** 当日復習対象件数 */
  todayReviewCount: number
  /** 登録表現総数 */
  registeredExpressionCount: number
}

/** API-01 ホーム情報取得(GET /api/dashboard)。 */
export function getDashboard(): Promise<DashboardResponse> {
  return request<DashboardResponse>('GET', '/api/dashboard')
}
