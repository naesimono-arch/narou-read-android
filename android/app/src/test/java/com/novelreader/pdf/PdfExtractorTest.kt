package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PdfExtractor の純関数（表紙メタ抽出）テスト。
 * 移植元: submission-B MetaAndCompareTest の TitleFromCharsTest / AuthorFromCharsTest（JUnit5→JUnit4）。
 * GlyphStripper/loadPages は実 PDF I/O ゆえ JVM 単体では走らせず実機 androidTest / オラクルへ回す（設計判断）。
 */
class PdfExtractorTest {

    // bottom は top+size 相当（メタ抽出は top/x0/size のみ使うため bottom の実値は無関係）
    private fun cb(text: String, size: Double, x0: Double, top: Double) =
        CharBox(text, "R", size, x0, top, top + size)

    @Test fun titleEmptyReturnsPlaceholder() =
        assertEquals("不明なタイトル", PdfExtractor.titleFromChars(emptyList()))

    @Test fun titlePicksMaxSizeAndOrdersByTopThenX0() {
        val chars = listOf(
            cb("小", 12.0, 0.0, 0.0),     // 小さい→除外
            cb("イ", 20.0, 50.0, 10.0),   // 同じ最大サイズ、top=10
            cb("タ", 20.0, 10.0, 0.0),    // top=0
            cb("ル", 20.0, 30.0, 0.0),    // top=0, x0=30
        )
        // top 昇順 → 同 top は x0 昇順：タ(0,10) ル(0,30) イ(10,50)
        assertEquals("タルイ", PdfExtractor.titleFromChars(chars))
    }

    @Test fun authorPicks12ptExcludingFooter() {
        val chars = listOf(
            cb("著", ParserRules.FONT_SIZE_AUTHOR, 100.0, 50.0),
            cb("者", ParserRules.FONT_SIZE_AUTHOR, 120.0, 50.0),
            cb("脚", ParserRules.FONT_SIZE_AUTHOR, 100.0, ParserRules.COVER_FOOTER_Y), // フッター→除外
            cb("大", 20.0, 100.0, 50.0), // サイズ違い→除外
        )
        assertEquals("著者", PdfExtractor.authorFromChars(chars))
    }
}
