package com.zhou.japaneselearning.dto;

import java.time.LocalDate;

import com.zhou.japaneselearning.entity.LearningExpression;

/**
 * API-02 表現登録(POST /api/expressions)のレスポンス。
 *
 * <p>API設計書 3.2 のResponse項目3つのみを返す。学習内容や正誤回数等、登録したEntityの他の項目は返さない。</p>
 *
 * <p>learningStage はAPI設計書で string 型と定義されているため、enumではなく文字列("NEW")として保持する。
 * nextReviewDate は LocalDate のまま保持し、JSONでは "YYYY-MM-DD" 形式の文字列として出力される。</p>
 *
 * @param expressionId   採番された表現ID
 * @param learningStage  学習段階(登録直後は "NEW")
 * @param nextReviewDate 初回復習予定日(登録日の翌日)
 */
public record ExpressionCreateResponse(int expressionId, String learningStage, LocalDate nextReviewDate) {

    /**
     * 登録後のEntityからレスポンスを作成する。
     * Entity → DTO の詰め替えをDTO側に置くことで、Controllerの処理を「Serviceの呼び出し」と「返却」に保つ。
     */
    public static ExpressionCreateResponse from(LearningExpression expression) {
        return new ExpressionCreateResponse(
                expression.getExpressionId(),
                expression.getLearningStage().name(),
                expression.getNextReviewDate());
    }
}
