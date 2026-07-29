package com.novelreader.pdf

import com.novelreader.model.TextSegment
import com.novelreader.parser.ChapterHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 生成（ChapterProcessor → HtmlExporter）と読み戻し（ChapterHtmlParser）の**往復契約**を守るテスト。
 *
 * **なぜこのテストが要るか（穴の経緯・2026-07-30）**:
 * ChapterProcessor には長らく「div/hr の HTML 文字列は Python f-string とバイト等価に揃える
 * （Task 7 のゴールデンの前提）」というコメントが付いていたが、これは**偽の主張**だった。
 * 実際に確認すると:
 * - `HtmlExporterGoldenTest` は HtmlExporter に固定入力を与えて golden_html/ と突き合わせるだけで、
 *   ChapterProcessor が出す `<hr>` / 前後書き div は入力側に含まれず**一度も通っていない**。
 * - `JvmGoldenRegressionTest` が固定するのは body_sha256 だが、その材料は
 *   `PdfExtractor.runFinalEngine` の生段落であって ChapterProcessor の HTML 出力ではない。
 * - `ChapterHtmlParserTest` は `wrapChapterHtml("前文<hr>後文")` と HTML を手書きするため、
 *   **生成側が `<hr>` を出さなくなっても緑のまま通る**。
 *
 * つまり「ChapterProcessor が `<hr>` を出す → ChapterHtmlParser が [TextSegment.HorizontalRule] に戻す」
 * という契約は、どのテストも守っていなかった。ここが切れると各スキンの場面転換線
 * （SceneDividerM/P/J 等）が本文から**無音で消える**——読者には欠落だと気づけない種類の事故になる。
 *
 * そこで本テストは両側を実物で回す（HTML を手書きしない）。片側だけをアサートすると
 * 「生成側だけ変えた」事故を捕まえられないため、**必ず生成物をパーサへ通して型で受ける**こと。
 */
class ChapterHtmlRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun chap(title: String, vararg body: String) = RawChapter(title, body.toMutableList())

    /**
     * 章列を本番と同じ2段（前後書き整形 → HTML 書き出し）で chap_1.html まで作り、
     * 実ファイルをパーサへ通して読み戻す。生成・読み戻しとも本番実装をそのまま使う。
     */
    private fun roundTrip(chapters: List<RawChapter>, outName: String): List<TextSegment> {
        val outDir = tmp.newFolder(outName)
        HtmlExporter.exportToMobileHtml(
            ChapterProcessor.processForewordAfterword(chapters),
            outDir,
            "テスト作品",
        )
        val parsed = ChapterHtmlParser.parse(File(outDir, "chap_1.html"))
            ?: error("chap_1.html が生成されていない（HtmlExporter の出力先規約が変わった可能性）")
        return parsed.segments
    }

    @Test
    fun `前書きの直後に置いた hr が HorizontalRule として読み戻る`() {
        // 前書きは「前書き div ＋ <hr>」を次章の先頭へ前置する＝区切り線は本文の一部として往復する。
        val segments = roundTrip(
            listOf(chap("前書き", "前置きの言葉"), chap("第一話", "本文いちぎょうめ")),
            "foreword",
        )

        assertEquals("前書き＋hr＋本文の3セグメントに畳まれる", 3, segments.size)
        val block = segments[0] as TextSegment.StyledBlock
        assertEquals("（前書き）", block.label)
        // 契約の核: 生成側の <hr> がパーサで HorizontalRule 型に戻る（UI はこの型で場面転換線を描く）。
        assertEquals(TextSegment.HorizontalRule, segments[1])
        assertEquals(TextSegment.Plain("本文いちぎょうめ"), segments[2])
    }

    @Test
    fun `後書きの直前に置いた hr が HorizontalRule として読み戻る`() {
        // 後書きは「<hr> ＋ 後書き div」を直前章の末尾へ追記する＝前書きとは hr の前後関係が逆になる。
        val segments = roundTrip(
            listOf(chap("第一話", "本文いちぎょうめ"), chap("後書き", "あとがきの言葉")),
            "afterword",
        )

        assertEquals("本文＋hr＋後書きの3セグメントに畳まれる", 3, segments.size)
        assertEquals(TextSegment.Plain("本文いちぎょうめ"), segments[0])
        assertEquals(TextSegment.HorizontalRule, segments[1])
        val block = segments[2] as TextSegment.StyledBlock
        assertEquals("（後書き）", block.label)
    }

    @Test
    fun `div_content が本文抽出の起点である契約`() {
        // HtmlExporter のテンプレートの class 名は装飾でなく契約: ChapterHtmlParser は
        // selectFirst("div.content") を起点にするため、class 名を変えると例外も出さず
        // segments が空になる（本文が丸ごと消えるのに緑のまま通る）＝ここで trip させる。
        val segments = roundTrip(listOf(chap("第一話", "本文いちぎょうめ")), "content-div")

        assertTrue(
            "div.content を起点に本文が取れていない（テンプレートの class 名が変わった疑い）",
            segments.isNotEmpty(),
        )
        assertEquals(TextSegment.Plain("本文いちぎょうめ"), segments[0])
    }
}
