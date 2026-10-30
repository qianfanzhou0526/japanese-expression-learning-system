package com.zhou.japaneselearning.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.exception.ExpressionNotFoundException;
import com.zhou.japaneselearning.exception.ReviewConflictException;
import com.zhou.japaneselearning.service.ReviewResult;
import com.zhou.japaneselearning.service.ReviewService;

/**
 * ReviewController(API-03 今日の復習対象取得 / API-04 復習結果更新)のテスト。
 * Serviceはモックとし、Web層(パス・JSON変換・入力チェック・ステータス・エラー変換)のみを検証する。
 */
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    /** APIに公開してはいけない内部項目(登録日時等)にも値を入れたEntity。 */
    private static LearningExpression entity(int id, String note) {
        LearningExpression e = new LearningExpression("例文" + id, "表現" + id, "意味" + id, note);
        e.setExpressionId(id);
        e.setLearningStage(LearningStage.DAILY);
        e.setConsecutiveCorrectCount(2);
        e.setTotalCorrectCount(5);
        e.setTotalWrongCount(3);
        e.setNextReviewDate(LocalDate.of(2026, 10, 31));
        e.setLastReviewDate(LocalDate.of(2026, 10, 30));
        e.setCreatedAt(LocalDateTime.of(2026, 9, 1, 8, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 9, 0, 0));
        return e;
    }

    // =====================================================================
    // API-03 今日の復習対象取得
    // =====================================================================
    @Nested
    class API03_今日の復習対象取得 {

        @Test
        void 正常系_200でServiceの優先順のまま5項目を返す_内部項目は含まない() throws Exception {
            when(reviewService.findTodayReviewTargets()).thenReturn(List.of(entity(5, "備考"), entity(2, null)));

            mockMvc.perform(get("/api/reviews/today"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", aMapWithSize(1)))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].expressionId").value(5))
                    .andExpect(jsonPath("$.items[0].promptText").value("例文5"))
                    .andExpect(jsonPath("$.items[0].answerText").value("表現5"))
                    .andExpect(jsonPath("$.items[0].expressionMeaning").value("意味5"))
                    .andExpect(jsonPath("$.items[0].note").value("備考"))
                    .andExpect(jsonPath("$.items[0]", aMapWithSize(5)))
                    .andExpect(jsonPath("$.items[1].expressionId").value(2))
                    // 備考が未入力の場合は null(API設計書「string / null」)
                    .andExpect(jsonPath("$.items[1].note").hasJsonPath())
                    .andExpect(jsonPath("$.items[1].note").value(nullValue()));
        }

        @Test
        void 対象0件の場合も200で空のitemsを返す() throws Exception {
            when(reviewService.findTodayReviewTargets()).thenReturn(List.of());

            mockMvc.perform(get("/api/reviews/today"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }
    }

    // =====================================================================
    // API-04 復習結果更新
    // =====================================================================
    @Nested
    class API04_復習結果更新 {

        private ResultActions postResult(String expressionId, String json) throws Exception {
            return mockMvc.perform(post("/api/reviews/" + expressionId + "/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }

        @ParameterizedTest
        @EnumSource(ReviewResult.class)
        void 正常系_200で更新後の学習状態7項目を返し_Serviceにresultが渡される(ReviewResult result) throws Exception {
            when(reviewService.recordReviewResult(10, result)).thenReturn(entity(10, "備考"));

            postResult("10", "{\"result\":\"" + result.name() + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expressionId").value(10))
                    .andExpect(jsonPath("$.learningStage").value("DAILY"))
                    .andExpect(jsonPath("$.consecutiveCorrectCount").value(2))
                    .andExpect(jsonPath("$.totalCorrectCount").value(5))
                    .andExpect(jsonPath("$.totalWrongCount").value(3))
                    .andExpect(jsonPath("$.nextReviewDate").value("2026-10-31"))
                    .andExpect(jsonPath("$.lastReviewDate").value("2026-10-30"))
                    // API設計書 3.4 の7項目のみ。学習内容・登録日時・更新日時は含まない
                    .andExpect(jsonPath("$", aMapWithSize(7)))
                    .andExpect(jsonPath("$.promptText").doesNotExist())
                    .andExpect(jsonPath("$.updatedAt").doesNotExist());

            verify(reviewService).recordReviewResult(10, result);
        }

        @Test
        void MASTEREDへ遷移した場合はnextReviewDateがnullとして返る() throws Exception {
            LearningExpression mastered = entity(10, null);
            mastered.setLearningStage(LearningStage.MASTERED);
            mastered.setConsecutiveCorrectCount(0);
            mastered.setNextReviewDate(null);
            when(reviewService.recordReviewResult(10, ReviewResult.CORRECT)).thenReturn(mastered);

            postResult("10", "{\"result\":\"CORRECT\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.learningStage").value("MASTERED"))
                    // 項目自体は存在し、値が null であること
                    .andExpect(jsonPath("$.nextReviewDate").hasJsonPath())
                    .andExpect(jsonPath("$.nextReviewDate").value(nullValue()))
                    .andExpect(jsonPath("$", aMapWithSize(7)));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"result\":\"WRONG\"}", "{\"result\":\"correct\"}", "{\"result\":\"\"}",
                // 数値・数字文字列はenumの定義順の番号として解釈されないこと(spring.jackson.datatype.enum の設定)
                "{\"result\":0}", "{\"result\":1}", "{\"result\":\"1\"}"})
        void resultが許容値以外の場合は400_INVALID_REQUEST(String json) throws Exception {
            postResult("10", json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verify(reviewService, never()).recordReviewResult(anyInt(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"{}", "{\"result\":null}"})
        void resultが未指定の場合は400_INVALID_REQUEST(String json) throws Exception {
            postResult("10", json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("result は必須です。"));

            verify(reviewService, never()).recordReviewResult(anyInt(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "abc", "1.5", "99999999999"})
        void expressionIdが1未満または整数として解釈できない場合は400_INVALID_EXPRESSION_ID(String invalidId) throws Exception {
            postResult(invalidId, "{\"result\":\"CORRECT\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXPRESSION_ID"));

            verify(reviewService, never()).recordReviewResult(anyInt(), any());
        }

        @Test
        void expressionIdの下限値1は受け付ける_境界値() throws Exception {
            when(reviewService.recordReviewResult(1, ReviewResult.CORRECT)).thenReturn(entity(1, null));

            postResult("1", "{\"result\":\"CORRECT\"}").andExpect(status().isOk());
        }

        @Test
        void expressionIdとresultの両方が不正な場合はINVALID_EXPRESSION_IDを優先する() throws Exception {
            postResult("0", "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXPRESSION_ID"));
        }

        @Test
        void 対象が存在しない場合は404_EXPRESSION_NOT_FOUND() throws Exception {
            when(reviewService.recordReviewResult(999, ReviewResult.CORRECT))
                    .thenThrow(new ExpressionNotFoundException(999));

            postResult("999", "{\"result\":\"CORRECT\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("EXPRESSION_NOT_FOUND"))
                    .andExpect(jsonPath("$", aMapWithSize(2)));
        }

        @ParameterizedTest
        @CsvSource({
                "ALREADY_REVIEWED_TODAY, ALREADY_REVIEWED_TODAY",
                "REVIEW_NOT_DUE,         REVIEW_NOT_DUE",
                "REVIEW_NOT_ALLOWED,     REVIEW_NOT_ALLOWED",
        })
        void 採点できない状態の場合は409_理由ごとのエラーコード(ReviewConflictException.Reason reason, String expectedCode)
                throws Exception {
            when(reviewService.recordReviewResult(10, ReviewResult.CORRECT))
                    .thenThrow(new ReviewConflictException(reason, "採点できません。"));

            postResult("10", "{\"result\":\"CORRECT\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(expectedCode))
                    .andExpect(jsonPath("$.message").value("採点できません。"));
        }
    }
}
