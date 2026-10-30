package com.zhou.japaneselearning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.exception.ExpressionNotFoundException;
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * LearningExpressionService の単体テスト(BIZ-01 表現登録 / BIZ-02 表現ID採番 / BIZ-05～08)。
 *
 * <p>Repositoryは Mockito のモック(偽物)に置き換え、DBやSpringを起動せずにServiceの業務ロジックだけを検証する。
 * 「Repositoryがこの値を返したら、Serviceはどう振る舞うか」「Repositoryに何を渡したか」を確認する。</p>
 *
 * <p>現在日時は固定した Clock を渡し、実行日に依存せず「翌日」等を検証できるようにしている。</p>
 */
@ExtendWith(MockitoExtension.class)
class LearningExpressionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    /** 基準日時: 2026-10-30 09:30:15.123(秒未満の切り捨ても検証するため、ミリ秒を含める) */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 9, 30, 15, 123_000_000);

    @Mock
    private LearningExpressionRepository repository;

    private LearningExpressionService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
    }

    /** 指定した日時を「現在」とするServiceを生成する。 */
    private LearningExpressionService serviceAt(LocalDateTime now) {
        return new LearningExpressionService(repository, Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
    }

    /** save() は受け取ったEntityをそのまま返すよう、モックの振る舞いを定義する。 */
    private void saveReturnsArgument() {
        when(repository.save(any(LearningExpression.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** save() に渡されたEntityを取り出す。 */
    private LearningExpression capturedSavedEntity() {
        ArgumentCaptor<LearningExpression> captor = ArgumentCaptor.forClass(LearningExpression.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    // BIZ-02 表現ID採番
    // =====================================================================
    @Nested
    class 表現ID採番 {

        @Test
        void 登録0件の場合は1() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of());

            assertThat(service.findNextExpressionId()).isEqualTo(1);
        }

        @Test
        void 欠番がある場合は最小の未使用ID_1_3_5なら2() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of(1, 3, 5));

            assertThat(service.findNextExpressionId()).isEqualTo(2);
        }

        @Test
        void 欠番がない場合は最大ID加算1_1_2_3なら4() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of(1, 2, 3));

            assertThat(service.findNextExpressionId()).isEqualTo(4);
        }

        @Test
        void ID1が欠番の場合_既存2と3なら1() {
            // ID 1 が削除済みのケース
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of(2, 3));

            assertThat(service.findNextExpressionId()).isEqualTo(1);
        }

        @Test
        void 設計書の例_1_3_5から順に登録すると2_4_6の順に採番される() {
            // 詳細設計書 BIZ-02「既存IDが 1, 3, 5 の場合、次回採番は2。その後4、その後6となる。」
            when(repository.findAllExpressionIdsOrderByAsc())
                    .thenReturn(List.of(1, 3, 5))
                    .thenReturn(List.of(1, 2, 3, 5))
                    .thenReturn(List.of(1, 2, 3, 4, 5));

            assertThat(service.findNextExpressionId()).isEqualTo(2);
            assertThat(service.findNextExpressionId()).isEqualTo(4);
            assertThat(service.findNextExpressionId()).isEqualTo(6);
        }

        @Test
        void 採番結果は既存IDのいずれとも重複しない() {
            List<Integer> existingIds = List.of(1, 2, 4, 5, 7, 8, 9);
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(existingIds);

            int nextId = service.findNextExpressionId();

            assertThat(nextId).isEqualTo(3).isPositive();
            assertThat(existingIds).doesNotContain(nextId);
        }
    }

    // =====================================================================
    // BIZ-01 表現登録
    // =====================================================================
    @Nested
    class 表現登録_正常系 {

        @Test
        void 採番したIDと設計書どおりの初期状態で登録される() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of(1, 3, 5));
            saveReturnsArgument();

            service.register("例文", "表現", "意味", "備考");

            LearningExpression saved = capturedSavedEntity();
            // 入力内容
            assertThat(saved.getPromptText()).isEqualTo("例文");
            assertThat(saved.getAnswerText()).isEqualTo("表現");
            assertThat(saved.getExpressionMeaning()).isEqualTo("意味");
            assertThat(saved.getNote()).isEqualTo("備考");
            // BIZ-02 で採番したID
            assertThat(saved.getExpressionId()).isEqualTo(2);
            // 初期学習状態(BIZ-01 処理内容 3～6)
            assertThat(saved.getLearningStage()).isEqualTo(LearningStage.NEW);
            assertThat(saved.getConsecutiveCorrectCount()).isZero();
            assertThat(saved.getTotalCorrectCount()).isZero();
            assertThat(saved.getTotalWrongCount()).isZero();
            assertThat(saved.getNextReviewDate()).isEqualTo(LocalDate.of(2026, 10, 31)); // 登録日の翌日
            assertThat(saved.getLastReviewDate()).isNull();
            // 登録日時・更新日時は現在日時(秒未満は切り捨て)
            LocalDateTime expectedNow = LocalDateTime.of(2026, 10, 30, 9, 30, 15);
            assertThat(saved.getCreatedAt()).isEqualTo(expectedNow);
            assertThat(saved.getUpdatedAt()).isEqualTo(expectedNow);
        }

        @Test
        void 返却値から表現ID_学習段階_次回復習予定日を取得できる() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of());
            saveReturnsArgument();

            LearningExpression result = service.register("例文", "表現", "意味", null);

            // BIZ-01 出力: expressionId / learningStage / nextReviewDate
            assertThat(result.getExpressionId()).isEqualTo(1);
            assertThat(result.getLearningStage()).isEqualTo(LearningStage.NEW);
            assertThat(result.getNextReviewDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        }

        @Test
        void 備考は任意_nullのまま登録される() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of());
            saveReturnsArgument();

            service.register("例文", "表現", "意味", null);

            assertThat(capturedSavedEntity().getNote()).isNull();
        }

        @Test
        void 月末の登録では次回復習予定日は翌月1日() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of());
            saveReturnsArgument();

            serviceAt(LocalDateTime.of(2027, 2, 28, 12, 0)).register("例文", "表現", "意味", null);

            assertThat(capturedSavedEntity().getNextReviewDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        }

        @Test
        void 年末の登録では次回復習予定日は翌年1月1日() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of());
            saveReturnsArgument();

            serviceAt(LocalDateTime.of(2026, 12, 31, 23, 59, 59)).register("例文", "表現", "意味", null);

            LearningExpression saved = capturedSavedEntity();
            assertThat(saved.getNextReviewDate()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        }
    }

    @Nested
    class 表現登録_入力チェック {

        /** null / 空文字 / 半角スペース / 全角スペース / タブ・改行のみ はすべて未入力扱い。 */
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "　　", "\t\n"})
        void 中国語例文が未入力または空白のみの場合はエラー_DBへアクセスしない(String invalid) {
            assertThatThrownBy(() -> service.register(invalid, "表現", "意味", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("promptText");

            // 採番(既存ID取得)も登録も行われないこと
            verifyNoInteractions(repository);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "　　", "\t\n"})
        void 日本語表現が未入力または空白のみの場合はエラー_DBへアクセスしない(String invalid) {
            assertThatThrownBy(() -> service.register("例文", invalid, "意味", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("answerText");

            verifyNoInteractions(repository);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "　　", "\t\n"})
        void 表現及び中国語意味が未入力または空白のみの場合はエラー_DBへアクセスしない(String invalid) {
            assertThatThrownBy(() -> service.register("例文", "表現", invalid, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expressionMeaning");

            verifyNoInteractions(repository);
        }

        @Test
        void 備考が空白のみでもエラーにならない() {
            when(repository.findAllExpressionIdsOrderByAsc()).thenReturn(List.of());
            saveReturnsArgument();

            service.register("例文", "表現", "意味", "   ");

            verify(repository).save(any(LearningExpression.class));
        }
    }

    // =====================================================================
    // BIZ-05～08 共通のテストデータ
    // =====================================================================

    /** 既存データの登録日時・更新日時。 */
    private static final LocalDateTime EXISTING_DATETIME = LocalDateTime.of(2026, 9, 1, 8, 0, 0);

    /**
     * 学習が進んだ状態の既存データを作成し、findById(id) で返るようにする。
     * 編集処理で学習状態が変更されていないことを検出できるよう、各項目を初期値とは異なる値にしている。
     */
    private LearningExpression givenExisting(int id) {
        LearningExpression e = new LearningExpression("旧例文", "旧表現", "旧意味", "旧備考");
        e.setExpressionId(id);
        e.setLearningStage(LearningStage.WEEKLY);
        e.setConsecutiveCorrectCount(3);
        e.setTotalCorrectCount(12);
        e.setTotalWrongCount(4);
        e.setNextReviewDate(LocalDate.of(2026, 11, 2));
        e.setLastReviewDate(LocalDate.of(2026, 10, 26));
        e.setCreatedAt(EXISTING_DATETIME);
        e.setUpdatedAt(EXISTING_DATETIME);
        when(repository.findById(id)).thenReturn(Optional.of(e));
        return e;
    }

    /** 学習状態・履歴・登録日時・表現IDが givenExisting の値から変化していないことを確認する。 */
    private static void assertLearningProgressUnchanged(LearningExpression e, int id) {
        assertThat(e.getExpressionId()).isEqualTo(id);
        assertThat(e.getLearningStage()).isEqualTo(LearningStage.WEEKLY);
        assertThat(e.getConsecutiveCorrectCount()).isEqualTo(3);
        assertThat(e.getTotalCorrectCount()).isEqualTo(12);
        assertThat(e.getTotalWrongCount()).isEqualTo(4);
        assertThat(e.getNextReviewDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(e.getLastReviewDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(e.getCreatedAt()).isEqualTo(EXISTING_DATETIME);
    }

    // =====================================================================
    // BIZ-05 表現検索(ID検索)
    // =====================================================================
    @Nested
    class ID検索 {

        @Test
        void 存在するIDの場合は該当する1件を返す() {
            LearningExpression existing = givenExisting(7);

            assertThat(service.findById(7)).isSameAs(existing);
        }

        @Test
        void IDの下限値1は検索できる_境界値() {
            LearningExpression existing = givenExisting(1);

            assertThat(service.findById(1)).isSameAs(existing);
        }

        @Test
        void 存在しないIDの場合は対象なし例外() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(999))
                    .isInstanceOf(ExpressionNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void IDが1未満の場合は入力不正_DBへアクセスしない_境界値(int invalidId) {
            assertThatThrownBy(() -> service.findById(invalidId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expressionId");

            verifyNoInteractions(repository);
        }
    }

    // =====================================================================
    // BIZ-05 表現検索(キーワード検索)
    // =====================================================================
    @Nested
    class キーワード検索 {

        @Test
        void Repositoryの検索結果を順序どおり返す() {
            LearningExpression first = givenSearchResult(1);
            LearningExpression second = givenSearchResult(3);
            when(repository.searchByKeyword("表現")).thenReturn(List.of(first, second));

            assertThat(service.searchByKeyword("表現")).containsExactly(first, second);
        }

        @Test
        void 該当0件の場合は空リストを返す() {
            when(repository.searchByKeyword("該当なし")).thenReturn(List.of());

            assertThat(service.searchByKeyword("該当なし")).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"100%", "a_b", "C:\\data", " 前後に空白 "})
        void キーワードは加工せずそのままRepositoryに渡す(String keyword) {
            // % _ \ のエスケープはRepositoryが担当するため、Serviceでは変換しない。前後の空白も除去しない
            when(repository.searchByKeyword(keyword)).thenReturn(List.of());

            service.searchByKeyword(keyword);

            verify(repository).searchByKeyword(keyword);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "　　", "\t\n"})
        void 未入力または空白のみのキーワードは入力不正_検索しない(String invalid) {
            assertThatThrownBy(() -> service.searchByKeyword(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keyword");

            verifyNoInteractions(repository);
        }

        private LearningExpression givenSearchResult(int id) {
            LearningExpression e = new LearningExpression("例文" + id, "表現" + id, "意味" + id, null);
            e.setExpressionId(id);
            return e;
        }
    }

    // =====================================================================
    // BIZ-06 表現編集
    // =====================================================================
    @Nested
    class 表現編集 {

        @Test
        void 学習内容4項目と更新日時のみ更新し_学習進捗と登録日時は変更しない() {
            LearningExpression e = givenExisting(7);
            saveReturnsArgument();

            LearningExpression result = service.update(7, "新例文", "新表現", "新意味", "新備考");

            // 更新可能項目
            assertThat(result.getPromptText()).isEqualTo("新例文");
            assertThat(result.getAnswerText()).isEqualTo("新表現");
            assertThat(result.getExpressionMeaning()).isEqualTo("新意味");
            assertThat(result.getNote()).isEqualTo("新備考");
            assertThat(result.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 9, 30, 15)); // 秒未満切り捨て
            // 更新不可項目(表現ID・学習段階・正誤回数・復習日・登録日時)
            assertLearningProgressUnchanged(result, 7);
            verify(repository).save(e);
        }

        @Test
        void 備考にnullを指定すると備考を削除できる() {
            LearningExpression e = givenExisting(7);

            service.update(7, "新例文", "新表現", "新意味", null);

            assertThat(e.getNote()).isNull();
        }

        @Test
        void 存在しないIDの場合は対象なし例外_保存しない() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(999, "新例文", "新表現", "新意味", null))
                    .isInstanceOf(ExpressionNotFoundException.class);

            verify(repository, never()).save(any());
        }

        @Test
        void IDが1未満の場合は入力不正_DBへアクセスしない_境界値() {
            assertThatThrownBy(() -> service.update(0, "新例文", "新表現", "新意味", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expressionId");

            verifyNoInteractions(repository);
        }

        /**
         * 必須3項目のいずれかが空白のみの場合。空白のバリエーション(null・空文字・全角スペース等)は
         * 同じ判定処理を使う BIZ-01 のテストで網羅しているため、ここでは各項目1パターンとする。
         */
        @ParameterizedTest
        @ValueSource(strings = {"promptText", "answerText", "expressionMeaning"})
        void 必須項目が空白のみの場合は入力不正_何も変更しない(String blankField) {
            LearningExpression e = givenExisting(7);
            String blank = "　";

            assertThatThrownBy(() -> service.update(7,
                    blankField.equals("promptText") ? blank : "新例文",
                    blankField.equals("answerText") ? blank : "新表現",
                    blankField.equals("expressionMeaning") ? blank : "新意味",
                    "新備考"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(blankField);

            // 学習内容・更新日時を含め、どの項目も変更されず、保存も行われない
            assertThat(e.getPromptText()).isEqualTo("旧例文");
            assertThat(e.getAnswerText()).isEqualTo("旧表現");
            assertThat(e.getExpressionMeaning()).isEqualTo("旧意味");
            assertThat(e.getNote()).isEqualTo("旧備考");
            assertThat(e.getUpdatedAt()).isEqualTo(EXISTING_DATETIME);
            assertLearningProgressUnchanged(e, 7);
            verify(repository, never()).save(any());
        }

        @Test
        void 存在しないIDかつ必須項目不正の場合は_設計書の処理順どおり対象なしを優先する() {
            // 詳細設計書 BIZ-06 処理内容: 1. 対象確認 → 2. 必須項目チェック
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(999, "", "", "", null))
                    .isInstanceOf(ExpressionNotFoundException.class);
        }
    }

    // =====================================================================
    // BIZ-07 学習状況集計
    // =====================================================================
    @Nested
    class 学習状況集計 {

        @Test
        void 登録表現数と_システム当日を基準日とした今日の復習件数を返す() {
            when(repository.count()).thenReturn(128L);
            when(repository.countTodayReviewTargets(LocalDate.of(2026, 10, 30))).thenReturn(12L);

            LearningStatistics statistics = service.getLearningStatistics();

            assertThat(statistics.registeredExpressionCount()).isEqualTo(128L);
            assertThat(statistics.todayReviewCount()).isEqualTo(12L);
        }

        @Test
        void 登録0件の場合はどちらも0() {
            when(repository.count()).thenReturn(0L);
            when(repository.countTodayReviewTargets(LocalDate.of(2026, 10, 30))).thenReturn(0L);

            assertThat(service.getLearningStatistics()).isEqualTo(new LearningStatistics(0L, 0L));
        }
    }

    // =====================================================================
    // BIZ-08 表現削除
    // =====================================================================
    @Nested
    class 表現削除 {

        @Test
        void 対象を物理削除し_正誤回数等の更新は行わない() {
            LearningExpression e = givenExisting(7);

            service.delete(7);

            verify(repository).delete(e);
            // 削除は復習結果として扱わないため、更新(save)は行わず、学習履歴も変更しない
            verify(repository, never()).save(any());
            assertLearningProgressUnchanged(e, 7);
        }

        @Test
        void 存在しないIDの場合は対象なし例外_削除しない() {
            when(repository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(999))
                    .isInstanceOf(ExpressionNotFoundException.class)
                    .hasMessageContaining("999");

            verify(repository, never()).delete(any());
        }

        @Test
        void IDが1未満の場合は入力不正_DBへアクセスしない_境界値() {
            assertThatThrownBy(() -> service.delete(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expressionId");

            verifyNoInteractions(repository);
        }
    }
}
