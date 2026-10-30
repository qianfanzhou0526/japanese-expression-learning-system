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
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * 学習表現に関する業務処理を担当するService。
 *
 * <p>Service層は「業務ルールの実装」と「トランザクション境界」を担う(詳細設計書 11章)。
 * 入力チェック・ID採番・初期学習状態の決定といった業務判断はここで行い、
 * DBの読み書きそのものはRepositoryへ委譲する。Controllerからは本クラスのメソッドを呼び出す想定。</p>
 *
 * <p>現時点の実装範囲は BIZ-01 表現登録 / BIZ-02 表現ID採番 のみ。</p>
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
     * 必須項目が未入力(null)・空文字・空白のみでないことを確認する(BIZ-01 処理内容1)。
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
