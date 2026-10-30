package com.zhou.japaneselearning.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zhou.japaneselearning.entity.LearningExpression;
import com.zhou.japaneselearning.entity.LearningStage;
import com.zhou.japaneselearning.exception.ExpressionNotFoundException;
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * 学習表現に関する業務処理を担当するService。
 *
 * <p>Service層は「業務ルールの実装」と「トランザクション境界」を担う(詳細設計書 11章)。
 * 入力チェック・ID採番・初期学習状態の決定といった業務判断はここで行い、
 * DBの読み書きそのものはRepositoryへ委譲する。Controllerからは本クラスのメソッドを呼び出す想定。</p>
 *
 * <p>担当する業務処理(復習に関する BIZ-03 / BIZ-04 は {@link ReviewService} が担当する):</p>
 * <ul>
 *   <li>BIZ-01 表現登録 / BIZ-02 表現ID採番</li>
 *   <li>BIZ-05 表現検索(ID検索・キーワード検索)</li>
 *   <li>BIZ-06 表現編集</li>
 *   <li>BIZ-07 学習状況集計</li>
 *   <li>BIZ-08 表現削除</li>
 * </ul>
 */
@Service
public class LearningExpressionService {

    private final LearningExpressionRepository repository;

    /**
     * 「現在日時」の取得元。
     * LocalDate.now() を直接呼ぶと、テストで日付を固定できず「翌日」の検証が実行日に依存してしまう。
     * Clockを経由させることで、テストでは任意の日時を指定した Clock を渡せるようにしている。
     */
    private final Clock clock;

    /**
     * Springが使用するコンストラクタ。
     * Repositoryはコンストラクタ引数で受け取る(コンストラクタインジェクション)。
     * フィールドを final にでき、テストでもモックを渡しやすいため、Springで推奨される方式。
     * 現在日時はPCのシステム時計・タイムゾーンを使用する(V1はローカル単一端末での利用)。
     *
     * コンストラクタが2つあるため、Springにどちらを使うかを @Autowired で明示している。
     */
    @Autowired
    public LearningExpressionService(LearningExpressionRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /**
     * テスト用コンストラクタ。日時を固定した Clock を渡すために使用する。
     * 同一パッケージのテストクラスからのみ呼べるよう、アクセス修飾子を付けていない(パッケージプライベート)。
     */
    LearningExpressionService(LearningExpressionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * BIZ-01 表現登録。
     *
     * <p>入力された学習内容を検証し、表現IDを採番した上で、初期学習状態を設定して登録する。</p>
     *
     * <p>@Transactional により、採番(既存IDの取得)から登録完了までを1つのトランザクションで実行する
     * (詳細設計書 10章「採番から登録完了までを1業務処理として扱う」)。
     * 途中で例外が発生した場合はロールバックされ、中途半端なデータは残らない。</p>
     *
     * @param promptText        中国語例文(必須)
     * @param answerText        日本語表現(必須)
     * @param expressionMeaning 表現及び中国語意味(必須)
     * @param note              備考(任意。null可)
     * @return 登録したEntity。呼び出し元は expressionId / learningStage / nextReviewDate を返却に使用する
     * @throws IllegalArgumentException 必須項目が未入力・空白のみの場合
     */
    @Transactional
    public LearningExpression register(String promptText, String answerText, String expressionMeaning, String note) {
        // 1. 必須3項目の入力チェック(採番より前に行い、不正入力時はDBへアクセスしない)
        requireNotBlank(promptText, "promptText");
        requireNotBlank(answerText, "answerText");
        requireNotBlank(expressionMeaning, "expressionMeaning");

        // 現在日時は1回だけ取得し、登録日・登録日時・更新日時で同じ時点を使う
        // (日付が変わる直前に実行された場合でも、各項目の日付がずれないようにするため)。
        // DB上の日時は秒までの形式のため、Java側でも秒未満を切り捨てて返却値とDBの値を一致させる。
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDate today = now.toLocalDate();

        LearningExpression expression = new LearningExpression(promptText, answerText, expressionMeaning, note);
        // 2. BIZ-02で表現IDを採番する
        expression.setExpressionId(findNextExpressionId());
        // 3. 学習段階は NEW
        expression.setLearningStage(LearningStage.NEW);
        // 4. 連続正解回数・累計正解回数・累計誤答回数は 0
        expression.setConsecutiveCorrectCount(0);
        expression.setTotalCorrectCount(0);
        expression.setTotalWrongCount(0);
        // 5. 次回復習予定日は登録日の翌日(登録当日は復習対象外)、最終復習日はNULL(未復習)
        expression.setNextReviewDate(today.plusDays(1));
        expression.setLastReviewDate(null);
        // 6. 登録日時・更新日時は現在日時
        expression.setCreatedAt(now);
        expression.setUpdatedAt(now);

        // 3・4・6はEntityの初期値と同じ値だが、設計書の手順と1対1で対応させ、
        // 初期状態がこのメソッドだけで読み取れるよう明示的に設定している。

        // 7. 登録を実行し、登録内容を返却する
        return repository.save(expression);
    }

    /**
     * BIZ-02 表現ID採番。現在未使用の最小正整数を返す。
     *
     * <p>処理内容(詳細設計書 BIZ-02):</p>
     * <ol>
     *   <li>既存IDを昇順で取得する</li>
     *   <li>1から順に既存IDと比較する</li>
     *   <li>最初に存在しない正整数を返す(例: [1, 3, 5] → 2)</li>
     *   <li>欠番がない場合は 最大ID + 1 を返す(例: [1, 2, 3] → 4)</li>
     *   <li>登録データが0件の場合は 1 を返す</li>
     * </ol>
     *
     * <p>既存IDは「1以上・重複なし・昇順」であるため、候補値(1から開始)と先頭から順に比較し、
     * 一致する間は候補値を進め、一致しなかった時点の候補値が最小の未使用IDとなる。
     * 最後まで一致した場合は候補値が自然に 最大ID + 1、0件の場合は初期値の 1 のままとなる。</p>
     *
     * <p>V1はローカル単一利用者のため、同時登録による採番の競合は考慮しない(詳細設計書 BIZ-02 同時実行)。</p>
     *
     * <p>採番と登録を同一トランザクションで行うため、本メソッドは register() の中からのみ呼び出す。
     * 外部(Controller等)からは呼ばせないが、テストクラスから直接検証できるようパッケージプライベートとしている。</p>
     *
     * @return 採番した表現ID(1以上)
     */
    int findNextExpressionId() {
        List<Integer> existingIds = repository.findAllExpressionIdsOrderByAsc();

        int candidate = 1;
        for (int existingId : existingIds) {
            if (existingId != candidate) {
                // candidate が既存IDに存在しない = 最小の未使用ID
                break;
            }
            candidate++;
        }
        return candidate;
    }

    /**
     * BIZ-05 表現検索(ID検索)。表現IDの完全一致で1件を取得する。
     *
     * <p>結果は最大1件のため、一覧ではなく1件のEntityを返す。対象が存在しない場合は
     * 「対象なし」として ExpressionNotFoundException を送出する(BIZ-05 処理内容4 / API-05 404)。</p>
     *
     * @param expressionId 検索する表現ID(1以上の正整数)
     * @return 該当する学習表現
     * @throws IllegalArgumentException    表現IDが1未満の場合
     * @throws ExpressionNotFoundException 該当する表現が存在しない場合
     */
    @Transactional(readOnly = true)
    public LearningExpression findById(int expressionId) {
        requirePositiveId(expressionId);
        return repository.findById(expressionId)
                .orElseThrow(() -> new ExpressionNotFoundException(expressionId));
    }

    /**
     * BIZ-05 表現検索(キーワード検索)。
     *
     * <p>中国語例文・日本語表現・表現及び中国語意味の3項目を部分一致で検索し、表現IDの昇順で返す。
     * 備考は検索対象外。キーワード中の % _ \ はRepository側で通常の文字として扱われる。</p>
     *
     * <p>未入力・空白のみのキーワードは不正入力とする(BIZ-05 処理内容2 / API設計書 6章)。
     * 全件表示を意図した空検索は設計上存在しないため、空文字で全件を返すことはしない。
     * キーワードの前後の空白は除去せず、入力されたまま検索条件とする(BIZ-01 と同様に入力値を加工しない)。</p>
     *
     * @param keyword 検索キーワード
     * @return 検索結果(表現ID昇順)。該当0件の場合は空リスト
     * @throws IllegalArgumentException キーワードが未入力・空白のみの場合
     */
    @Transactional(readOnly = true)
    public List<LearningExpression> searchByKeyword(String keyword) {
        requireNotBlank(keyword, "keyword");
        return repository.searchByKeyword(keyword);
    }

    /**
     * BIZ-06 表現編集。学習内容の4項目のみを更新し、学習進捗は変更しない。
     *
     * <p>更新するのは 中国語例文・日本語表現・表現及び中国語意味・備考 と 更新日時 のみ。
     * 表現ID・学習段階・正誤回数・復習日・登録日時は変更しない(BIZ-06 更新不可項目)。
     * このメソッドはそれらの項目を引数として受け取らないため、呼び出し側から変更されることはない。</p>
     *
     * <p>処理順は詳細設計書の処理内容に従い「対象確認 → 必須項目チェック → 更新」とする。
     * 対象確認から更新完了までを1トランザクションとする(詳細設計書 10章)。</p>
     *
     * @param expressionId      編集対象の表現ID(1以上の正整数)
     * @param promptText        中国語例文(必須)
     * @param answerText        日本語表現(必須)
     * @param expressionMeaning 表現及び中国語意味(必須)
     * @param note              備考(任意。null を指定すると備考を削除する)
     * @return 更新後の学習表現
     * @throws IllegalArgumentException    表現IDが1未満、または必須項目が未入力・空白のみの場合
     * @throws ExpressionNotFoundException 編集対象の表現が存在しない場合
     */
    @Transactional
    public LearningExpression update(int expressionId, String promptText, String answerText,
            String expressionMeaning, String note) {
        // 1. 対象表現を確認する(ID不正・対象なしの判定は findById と共通)。
        //    同一クラス内のメソッド呼び出しはSpringのトランザクション制御(プロキシ)を経由しないため、
        //    findById の readOnly 指定は適用されず、このメソッドの更新可能なトランザクションの中で実行される。
        LearningExpression expression = findById(expressionId);

        // 2. 必須3項目の入力チェック
        requireNotBlank(promptText, "promptText");
        requireNotBlank(answerText, "answerText");
        requireNotBlank(expressionMeaning, "expressionMeaning");

        // 3. 学習内容の4項目のみ更新する(4. 学習状態・正誤回数・復習日には触れない)
        expression.setPromptText(promptText);
        expression.setAnswerText(answerText);
        expression.setExpressionMeaning(expressionMeaning);
        expression.setNote(note);
        // 5. 更新日時を現在日時にする
        expression.setUpdatedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));

        return repository.save(expression);
    }

    /**
     * BIZ-07 学習状況集計。ホーム画面に表示する登録表現数と今日の復習件数を取得する。
     *
     * <p>今日の復習件数は BIZ-03 と同一の抽出条件で数える必要があるため、
     * 同じ条件定義を共有する Repository#countTodayReviewTargets を使用する。
     * 2件の集計を1つの読み取り専用トランザクションで行い、同一時点の値として返す。</p>
     *
     * @return 登録表現数(MASTEREDを含む全件)と今日の復習件数
     */
    @Transactional(readOnly = true)
    public LearningStatistics getLearningStatistics() {
        long registeredExpressionCount = repository.count();
        long todayReviewCount = repository.countTodayReviewTargets(LocalDate.now(clock));
        return new LearningStatistics(registeredExpressionCount, todayReviewCount);
    }

    /**
     * BIZ-08 表現削除。指定した表現を物理削除する。
     *
     * <p>削除は復習結果(正解・不正解)として扱わないため、正誤回数や最終復習日の更新は行わずに行ごと削除する。
     * 削除により空いた表現IDは、次回の登録時に BIZ-02 の採番で再利用される(本メソッドでの特別な処理は不要)。</p>
     *
     * <p>存在確認から削除完了までを1トランザクションとする(詳細設計書 10章)。
     * Spring Data の deleteById は対象が存在しない場合に何もせず終了するため、
     * 設計どおり「存在しない場合は対象なし」とするよう、先に取得してから削除している。</p>
     *
     * @param expressionId 削除対象の表現ID(1以上の正整数)
     * @throws IllegalArgumentException    表現IDが1未満の場合
     * @throws ExpressionNotFoundException 削除対象の表現が存在しない場合
     */
    @Transactional
    public void delete(int expressionId) {
        LearningExpression expression = findById(expressionId);
        repository.delete(expression);
    }

    /**
     * 表現IDが1以上の正整数であることを確認する(BIZ-05 ID検索の入力条件 / API-05・07・08 の400条件)。
     *
     * <p>0以下のIDは存在し得ない(DBのCHECK制約 expression_id > 0)ため、検索・編集・削除の前に
     * 「対象なし」ではなく「入力不正」として扱う。</p>
     */
    private static void requirePositiveId(int expressionId) {
        if (expressionId < 1) {
            throw new IllegalArgumentException("expressionId は1以上の正整数で指定してください。");
        }
    }

    /**
     * 必須項目が未入力(null)・空文字・空白のみでないことを確認する(BIZ-01 / BIZ-06 の必須3項目、BIZ-05 のキーワード)。
     *
     * <p>String#isBlank は半角スペースだけでなく全角スペース(U+3000)・タブ・改行も空白と判定する。</p>
     *
     * <p>不正時は IllegalArgumentException を送出する。API層でのHTTP 400への変換や
     * エラーコード・メッセージ文言は、詳細設計で確定した後にController実装時に対応する。</p>
     */
    private static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " は必須です。");
        }
    }
}
