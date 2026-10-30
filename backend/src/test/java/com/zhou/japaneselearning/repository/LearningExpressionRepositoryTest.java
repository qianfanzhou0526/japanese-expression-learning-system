package com.zhou.japaneselearning.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;

/**
 * LearningExpressionRepository のテスト。
 *
 * <p>@DataJpaTest はJPA関連のBeanだけを起動する軽量なテスト用アノテーション
 * (Controller等は起動しない)。また各テストメソッドは終了時に自動でロールバックされるため、
 * テスト間でデータが残らず、各テストは常に空のテーブルから始まる。</p>
 *
 * <p>@DataJpaTest は既定でDataSourceをH2等の組込みDBに置き換えようとする。
 * 本プロジェクトはSQLite固有の挙動(TEXT日付の比較・LIKE等)を確認したいため、
 * replace = NONE で置き換えを無効にし、@ActiveProfiles("test") でテスト専用のSQLite DB
 * (application-test.properties)を使用する。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class LearningExpressionRepositoryTest {

    /** テストの基準日(システム当日の代わり)。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Autowired
    private LearningExpressionRepository repository;

    /**
     * テストデータを1件作成して保存する。
     * 学習内容は検索テストで使う文字列、学習状態はテスト観点ごとに指定する。
     */
    private LearningExpression save(int id, String prompt, String answer, String meaning, String note,
            LearningStage stage, int totalWrongCount, LocalDate nextReviewDate, LocalDate lastReviewDate) {
        LearningExpression e = new LearningExpression(prompt, answer, meaning, note);
        e.setExpressionId(id);
        e.setLearningStage(stage);
        e.setTotalWrongCount(totalWrongCount);
        e.setNextReviewDate(nextReviewDate);
        e.setLastReviewDate(lastReviewDate);
        e.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 10, 1, 9, 0, 0));
        // saveAndFlush: 直後のクエリ実行前に確実にDBへINSERTさせる
        return repository.saveAndFlush(e);
    }

    /** 学習状態のみを指定するテストデータ(復習対象判定用)。 */
    private LearningExpression saveForReview(int id, LearningStage stage, int totalWrongCount,
            LocalDate nextReviewDate, LocalDate lastReviewDate) {
        return save(id, "例文" + id, "表現" + id, "意味" + id, null, stage, totalWrongCount, nextReviewDate, lastReviewDate);
    }

    /** 学習内容のみを指定するテストデータ(キーワード検索用)。学習状態は登録直後と同じ。 */
    private LearningExpression saveForSearch(int id, String prompt, String answer, String meaning, String note) {
        return save(id, prompt, answer, meaning, note, LearningStage.NEW, 0, TODAY.plusDays(1), null);
    }

    private static List<Integer> idsOf(List<LearningExpression> list) {
        return list.stream().map(LearningExpression::getExpressionId).toList();
    }

    // =====================================================================
    // 基本操作(JpaRepository継承メソッド): Entityのマッピングと合わせて確認する
    // =====================================================================
    @Nested
    class 基本操作 {

        @Test
        void 保存した内容をfindByIdで取得できる() {
            save(1, "例文", "表現", "意味", "備考", LearningStage.DAILY, 2, TODAY, TODAY.minusDays(1));

            LearningExpression found = repository.findById(1).orElseThrow();

            assertThat(found.getPromptText()).isEqualTo("例文");
            assertThat(found.getNote()).isEqualTo("備考");
            assertThat(found.getLearningStage()).isEqualTo(LearningStage.DAILY);
            assertThat(found.getTotalWrongCount()).isEqualTo(2);
            assertThat(found.getNextReviewDate()).isEqualTo(TODAY);
            assertThat(found.getLastReviewDate()).isEqualTo(TODAY.minusDays(1));
            assertThat(found.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0, 0));
        }

        @Test
        void 存在しないIDはfindByIdで空になる() {
            assertThat(repository.findById(999)).isEmpty();
        }

        @Test
        void countは全登録件数を返す_MASTEREDも含む() {
            saveForReview(1, LearningStage.NEW, 0, TODAY.plusDays(1), null);
            saveForReview(2, LearningStage.MASTERED, 0, null, TODAY.minusDays(1));

            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        void deleteで物理削除される() {
            LearningExpression e = saveForReview(1, LearningStage.NEW, 0, TODAY.plusDays(1), null);

            repository.delete(e);
            repository.flush();

            assertThat(repository.findById(1)).isEmpty();
            assertThat(repository.count()).isZero();
        }
    }

    // =====================================================================
    // findAllExpressionIdsOrderByAsc (BIZ-02)
    // =====================================================================
    @Nested
    class 表現ID一覧取得 {

        @Test
        void 登録0件の場合は空リスト() {
            assertThat(repository.findAllExpressionIdsOrderByAsc()).isEmpty();
        }

        @Test
        void 登録順に関係なくID昇順で返す_欠番もそのまま() {
            // 設計書の例「既存IDが 1, 3, 5」の状態を、あえて順不同で登録する
            saveForReview(5, LearningStage.NEW, 0, TODAY.plusDays(1), null);
            saveForReview(1, LearningStage.NEW, 0, TODAY.plusDays(1), null);
            saveForReview(3, LearningStage.NEW, 0, TODAY.plusDays(1), null);

            assertThat(repository.findAllExpressionIdsOrderByAsc()).containsExactly(1, 3, 5);
        }
    }

    // =====================================================================
    // findTodayReviewTargets / countTodayReviewTargets (BIZ-03 / BIZ-07)
    // =====================================================================
    @Nested
    class 当日復習対象 {

        @Test
        void 対象0件の場合は空リストと件数0() {
            saveForReview(1, LearningStage.NEW, 0, TODAY.plusDays(1), null); // 登録当日(翌日が初回)

            assertThat(repository.findTodayReviewTargets(TODAY)).isEmpty();
            assertThat(repository.countTodayReviewTargets(TODAY)).isZero();
        }

        @Test
        void 抽出条件_対象と対象外を正しく判定する() {
            // ---- 対象となるデータ ----
            saveForReview(1, LearningStage.DAILY, 0, TODAY, null);                       // 予定日 = 当日(境界値)、未復習
            saveForReview(2, LearningStage.WEEKLY, 0, TODAY.minusDays(3), TODAY.minusDays(10)); // 期限切れの未実施分、最終復習は過去
            saveForReview(3, LearningStage.NEW, 0, TODAY.minusDays(1), null);            // NEW も対象
            saveForReview(4, LearningStage.MONTHLY, 0, LocalDate.of(2025, 12, 31), LocalDate.of(2025, 11, 30)); // 年またぎの過去日
            // ---- 対象外となるデータ ----
            saveForReview(11, LearningStage.DAILY, 0, TODAY.plusDays(1), null);          // 予定日が翌日(境界値)
            saveForReview(12, LearningStage.DAILY, 0, TODAY.minusDays(1), TODAY);        // 当日すでに採点済み
            saveForReview(13, LearningStage.MASTERED, 0, TODAY.minusDays(1), null);      // MASTERED は除外(予定日が過去でも)
            saveForReview(14, LearningStage.MASTERED, 0, null, TODAY.minusDays(1));      // MASTERED(予定日NULL)
            saveForReview(15, LearningStage.DAILY, 0, null, null);                       // 予定日NULL

            assertThat(idsOf(repository.findTodayReviewTargets(TODAY))).containsExactlyInAnyOrder(1, 2, 3, 4);
        }

        @Test
        void 並び順_累計誤答回数の降順_同数は表現IDの昇順() {
            saveForReview(1, LearningStage.DAILY, 1, TODAY, null);
            saveForReview(2, LearningStage.DAILY, 5, TODAY, null);
            saveForReview(3, LearningStage.DAILY, 1, TODAY, null);
            saveForReview(4, LearningStage.DAILY, 0, TODAY, null);
            saveForReview(5, LearningStage.DAILY, 5, TODAY, null);

            assertThat(idsOf(repository.findTodayReviewTargets(TODAY))).containsExactly(2, 5, 1, 3, 4);
        }

        @Test
        void 件数取得は一覧取得と同じ条件で数える() {
            // 抽出条件テストと同じデータ構成(対象4件・対象外5件)
            saveForReview(1, LearningStage.DAILY, 0, TODAY, null);
            saveForReview(2, LearningStage.WEEKLY, 0, TODAY.minusDays(3), TODAY.minusDays(10));
            saveForReview(3, LearningStage.NEW, 0, TODAY.minusDays(1), null);
            saveForReview(4, LearningStage.MONTHLY, 0, LocalDate.of(2025, 12, 31), LocalDate.of(2025, 11, 30));
            saveForReview(11, LearningStage.DAILY, 0, TODAY.plusDays(1), null);
            saveForReview(12, LearningStage.DAILY, 0, TODAY.minusDays(1), TODAY);
            saveForReview(13, LearningStage.MASTERED, 0, TODAY.minusDays(1), null);
            saveForReview(14, LearningStage.MASTERED, 0, null, TODAY.minusDays(1));
            saveForReview(15, LearningStage.DAILY, 0, null, null);

            long count = repository.countTodayReviewTargets(TODAY);

            assertThat(count).isEqualTo(4);
            assertThat(count).isEqualTo(repository.findTodayReviewTargets(TODAY).size());
        }
    }

    // =====================================================================
    // searchByKeyword (BIZ-05 キーワード検索)
    // =====================================================================
    @Nested
    class キーワード検索 {

        @Test
        void 三項目のいずれかに部分一致すれば対象_備考は対象外_ID昇順() {
            saveForSearch(4, "其他", "その他", "意味", "キーワード");         // 備考のみ一致 → 対象外
            saveForSearch(3, "其他", "その他", "～キーワード～の意味", null); // 意味に一致
            saveForSearch(1, "含キーワード的例文", "その他", "意味", null);   // 例文に一致
            saveForSearch(2, "其他", "前キーワード後", "意味", null);         // 日本語表現に一致(中間一致)
            saveForSearch(5, "其他", "その他", "意味", null);                 // どれにも一致しない

            assertThat(idsOf(repository.searchByKeyword("キーワード"))).containsExactly(1, 2, 3);
        }

        @Test
        void 該当なしの場合は空リスト() {
            saveForSearch(1, "例文", "表現", "意味", null);

            assertThat(repository.searchByKeyword("存在しない語")).isEmpty();
        }

        @Test
        void パーセント記号はワイルドカードではなく通常文字として扱う() {
            saveForSearch(1, "例文", "100%正しい", "意味", null);
            saveForSearch(2, "例文", "1000円", "意味", null); // エスケープしないと "100%" が "100…" に一致してしまう

            assertThat(idsOf(repository.searchByKeyword("100%"))).containsExactly(1);
        }

        @Test
        void アンダースコアはワイルドカードではなく通常文字として扱う() {
            saveForSearch(1, "例文", "a_b", "意味", null);
            saveForSearch(2, "例文", "axb", "意味", null); // エスケープしないと "_" が任意の1文字として一致してしまう

            assertThat(idsOf(repository.searchByKeyword("a_b"))).containsExactly(1);
        }

        @Test
        void エスケープ文字の円記号自体も通常文字として扱う() {
            saveForSearch(1, "例文", "C:\\data", "意味", null);
            saveForSearch(2, "例文", "C:data", "意味", null);

            assertThat(idsOf(repository.searchByKeyword("C:\\data"))).containsExactly(1);
            assertThat(idsOf(repository.searchByKeyword("\\"))).containsExactly(1);
        }
    }
}
