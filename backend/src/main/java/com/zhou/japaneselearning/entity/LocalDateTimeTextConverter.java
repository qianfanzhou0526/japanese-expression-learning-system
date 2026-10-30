package com.zhou.japaneselearning.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * LocalDateTime と DBの日時文字列(YYYY-MM-DD HH:MM:SS)を相互変換するJPAコンバータ。
 *
 * <p>DB設計書ではSQLiteの日時をTEXT型・YYYY-MM-DD HH:MM:SS形式(19桁)で保持すると定めている。
 * Hibernate + SQLite JDBCの既定動作ではエポックミリ秒の文字列として保存されるため、
 * 本コンバータで形式を固定する(理由の詳細は {@link LocalDateTextConverter} を参照)。</p>
 *
 * <p>設計書の形式は秒までのため、ミリ秒以下は保存時に切り捨てられる。</p>
 */
@Converter
public class LocalDateTimeTextConverter implements AttributeConverter<LocalDateTime, String> {

    /** DB設計書の日時形式 YYYY-MM-DD HH:MM:SS。日付と時刻の区切りは "T" ではなく半角スペース。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    /** Java → DB: 保存時に呼ばれる。 */
    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.format(FORMATTER);
    }

    /** DB → Java: 取得時に呼ばれる。 */
    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDateTime.parse(dbData, FORMATTER);
    }
}
