package com.zhou.japaneselearning.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 学習表現Entity。learning_expression テーブルの1行を表すJavaオブジェクト。
 *
 * <p>Entityの役割は「テーブルの列とJavaのフィールドの対応付け」のみとし、
 * 学習段階の遷移・次回復習日の計算・表現IDの採番等の業務ロジックはService層で実装する(詳細設計書 11章)。
 * また、API の Request/Response には本Entityを直接使わず、DTOへ詰め替える方針とする。</p>
 *
 * <p>テーブル定義(列・制約)は schema.sql で管理しており、Hibernateによる自動生成は行わない
 * (spring.jpa.hibernate.ddl-auto=none)。そのため本クラスの @Column の nullable 等は
 * DBへ反映されるものではなく、schema.sql の定義と一致させた「対応関係の明示」として記載している。</p>
 */
@Entity
@Table(name = "learning_expression")
public class LearningExpression {

    /**
     * 表現ID。
     * 「最小の未使用IDを採番する」要件(DB設計書 制約・方針 2章)を満たすため、
     * DBやHibernateの自動採番(@GeneratedValue)は使わず、Service層で採番した値を保存前に設定する。
     * 1以上であることはDBの CHECK (expression_id > 0) で担保される。
     */
    @Id
    @Column(name = "expression_id", nullable = false)
    private Integer expressionId;

    /** 中国語例文。復習時に提示する問題文。 */
    @Column(name = "prompt_text", nullable = false)
    private String promptText;

    /** 日本語表現。利用者が想起する正答。 */
    @Column(name = "answer_text", nullable = false)
    private String answerText;

    /** 表現及び中国語意味。 */
    @Column(name = "expression_meaning", nullable = false)
    private String expressionMeaning;

    /** 備考。任意入力のためNULLを許容する。キーワード検索の対象外。 */
    @Column(name = "note")
    private String note;

    /**
     * 学習段階。
     * EnumType.STRING を指定し、enumの定数名("NEW"等)をそのまま文字列として保存する。
     * 既定の EnumType.ORDINAL は定義順の番号(0,1,2...)を保存するため、
     * DBの CHECK 制約(文字列5値)と合わず、enumの並び替えでデータが壊れる危険もある。
     *
     * 初期値 NEW は schema.sql の DEFAULT 'NEW' に対応する。
     * JPAはINSERT時に全列の値を明示的に送るため、DB側のDEFAULTは使われない。
     * そのため、Java側でも同じ初期値を持たせておく。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "learning_stage", nullable = false)
    private LearningStage learningStage = LearningStage.NEW;

    /*
     * 各計数項目はNULL不可のため、プリミティブ型 int を使用する。
     * int の初期値 0 は schema.sql の DEFAULT 0 に対応する。
     */

    /** 現在の学習段階における連続正解回数。段階遷移の判定に使用する。 */
    @Column(name = "consecutive_correct_count", nullable = false)
    private int consecutiveCorrectCount;

    /** 累計正解回数。 */
    @Column(name = "total_correct_count", nullable = false)
    private int totalCorrectCount;

    /** 累計誤答回数。復習対象の出題優先順位(降順)に使用する。 */
    @Column(name = "total_wrong_count", nullable = false)
    private int totalWrongCount;

    /*
     * 日付・日時はDB上はTEXT列だが、Java側では日付計算(翌日・7日後・翌月同日等)を
     * 行いやすいよう LocalDate / LocalDateTime で扱う。
     * TEXTとの変換形式は @Convert で指定したコンバータが設計書の形式に固定する。
     */

    /** 次回復習予定日(YYYY-MM-DD)。MASTERED遷移時はNULL。 */
    @Convert(converter = LocalDateTextConverter.class)
    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    /** 最終復習日(YYYY-MM-DD)。未復習時はNULL。同一日の重複採点防止に使用する。 */
    @Convert(converter = LocalDateTextConverter.class)
    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;

    /**
     * 登録日時(YYYY-MM-DD HH:MM:SS)。
     * updatable = false により、Hibernateが発行するUPDATE文にこの列を含めない。
     * 表現編集(BIZ-06)で created_at は更新不可と定められているため、誤って書き換わることを防ぐ。
     */
    @Convert(converter = LocalDateTimeTextConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新日時(YYYY-MM-DD HH:MM:SS)。レコード更新時に更新する。 */
    @Convert(converter = LocalDateTimeTextConverter.class)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA仕様で必須の引数なしコンストラクタ。
     * HibernateがDBから取得した行をオブジェクト化する際に使用する。
     * アプリのコードから不完全な状態で生成されないよう protected としている。
     */
    protected LearningExpression() {
    }

    /**
     * 新規登録用のコンストラクタ。
     * NULL不可の学習内容3項目を必須引数とし、学習状態は各フィールドの初期値(NEW / 0)で開始する。
     * 表現ID・次回復習日・登録日時等はService層で採番・計算した値をsetterで設定する。
     */
    public LearningExpression(String promptText, String answerText, String expressionMeaning, String note) {
        this.promptText = promptText;
        this.answerText = answerText;
        this.expressionMeaning = expressionMeaning;
        this.note = note;
    }

    // ---- getter / setter ----
    // Lombokは使用しない方針のため明示的に記述する。

    public Integer getExpressionId() {
        return expressionId;
    }

    public void setExpressionId(Integer expressionId) {
        this.expressionId = expressionId;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public String getExpressionMeaning() {
        return expressionMeaning;
    }

    public void setExpressionMeaning(String expressionMeaning) {
        this.expressionMeaning = expressionMeaning;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LearningStage getLearningStage() {
        return learningStage;
    }

    public void setLearningStage(LearningStage learningStage) {
        this.learningStage = learningStage;
    }

    public int getConsecutiveCorrectCount() {
        return consecutiveCorrectCount;
    }

    public void setConsecutiveCorrectCount(int consecutiveCorrectCount) {
        this.consecutiveCorrectCount = consecutiveCorrectCount;
    }

    public int getTotalCorrectCount() {
        return totalCorrectCount;
    }

    public void setTotalCorrectCount(int totalCorrectCount) {
        this.totalCorrectCount = totalCorrectCount;
    }

    public int getTotalWrongCount() {
        return totalWrongCount;
    }

    public void setTotalWrongCount(int totalWrongCount) {
        this.totalWrongCount = totalWrongCount;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public LocalDate getLastReviewDate() {
        return lastReviewDate;
    }

    public void setLastReviewDate(LocalDate lastReviewDate) {
        this.lastReviewDate = lastReviewDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
