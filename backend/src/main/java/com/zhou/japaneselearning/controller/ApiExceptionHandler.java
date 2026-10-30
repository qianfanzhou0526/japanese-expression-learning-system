package com.zhou.japaneselearning.controller;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.zhou.japaneselearning.dto.ApiErrorResponse;
import com.zhou.japaneselearning.exception.ExpressionNotFoundException;
import com.zhou.japaneselearning.exception.ReviewConflictException;

/**
 * 全Controller共通の例外ハンドラ。例外をAPI設計書 5章のエラー形式 {code, message} のレスポンスに変換する。
 *
 * <p>@RestControllerAdvice を付けたクラスは、全Controllerで発生した例外を横断的に受け取れる。
 * 各Controllerに try-catch を書かずに、エラー時のHTTPステータスとレスポンス形式をここに集約する。</p>
 *
 * <p>ResponseEntityExceptionHandler(Spring提供)を継承することで、Spring MVCの標準例外
 * (入力チェック違反・JSON形式不正・型変換エラー・存在しないURL・未対応のHTTPメソッド等)は、
 * Springが定める本来のHTTPステータス(400 / 404 / 405 等)のまま処理される。
 * 本クラスではそのレスポンスボディだけを {code, message} 形式に置き換えている。</p>
 *
 * <p>エラーコード(V1):</p>
 * <table>
 *   <tr><th>code</th><th>HTTP</th><th>発生条件</th></tr>
 *   <tr><td>INVALID_REQUEST</td><td>400</td><td>一般的な入力値不正・JSON形式不正・必須項目不足等</td></tr>
 *   <tr><td>INVALID_EXPRESSION_ID</td><td>400</td><td>パスの expressionId が1未満、または数値として解釈できない</td></tr>
 *   <tr><td>EXPRESSION_NOT_FOUND</td><td>404</td><td>指定された表現が存在しない</td></tr>
 *   <tr><td>ALREADY_REVIEWED_TODAY</td><td>409</td><td>同一表現が当日すでに採点済み</td></tr>
 *   <tr><td>REVIEW_NOT_DUE</td><td>409</td><td>復習予定日前</td></tr>
 *   <tr><td>REVIEW_NOT_ALLOWED</td><td>409</td><td>MASTERED等、採点対象外の状態</td></tr>
 *   <tr><td>INTERNAL_SERVER_ERROR</td><td>500</td><td>想定外のサーバー内部エラー</td></tr>
 * </table>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String INVALID_REQUEST = "INVALID_REQUEST";
    static final String INVALID_EXPRESSION_ID = "INVALID_EXPRESSION_ID";
    static final String EXPRESSION_NOT_FOUND = "EXPRESSION_NOT_FOUND";
    static final String ALREADY_REVIEWED_TODAY = "ALREADY_REVIEWED_TODAY";
    static final String REVIEW_NOT_DUE = "REVIEW_NOT_DUE";
    static final String REVIEW_NOT_ALLOWED = "REVIEW_NOT_ALLOWED";
    static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    /** 指定された表現が存在しない → 404 Not Found。 */
    @ExceptionHandler(ExpressionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ExpressionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, EXPRESSION_NOT_FOUND, ex.getMessage());
    }

    /**
     * 採点を受け付けられない状態 → 409 Conflict。
     *
     * <p>エラーコードは例外が持つ拒否理由(enum)から決める。メッセージ文字列では判定しないため、
     * 文言を変更してもコードの判定には影響しない。switch式は enum の全定数を網羅しないとコンパイルエラーとなるため、
     * 将来理由が追加された場合もここでの対応漏れに気付ける。</p>
     */
    @ExceptionHandler(ReviewConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleReviewConflict(ReviewConflictException ex) {
        String code = switch (ex.getReason()) {
            case ALREADY_REVIEWED_TODAY -> ALREADY_REVIEWED_TODAY;
            case REVIEW_NOT_DUE -> REVIEW_NOT_DUE;
            case REVIEW_NOT_ALLOWED -> REVIEW_NOT_ALLOWED;
        };
        return error(HttpStatus.CONFLICT, code, ex.getMessage());
    }

    /**
     * Serviceの入力チェック違反(IllegalArgumentException)→ 400 Bad Request。
     *
     * <p>通常の入力不正はDTO・Controller引数の Bean Validation で先に検出されるが、
     * DTOとServiceの判定差異に備えて、Serviceが送出した入力不正も同じ 400 として返す。</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, INVALID_REQUEST, ex.getMessage());
    }

    /**
     * 想定外の例外 → 500 Internal Server Error。
     *
     * <p>原因調査のため例外の詳細はログに出力し、レスポンスには内部情報(スタックトレース等)を含めない。
     * Spring MVCの標準例外は継承元の ResponseEntityExceptionHandler がより具体的に処理するため、ここには来ない。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("想定外のエラーが発生しました。", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR, "サーバー内部エラーが発生しました。");
    }

    /**
     * Spring MVC標準例外のレスポンスボディを {code, message} 形式に置き換える。
     *
     * <p>継承元の ResponseEntityExceptionHandler が各標準例外を処理した後、最終的にこのメソッドが呼ばれる。
     * HTTPステータス(status)とヘッダー(headers)はSpringが決めたものをそのまま使い、ボディのみ差し替える。</p>
     *
     * <p>パスの expressionId に関するエラーは INVALID_EXPRESSION_ID、それ以外の4xx(未対応のHTTPメソッドによる405、
     * 未定義のURLによる404等のリクエスト自体の誤りを含む)は INVALID_REQUEST、5xx は INTERNAL_SERVER_ERROR とする。</p>
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return new ResponseEntity<>(toErrorResponse(ex, statusCode), headers, statusCode);
    }

    private static ApiErrorResponse toErrorResponse(Exception ex, HttpStatusCode statusCode) {
        // パスの expressionId が数値として解釈できない(例: /api/expressions/abc)。型変換は検証より前に行われる
        if (ex instanceof MethodArgumentTypeMismatchException mismatch
                && mismatch.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return new ApiErrorResponse(INVALID_EXPRESSION_ID, ExpressionController.INVALID_EXPRESSION_ID_MESSAGE);
        }
        // Controllerメソッド引数の制約違反(パスの @Min(1)、クエリパラメータの @NotBlank 等)。
        // メソッド引数に制約がある場合、同じメソッドの @Valid @RequestBody の違反もこの例外にまとめて含まれる
        if (ex instanceof HandlerMethodValidationException validation) {
            boolean pathVariableInvalid = validation.getParameterValidationResults().stream()
                    .anyMatch(result -> result.getMethodParameter().hasParameterAnnotation(PathVariable.class));
            String message = validation.getAllErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining(" "));
            // expressionId の不正を優先して返す(対象が特定できない状態では、ボディの内容を検討する意味がないため)
            return new ApiErrorResponse(pathVariableInvalid ? INVALID_EXPRESSION_ID : INVALID_REQUEST, message);
        }
        // DTOの入力チェック違反。違反した全項目のメッセージを項目名順に連結する(複数項目の同時不足に対応)
        if (ex instanceof MethodArgumentNotValidException validation) {
            String message = validation.getBindingResult().getFieldErrors().stream()
                    .sorted(Comparator.comparing(FieldError::getField))
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(" "));
            return new ApiErrorResponse(INVALID_REQUEST, message);
        }
        // 必須のクエリパラメータが無い(例: API-06 で keyword を指定しない)
        if (ex instanceof MissingServletRequestParameterException missing) {
            return new ApiErrorResponse(INVALID_REQUEST, missing.getParameterName() + " は必須です。");
        }
        // リクエストボディが無い、JSONとして解析できない、result に許容値以外が指定された等
        if (ex instanceof HttpMessageNotReadableException) {
            return new ApiErrorResponse(INVALID_REQUEST, "リクエストの形式が不正です。");
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message = status != null ? status.getReasonPhrase() : "リクエストを処理できませんでした。";
        return new ApiErrorResponse(statusCode.is4xxClientError() ? INVALID_REQUEST : INTERNAL_SERVER_ERROR, message);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
