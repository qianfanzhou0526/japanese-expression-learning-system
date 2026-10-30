package com.zhou.japaneselearning.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 画面(Vue)のURLを index.html へ転送するController(V1通常利用時の画面配信用)。
 *
 * <p>フロントエンドは Vue Router の History モード(/review 等の通常のURL形式)で画面を切り替える。
 * 画面内の遷移はブラウザ側だけで完結するが、/review 等をブラウザで直接開いたり再読み込みしたりすると、
 * そのURLへのリクエストがSpring Bootに届く。Spring Boot にはそのパスのファイルが無いため、
 * 画面のURLへのGETを index.html(Vueアプリの入口)へ転送し、表示する画面の判定はVue Routerに任せる。</p>
 *
 * <ul>
 *   <li>対象はV1の画面URLのみを明示する。/(S01)は Spring Boot の静的ファイル配信が index.html を返すため対象外</li>
 *   <li>/api/** や、存在しない任意のURLは転送しない(従来どおりAPIまたは404として処理される)</li>
 *   <li>画面を追加した場合は、Vue Router のルートと合わせてここにURLを追加する</li>
 * </ul>
 *
 * <p>@RestController ではなく @Controller を使用している。戻り値の "forward:..." を
 * レスポンスボディの文字列ではなく、サーバー内部での転送先として扱わせるため。</p>
 */
@Controller
public class SpaForwardController {

    /** S02 新規登録 / S03 復習 / S04 表現検索 の画面URL。 */
    @GetMapping({"/register", "/review", "/expressions"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
