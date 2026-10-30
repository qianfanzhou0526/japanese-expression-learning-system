package com.zhou.japaneselearning.dto;

/**
 * APIのエラーレスポンス(API設計書 5章「エラーレスポンスの基本形」)。
 *
 * <p>項目名(code / message)はAPI設計書で固定されている。
 * code にはV1で定めたエラーコード(INVALID_REQUEST 等)を設定する(ApiExceptionHandler を参照)。</p>
 *
 * @param code    アプリケーションエラーコード
 * @param message 利用者またはフロントエンド向けエラーメッセージ
 */
public record ApiErrorResponse(String code, String message) {
}
