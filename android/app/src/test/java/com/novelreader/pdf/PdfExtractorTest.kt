package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PdfExtractor の純関数（表紙メタ抽出）テスト。
 * 移植元: submission-B MetaAndCompareTest の TitleFromCharsTest / AuthorFromCharsTest（JUnit5→JUnit4）。
 * GlyphStripper/loadPages を含む実 PDF I/O の回帰は [JvmGoldenRegressionTest]（Robolectric）が担う
 * （旧記述「JVM 単体では実 PDF を走らせない」は 2026-07-16 に実測で覆った＝docs/knowledge/robolectric-pdfbox-android-real-pdf-parity.md）。
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

    // --- グリフの字を決める経路（ADR 0041 決定1）---
    //
    // かつてここに `normalizeGlyphUnicode` の写像テスト 3 本が在ったが、写像そのものを撤去したので
    // 一緒に落とした（FF5E→301C・FF0D→2212・矢印回転。追従先だった pdfminer は既に存在しない実装＝
    // **死んだオラクル**で、実際には 6 系統すべてで web 原文と食い違っていた）。
    //
    // 置き換え先は [GlyphDecoder]（字は ToUnicode の逆引きでなくフォントの符号化から決める）で、
    // その契約を見張るのは `WebAnchoredOracleTest` の S2b＝**実 PDF 4 本の全文で 19 コードポイントの
    // 出現数を web 原文オラクルと突き合わせる**ゲート。ここに合成入力の単体テストを置き直さないのは、
    // [GlyphDecoder] の入力が PDFBox の TextPosition（フォント・符号化・ToUnicode を伴う実物）で、
    // 手で組んだモックでは「どちらの層を見ているか」という検証したい当のものが再現できないため。
}
