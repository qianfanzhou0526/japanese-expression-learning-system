package com.zhou.japaneselearning.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.service.LearningExpressionService;

/**
 * ExpressionController(API-02 表現登録)のテスト。
 *
 * <p>Serviceはモックとし、「HTTPリクエスト → DTO変換・入力チェック → Service呼び出し → レスポンスJSON」の
 * Web層の動作のみを検証する。登録処理そのもの(採番・初期状態)はServiceのテストで検証済み。</p>
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
}
