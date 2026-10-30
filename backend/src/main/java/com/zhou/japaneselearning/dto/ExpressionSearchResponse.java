package com.zhou.japaneselearning.dto;

import java.util.List;

import com.zhou.japaneselearning.entity.LearningExpression;

/**
 * API-06 キーワード検索(GET /api/expressions/search)のレスポンス。
 *
 * <p>検索結果一覧(S04-TBL-01)に表示する3項目のみを返す(API設計書 3.6)。
 * 中国語例文・備考は一覧に表示しないため返さず、必要な場合は選択後に API-05 で詳細を取得する。
 * 該当0件の場合は items が空配列となる。</p>
 *
 * @param items 検索結果一覧(表現IDの昇順)
 */
public record ExpressionSearchResponse(List<Item> items) {

    /**
     * 検索結果1件。
     *
     * @param expressionId      表現ID
     * @param answerText        日本語表現
     * @param expressionMeaning 表現及び中国語意味
     */
    public record Item(int expressionId, String answerText, String expressionMeaning) {

        static Item from(LearningExpression e) {
            return new Item(e.getExpressionId(), e.getAnswerText(), e.getExpressionMeaning());
        }
    }

    public static ExpressionSearchResponse from(List<LearningExpression> expressions) {
        return new ExpressionSearchResponse(expressions.stream().map(Item::from).toList());
    }
}
