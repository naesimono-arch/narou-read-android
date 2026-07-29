package com.novelreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * HtmlExporter.exportToMobileHtml のバイト等価ゴールデンテスト
 * （同一入力 _GOLDEN_CHAPTERS → 生成 → fixture 照合）。
 *
 * **比較対象の正本は fixture 自身**＝`src/test/resources/golden_html/` 配下の index/chap_1/chap_2 で、
 * 先頭改行・行インデント・末尾空白（末尾改行なし）まで一致することを検証する。
 * fixture は BOM 無し LF の UTF-8＝UTF-8 デコード後の文字列一致はバイト一致と等価。
 *
 * 出自: この fixture は撤去前の Python html_exporter の出力を複製したもの（python/ は 2026-07-05 に
 * 完全撤去済み＝現在この整形を守っているのは Python 実装ではなく本テストと fixture だけ）。
 */
class HtmlExporterGoldenTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ゴールデン生成時の入力（_GOLDEN_CHAPTERS / _GOLDEN_BOOK_TITLE 相当）と同一。
    // 書籍 id は出力に一切現れないため入力に持たない（exportToMobileHtml の引数からも撤去済み）。
    private val goldenChapters = listOf(
        ProcessedChapter(
            "第一話　ゴールデンテスト",
            "この<ruby>物語<rt>ものがたり</rt></ruby>は始まる。\n第二段落。",
        ),
        ProcessedChapter(
            "第二話　終章",
            "すべては<ruby>終<rt>お</rt></ruby>わった。",
        ),
    )
    private val goldenBookTitle = "テスト小説"

    private fun readGolden(name: String): String =
        javaClass.getResourceAsStream("/golden_html/$name")
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: error("golden fixture 未配置: $name（src/test/resources/golden_html/ を確認）")

    @Test
    fun goldenHtmlByteEqual() {
        val outDir = tmp.newFolder("out")
        HtmlExporter.exportToMobileHtml(
            goldenChapters,
            outDir,
            goldenBookTitle,
        ) { _, _ -> } // progress は no-op（生成物には影響しない）

        for (name in listOf("index.html", "chap_1.html", "chap_2.html")) {
            val actual = File(outDir, name).readBytes().toString(Charsets.UTF_8)
            assertEquals("$name の内容がゴールデンと異なります", readGolden(name), actual)
        }
    }
}
