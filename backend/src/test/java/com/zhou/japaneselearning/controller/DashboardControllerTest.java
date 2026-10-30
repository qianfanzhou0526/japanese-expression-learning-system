package com.zhou.japaneselearning.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.zhou.japaneselearning.service.LearningExpressionService;
import com.zhou.japaneselearning.service.LearningStatistics;

/**
 * DashboardController(API-01)のテスト。
 *
 * <p>@WebMvcTest はController・例外ハンドラ・JSON変換などWeb層だけを起動するテスト用アノテーション。
 * Service・Repository・DBは起動しないため、Serviceは @MockitoBean でモックに置き換える。
 * MockMvc を使うと、実際にサーバーを起動せずにHTTPリクエストを送ったときの動作を検証できる。</p>
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LearningExpressionService learningExpressionService;

    @Test
    void API01_正常系_200で今日の復習件数と登録表現数の2項目のみ返す() throws Exception {
        when(learningExpressionService.getLearningStatistics()).thenReturn(new LearningStatistics(128L, 12L));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.todayReviewCount").value(12))
                .andExpect(jsonPath("$.registeredExpressionCount").value(128))
                // API設計書 3.1 の2項目以外を含まないこと
                .andExpect(jsonPath("$", aMapWithSize(2)));

        verify(learningExpressionService).getLearningStatistics();
    }

    @Test
    void 想定外の例外は500となり_エラー形式codeとmessageで返す() throws Exception {
        when(learningExpressionService.getLearningStatistics()).thenThrow(new RuntimeException("DB接続失敗などの内部情報"));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("サーバー内部エラーが発生しました。"))
                // 内部の例外メッセージをレスポンスに含めない
                .andExpect(jsonPath("$", aMapWithSize(2)));
    }

    @Test
    void 未対応のHTTPメソッドは500ではなく405のままエラー形式で返す() throws Exception {
        // 共通例外ハンドラの「想定外の例外 → 500」が、Spring標準の例外まで500に変えてしまわないことの確認
        mockMvc.perform(post("/api/dashboard"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
