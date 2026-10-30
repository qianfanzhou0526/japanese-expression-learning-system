package com.zhou.japaneselearning.dto;

import com.zhou.japaneselearning.entity.LearningExpression;

/**
 * 学習表現の詳細(学習内容)のレスポンス。
 *
 * <p>以下の2つのAPIで共通に使用する(どちらも同じ5項目を返すと設計で定めている)。</p>
 * <ul>
 *   <li>API-05 表現ID検索・詳細取得(GET /api/expressions/{expressionId})</li>
 *   <li>API-07 表現更新(PUT /api/expressions/{expressionId})の更新後の内容</li>
 * </ul>
 *
 * <p>編集画面で扱う学習内容のみを返し、学習状態・正誤回数・復習日・登録日時等は返さない。</p>
 *
 * @param expressionId      表現ID
 * @param promptText        中国語例文
 * @param answerText        日本語表現
 * @param expressionMeaning 表現及び中国語意味
 * @param note              備考(未入力の場合は null)
 */
public record ExpressionDetailResponse(
        int expressionId, String promptText, String answerText, String expressionMeaning, String note) {

    public static ExpressionDetailResponse from(LearningExpression e) {
        return new ExpressionDetailResponse(
                e.getExpressionId(), e.getPromptText(), e.getAnswerText(), e.getExpressionMeaning(), e.getNote());
    }
}
