package com.zhou.japaneselearning.service;

/**
 * BIZ-07 学習状況集計の結果(ホーム画面に表示する2項目)。
 *
 * <p>Serviceが2つの集計値をまとめて返すための値オブジェクトであり、APIのResponse DTOではない。
 * APIの項目名(registeredExpressionCount / todayReviewCount)への詰め替えはController側で行う。</p>
 *
 * <p>record は「値を保持するだけのクラス」を簡潔に定義するJavaの構文(Java 16以降)。
 * コンストラクタ・getter相当のメソッド(registeredExpressionCount() 等)・equals/hashCode/toString が自動生成される。</p>
 *
 * @param registeredExpressionCount 登録表現数(learning_expression の全登録件数。MASTEREDも含む)
 * @param todayReviewCount          今日の復習件数(BIZ-03と同一の復習対象条件に一致する件数)
 */
public record LearningStatistics(long registeredExpressionCount, long todayReviewCount) {
}
