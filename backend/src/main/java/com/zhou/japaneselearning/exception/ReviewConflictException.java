package com.zhou.japaneselearning.exception;

/**
 * 学習表現の現在の状態と競合するため、採点を受け付けられないことを表す例外。
 *
 * <p>Service層で送出し、Controller層でHTTP 409 Conflict に変換する想定
 * (API設計書 5章「同一日の重複採点等、現在状態との競合」)。HTTPへの変換は本クラスでは扱わない。</p>
 *
 * <p>送出するケース(BIZ-04):</p>
 * <ul>
 *   <li>同一表現を同じ日にすでに採点済み(詳細設計書 5.1)</li>
 *   <li>MASTERED の表現に対する採点</li>
 *   <li>次回復習予定日が未到来(当日より後)の表現に対する採点</li>
 * </ul>
 */
public class ReviewConflictException extends RuntimeException {

    public ReviewConflictException(String message) {
        super(message);
    }
}
