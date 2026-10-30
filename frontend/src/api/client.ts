/**
 * バックエンドAPI呼び出しの共通処理。
 *
 * 各画面は fetch を直接使わず、src/api/ 配下のAPI関数(getDashboard 等)を経由して通信する。
 * ここでは全APIに共通する「JSONの送受信」と「エラーの統一的な扱い」だけを担当する。
 *
 * URL は /api/... の相対パスで指定する。開発時は Vite のプロキシにより Spring Boot へ転送される。
 */

/**
 * API呼び出しの失敗を表すエラー。画面側はこのエラーの message をそのまま利用者向けに表示できる。
 *
 * - status: HTTPステータス。通信自体ができなかった場合は null
 * - code: バックエンドが返したエラーコード(INVALID_REQUEST 等)。取得できなかった場合は null
 */
export class ApiError extends Error {
  readonly status: number | null
  readonly code: string | null

  constructor(message: string, status: number | null, code: string | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const CONNECTION_ERROR_MESSAGE =
  'サーバーに接続できませんでした。バックエンドが起動しているか確認してください。'

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

/**
 * APIを呼び出し、成功時はレスポンスのJSONを返す。失敗時は ApiError を送出する。
 *
 * @param method HTTPメソッド
 * @param path   /api から始まる相対パス
 * @param body   リクエストボディ(JSONに変換して送信する)。不要な場合は省略
 */
export async function request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    // ネットワークエラー(開発サーバー自体が停止している等)では fetch が例外を送出する
    throw new ApiError(CONNECTION_ERROR_MESSAGE, null, null)
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  // 204 No Content(削除API等)はボディが無いため、JSONとして読み込まない
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * エラーレスポンスを ApiError に変換する。
 *
 * バックエンドは {code, message} 形式のJSONを返す。それ以外のレスポンス
 * (バックエンド停止時にプロキシが返すエラー等)は、ステータスに応じた汎用メッセージとする。
 */
async function toApiError(response: Response): Promise<ApiError> {
  try {
    const data: unknown = await response.json()
    if (isErrorBody(data)) {
      return new ApiError(data.message, response.status, data.code)
    }
  } catch {
    // JSONとして解析できない場合は、下の汎用メッセージを使用する
  }
  const message =
    response.status >= 500
      ? CONNECTION_ERROR_MESSAGE
      : `リクエストを処理できませんでした。(HTTP ${response.status})`
  return new ApiError(message, response.status, null)
}

function isErrorBody(data: unknown): data is { code: string; message: string } {
  return (
    typeof data === 'object' &&
    data !== null &&
    typeof (data as Record<string, unknown>).code === 'string' &&
    typeof (data as Record<string, unknown>).message === 'string'
  )
}
