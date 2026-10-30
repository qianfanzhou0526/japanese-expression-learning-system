package com.zhou.japaneselearning.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * API-07 表現更新(PUT /api/expressions/{expressionId})のリクエスト。
 *
 * <p>更新可能な学習内容4項目のみを受け取る。学習段階・正誤回数・復習日は通常編集APIのRequestに含めない
 * (API設計書 3.7)ため項目自体を定義しておらず、JSONに含まれていても無視される。
 * 更新対象の表現IDはパスで指定するため、ボディには含めない。</p>
 *
 * <p>項目はAPI-02のリクエストと同じだが、「登録」と「更新」で将来の変更理由が異なり得るため、別のDTOとしている。</p>
 *
 * @param promptText        中国語例文(必須)
 * @param answerText        日本語表現(必須)
 * @param expressionMeaning 表現及び中国語意味(必須)
 * @param note              備考(任意。省略・null の場合は備考を削除する)
 */
public record ExpressionUpdateRequest(
        @NotBlank(message = "promptText は必須です。") String promptText,
        @NotBlank(message = "answerText は必須です。") String answerText,
        @NotBlank(message = "expressionMeaning は必須です。") String expressionMeaning,
        String note) {
}
