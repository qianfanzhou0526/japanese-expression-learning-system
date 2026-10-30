package com.zhou.japaneselearning.dto;

import java.time.LocalDate;

import com.zhou.japaneselearning.entity.LearningExpression;

/**
 * API-04 復習結果更新(POST /api/reviews/{expressionId}/result)のレスポンス。採点後の学習状態を返す。
 *
 * <p>nextReviewDate は MASTERED へ遷移した場合 null となり、JSONでは "nextReviewDate": null として出力される
 * (API設計書 3.4「string / null」)。学習内容や登録日時・更新日時は返さない。</p>
 *
 * @param expressionId            更新対象ID
 * @param learningStage           更新後の学習段階
 * @param consecutiveCorrectCount 更新後の連続正解回数
 * @param totalCorrectCount       更新後の累計正解回数
 * @param totalWrongCount         更新後の累計誤答回数
 * @param nextReviewDate          更新後の次回復習予定日(MASTERED遷移時は null)
 * @param lastReviewDate          更新後の最終復習日
 */
public record ReviewResultResponse(
        int expressionId,
        String learningStage,
        int consecutiveCorrectCount,
        int totalCorrectCount,
        int totalWrongCount,
        LocalDate nextReviewDate,
        LocalDate lastReviewDate) {

    public static ReviewResultResponse from(LearningExpression e) {
        return new ReviewResultResponse(
                e.getExpressionId(),
                e.getLearningStage().name(),
                e.getConsecutiveCorrectCount(),
                e.getTotalCorrectCount(),
                e.getTotalWrongCount(),
                e.getNextReviewDate(),
                e.getLastReviewDate());
    }
}
