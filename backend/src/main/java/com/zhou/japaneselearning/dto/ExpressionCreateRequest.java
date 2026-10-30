package com.zhou.japaneselearning.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * API-02 表現登録(POST /api/expressions)のリクエスト。
 *
 * <p>受け取るのはAPI設計書 3.2 のRequest項目4つのみ。
 * expressionId は利用者から受け取らない(バックエンドで採番する)ため、項目自体を定義していない。
 * JSONに定義外の項目(expressionId 等)が含まれていても無視される(Spring Bootの既定設定)。</p>
 *
 * <p>@NotBlank は Bean Validation の制約で、null・空文字・空白のみを不正とする。
 * Controllerの引数に @Valid を付けると、Serviceを呼ぶ前にSpringが自動で検証し、
 * 違反があれば 400 Bad Request となる。設計書で未確定の文字数上限等の制約は付けていない。</p>
 *
 * <p>同じ必須チェックはService(BIZ-01)にもある。DTO側はAPIの入口で不正なリクエストを早期に弾くためのもので、
 * Service側はAPI以外から呼ばれた場合も含めて業務ルールを保証するためのものとして、両方に置いている。</p>
 *
 * @param promptText        中国語例文(必須)
 * @param answerText        日本語表現(必須)
 * @param expressionMeaning 表現及び中国語意味(必須)
 * @param note              備考(任意)
 */
public record ExpressionCreateRequest(
        @NotBlank(message = "promptText は必須です。") String promptText,
        @NotBlank(message = "answerText は必須です。") String answerText,
        @NotBlank(message = "expressionMeaning は必須です。") String expressionMeaning,
        String note) {
}
