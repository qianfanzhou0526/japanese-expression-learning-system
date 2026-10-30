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
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * LearningExpressionService の単体テスト(BIZ-01 表現登録 / BIZ-02 表現ID採番)。
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
}
