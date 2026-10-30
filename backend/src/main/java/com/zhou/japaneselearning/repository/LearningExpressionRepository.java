package com.zhou.japaneselearning.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zhou.japaneselearning.entity.LearningExpression;

/**
 * 学習表現(learning_expression)へのデータアクセスを担当するRepository。
 *
 * <p>Spring Data JPAではインターフェースを宣言するだけで、実装クラスが起動時に自動生成される。
 * JpaRepository を継承することで、以下の基本操作は実装なしで利用できる。</p>
 * <ul>
 *   <li>findById : 表現IDで1件取得(BIZ-04 / 05 / 06 / 08)</li>
 *   <li>save     : 登録・更新(BIZ-01 / 04 / 06)</li>
 *   <li>delete   : 物理削除(BIZ-08)</li>
 *   <li>count    : 全件数 = 登録表現数(BIZ-07)</li>
 * </ul>
 *
 * <p>Repositoryの責務は「どのデータを、どの条件・順序で取得するか」のみとする。
 * 表現IDの採番判定、学習段階の遷移、入力値チェック等の業務ロジックはService層で実装する(詳細設計書 11章)。</p>
 *
 * <p>型引数 {@code <LearningExpression, Integer>} は「扱うEntity」と「主キー(@Id)の型」を表す。</p>
 */
public interface LearningExpressionRepository extends JpaRepository<LearningExpression, Integer> {

    /**
     * 当日の復習対象の抽出条件(BIZ-03 判定・処理 1～3)。
     *
     * <p>一覧取得(findTodayReviewTargets)と件数取得(countTodayReviewTargets)で同一条件を使う必要がある
     * (BIZ-07「BIZ-03と同一の復習対象条件」)。条件を1か所に定義して両クエリで共有し、片方だけ修正される事故を防ぐ。
     * アノテーションの値にはコンパイル時定数しか使えないため、インターフェースの定数(暗黙的に static final)としている。</p>
     *
     * <ul>
     *   <li>MASTERED ではない(JPQLではenum定数を完全修飾名で記述する)</li>
     *   <li>次回復習予定日がNULLではなく、当日以前(期限切れの未実施分も含む)</li>
     *   <li>当日まだ採点していない(未復習、または最終復習日が当日以外)</li>
     * </ul>
     *
     * <p>日付列はDB上 'YYYY-MM-DD' 形式のTEXTであり、パラメータの LocalDate もEntityのコンバータにより
     * 同じ形式の文字列へ変換されてから比較される。この形式では文字列の大小が日付の前後と一致するため、
     * {@code <=} による比較が正しく機能する。</p>
     */
    String TODAY_REVIEW_CONDITION = """
            WHERE e.learningStage <> com.zhou.japaneselearning.entity.LearningStage.MASTERED
              AND e.nextReviewDate IS NOT NULL
              AND e.nextReviewDate <= :today
              AND (e.lastReviewDate IS NULL OR e.lastReviewDate <> :today)
            """;

    /**
     * 既存の表現IDを昇順で全件取得する(BIZ-02 表現ID採番 処理内容1)。
     *
     * <p>最小未使用IDの判定(1から順に比較する処理)はService層で行い、ここではIDの一覧のみを返す。
     * Entity全体ではなくIDのみを取得するため、SELECT句で e.expressionId を指定している。</p>
     *
     * @return 表現IDの昇順リスト。登録データが0件の場合は空リスト
     */
    @Query("SELECT e.expressionId FROM LearningExpression e ORDER BY e.expressionId ASC")
    List<Integer> findAllExpressionIdsOrderByAsc();

    /**
     * 当日の復習対象を優先順位順に取得する(BIZ-03 復習対象取得 / API-03)。
     *
     * <p>並び順は累計誤答回数の降順(苦手な表現を優先)、同数の場合は表現IDの昇順(BIZ-03 判定・処理 4)。</p>
     *
     * <p>JPQLはテーブル名・列名ではなく、Entityのクラス名(LearningExpression)とフィールド名で記述する。</p>
     *
     * @param today 基準日(システム当日)。テストで任意の日付を指定できるよう、Repository内で現在日付は取得しない
     * @return 復習対象リスト。対象0件の場合は空リスト
     */
    @Query("SELECT e FROM LearningExpression e "
            + TODAY_REVIEW_CONDITION
            + "ORDER BY e.totalWrongCount DESC, e.expressionId ASC")
    List<LearningExpression> findTodayReviewTargets(@Param("today") LocalDate today);

    /**
     * 当日の復習対象件数を取得する(BIZ-07 学習状況集計「今日の復習件数」/ API-01)。
     *
     * <p>件数のみが必要なため、Entityを取得せずDB側で COUNT を行う。抽出条件は findTodayReviewTargets と共通。</p>
     *
     * @param today 基準日(システム当日)
     * @return 当日の復習対象件数
     */
    @Query("SELECT COUNT(e) FROM LearningExpression e " + TODAY_REVIEW_CONDITION)
    long countTodayReviewTargets(@Param("today") LocalDate today);

    /**
     * キーワードで部分一致検索する(BIZ-05 表現検索 キーワード検索 / API-06)。
     *
     * <p>検索対象は中国語例文・日本語表現・表現及び中国語意味の3項目。備考(note)は対象外。
     * 結果は表現IDの昇順とする。</p>
     *
     * <p>キーワード内の {@code %} と {@code _} はLIKEのワイルドカードではなく通常の文字として扱う。
     * そのためエスケープ処理を行った上で {@link #searchByLikePattern(String)} を呼び出す。
     * (default メソッドはインターフェースに実装を持たせる仕組みで、Spring Dataの自動生成対象にはならない)</p>
     *
     * <p>未入力・空白のみのキーワードは不正入力としてService層で事前にチェックする前提とし、ここでは判定しない。</p>
     *
     * @param keyword 検索キーワード(エスケープ前の、利用者が入力したままの文字列)
     * @return 検索結果(表現ID昇順)。該当0件の場合は空リスト
     */
    default List<LearningExpression> searchByKeyword(String keyword) {
        return searchByLikePattern("%" + escapeLikeWildcards(keyword) + "%");
    }

    /**
     * LIKEパターンを指定して3項目を部分一致検索する。
     *
     * <p>ESCAPE '\' により、パターン中の「\%」「\_」「\\」をそれぞれ文字そのものとして扱わせる。
     * 引数はエスケープ済みのパターンである必要があるため、通常は {@link #searchByKeyword(String)} を使用すること。</p>
     *
     * @param pattern エスケープ済みのLIKEパターン(前後に % を付与したもの)
     * @return 検索結果(表現ID昇順)
     */
    @Query("""
            SELECT e FROM LearningExpression e
            WHERE e.promptText LIKE :pattern ESCAPE '\\'
               OR e.answerText LIKE :pattern ESCAPE '\\'
               OR e.expressionMeaning LIKE :pattern ESCAPE '\\'
            ORDER BY e.expressionId ASC
            """)
    List<LearningExpression> searchByLikePattern(@Param("pattern") String pattern);

    /**
     * LIKEで特別な意味を持つ文字をエスケープする。
     *
     * <p>エスケープ文字 '\' 自体を最初に置換しないと、後から付与した '\' まで二重に置換されてしまうため、
     * 置換順序は「\ → % → _」とする。</p>
     *
     * @param keyword 利用者が入力したキーワード
     * @return エスケープ済み文字列
     */
    private static String escapeLikeWildcards(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
