package com.zhou.japaneselearning.controller;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.zhou.japaneselearning.dto.ApiErrorResponse;

/**
 * 全Controller共通の例外ハンドラ。例外をAPI設計書 5章のエラー形式 {code, message} のレスポンスに変換する。
 *
 * <p>@RestControllerAdvice を付けたクラスは、全Controllerで発生した例外を横断的に受け取れる。
 * 各Controllerに try-catch を書かずに、エラー時のHTTPステータスとレスポンス形式をここに集約する。</p>
 *
 * <p>ResponseEntityExceptionHandler(Spring提供)を継承することで、Spring MVCの標準例外
 * (入力チェック違反・JSON形式不正・存在しないURL・未対応のHTTPメソッド等)は、
 * Springが定める本来のHTTPステータス(400 / 404 / 405 等)のまま処理される。
 * 本クラスではそのレスポンスボディだけを {code, message} 形式に置き換えている。</p>
 *
 * <p>エラーコード(code)はV1で定めた以下の値を使用する。現時点では API-01 / API-02 で発生し得る
 * INVALID_REQUEST と INTERNAL_SERVER_ERROR のみ実装している。
 * INVALID_EXPRESSION_ID / EXPRESSION_NOT_FOUND / ALREADY_REVIEWED_TODAY / REVIEW_NOT_DUE / REVIEW_NOT_ALLOWED は、
 * それらを返すAPI(API-03以降)の実装時に追加する。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 一般的な入力値不正・JSON形式不正・必須項目不足等(400)。 */
    static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** 想定外のサーバー内部エラー(500)。 */
    static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    /**
     * Serviceの入力チェック違反(IllegalArgumentException)→ 400 Bad Request。
     *
     * <p>通常の入力不正はDTOの Bean Validation で先に検出されるが、DTOで表現できない業務ルール
     * (例: 後続APIで扱う expressionId の範囲チェック)や、DTOとServiceの判定差異に備えて、
     * Serviceが送出した入力不正も同じ 400 として返す。</p>
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
     * <p>エラーコードは、4xx(400の入力チェック違反・JSON形式不正のほか、未対応のHTTPメソッドによる405、
     * 未定義のURLによる404等のリクエスト自体の誤り)を INVALID_REQUEST、5xx を INTERNAL_SERVER_ERROR とする。</p>
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String message;
        if (ex instanceof MethodArgumentNotValidException validationException) {
            // DTOの入力チェック違反。違反した全項目のメッセージを項目名順に連結する(複数項目の同時不足に対応)
            message = validationException.getBindingResult().getFieldErrors().stream()
                    .sorted((a, b) -> a.getField().compareTo(b.getField()))
                    .map(fieldError -> fieldError.getDefaultMessage())
                    .collect(Collectors.joining(" "));
        } else if (ex instanceof HttpMessageNotReadableException) {
            // リクエストボディが無い、JSONとして解析できない等
            message = "リクエストの形式が不正です。";
        } else {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            message = status != null ? status.getReasonPhrase() : "リクエストを処理できませんでした。";
        }
        String code = statusCode.is4xxClientError() ? INVALID_REQUEST : INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(new ApiErrorResponse(code, message), headers, statusCode);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
