package com.zhou.japaneselearning.dto;

import java.util.List;

import com.zhou.japaneselearning.entity.LearningExpression;

/**
 * API-03 今日の復習対象取得(GET /api/reviews/today)のレスポンス。
 *
 * <p>復習対象は Service が返した優先順(累計誤答回数の降順、同数は表現IDの昇順)のまま items に格納する。
 * 対象0件の場合は items が空配列となる(API設計書 3.3)。</p>
 *
 * @param items 復習対象一覧
 */
public record ReviewTargetsResponse(List<Item> items) {

    /**
     * 復習対象1件。answerText / expressionMeaning / note は画面の「回答後」に表示する項目だが、
     * 1回の取得で復習を進められるよう、設計どおり最初から含めて返す。学習状態等の内部項目は含めない。
     *
     * @param expressionId      表現ID
     * @param promptText        中国語例文
     * @param answerText        日本語表現(回答後表示用)
     * @param expressionMeaning 表現及び中国語意味(回答後表示用)
     * @param note              備考(回答後表示用。未入力の場合は null)
     */
    public record Item(int expressionId, String promptText, String answerText, String expressionMeaning, String note) {

        static Item from(LearningExpression e) {
            return new Item(e.getExpressionId(), e.getPromptText(), e.getAnswerText(), e.getExpressionMeaning(), e.getNote());
        }
    }

    public static ReviewTargetsResponse from(List<LearningExpression> expressions) {
        return new ReviewTargetsResponse(expressions.stream().map(Item::from).toList());
    }
}
