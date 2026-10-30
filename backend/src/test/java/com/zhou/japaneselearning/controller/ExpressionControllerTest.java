package com.zhou.japaneselearning.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
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
import com.zhou.japaneselearning.service.LearningExpressionService;

/**
 * ExpressionController(API-02 / API-05～08)のテスト。
 *
 * <p>Serviceはモックとし、「HTTPリクエスト → DTO変換・入力チェック → Service呼び出し → レスポンスJSON」の
 * Web層の動作のみを検証する。業務処理そのもの(採番・検索条件・編集不可項目の保護等)はServiceのテストで検証済み。</p>
 *
 * <p>API-02 のテストは「正常系」「入力不正」、API-05～08 のテストはAPIごとのクラスにまとめている。</p>
 */
@WebMvcTest(ExpressionController.class)
class ExpressionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LearningExpressionService learningExpressionService;

    private ResultActions postExpression(String json) throws Exception {
        return mockMvc.perform(post("/api/expressions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Serviceが返す登録後のEntity。APIに公開してはいけない内部項目にも値を入れておく。 */
    private static LearningExpression registeredEntity() {
        LearningExpression e = new LearningExpression("例文", "表現", "意味", "備考");
        e.setExpressionId(2);
        e.setLearningStage(LearningStage.NEW);
        e.setNextReviewDate(LocalDate.of(2026, 10, 31));
        e.setCreatedAt(LocalDateTime.of(2026, 10, 30, 9, 30, 15));
        e.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 9, 30, 15));
        return e;
    }

    @Nested
    class 正常系 {

        @Test
        void API02_201でexpressionId_learningStage_nextReviewDateの3項目のみ返す() throws Exception {
            when(learningExpressionService.register("例文", "表現", "意味", "備考")).thenReturn(registeredEntity());

            postExpression("""
                    {"promptText":"例文","answerText":"表現","expressionMeaning":"意味","note":"備考"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.expressionId").value(2))
                    .andExpect(jsonPath("$.learningStage").value("NEW"))
                    .andExpect(jsonPath("$.nextReviewDate").value("2026-10-31")) // YYYY-MM-DD形式の文字列
                    // API設計書 3.2 の3項目のみ。Entityの内部項目(学習内容・正誤回数・登録日時等)を含まない
                    .andExpect(jsonPath("$", aMapWithSize(3)))
                    .andExpect(jsonPath("$.promptText").doesNotExist())
                    .andExpect(jsonPath("$.consecutiveCorrectCount").doesNotExist())
                    .andExpect(jsonPath("$.totalWrongCount").doesNotExist())
                    .andExpect(jsonPath("$.lastReviewDate").doesNotExist())
                    .andExpect(jsonPath("$.createdAt").doesNotExist());
        }

        @Test
        void Serviceにリクエストの4項目がそのまま渡される() throws Exception {
            when(learningExpressionService.register("例文", "表現", "意味", "備考")).thenReturn(registeredEntity());

            postExpression("""
                    {"promptText":"例文","answerText":"表現","expressionMeaning":"意味","note":"備考"}
                    """)
                    .andExpect(status().isCreated());

            verify(learningExpressionService).register("例文", "表現", "意味", "備考");
        }

        @Test
        void 備考は任意_省略するとnullとしてServiceに渡される() throws Exception {
            when(learningExpressionService.register("例文", "表現", "意味", null)).thenReturn(registeredEntity());

            postExpression("""
                    {"promptText":"例文","answerText":"表現","expressionMeaning":"意味"}
                    """)
                    .andExpect(status().isCreated());

            verify(learningExpressionService).register("例文", "表現", "意味", null);
        }

        @Test
        void リクエストに含まれるexpressionIdや学習状態は受け取らずに無視する() throws Exception {
            // API設計書 3.2「expressionId は利用者から受け取らない」
            when(learningExpressionService.register("例文", "表現", "意味", null)).thenReturn(registeredEntity());

            postExpression("""
                    {"expressionId":99,"learningStage":"MASTERED","totalWrongCount":5,
                     "promptText":"例文","answerText":"表現","expressionMeaning":"意味"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.expressionId").value(2));

            verify(learningExpressionService).register("例文", "表現", "意味", null);
        }
    }

    @Nested
    class 入力不正 {

        @ParameterizedTest
        @ValueSource(strings = {"promptText", "answerText", "expressionMeaning"})
        void 必須項目が省略された場合は400_Serviceを呼ばない(String missingField) throws Exception {
            String json = """
                    {"promptText":"例文","answerText":"表現","expressionMeaning":"意味"}
                    """.replace("\"" + missingField + "\":", "\"unused\":");

            postExpression(json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString(missingField)))
                    .andExpect(jsonPath("$", aMapWithSize(2)));

            verify(learningExpressionService, never()).register(any(), any(), any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void 必須項目が空文字または半角空白のみの場合は400_Serviceを呼ばない(String blank) throws Exception {
            postExpression("""
                    {"promptText":"%s","answerText":"表現","expressionMeaning":"意味"}
                    """.formatted(blank))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("promptText")));

            verify(learningExpressionService, never()).register(any(), any(), any(), any());
        }

        @Test
        void 必須項目が複数不足している場合は全項目をメッセージに含める() throws Exception {
            postExpression("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(allOf(
                            containsString("promptText"),
                            containsString("answerText"),
                            containsString("expressionMeaning"))));
        }

        @Test
        void 全角空白のみの場合も400_Serviceを呼ばない() throws Exception {
            // Hibernate Validator の @NotBlank は String#isBlank で判定するため、全角スペース(U+3000)も空白とみなす。
            // Serviceの必須チェックと同じ判定範囲であり、DTOの段階で拒否される
            postExpression("""
                    {"promptText":"　","answerText":"表現","expressionMeaning":"意味"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("promptText")));

            verify(learningExpressionService, never()).register(any(), any(), any(), any());
        }

        @Test
        void Serviceが入力不正例外を送出した場合は400_エラー形式で返す() throws Exception {
            // DTOの入力チェックを通過した後に、Service側の業務ルールで不正と判定された場合の変換を確認する
            when(learningExpressionService.register("例文", "表現", "意味", null))
                    .thenThrow(new IllegalArgumentException("promptText は必須です。"));

            postExpression("""
                    {"promptText":"例文","answerText":"表現","expressionMeaning":"意味"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("promptText は必須です。"));
        }

        @Test
        void JSONとして解析できない場合は400() throws Exception {
            postExpression("{\"promptText\": ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("リクエストの形式が不正です。"));

            verify(learningExpressionService, never()).register(any(), any(), any(), any());
        }
    }

    // =====================================================================
    // API-05～08 共通
    // =====================================================================

    /** Serviceが返す既存の表現。APIに公開してはいけない学習状態等にも値を入れておく。 */
    private static LearningExpression existingEntity(int id, String note) {
        LearningExpression e = new LearningExpression("例文" + id, "表現" + id, "意味" + id, note);
        e.setExpressionId(id);
        e.setLearningStage(LearningStage.WEEKLY);
        e.setConsecutiveCorrectCount(3);
        e.setTotalCorrectCount(12);
        e.setTotalWrongCount(4);
        e.setNextReviewDate(LocalDate.of(2026, 11, 2));
        e.setLastReviewDate(LocalDate.of(2026, 10, 26));
        e.setCreatedAt(LocalDateTime.of(2026, 9, 1, 8, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 9, 0, 0));
        return e;
    }

    /** パスの expressionId として不正な値(1未満・整数として解釈できない値)。 */
    static final List<String> INVALID_IDS = List.of("0", "-1", "abc", "1.5", "99999999999");

    // =====================================================================
    // API-05 表現ID検索・詳細取得
    // =====================================================================
    @Nested
    class API05_表現ID検索 {

        @Test
        void 正常系_200で5項目のみ返す_学習状態等は含まない() throws Exception {
            when(learningExpressionService.findById(7)).thenReturn(existingEntity(7, "備考"));

            mockMvc.perform(get("/api/expressions/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expressionId").value(7))
                    .andExpect(jsonPath("$.promptText").value("例文7"))
                    .andExpect(jsonPath("$.answerText").value("表現7"))
                    .andExpect(jsonPath("$.expressionMeaning").value("意味7"))
                    .andExpect(jsonPath("$.note").value("備考"))
                    .andExpect(jsonPath("$", aMapWithSize(5)))
                    .andExpect(jsonPath("$.learningStage").doesNotExist())
                    .andExpect(jsonPath("$.totalWrongCount").doesNotExist())
                    .andExpect(jsonPath("$.createdAt").doesNotExist());
        }

        @Test
        void 備考が未入力の場合はnullで返す() throws Exception {
            when(learningExpressionService.findById(7)).thenReturn(existingEntity(7, null));

            mockMvc.perform(get("/api/expressions/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.note").hasJsonPath())
                    .andExpect(jsonPath("$.note").value(nullValue()));
        }

        @Test
        void 存在しない場合は404_EXPRESSION_NOT_FOUND() throws Exception {
            when(learningExpressionService.findById(999)).thenThrow(new ExpressionNotFoundException(999));

            mockMvc.perform(get("/api/expressions/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("EXPRESSION_NOT_FOUND"))
                    .andExpect(jsonPath("$", aMapWithSize(2)));
        }

        @ParameterizedTest
        @FieldSource("com.zhou.japaneselearning.controller.ExpressionControllerTest#INVALID_IDS")
        void expressionIdが不正な場合は400_INVALID_EXPRESSION_ID(String invalidId) throws Exception {
            mockMvc.perform(get("/api/expressions/" + invalidId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXPRESSION_ID"))
                    .andExpect(jsonPath("$.message").value("expressionId は1以上の正整数で指定してください。"));

            verify(learningExpressionService, never()).findById(anyInt());
        }
    }

    // =====================================================================
    // API-06 キーワード検索
    // =====================================================================
    @Nested
    class API06_キーワード検索 {

        @Test
        void 正常系_200でServiceの順序のまま3項目のみ返す() throws Exception {
            when(learningExpressionService.searchByKeyword("表現"))
                    .thenReturn(List.of(existingEntity(1, "備考"), existingEntity(3, null)));

            mockMvc.perform(get("/api/expressions/search").param("keyword", "表現"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", aMapWithSize(1)))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].expressionId").value(1))
                    .andExpect(jsonPath("$.items[0].answerText").value("表現1"))
                    .andExpect(jsonPath("$.items[0].expressionMeaning").value("意味1"))
                    .andExpect(jsonPath("$.items[1].expressionId").value(3))
                    // API設計書 3.6 の3項目のみ。中国語例文・備考・学習状態は返さない
                    .andExpect(jsonPath("$.items[0]", aMapWithSize(3)))
                    .andExpect(jsonPath("$.items[0].promptText").doesNotExist())
                    .andExpect(jsonPath("$.items[0].note").doesNotExist());
        }

        @Test
        void 該当0件の場合も200で空のitemsを返す() throws Exception {
            when(learningExpressionService.searchByKeyword("該当なし")).thenReturn(List.of());

            mockMvc.perform(get("/api/expressions/search").param("keyword", "該当なし"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }

        @Test
        void 記号を含むキーワードもそのままServiceに渡す() throws Exception {
            when(learningExpressionService.searchByKeyword("100%_\\")).thenReturn(List.of());

            mockMvc.perform(get("/api/expressions/search").param("keyword", "100%_\\"))
                    .andExpect(status().isOk());

            verify(learningExpressionService).searchByKeyword("100%_\\");
        }

        @Test
        void keyword未指定の場合は400_INVALID_REQUEST() throws Exception {
            mockMvc.perform(get("/api/expressions/search"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("keyword は必須です。"));

            verify(learningExpressionService, never()).searchByKeyword(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "　　"})
        void keywordが空文字または空白のみの場合は400_INVALID_REQUEST(String blank) throws Exception {
            mockMvc.perform(get("/api/expressions/search").param("keyword", blank))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("keyword は必須です。"));

            verify(learningExpressionService, never()).searchByKeyword(any());
        }
    }

    // =====================================================================
    // API-07 表現更新
    // =====================================================================
    @Nested
    class API07_表現更新 {

        private ResultActions putExpression(String expressionId, String json) throws Exception {
            return mockMvc.perform(put("/api/expressions/" + expressionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }

        private static final String VALID_BODY = """
                {"promptText":"新例文","answerText":"新表現","expressionMeaning":"新意味","note":"新備考"}
                """;

        @Test
        void 正常系_200でAPI05と同じ5項目を返し_Serviceにパスのidと4項目が渡される() throws Exception {
            LearningExpression updated = existingEntity(7, "新備考");
            when(learningExpressionService.update(7, "新例文", "新表現", "新意味", "新備考")).thenReturn(updated);

            putExpression("7", VALID_BODY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expressionId").value(7))
                    .andExpect(jsonPath("$.note").value("新備考"))
                    .andExpect(jsonPath("$", aMapWithSize(5)))
                    .andExpect(jsonPath("$.learningStage").doesNotExist())
                    .andExpect(jsonPath("$.updatedAt").doesNotExist());

            verify(learningExpressionService).update(7, "新例文", "新表現", "新意味", "新備考");
        }

        @Test
        void ボディの学習状態やexpressionIdは受け取らず_パスのidのみを使用する() throws Exception {
            // API設計書 3.7「learning_stage、各正誤回数、next_review_date、last_review_date は通常編集APIのRequestに含めない」
            when(learningExpressionService.update(7, "新例文", "新表現", "新意味", null)).thenReturn(existingEntity(7, null));

            putExpression("7", """
                    {"expressionId":99,"learningStage":"MASTERED","totalWrongCount":0,"nextReviewDate":"2099-01-01",
                     "promptText":"新例文","answerText":"新表現","expressionMeaning":"新意味"}
                    """)
                    .andExpect(status().isOk());

            verify(learningExpressionService).update(7, "新例文", "新表現", "新意味", null);
        }

        @ParameterizedTest
        @ValueSource(strings = {"promptText", "answerText", "expressionMeaning"})
        void 必須項目が空白のみの場合は400_INVALID_REQUEST(String blankField) throws Exception {
            String json = VALID_BODY.replaceFirst("(\"" + blankField + "\":)\"[^\"]*\"", "$1\"　\"");

            putExpression("7", json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString(blankField)));

            verify(learningExpressionService, never()).update(anyInt(), any(), any(), any(), any());
        }

        @Test
        void 存在しない場合は404_EXPRESSION_NOT_FOUND() throws Exception {
            when(learningExpressionService.update(999, "新例文", "新表現", "新意味", "新備考"))
                    .thenThrow(new ExpressionNotFoundException(999));

            putExpression("999", VALID_BODY)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("EXPRESSION_NOT_FOUND"));
        }

        @ParameterizedTest
        @FieldSource("com.zhou.japaneselearning.controller.ExpressionControllerTest#INVALID_IDS")
        void expressionIdが不正な場合は400_INVALID_EXPRESSION_ID(String invalidId) throws Exception {
            putExpression(invalidId, VALID_BODY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXPRESSION_ID"));

            verify(learningExpressionService, never()).update(anyInt(), any(), any(), any(), any());
        }

        @Test
        void expressionIdとボディの両方が不正な場合はINVALID_EXPRESSION_IDを優先する() throws Exception {
            putExpression("0", "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXPRESSION_ID"));
        }
    }

    // =====================================================================
    // API-08 表現削除
    // =====================================================================
    @Nested
    class API08_表現削除 {

        @Test
        void 正常系_204でレスポンスボディなし_Serviceにidが渡される() throws Exception {
            mockMvc.perform(delete("/api/expressions/7"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(learningExpressionService).delete(7);
        }

        @Test
        void 存在しない場合は404_EXPRESSION_NOT_FOUND() throws Exception {
            doThrow(new ExpressionNotFoundException(999)).when(learningExpressionService).delete(999);

            mockMvc.perform(delete("/api/expressions/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("EXPRESSION_NOT_FOUND"));
        }

        @ParameterizedTest
        @FieldSource("com.zhou.japaneselearning.controller.ExpressionControllerTest#INVALID_IDS")
        void expressionIdが不正な場合は400_INVALID_EXPRESSION_ID(String invalidId) throws Exception {
            mockMvc.perform(delete("/api/expressions/" + invalidId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EXPRESSION_ID"));

            verify(learningExpressionService, never()).delete(anyInt());
        }
    }
}
