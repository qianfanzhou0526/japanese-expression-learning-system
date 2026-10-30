package com.zhou.japaneselearning.dto;

import com.zhou.japaneselearning.service.ReviewResult;

import jakarta.validation.constraints.NotNull;

/**
 * API-04 復習結果更新(POST /api/reviews/{expressionId}/result)のリクエスト。
 *
 * <p>result を enum(ReviewResult)で受け取ることで、"CORRECT" / "INCORRECT" 以外の文字列
 * (小文字の "correct" 等も含む)はJSONからの変換時点で拒否され、400 となる。
 * result 自体が省略された場合は @NotNull により 400 となる。</p>
 *
 * @param result 自己採点結果(CORRECT / INCORRECT)
 */
public record ReviewResultRequest(@NotNull(message = "result は必須です。") ReviewResult result) {
}
