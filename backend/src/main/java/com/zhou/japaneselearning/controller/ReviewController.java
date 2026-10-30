package com.zhou.japaneselearning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zhou.japaneselearning.dto.ReviewResultRequest;
import com.zhou.japaneselearning.dto.ReviewResultResponse;
import com.zhou.japaneselearning.dto.ReviewTargetsResponse;
import com.zhou.japaneselearning.service.ReviewService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

/**
 * 復習画面(S03)向けのAPI(/api/reviews)を提供するController。
 *
 * <ul>
 *   <li>API-03 今日の復習対象取得 GET  /api/reviews/today</li>
 *   <li>API-04 復習結果更新       POST /api/reviews/{expressionId}/result</li>
 * </ul>
 *
 * <p>expressionId の検証方法は ExpressionController と同じ(@Min(1) / 数値以外は型変換エラー)。</p>
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * API-03 今日の復習対象取得(BIZ-03)。優先順の復習対象一覧を返す。対象0件でも 200 で空の items を返す。
     */
    @GetMapping("/today")
    public ReviewTargetsResponse getTodayReviewTargets() {
        return ReviewTargetsResponse.from(reviewService.findTodayReviewTargets());
    }

    /**
     * API-04 復習結果更新(BIZ-04)。自己採点結果を反映し、更新後の学習状態を返す。
     *
     * <p>対象が存在しない場合は 404、当日採点済み・予定日前・採点対象外の状態の場合は 409 となる
     * (Serviceが送出する例外を ApiExceptionHandler が変換する)。</p>
     */
    @PostMapping("/{expressionId}/result")
    public ReviewResultResponse recordResult(
            @PathVariable @Min(value = 1, message = ExpressionController.INVALID_EXPRESSION_ID_MESSAGE) int expressionId,
            @Valid @RequestBody ReviewResultRequest request) {
        return ReviewResultResponse.from(reviewService.recordReviewResult(expressionId, request.result()));
    }
}
