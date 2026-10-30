package com.zhou.japaneselearning.service;

/**
 * 自己採点の結果(BIZ-04 入力 result / API-04 Request項目 result)。
 *
 * <p>DBには保存されない値のため、entity パッケージではなく、これを扱う service パッケージに置いている。
 * 定数名はAPIで受け取る文字列(CORRECT / INCORRECT)と一致させること。</p>
 */
public enum ReviewResult {
    /** 正解。 */
    CORRECT,
    /** 不正解。 */
    INCORRECT
}
