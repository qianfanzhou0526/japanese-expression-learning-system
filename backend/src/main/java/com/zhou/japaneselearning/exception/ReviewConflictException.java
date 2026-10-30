package com.zhou.japaneselearning.exception;

/**
 * 学習表現の現在の状態と競合するため、採点を受け付けられないことを表す例外。
 *
 * <p>Service層で送出し、Controller層でHTTP 409 Conflict に変換する想定
 * (API設計書 5章「同一日の重複採点等、現在状態との競合」)。HTTPへの変換は本クラスでは扱わない。</p>
 *
 * <p>拒否理由は {@link Reason} で区別する。呼び出し側(API層)はメッセージ文字列ではなく
 * {@link #getReason()} で理由を判定するため、メッセージの文言を変更しても判定に影響しない。</p>
 */
public class ReviewConflictException extends RuntimeException {

    /** 採点を受け付けない理由(BIZ-04)。 */
    public enum Reason {
        /** 同一表現を同じ日にすでに採点済み(詳細設計書 5.1)。 */
        ALREADY_REVIEWED_TODAY,
        /** 次回復習予定日が未到来(当日より後)。 */
        REVIEW_NOT_DUE,
        /** MASTERED 等、採点対象外の状態。 */
        REVIEW_NOT_ALLOWED
    }

    private final Reason reason;

    public ReviewConflictException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
