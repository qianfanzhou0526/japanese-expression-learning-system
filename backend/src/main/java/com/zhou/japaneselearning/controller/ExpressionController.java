package com.zhou.japaneselearning.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.zhou.japaneselearning.dto.ExpressionCreateRequest;
import com.zhou.japaneselearning.dto.ExpressionCreateResponse;
import com.zhou.japaneselearning.dto.ExpressionDetailResponse;
import com.zhou.japaneselearning.dto.ExpressionSearchResponse;
import com.zhou.japaneselearning.dto.ExpressionUpdateRequest;
import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.service.LearningExpressionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 学習表現(/api/expressions)のAPIを提供するController。
 *
 * <ul>
 *   <li>API-02 表現登録       POST   /api/expressions</li>
 *   <li>API-05 表現ID検索     GET    /api/expressions/{expressionId}</li>
 *   <li>API-06 キーワード検索 GET    /api/expressions/search?keyword=...</li>
 *   <li>API-07 表現更新       PUT    /api/expressions/{expressionId}</li>
 *   <li>API-08 表現削除       DELETE /api/expressions/{expressionId}</li>
 * </ul>
 *
 * <p>パスの expressionId には @Min(1) を付け、API境界で「1以上」を検証する。
 * Spring MVCはメソッド引数に制約アノテーションがあると自動で検証し、違反時は
 * HandlerMethodValidationException を送出する(ApiExceptionHandler で 400 / INVALID_EXPRESSION_ID に変換)。
 * 数値として解釈できない値(例: "abc")は、検証より前の型変換で失敗し、同じく 400 / INVALID_EXPRESSION_ID となる。</p>
 */
@RestController
@RequestMapping("/api/expressions")
public class ExpressionController {

    /** パスの expressionId が1未満の場合のメッセージ。 */
    static final String INVALID_EXPRESSION_ID_MESSAGE = "expressionId は1以上の正整数で指定してください。";

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

    /**
     * API-05 表現ID検索・詳細取得(BIZ-05 ID検索)。
     * 対象が存在しない場合、Serviceが送出する ExpressionNotFoundException により 404 となる。
     */
    @GetMapping("/{expressionId}")
    public ExpressionDetailResponse findById(
            @PathVariable @Min(value = 1, message = INVALID_EXPRESSION_ID_MESSAGE) int expressionId) {
        return ExpressionDetailResponse.from(learningExpressionService.findById(expressionId));
    }

    /**
     * API-06 キーワード検索(BIZ-05 キーワード検索)。
     *
     * <p>@RequestParam は URL のクエリパラメータ(?keyword=...)を引数に受け取る。
     * keyword 自体が無い場合は MissingServletRequestParameterException、空文字・空白のみの場合は @NotBlank 違反となり、
     * いずれも 400 / INVALID_REQUEST となる。</p>
     *
     * <p>パス "/search" は "/{expressionId}" より具体的なパスとして優先されるため、ID検索と競合しない。</p>
     */
    @GetMapping("/search")
    public ExpressionSearchResponse search(
            @RequestParam @NotBlank(message = "keyword は必須です。") String keyword) {
        return ExpressionSearchResponse.from(learningExpressionService.searchByKeyword(keyword));
    }

    /**
     * API-07 表現更新(BIZ-06)。学習内容4項目のみを更新し、更新後の内容(API-05と同じ5項目)を返す。
     * 成功時は 200 OK。
     */
    @PutMapping("/{expressionId}")
    public ExpressionDetailResponse update(
            @PathVariable @Min(value = 1, message = INVALID_EXPRESSION_ID_MESSAGE) int expressionId,
            @Valid @RequestBody ExpressionUpdateRequest request) {
        LearningExpression updated = learningExpressionService.update(
                expressionId,
                request.promptText(),
                request.answerText(),
                request.expressionMeaning(),
                request.note());
        return ExpressionDetailResponse.from(updated);
    }

    /**
     * API-08 表現削除(BIZ-08)。成功時は 204 No Content(レスポンスボディなし)。
     * 戻り値を void とし、@ResponseStatus でステータスのみを指定する。
     */
    @DeleteMapping("/{expressionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Min(value = 1, message = INVALID_EXPRESSION_ID_MESSAGE) int expressionId) {
        learningExpressionService.delete(expressionId);
    }
}
