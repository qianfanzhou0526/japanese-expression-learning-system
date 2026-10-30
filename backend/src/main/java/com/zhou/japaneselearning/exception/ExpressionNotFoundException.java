package com.zhou.japaneselearning.exception;

/**
 * 指定した表現IDの学習表現が存在しないことを表す例外。
 *
 * <p>Service層で送出し、Controller層でHTTP 404 Not Found に変換する想定
 * (API設計書 5章「指定した表現が存在しない」)。HTTPへの変換は本クラスでは扱わない。</p>
 *
 * <p>RuntimeException(非チェック例外)を継承しているため、呼び出し側でのtry-catchやthrows宣言は不要。
 * また、Springの @Transactional は非チェック例外の発生時に自動でロールバックする。</p>
 */
public class ExpressionNotFoundException extends RuntimeException {

    private final int expressionId;

    public ExpressionNotFoundException(int expressionId) {
        super("表現ID " + expressionId + " の学習表現は存在しません。");
        this.expressionId = expressionId;
    }

    /** 見つからなかった表現ID。エラーレスポンス作成時等に利用できるよう保持する。 */
    public int getExpressionId() {
        return expressionId;
    }
}
