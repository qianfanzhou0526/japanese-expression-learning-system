package com.zhou.japaneselearning.dto;

/**
 * API-01 ホーム情報取得(GET /api/dashboard)のレスポンス。
 *
 * <p>DTO(Data Transfer Object)は、APIでやり取りするデータの形だけを定義するクラス。
 * Entityをそのまま返さずDTOへ詰め替えることで、DBの内部項目がAPIへ漏れることを防ぎ、
 * DB構造とAPI仕様を独立して変更できるようにする(詳細設計書 11章)。</p>
 *
 * <p>record のコンポーネント名がそのままJSONの項目名になる。項目名・順序はAPI設計書 3.1 に合わせている。</p>
 *
 * @param todayReviewCount          当日復習対象件数
 * @param registeredExpressionCount 登録表現総数
 */
public record DashboardResponse(long todayReviewCount, long registeredExpressionCount) {
}
