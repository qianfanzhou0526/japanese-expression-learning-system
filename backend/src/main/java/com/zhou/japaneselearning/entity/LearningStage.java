package com.zhou.japaneselearning.entity;

/**
 * 学習段階。learning_expression.learning_stage 列に保持する値を表す。
 *
 * <p>DB側では CHECK 制約で NEW / DAILY / WEEKLY / MONTHLY / MASTERED の5値に制限している。
 * Java側でもenumとすることで、存在しない段階名(タイプミス等)をコンパイル時に防ぐ。
 * 定数名はDBに保存される文字列と完全に一致させる必要があるため、名前を変更しないこと。</p>
 *
 * <p>段階遷移の判定(どの段階からどの段階へ進むか)は業務ロジックのためService層で実装する。</p>
 */
public enum LearningStage {
    /** 登録直後。初回復習前の状態。 */
    NEW,
    /** 毎日復習する段階。 */
    DAILY,
    /** 7日ごとに復習する段階。 */
    WEEKLY,
    /** 1か月ごとに復習する段階。 */
    MONTHLY,
    /** 定着済み。通常の復習対象から除外される。 */
    MASTERED
}
