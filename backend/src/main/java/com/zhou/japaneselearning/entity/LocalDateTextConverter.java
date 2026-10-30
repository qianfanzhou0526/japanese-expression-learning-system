package com.zhou.japaneselearning.entity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * LocalDate と DBの日付文字列(YYYY-MM-DD)を相互変換するJPAコンバータ。
 *
 * <p>DB設計書ではSQLiteの日付をTEXT型・YYYY-MM-DD形式で保持すると定めている。
 * しかしHibernate + SQLite JDBCの既定動作では、LocalDateがエポックミリ秒の文字列
 * (例: "1793372400000")として保存され、設計書の形式と一致しない。
 * そのため本コンバータで保存形式を明示的に固定する。</p>
 *
 * <p>この形式で保存することで、文字列の大小比較が日付の前後関係と一致するため、
 * 「next_review_date が当日以前」といった条件をSQLの比較演算でそのまま判定できる。</p>
 *
 * <p>autoApply は使わず、Entity側で @Convert を明示して適用箇所を分かりやすくしている。</p>
 */
@Converter
public class LocalDateTextConverter implements AttributeConverter<LocalDate, String> {

    /** DB設計書の日付形式 YYYY-MM-DD(ISO-8601の日付形式と同一)。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Java → DB: 保存時に呼ばれる。NULL(未復習・MASTERED等)はそのままNULLとして保存する。 */
    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.format(FORMATTER);
    }

    /** DB → Java: 取得時に呼ばれる。 */
    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDate.parse(dbData, FORMATTER);
    }
}
