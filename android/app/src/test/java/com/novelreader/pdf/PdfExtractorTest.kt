package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    // --- グリフ正規化（task_diary #35）: PDFBox-android の CID→Unicode を pdfminer(オラクル)へ揃える ---

    @Test fun normalizeMapsFullwidthTildeToWaveDash() {
        // '\uFF5E'(PDFBoxが返す) → '\u301C'(pdfminerが返す)
        assertEquals("\u301C", normalizeGlyphUnicode("\uFF5E"))
        assertEquals("前世を思い出しました　\u301Cあれ", normalizeGlyphUnicode("前世を思い出しました　\uFF5Eあれ"))
    }

    @Test fun normalizeMapsDashAndArrowsToGolden() {
        // 1:1 コードポイント写像（N6169DZ 章題ドリフトを golden へ寄せる）
        assertEquals("\u2212", normalizeGlyphUnicode("\uFF0D"))   // FULLWIDTH HYPHEN-MINUS → MINUS SIGN
        assertEquals("\u2190", normalizeGlyphUnicode("\u2191"))   // UP → LEFT ARROW
        assertEquals("\u2192", normalizeGlyphUnicode("\u2193"))   // DOWN → RIGHT ARROW
        // 文中混在・複数写像の同時適用（章題「第－1話　↑戻る」相当）
        assertEquals("第\u22121話　\u2190戻る", normalizeGlyphUnicode("第\uFF0D1話　\u2191戻る"))
    }

    @Test fun normalizeLeavesUnmappedUntouched() {
        // 既に写像先のものは不変（二重変換しない）
        assertEquals("\u301C", normalizeGlyphUnicode("\u301C"))
        assertEquals("\u2212", normalizeGlyphUnicode("\u2212"))
        assertEquals("\u2190\u2192", normalizeGlyphUnicode("\u2190\u2192"))
        // 無関係な文字は不変かつ同一インスタンス（ホットパスで新規確保しない設計＝assertSame 契約）
        val plain = "婚約の継続"
        assertEquals(plain, normalizeGlyphUnicode(plain))
        assertSame(plain, normalizeGlyphUnicode(plain))
    }
}
