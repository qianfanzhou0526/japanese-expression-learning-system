package com.zhou.japaneselearning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.exception.ReviewConflictException;
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * BIZ-04 復習結果更新 の結合テスト(Service → Repository → テスト用SQLite DB)。
 *
 * <p>同一表現を同日に2回採点した場合に、1回目は正しく更新され、
 * 2回目は ReviewConflictException で拒否されてDBが変化しないことを確認する。</p>
 *
 * <p>本テストはテストメソッド全体を @Transactional にしていない。
 * テスト全体を1トランザクションにすると、1回目と2回目の採点が同じトランザクション・同じキャッシュ
 * (永続化コンテキスト)を共有してしまい、「API呼び出しごとに別トランザクションで実行される」実際の動作と異なるためである。
 * ここでは Service の @Transactional により採点ごとに実際にコミット/ロールバックさせ、
 * 代わりにテストの前後でテスト用DBのデータを削除して、他のテストへ影響を残さないようにしている。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewServiceIntegrationTest {

    private static final int ID = 1;

    /** 準備データの登録日時・更新日時(採点前の値)。 */
    private static final LocalDateTime INITIAL_DATETIME = LocalDateTime.of(2026, 9, 1, 8, 0, 0);

    /** DBの1行をEntityを介さずに直接読み出すSQL(JPAのキャッシュの影響を受けない)。 */
    private static final String SELECT_ROW = "SELECT * FROM learning_expression WHERE expression_id = ?";

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private LearningExpressionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /** テスト開始前: 前のテストのデータが残っていても影響を受けないよう、テスト用DBを空にする。 */
    @BeforeEach
    void cleanUpBefore() {
        deleteAllInTestDb();
    }

    /** テスト終了後: 他のテストクラスへデータを残さないよう、テスト用DBを空にする(失敗時も実行される)。 */
    @AfterEach
    void cleanUpAfter() {
        deleteAllInTestDb();
    }

    private void deleteAllInTestDb() {
        // テスト専用DBであることを確認してから削除する(本番DBのデータを誤って消さないための安全確認)
        assertThat(datasourceUrl).isEqualTo("jdbc:sqlite:target/japanese_expression_learning_test.db");
        repository.deleteAll();
    }

    private Map<String, Object> selectRow() {
        return jdbcTemplate.queryForMap(SELECT_ROW, ID);
    }

    @Test
    void 同一表現を同日に2回採点すると2回目は競合となり_DBは1回目の結果のまま変化しない() {
        // ---- 1. 当日が復習予定日であるデータを準備する(DAILY・連続正解2・累計正解3・累計誤答1・前日に採点済み) ----
        LocalDate today = LocalDate.now();
        LearningExpression e = new LearningExpression("例文", "表現", "意味", "備考");
        e.setExpressionId(ID);
        e.setLearningStage(LearningStage.DAILY);
        e.setConsecutiveCorrectCount(2);
        e.setTotalCorrectCount(3);
        e.setTotalWrongCount(1);
        e.setNextReviewDate(today);
        e.setLastReviewDate(today.minusDays(1));
        e.setCreatedAt(INITIAL_DATETIME);
        e.setUpdatedAt(INITIAL_DATETIME);
        repository.save(e);

        // ---- 2. 1回目の採点(正解)を実行し、正常に更新されること ----
        LocalDateTime before = LocalDateTime.now().withNano(0);
        LearningExpression result = reviewService.recordReviewResult(ID, ReviewResult.CORRECT);
        LocalDateTime after = LocalDateTime.now();

        assertThat(result.getLearningStage()).isEqualTo(LearningStage.DAILY);
        assertThat(result.getConsecutiveCorrectCount()).isEqualTo(3);

        // ---- 3. DBから再取得し、各項目が設計どおり更新されていること(DAILY・正解: 詳細設計書 5.2) ----
        Map<String, Object> rowAfterFirst = selectRow();
        LocalDate lastReviewDate = LocalDate.parse((String) rowAfterFirst.get("last_review_date"));
        LocalDateTime updatedAt = LocalDateTime.parse(((String) rowAfterFirst.get("updated_at")).replace(' ', 'T'));

        assertThat(rowAfterFirst.get("learning_stage")).isEqualTo("DAILY");                          // 5回未満のためDAILYのまま
        assertThat(((Number) rowAfterFirst.get("consecutive_correct_count")).intValue()).isEqualTo(3); // 2 → 3
        assertThat(((Number) rowAfterFirst.get("total_correct_count")).intValue()).isEqualTo(4);       // 3 → 4
        assertThat(((Number) rowAfterFirst.get("total_wrong_count")).intValue()).isEqualTo(1);         // 変化なし
        // 最終復習日 = 採点日(システム当日)。採点の実行前後の日付の範囲内であること
        assertThat(lastReviewDate).isBetween(before.toLocalDate(), after.toLocalDate());
        // 次回復習予定日 = 採点日の翌日(YYYY-MM-DD形式)
        assertThat(rowAfterFirst.get("next_review_date")).isEqualTo(lastReviewDate.plusDays(1).toString());
        // 更新日時 = 採点時の現在日時(YYYY-MM-DD HH:MM:SS形式)
        assertThat(updatedAt).isBetween(before, after);
        assertThat((String) rowAfterFirst.get("updated_at")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        // 登録日時・学習内容は変更されない
        assertThat(rowAfterFirst.get("created_at")).isEqualTo("2026-09-01 08:00:00");
        assertThat(rowAfterFirst.get("prompt_text")).isEqualTo("例文");
        assertThat(rowAfterFirst.get("note")).isEqualTo("備考");

        // ---- 4・5. 同じ日に同じ表現へ2回目の採点を実行すると ReviewConflictException となること ----
        // 2回目は INCORRECT とする。誤って適用されれば段階・連続正解・累計誤答・予定日がすべて変わるため、
        // 「更新されていないこと」をより確実に検出できる
        assertThatThrownBy(() -> reviewService.recordReviewResult(ID, ReviewResult.INCORRECT))
                .isInstanceOf(ReviewConflictException.class)
                .hasMessageContaining("すでに採点済み");

        // ---- 6. 2回目の採点によってDBのデータが追加更新されていないこと(全列が1回目の後と同一) ----
        assertThat(selectRow()).isEqualTo(rowAfterFirst);
        assertThat(repository.count()).isEqualTo(1);
    }
}
