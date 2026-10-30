package com.zhou.japaneselearning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zhou.japaneselearning.dto.DashboardResponse;
import com.zhou.japaneselearning.service.LearningExpressionService;
import com.zhou.japaneselearning.service.LearningStatistics;

/**
 * ホーム画面(S01)向けのAPIを提供するController。
 *
 * <p>Controllerの責務は「HTTPリクエストを受け取り、Serviceを呼び出し、結果をHTTPレスポンスに変換する」ことのみ。
 * 業務ロジックやDBアクセスは行わない(詳細設計書 11章)。</p>
 *
 * <p>@RestController を付けたクラスのメソッドの戻り値は、JSONに変換されてレスポンスボディになる。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final LearningExpressionService learningExpressionService;

    public DashboardController(LearningExpressionService learningExpressionService) {
        this.learningExpressionService = learningExpressionService;
    }

    /**
     * API-01 ホーム情報取得。今日の復習件数と登録表現数を返す(BIZ-07)。
     *
     * <p>戻り値をそのまま返した場合、HTTPステータスは 200 OK となる。</p>
     */
    @GetMapping
    public DashboardResponse getDashboard() {
        LearningStatistics statistics = learningExpressionService.getLearningStatistics();
        return new DashboardResponse(statistics.todayReviewCount(), statistics.registeredExpressionCount());
    }
}
