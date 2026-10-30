-- ============================================================
-- 日本語表現学習システム V1 スキーマ定義
-- 基本設計書(データベース)の定義に従う。
--
-- アプリ起動時にSpring Boot(spring.sql.init)が毎回実行する。
-- 既存データを消さないよう CREATE TABLE IF NOT EXISTS とし、
-- テーブルが既に存在する場合は何もしない。
-- ※そのため、テーブル定義を変更してもこのファイルの修正だけでは既存DBに反映されない。
-- ============================================================

-- 学習表現: 日本語表現の内容、学習状態、正誤回数、復習予定日を一元管理する。
CREATE TABLE IF NOT EXISTS learning_expression (
    -- 表現ID。採番はアプリ側(最小未使用ID)で行う方針のため AUTOINCREMENT は使用しない。
    expression_id             INTEGER NOT NULL PRIMARY KEY CHECK (expression_id > 0),
    prompt_text               TEXT    NOT NULL,
    answer_text               TEXT    NOT NULL,
    expression_meaning        TEXT    NOT NULL,
    note                      TEXT,
    learning_stage            TEXT    NOT NULL DEFAULT 'NEW'
                                  CHECK (learning_stage IN ('NEW', 'DAILY', 'WEEKLY', 'MONTHLY', 'MASTERED')),
    consecutive_correct_count INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_correct_count >= 0),
    total_correct_count       INTEGER NOT NULL DEFAULT 0 CHECK (total_correct_count >= 0),
    total_wrong_count         INTEGER NOT NULL DEFAULT 0 CHECK (total_wrong_count >= 0),
    -- 日付・日時はSQLiteの方針に従いTEXTで保持する。
    -- 日付は YYYY-MM-DD(10桁)、日時は YYYY-MM-DD HH:MM:SS(19桁)。
    next_review_date          TEXT,
    last_review_date          TEXT,
    created_at                TEXT    NOT NULL,
    updated_at                TEXT    NOT NULL
);
