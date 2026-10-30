package com.zhou.japaneselearning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * BIZ-01 表現登録 / BIZ-02 表現ID採番 の結合テスト。
 *
 * <p>単体テスト(LearningExpressionServiceTest)ではRepositoryをモックにしているため、
 * 「実際のDBで採番・登録した結果、既存データが上書きされないか」までは確認できない。
 * 本テストでは Service → Repository → テスト用SQLite DB を実際に通して確認する。</p>
 *
 * <ul>
 *   <li>@SpringBootTest: アプリ全体と同じ構成でSpringを起動し、本物のService・Repositoryを使う</li>
 *   <li>@ActiveProfiles("test"): application-test.properties により target/ 配下のテスト専用DBを使う</li>
 *   <li>@Transactional: テスト終了時にロールバックし、テスト用DBにもデータを残さない
 *       (Serviceの @Transactional はこのテストのトランザクションに参加する)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningExpressionServiceIntegrationTest {

    /** 既存データ1, 3, 5 をDBから直接読み出すSQL。Entityを介さず、DBに保存された値そのものを比較するために使う。 */
    private static final String SELECT_EXISTING_ROWS =
            "SELECT * FROM learning_expression WHERE expression_id IN (1, 3, 5) ORDER BY expression_id";

    @Autowired
    private LearningExpressionService service;

    @Autowired
    private LearningExpressionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /** 既存データを1件登録する。学習状態は登録直後とは異なる値にして、上書きされたら検知できるようにする。 */
    private void saveExisting(int id, LearningStage stage, int consecutive, int totalCorrect, int totalWrong,
            LocalDate nextReviewDate, LocalDate lastReviewDate) {
        LearningExpression e = new LearningExpression("既存例文" + id, "既存表現" + id, "既存意味" + id, "既存備考" + id);
        e.setExpressionId(id);
        e.setLearningStage(stage);
        e.setConsecutiveCorrectCount(consecutive);
        e.setTotalCorrectCount(totalCorrect);
        e.setTotalWrongCount(totalWrong);
        e.setNextReviewDate(nextReviewDate);
        e.setLastReviewDate(lastReviewDate);
        e.setCreatedAt(LocalDateTime.of(2026, 1, id, 10, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 2, id, 11, 0, 0));
        repository.saveAndFlush(e);
    }

    @Test
    void 既存ID_1_3_5の状態で登録すると2が採番され_既存データは変更されない() {
        // ---- 前提: テスト専用DBを使用していること(本番DB data/ ではない) ----
        assertThat(datasourceUrl).isEqualTo("jdbc:sqlite:target/japanese_expression_learning_test.db");
        assertThat(repository.count()).isZero();

        // ---- 準備: 既存データ 1, 3, 5 を登録し、DB上の値を記録しておく ----
        saveExisting(1, LearningStage.DAILY, 2, 3, 1, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 29));
        saveExisting(3, LearningStage.WEEKLY, 4, 10, 2, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 10, 25));
        saveExisting(5, LearningStage.MASTERED, 0, 20, 5, null, LocalDate.of(2026, 10, 20));
        List<Map<String, Object>> existingRowsBefore = jdbcTemplate.queryForList(SELECT_EXISTING_ROWS);
        assertThat(existingRowsBefore).hasSize(3);

        // ---- 実行: Serviceの表現登録(BIZ-01、内部でBIZ-02の採番を行う) ----
        // 実行前後の日時を記録し、登録日時がその範囲内であることを確認する(実行日に依存しない検証)
        LocalDateTime before = LocalDateTime.now().withNano(0);
        LearningExpression result = service.register("新規例文", "新規表現", "新規意味", "新規備考");
        LocalDateTime after = LocalDateTime.now();

        // Hibernateはトランザクション終了時までINSERTを遅らせることがあるため、ここでDBへ書き込ませる。
        // 以降の検証はJPAのキャッシュを経由しない JdbcTemplate でDBの値を直接読み出すため、この書き込みが必要。
        repository.flush();

        // ---- 検証1: 新規表現に ID 2 が採番されること ----
        assertThat(result.getExpressionId()).isEqualTo(2);

        // ---- 検証2: 登録後に ID 1, 2, 3, 5 が存在すること ----
        assertThat(repository.findAllExpressionIdsOrderByAsc()).containsExactly(1, 2, 3, 5);

        // ---- 検証3: 既存の ID 1, 3, 5 のデータが上書き・変更されていないこと(DBの全列を登録前と比較) ----
        List<Map<String, Object>> existingRowsAfter = jdbcTemplate.queryForList(SELECT_EXISTING_ROWS);
        assertThat(existingRowsAfter).isEqualTo(existingRowsBefore);

        // ---- 検証4: 新規登録した ID 2 の内容および初期値がBIZ-01の設計どおりであること(DBの値を直接確認) ----
        Map<String, Object> newRow = jdbcTemplate.queryForMap(
                "SELECT * FROM learning_expression WHERE expression_id = 2");
        LocalDateTime createdAt = LocalDateTime.parse(((String) newRow.get("created_at")).replace(' ', 'T'));

        // 入力内容
        assertThat(newRow.get("prompt_text")).isEqualTo("新規例文");
        assertThat(newRow.get("answer_text")).isEqualTo("新規表現");
        assertThat(newRow.get("expression_meaning")).isEqualTo("新規意味");
        assertThat(newRow.get("note")).isEqualTo("新規備考");
        // 初期学習状態(BIZ-01 処理内容 3～6)
        assertThat(newRow.get("learning_stage")).isEqualTo("NEW");
        assertThat(((Number) newRow.get("consecutive_correct_count")).intValue()).isZero();
        assertThat(((Number) newRow.get("total_correct_count")).intValue()).isZero();
        assertThat(((Number) newRow.get("total_wrong_count")).intValue()).isZero();
        assertThat(newRow.get("last_review_date")).isNull();
        // 登録日時: 実行前後の範囲内、設計書の形式 YYYY-MM-DD HH:MM:SS(19桁)
        assertThat(createdAt).isBetween(before, after);
        assertThat((String) newRow.get("created_at")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        assertThat(newRow.get("updated_at")).isEqualTo(newRow.get("created_at"));
        // 次回復習予定日: 登録日の翌日、設計書の形式 YYYY-MM-DD
        assertThat(newRow.get("next_review_date"))
                .isEqualTo(createdAt.toLocalDate().plusDays(1).toString());
    }
}
