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
import com.zhou.japaneselearning.exception.ReviewConflictException;
import com.zhou.japaneselearning.repository.LearningExpressionRepository;

/**
 * 復習に関する業務処理を担当するService(F02 今日の復習 / F03 自己採点)。
 *
 * <ul>
 *   <li>BIZ-03 復習対象取得</li>
 *   <li>BIZ-04 復習結果更新(学習段階の遷移・正誤回数・復習日の更新)</li>
 * </ul>
 *
 * <p>表現の登録・検索・編集・削除は {@link LearningExpressionService} が担当し、
 * 本クラスは「復習」という業務の流れに関する処理に責務を限定している。</p>
 */
@Service
public class ReviewService {

    /** 次の学習段階へ昇級するために必要な連続正解回数(要件定義書 2.4.3「連続5回正解」)。 */
    private static final int PROMOTION_THRESHOLD = 5;

    private final LearningExpressionRepository repository;

    /** 「現在日時」の取得元。考え方は LearningExpressionService と同じ(テストで日時を固定するため)。 */
    private final Clock clock;

    @Autowired
    public ReviewService(LearningExpressionRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** テスト用コンストラクタ(日時を固定した Clock を渡す)。 */
    ReviewService(LearningExpressionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * BIZ-03 復習対象取得。システム当日の復習対象を優先順に返す。
     *
     * <p>抽出条件(MASTERED除外・予定日到来・当日未採点)と並び順(累計誤答回数の降順、同数は表現IDの昇順)は
     * Repositoryのクエリで実装済みのため、Serviceは基準日(システム当日)を決めて渡すことのみを担当する。
     * 対象が0件でも状態更新は行わず、空リストを返す(BIZ-03 判定・処理 5)。</p>
     *
     * <p>readOnly = true は「このトランザクションでは更新しない」ことの宣言。
     * Hibernateが変更検知(更新の有無の確認)を省略できるため、読み取り専用の処理に指定する。</p>
     *
     * @return 当日の復習対象(優先順)。0件の場合は空リスト
     */
    @Transactional(readOnly = true)
    public List<LearningExpression> findTodayReviewTargets() {
        return repository.findTodayReviewTargets(LocalDate.now(clock));
    }

    /**
     * BIZ-04 復習結果更新。自己採点の結果に応じて学習状態を更新する。
     *
     * <p>@Transactional により「対象確認・重複判定・状態更新」を1トランザクションで行う(詳細設計書 10章)。
     * 採点を受け付けない場合は例外を送出し、データは一切更新しない。</p>
     *
     * <p>Entityの値を変更した後に save() を呼んでいるが、トランザクション内でDBから取得したEntityは
     * Hibernateに管理されており、変更はコミット時に自動でUPDATEされる(変更検知)。
     * save() は「ここで保存する」という意図を読み手に明示するために呼んでいる。</p>
     *
     * @param expressionId 採点対象の表現ID
     * @param result       自己採点結果(CORRECT / INCORRECT)
     * @return 更新後のEntity(学習段階・連続正解回数・累計正誤回数・次回復習予定日・最終復習日の返却に使用する)
     * @throws ExpressionNotFoundException 指定した表現IDが存在しない場合
     * @throws IllegalArgumentException    result が指定されていない(null)場合
     * @throws ReviewConflictException     当日採点済み・MASTERED・復習予定日未到来のいずれかの場合
     */
    @Transactional
    public LearningExpression recordReviewResult(int expressionId, ReviewResult result) {
        // 1. 対象表現を取得する。存在しない場合は終了する(詳細設計書 5.1-1)
        LearningExpression expression = repository.findById(expressionId)
                .orElseThrow(() -> new ExpressionNotFoundException(expressionId));

        // 2. result が CORRECT / INCORRECT のいずれかであること(5.1-2)。
        //    enum型のため、CORRECT / INCORRECT 以外の値はこのメソッドに渡らない。
        //    不正な文字列は Controller がリクエストを ReviewResult に変換する時点で拒否される想定。
        if (result == null) {
            throw new IllegalArgumentException("result は必須です。");
        }

        // 現在日時は1回だけ取得し、判定・最終復習日・更新日時で同じ時点を使う(日付変更直前の実行でもずれない)
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDate today = now.toLocalDate();

        // 3. 採点を受け付けられる状態かを確認する(受け付けない場合は何も更新せず例外)
        verifyReviewable(expression, today);

        // 4. 現在の学習段階と結果に応じて状態遷移を適用する(5.2)
        if (result == ReviewResult.CORRECT) {
            applyCorrect(expression, today);
        } else {
            applyIncorrect(expression, today);
        }

        // 5. 採点成功時の共通更新(5.1-5)
        expression.setLastReviewDate(today);
        expression.setUpdatedAt(now);

        // 6. 更新後の学習状態を返却する
        return repository.save(expression);
    }

    /**
     * 採点を受け付けられる状態かを判定する。受け付けない場合は ReviewConflictException を送出する。
     *
     * <p>判定順は、設計書記載の重複採点判定を先に行い、追加ルール(MASTERED・予定日未到来)をその後に行う。
     * いずれも「現在状態との競合」(HTTP 409相当)であり、どの判定で拒否されてもデータは更新しない。</p>
     */
    private static void verifyReviewable(LearningExpression expression, LocalDate today) {
        int id = expression.getExpressionId();
        // 同一表現は1学習日につき最大1回のみ採点可能(詳細設計書 5.1-3)
        if (today.equals(expression.getLastReviewDate())) {
            throw new ReviewConflictException(ReviewConflictException.Reason.ALREADY_REVIEWED_TODAY,
                    "表現ID " + id + " は本日すでに採点済みです。");
        }
        // MASTERED は通常出題せず、V1では再活性化しない(詳細設計書 5.2)
        if (expression.getLearningStage() == LearningStage.MASTERED) {
            throw new ReviewConflictException(ReviewConflictException.Reason.REVIEW_NOT_ALLOWED,
                    "表現ID " + id + " は定着済み(MASTERED)のため採点できません。");
        }
        // MASTERED以外で予定日がNULLになることは通常ない(データ不整合)。
        // 復習対象(BIZ-03)にも含まれず「予定日前」でもないため、採点対象外の状態として拒否する。
        LocalDate nextReviewDate = expression.getNextReviewDate();
        if (nextReviewDate == null) {
            throw new ReviewConflictException(ReviewConflictException.Reason.REVIEW_NOT_ALLOWED,
                    "表現ID " + id + " は復習予定日が設定されていないため採点できません。");
        }
        // 次回復習予定日が当日より後の表現は採点しない(詳細設計書への追加事項)
        if (nextReviewDate.isAfter(today)) {
            throw new ReviewConflictException(ReviewConflictException.Reason.REVIEW_NOT_DUE,
                    "表現ID " + id + " はまだ復習予定日になっていません。");
        }
    }

    /**
     * 正解時の状態遷移(詳細設計書 5.2 / 5.3)。
     *
     * <ul>
     *   <li>累計正解回数を1加算する</li>
     *   <li>NEW: DAILYへ遷移し、連続正解回数を1とする(この1回も昇級判定の5回に含む。確認事項①)</li>
     *   <li>DAILY / WEEKLY / MONTHLY: 連続正解回数を1加算し、5に到達したら次段階へ昇級して0に戻す
     *       (次段階での連続正解を新たに数えるため)</li>
     *   <li>次回復習予定日は「遷移後」の段階の間隔で、採点日を起点に計算する</li>
     * </ul>
     */
    private static void applyCorrect(LearningExpression expression, LocalDate today) {
        expression.setTotalCorrectCount(expression.getTotalCorrectCount() + 1);

        LearningStage currentStage = expression.getLearningStage();
        LearningStage nextStage;
        int consecutiveCorrectCount;

        if (currentStage == LearningStage.NEW) {
            nextStage = LearningStage.DAILY;
            consecutiveCorrectCount = 1;
        } else {
            consecutiveCorrectCount = expression.getConsecutiveCorrectCount() + 1;
            nextStage = currentStage;
            // 通常は5ちょうどで昇級するが、データ不整合で5を超えていた場合も昇級できるよう >= で判定する
            if (consecutiveCorrectCount >= PROMOTION_THRESHOLD) {
                nextStage = promotedStageOf(currentStage);
                consecutiveCorrectCount = 0;
            }
        }

        expression.setLearningStage(nextStage);
        expression.setConsecutiveCorrectCount(consecutiveCorrectCount);
        expression.setNextReviewDate(calculateNextReviewDate(nextStage, today));
    }

    /**
     * 不正解時の状態遷移(詳細設計書 5.2 / 5.3)。
     *
     * <p>現在の段階(NEW / DAILY / WEEKLY / MONTHLY)にかかわらず、DAILYへ戻し、
     * 連続正解回数を0にリセットし、累計誤答回数を1加算する。次回復習予定日は採点日の翌日となる。</p>
     */
    private static void applyIncorrect(LearningExpression expression, LocalDate today) {
        expression.setTotalWrongCount(expression.getTotalWrongCount() + 1);
        expression.setLearningStage(LearningStage.DAILY);
        expression.setConsecutiveCorrectCount(0);
        expression.setNextReviewDate(calculateNextReviewDate(LearningStage.DAILY, today));
    }

    /** 連続5回正解による昇級先(DAILY → WEEKLY → MONTHLY → MASTERED)。 */
    private static LearningStage promotedStageOf(LearningStage stage) {
        return switch (stage) {
            case DAILY -> LearningStage.WEEKLY;
            case WEEKLY -> LearningStage.MONTHLY;
            case MONTHLY -> LearningStage.MASTERED;
            // NEW は applyCorrect で個別に扱い、MASTERED は採点前に拒否するため、ここには到達しない
            case NEW, MASTERED -> throw new IllegalStateException("昇級できない学習段階です: " + stage);
        };
    }

    /**
     * 次回復習予定日を計算する(詳細設計書 5.4)。起点は採点日(当日)であり、元の予定日ではない。
     *
     * <ul>
     *   <li>DAILY: 採点日の翌日</li>
     *   <li>WEEKLY: 採点日の7日後</li>
     *   <li>MONTHLY: 採点日の翌暦月の同日。
     *       LocalDate#plusMonths は翌月に同日が存在しない場合、自動的に翌月の末日とする
     *       (例: 1月31日 → 2月28日、うるう年は2月29日)。設計書のルールと一致する。</li>
     *   <li>MASTERED: NULL(通常の復習対象から除外する)</li>
     * </ul>
     */
    private static LocalDate calculateNextReviewDate(LearningStage stage, LocalDate today) {
        return switch (stage) {
            case DAILY -> today.plusDays(1);
            case WEEKLY -> today.plusDays(7);
            case MONTHLY -> today.plusMonths(1);
            case MASTERED -> null;
            // NEW の初回予定日は登録時(BIZ-01)に設定され、採点後にNEWへ遷移することはないため、ここには到達しない
            case NEW -> throw new IllegalStateException("採点後の学習段階がNEWになることはありません。");
        };
    }
}
