package com.zhou.japaneselearning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.exception.ExpressionNotFoundException;
import com.zhou.japaneselearning.exception.ReviewConflictException;
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * ReviewService の単体テスト(BIZ-03 復習対象取得 / BIZ-04 復習結果更新)。
 *
 * <p>Repositoryはモックに置き換え、現在日時は固定した Clock で指定する。</p>
 *
 * <p>MockitoExtension は既定で「定義したのに使われなかったモック定義(stub)」をエラーにする(strict stubs)。
 * そのため、モックの振る舞いは各テストで実際に必要なものだけを定義している。
 * なお save() は振る舞いを定義しない場合、戻り値として null を返す。状態遷移のテストでは
 * 戻り値ではなく、Serviceが変更した Entity 自体を検証するため、save() の定義は不要としている。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final int ID = 10;

    /** 基準日時: 2026-10-30 10:15:30.500(秒未満の切り捨ても検証する)。 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 15, 30, 500_000_000);
    private static final LocalDate TODAY = NOW.toLocalDate();

    /** 既存データの登録日時・更新日時(採点で変化しないこと/変化することの確認用)。 */
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 8, 0, 0);

    @Mock
    private LearningExpressionRepository repository;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
    }

    private ReviewService serviceAt(LocalDateTime now) {
        return new ReviewService(repository, Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
    }

    /**
     * 採点対象のテストデータを作成し、findById(ID) で返るようにする。
     * 既定では「予定日 = 当日、最終復習日 = 前日、累計正解3・累計誤答2」とする。
     */
    private LearningExpression givenExpression(LearningStage stage, int consecutive, LocalDate nextReviewDate,
            LocalDate lastReviewDate) {
        LearningExpression e = new LearningExpression("例文", "表現", "意味", "備考");
        e.setExpressionId(ID);
        e.setLearningStage(stage);
        e.setConsecutiveCorrectCount(consecutive);
        e.setTotalCorrectCount(3);
        e.setTotalWrongCount(2);
        e.setNextReviewDate(nextReviewDate);
        e.setLastReviewDate(lastReviewDate);
        e.setCreatedAt(CREATED_AT);
        e.setUpdatedAt(CREATED_AT);
        when(repository.findById(ID)).thenReturn(Optional.of(e));
        return e;
    }

    /** 予定日 = 当日、最終復習日 = 前日 の標準的な採点対象。 */
    private LearningExpression givenExpression(LearningStage stage, int consecutive) {
        return givenExpression(stage, consecutive, TODAY, TODAY.minusDays(1));
    }

    /** 学習状態の主要項目をまとめて検証する。 */
    private static void assertState(LearningExpression e, LearningStage stage, int consecutive,
            int totalCorrect, int totalWrong, LocalDate nextReviewDate) {
        assertThat(e.getLearningStage()).as("learningStage").isEqualTo(stage);
        assertThat(e.getConsecutiveCorrectCount()).as("consecutiveCorrectCount").isEqualTo(consecutive);
        assertThat(e.getTotalCorrectCount()).as("totalCorrectCount").isEqualTo(totalCorrect);
        assertThat(e.getTotalWrongCount()).as("totalWrongCount").isEqualTo(totalWrong);
        assertThat(e.getNextReviewDate()).as("nextReviewDate").isEqualTo(nextReviewDate);
    }

    // =====================================================================
    // BIZ-03 復習対象取得
    // =====================================================================
    @Nested
    class 復習対象取得 {

        @Test
        void システム当日を基準日としてRepositoryに渡し_結果を順序どおり返す() {
            LearningExpression a = new LearningExpression("a", "a", "a", null);
            LearningExpression b = new LearningExpression("b", "b", "b", null);
            when(repository.findTodayReviewTargets(TODAY)).thenReturn(List.of(a, b));

            List<LearningExpression> result = service.findTodayReviewTargets();

            assertThat(result).containsExactly(a, b);
            verify(repository).findTodayReviewTargets(TODAY);
        }

        @Test
        void 対象0件の場合は空リストを返し_状態更新は行わない() {
            when(repository.findTodayReviewTargets(TODAY)).thenReturn(List.of());

            assertThat(service.findTodayReviewTargets()).isEmpty();
            verify(repository, never()).save(any());
        }
    }

    // =====================================================================
    // BIZ-04 状態遷移(詳細設計書 5.2 の各行)
    // =====================================================================
    @Nested
    class 状態遷移_正解 {

        @Test
        void NEW_正解でDAILYへ_連続正解1_翌日() {
            LearningExpression e = givenExpression(LearningStage.NEW, 0);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.DAILY, 1, 4, 2, TODAY.plusDays(1));
        }

        @Test
        void DAILY_正解_5回未満はDAILYのまま_連続正解加算_翌日() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 3);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.DAILY, 4, 4, 2, TODAY.plusDays(1));
        }

        @Test
        void DAILY_正解_5回到達でWEEKLYへ_連続正解0_7日後() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 4);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.WEEKLY, 0, 4, 2, TODAY.plusDays(7));
        }

        @Test
        void WEEKLY_正解_5回未満はWEEKLYのまま_7日後() {
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 0);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.WEEKLY, 1, 4, 2, TODAY.plusDays(7));
        }

        @Test
        void WEEKLY_正解_5回到達でMONTHLYへ_連続正解0_翌暦月同日() {
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 4);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.MONTHLY, 0, 4, 2, LocalDate.of(2026, 11, 30));
        }

        @Test
        void MONTHLY_正解_5回未満はMONTHLYのまま_翌暦月同日() {
            LearningExpression e = givenExpression(LearningStage.MONTHLY, 2);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.MONTHLY, 3, 4, 2, LocalDate.of(2026, 11, 30));
        }

        @Test
        void MONTHLY_正解_5回到達でMASTEREDへ_連続正解0_予定日NULL() {
            LearningExpression e = givenExpression(LearningStage.MONTHLY, 4);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.MASTERED, 0, 4, 2, null);
        }
    }

    @Nested
    class 状態遷移_不正解 {

        @Test
        void NEW_不正解でDAILYへ_連続正解0_翌日() {
            LearningExpression e = givenExpression(LearningStage.NEW, 0);

            service.recordReviewResult(ID, ReviewResult.INCORRECT);

            assertState(e, LearningStage.DAILY, 0, 3, 3, TODAY.plusDays(1));
        }

        @Test
        void DAILY_不正解でDAILYのまま_連続正解0にリセット_翌日() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 4);

            service.recordReviewResult(ID, ReviewResult.INCORRECT);

            assertState(e, LearningStage.DAILY, 0, 3, 3, TODAY.plusDays(1));
        }

        @Test
        void WEEKLY_不正解でDAILYへ戻る_連続正解0_翌日() {
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 3);

            service.recordReviewResult(ID, ReviewResult.INCORRECT);

            assertState(e, LearningStage.DAILY, 0, 3, 3, TODAY.plusDays(1));
        }

        @Test
        void MONTHLY_不正解でDAILYへ戻る_連続正解0_翌日() {
            LearningExpression e = givenExpression(LearningStage.MONTHLY, 4);

            service.recordReviewResult(ID, ReviewResult.INCORRECT);

            assertState(e, LearningStage.DAILY, 0, 3, 3, TODAY.plusDays(1));
        }
    }

    @Nested
    class 段階を通した遷移 {

        @Test
        void NEWの正解を含め5回連続正解でWEEKLYへ昇級する() {
            // 確認事項①: NEWでの正解(連続1)の後、DAILYでさらに4回正解した時点でWEEKLYへ
            LearningExpression e = givenExpression(LearningStage.NEW, 0, TODAY, null);

            LocalDate day = TODAY;
            for (int i = 1; i <= 5; i++) {
                serviceAt(day.atTime(9, 0)).recordReviewResult(ID, ReviewResult.CORRECT);
                if (i < 5) {
                    assertThat(e.getLearningStage()).isEqualTo(LearningStage.DAILY);
                    assertThat(e.getConsecutiveCorrectCount()).isEqualTo(i);
                }
                day = e.getNextReviewDate(); // 次回予定日に次の採点を行う
            }

            assertState(e, LearningStage.WEEKLY, 0, 3 + 5, 2, TODAY.plusDays(4).plusDays(7));
        }
    }

    // =====================================================================
    // BIZ-04 共通更新・更新しない項目
    // =====================================================================
    @Nested
    class 共通更新 {

        @ParameterizedTest
        @EnumSource(ReviewResult.class)
        void 最終復習日は当日_更新日時は現在日時_秒未満切り捨て(ReviewResult result) {
            LearningExpression e = givenExpression(LearningStage.DAILY, 1);

            service.recordReviewResult(ID, result);

            assertThat(e.getLastReviewDate()).isEqualTo(TODAY);
            assertThat(e.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 10, 15, 30));
        }

        @ParameterizedTest
        @EnumSource(ReviewResult.class)
        void 学習内容と登録日時と表現IDは変更しない(ReviewResult result) {
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 2);

            service.recordReviewResult(ID, result);

            assertThat(e.getExpressionId()).isEqualTo(ID);
            assertThat(e.getPromptText()).isEqualTo("例文");
            assertThat(e.getAnswerText()).isEqualTo("表現");
            assertThat(e.getExpressionMeaning()).isEqualTo("意味");
            assertThat(e.getNote()).isEqualTo("備考");
            assertThat(e.getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        void 更新後のEntityを保存して返却する() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 0);
            // 戻り値を検証するテストのみ、save() が受け取ったEntityをそのまま返すよう定義する
            when(repository.save(any(LearningExpression.class))).thenAnswer(invocation -> invocation.getArgument(0));

            LearningExpression result = service.recordReviewResult(ID, ReviewResult.CORRECT);

            verify(repository).save(e);
            assertThat(result).isSameAs(e);
        }

        @Test
        void 期限切れの表現は採点日を起点に次回予定日を計算する() {
            // 予定日を10日過ぎたWEEKLYの表現。元の予定日+7日ではなく、採点日+7日となる
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 1, TODAY.minusDays(10), TODAY.minusDays(17));

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getNextReviewDate()).isEqualTo(TODAY.plusDays(7));
        }
    }

    // =====================================================================
    // BIZ-04 次回復習予定日の境界値(月末・うるう年・年末)
    // =====================================================================
    @Nested
    class 次回復習予定日_境界値 {

        @ParameterizedTest(name = "{0} に採点 → {1}")
        @CsvSource({
                "2027-01-31, 2027-02-28", // 翌月に31日がない(平年)
                "2028-01-31, 2028-02-29", // 翌月に31日がない(うるう年)
                "2027-01-29, 2027-02-28", // 平年は2月29日がない
                "2028-01-29, 2028-02-29", // うるう年は2月29日がある
                "2026-03-31, 2026-04-30", // 31日 → 30日の月
                "2026-12-31, 2027-01-31", // 年をまたぐ
                "2026-10-30, 2026-11-30", // 通常
        })
        void MONTHLYは翌暦月の同日_存在しない日は月末(LocalDate reviewDate, LocalDate expected) {
            LearningExpression e = givenExpression(LearningStage.MONTHLY, 0, reviewDate, reviewDate.minusMonths(1));

            serviceAt(reviewDate.atTime(12, 0)).recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getNextReviewDate()).isEqualTo(expected);
        }

        @Test
        void MONTHLYへの昇級時も月末を正しく扱う_1月31日に昇級すると2月28日() {
            LocalDate reviewDate = LocalDate.of(2027, 1, 31);
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 4, reviewDate, reviewDate.minusDays(7));

            serviceAt(reviewDate.atTime(12, 0)).recordReviewResult(ID, ReviewResult.CORRECT);

            assertState(e, LearningStage.MONTHLY, 0, 4, 2, LocalDate.of(2027, 2, 28));
        }

        @ParameterizedTest(name = "{0} に採点 → {1}")
        @CsvSource({
                "2026-12-28, 2027-01-04", // 年をまたぐ
                "2027-02-25, 2027-03-04", // 平年の2月末をまたぐ
                "2028-02-25, 2028-03-03", // うるう年の2月末をまたぐ
        })
        void WEEKLYは7日後_月末年末をまたぐ(LocalDate reviewDate, LocalDate expected) {
            LearningExpression e = givenExpression(LearningStage.WEEKLY, 0, reviewDate, reviewDate.minusDays(7));

            serviceAt(reviewDate.atTime(12, 0)).recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getNextReviewDate()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} に採点 → {1}")
        @CsvSource({
                "2026-12-31, 2027-01-01", // 年をまたぐ
                "2027-02-28, 2027-03-01", // 平年の2月末
                "2028-02-28, 2028-02-29", // うるう年の2月末
        })
        void DAILYは翌日_月末年末をまたぐ(LocalDate reviewDate, LocalDate expected) {
            LearningExpression e = givenExpression(LearningStage.DAILY, 0, reviewDate, reviewDate.minusDays(1));

            serviceAt(reviewDate.atTime(12, 0)).recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getNextReviewDate()).isEqualTo(expected);
        }

        @Test
        void 日付変更直前に採点しても最終復習日と予定日は同じ日付を基準にする() {
            LocalDateTime justBeforeMidnight = LocalDateTime.of(2026, 10, 30, 23, 59, 59, 999_000_000);
            LearningExpression e = givenExpression(LearningStage.DAILY, 0, LocalDate.of(2026, 10, 30), null);

            serviceAt(justBeforeMidnight).recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getLastReviewDate()).isEqualTo(LocalDate.of(2026, 10, 30));
            assertThat(e.getNextReviewDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(e.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 23, 59, 59));
        }
    }

    // =====================================================================
    // BIZ-04 採点を受け付けないケース(データは一切更新しない)
    // =====================================================================
    @Nested
    class 採点拒否 {

        /** 採点拒否時に、Entityのどの項目も変化していないことを確認する。 */
        private void assertUnchanged(LearningExpression e, LearningStage stage, int consecutive,
                LocalDate nextReviewDate, LocalDate lastReviewDate) {
            assertState(e, stage, consecutive, 3, 2, nextReviewDate);
            assertThat(e.getLastReviewDate()).isEqualTo(lastReviewDate);
            assertThat(e.getUpdatedAt()).isEqualTo(CREATED_AT);
            verify(repository, never()).save(any());
        }

        /**
         * ReviewConflictException が発生し、その拒否理由が期待どおりであることを確認する。
         * API層はメッセージ文言ではなく拒否理由でエラーコードを決めるため、理由を検証する。
         */
        private void assertConflict(ThrowingCallable call, ReviewConflictException.Reason expectedReason) {
            assertThatThrownBy(call)
                    .isInstanceOfSatisfying(ReviewConflictException.class,
                            ex -> assertThat(ex.getReason()).isEqualTo(expectedReason));
        }

        @ParameterizedTest
        @EnumSource(ReviewResult.class)
        void 当日すでに採点済みの場合は競合として拒否(ReviewResult result) {
            LearningExpression e = givenExpression(LearningStage.DAILY, 2, TODAY, TODAY);

            assertConflict(() -> service.recordReviewResult(ID, result), ReviewConflictException.Reason.ALREADY_REVIEWED_TODAY);

            assertUnchanged(e, LearningStage.DAILY, 2, TODAY, TODAY);
        }

        @Test
        void 前日に採点済みの場合は採点できる_境界値() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 2, TODAY, TODAY.minusDays(1));

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getLastReviewDate()).isEqualTo(TODAY);
        }

        @ParameterizedTest
        @EnumSource(ReviewResult.class)
        void MASTEREDは競合として拒否_最終復習日も正誤回数も更新しない(ReviewResult result) {
            LearningExpression e = givenExpression(LearningStage.MASTERED, 0, null, TODAY.minusDays(30));

            assertConflict(() -> service.recordReviewResult(ID, result), ReviewConflictException.Reason.REVIEW_NOT_ALLOWED);

            assertUnchanged(e, LearningStage.MASTERED, 0, null, TODAY.minusDays(30));
        }

        @ParameterizedTest
        @EnumSource(ReviewResult.class)
        void 予定日が翌日の表現は競合として拒否_境界値(ReviewResult result) {
            LearningExpression e = givenExpression(LearningStage.NEW, 0, TODAY.plusDays(1), null);

            assertConflict(() -> service.recordReviewResult(ID, result), ReviewConflictException.Reason.REVIEW_NOT_DUE);

            assertUnchanged(e, LearningStage.NEW, 0, TODAY.plusDays(1), null);
        }

        @Test
        void 予定日が当日の表現は採点できる_境界値() {
            LearningExpression e = givenExpression(LearningStage.NEW, 0, TODAY, null);

            service.recordReviewResult(ID, ReviewResult.CORRECT);

            assertThat(e.getLearningStage()).isEqualTo(LearningStage.DAILY);
        }

        @Test
        void MASTERED以外で予定日がNULLのデータは採点対象外として拒否() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 0, null, null);

            assertConflict(() -> service.recordReviewResult(ID, ReviewResult.CORRECT), ReviewConflictException.Reason.REVIEW_NOT_ALLOWED);

            assertUnchanged(e, LearningStage.DAILY, 0, null, null);
        }

        @Test
        void 存在しない表現IDの場合は対象なし例外() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordReviewResult(999, ReviewResult.CORRECT))
                    .isInstanceOf(ExpressionNotFoundException.class)
                    .hasMessageContaining("999");

            verify(repository, never()).save(any());
        }

        @Test
        void resultがnullの場合は入力不正例外() {
            LearningExpression e = givenExpression(LearningStage.DAILY, 1);

            assertThatThrownBy(() -> service.recordReviewResult(ID, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("result");

            assertUnchanged(e, LearningStage.DAILY, 1, TODAY, TODAY.minusDays(1));
        }
    }
}
