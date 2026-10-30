package com.zhou.japaneselearning.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SpaForwardController のテスト。
 * MockMvc は forward 先の処理までは実行しないため、「どこへ転送したか(forwardedUrl)」で検証する。
 */
@WebMvcTest(SpaForwardController.class)
class SpaForwardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/register", "/review", "/expressions"})
    void 画面URLはindex_htmlへ転送される(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/unknown", "/review/extra", "/api/unknown"})
    void 画面URL以外は転送されず404となる(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }
}
