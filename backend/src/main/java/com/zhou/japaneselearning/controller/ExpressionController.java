package com.zhou.japaneselearning.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.zhou.japaneselearning.dto.ExpressionCreateRequest;
import com.zhou.japaneselearning.dto.ExpressionCreateResponse;
import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.service.LearningExpressionService;

import jakarta.validation.Valid;

/**
 * 学習表現(/api/expressions)のAPIを提供するController。
 *
 * <p>現時点の実装範囲は API-02 表現登録 のみ。
 * API-05～08(ID検索・キーワード検索・更新・削除)は後続で本クラスに追加する予定。</p>
 */
@RestController
@RequestMapping("/api/expressions")
public class ExpressionController {

    private final LearningExpressionService learningExpressionService;

    public ExpressionController(LearningExpressionService learningExpressionService) {
        this.learningExpressionService = learningExpressionService;
    }

    /**
     * API-02 表現登録。入力された学習表現を登録し、採番した表現ID等を返す(BIZ-01 / BIZ-02)。
     *
     * <ul>
     *   <li>@RequestBody: リクエストボディのJSONを ExpressionCreateRequest に変換する</li>
     *   <li>@Valid: 変換後にDTOのBean Validation(@NotBlank)を実行する。違反時はこのメソッドは実行されず、
     *       ApiExceptionHandler が 400 Bad Request を返す</li>
     *   <li>@ResponseStatus(CREATED): 成功時のステータスを 201 Created とする(API設計書 3.2)</li>
     * </ul>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpressionCreateResponse create(@Valid @RequestBody ExpressionCreateRequest request) {
        LearningExpression registered = learningExpressionService.register(
                request.promptText(),
                request.answerText(),
                request.expressionMeaning(),
                request.note());
        return ExpressionCreateResponse.from(registered);
    }
}
