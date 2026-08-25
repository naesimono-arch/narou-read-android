package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 抽出パイプラインが **1 ページ分より多くのグリフを同時に保持しない**ことと、
 * その結果が全ページ保持経路と 1 文字も違わないことを機械で固定する。
 *
 * なぜこのテストが要るか（守っている真因）: 旧経路は全ページ分の [CharBox] を
 * `List<List<CharBox>>` として同時生存させており、保持量がページ数に比例した
 * （N6169DZ 8,668 ページで Dalvik ピーク実測 514MB＝約 59KB/ページ）。192MB 級の端末では
 * [OutOfMemoryError] で取込が失敗し、512MB 級の実機でも天井を使い切る寸前だった。
 *
 * ⚠️ **人間が実機でこの分岐を確認できない**のがこのテストの存在理由: 開発機は高性能で、
 * 低スペック側の経路は実機テストでは踏めない。ヒープ天井は注入で叩ける形にしてあり
 * （[PdfExtractor.runFinalEngine] の internal overload・[PdfExtractor.canMaterializeAllPages]）、
 * 両経路の等価性を機械の網だけで保証する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfExtractorStreamingTest {

    @Before
    fun initResourceLoader() {
        // 実 PDF を開くテストがあるため、本番と同じく PDDocument.load の前に一度だけ init する
        // （AAR 同梱 CMap/glyphlist。欠くと CID→Unicode 解決がズレる）。
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    // ── 合成文書（DetectedRulesTest と同じ寸法・同じ構造） ──────────────────────

    private fun cb(text: String, size: Double, x0: Double, top: Double, font: String = "R") =
        CharBox(text, font, size, x0, top, top + size)

    /**
     * [pageCount] ページの縦書き文書を組む。index 0-2＝前付・末尾1ページ＝奥付（どちらも本文から除外）。
     * ⚠️ 呼ぶたびに **新しい CharBox を作る**こと: [TextProcessor.associateRuby] が `rubyText` を
     * in-place で書き込むため、同じインスタンスを2回流すとルビが二重に付いて比較が壊れる。
     */
    private fun buildDoc(pageCount: Int): List<List<CharBox>> {
        val bodySize = 14.0
        val rubySize = 7.0
        val step = 22.68
        val rubyOffset = 14.84
        val baseX = 400.0
        fun pageNum() = cb("9", 12.0, 200.0, 528.98)
        fun contentPage(index: Int): List<CharBox> {
            val cs = mutableListOf(pageNum())
            // 3 ページに 1 回だけ章題を置く（章分割の縫合もページを跨ぐ状態なので網に掛ける）。
            if (index % 3 == 0) cs += cb("章", bodySize, baseX + step, 60.0, font = "NotoSerif Bold")
            for (col in 0 until 5) {
                val x0 = baseX - col * step
                cs += cb("森川海空雲"[col].toString(), bodySize, x0, 100.0)
                if (col % 2 == 0) cs += cb("ル", rubySize, x0 + rubyOffset, 100.0)
            }
            // 段落切れ＝2*step 空けた開き括弧始まりの列（空行 1 行を伴う）。
            cs += cb("「", bodySize, baseX - 6 * step, 100.0)
            return cs
        }
        return (0 until pageCount).map { i ->
            if (i < 3 || i == pageCount - 1) listOf(pageNum()) else contentPage(i)
        }
    }

    /**
     * ページを 1 枚ずつ渡し、**consume から戻った瞬間にそのページを空にする**供給源。
     *
     * これが「保持していないこと」の検出器そのもの: 消費側が参照を持ち越していれば、後で読んだときに
     * 空リストが見えて結果が変わる＝ページ数比例の保持が復活した瞬間にテストが赤くなる。
     * GC やヒープ計測に頼らないので**完全に決定的**（メモリ計測は flaky でゲートに使えない）。
     * 走査ごとにページを組み直すので、複数回走査する [DetectedRules.detect] でも各走査は完全なページを見る。
     */
    private inner class SelfClearingPageSource(private val pageCount: Int) : PageGlyphSource {
        var maxPagesHandedOut = 0
            private set

        override fun forEachPage(consume: (Int, List<CharBox>) -> Unit) {
            val doc = buildDoc(pageCount)
            var live = 0
            for (i in 0 until pageCount) {
                val page = doc[i].toMutableList()
                live++
                maxPagesHandedOut = maxOf(maxPagesHandedOut, live)
                consume(i, page)
                page.clear()
                live--
            }
        }
    }

    // ── ①保持していないことの直接証明 ───────────────────────────────────────

    @Test
    fun `rules 検出は消費済みページを保持しない`() {
        val pageCount = 40
        val expected = DetectedRules.detect(buildDoc(pageCount))
        val streamed = DetectedRules.detect(SelfClearingPageSource(pageCount), pageCount)
        assertEquals(
            "ページを使い捨てても検出値が変わらない＝走査を跨いで CharBox を保持していない",
            expected,
            streamed,
        )
    }

    @Test
    fun `段落化は消費済みページを保持しない`() {
        val pageCount = 40
        val rules = DetectedRules.detect(buildDoc(pageCount))
        val expected = TextProcessor.processPages(buildDoc(pageCount), pageCount, rules)

        val streamed = mutableListOf<String>()
        val streamer = TextProcessor.ParagraphStreamer(pageCount, rules) { streamed.add(it) }
        SelfClearingPageSource(pageCount).forEachPage { i, chars -> streamer.addPage(i, chars) }
        streamer.finish()

        assertEquals("使い捨て供給でも段落列が完全一致する", expected, streamed)
        assertTrue("段落が1本も出ないなら検出力が無い", streamed.isNotEmpty())
    }

    // ── ②ヒープ天井による経路選択の境界（人間が実機で踏めない分岐） ─────────────

    @Test
    fun `ヒープ天井が小さい端末では全ページ保持を選ばない`() {
        // Android Go 級（96MB）で 8,668 ページ＝旧経路が落ちた実測条件。
        assertFalse(
            "96MB 端末で 8,668 ページを全ページ保持してはならない",
            PdfExtractor.canMaterializeAllPages(totalPages = 8_668, maxMemoryBytes = 96L * 1024 * 1024),
        )
        // 実機 OPPO Find X6 Pro 級（512MB）でも、この規模は見積り 568MB＝予算を超える。
        assertFalse(
            "512MB 端末でも 8,668 ページは予算を超える（実機でも天井間際だった実測と整合）",
            PdfExtractor.canMaterializeAllPages(totalPages = 8_668, maxMemoryBytes = 512L * 1024 * 1024),
        )
    }

    @Test
    fun `ヒープに余裕があり文書が小さければ全ページ保持を選ぶ`() {
        // 通常の蔵書（数百ページ）は従来どおり再パースの無い高速経路を通る＝巨大 PDF のために
        // 普通の本を遅くしていないことの担保。
        assertTrue(
            "192MB 端末で 799 ページ（中編）は全ページ保持で足りる",
            PdfExtractor.canMaterializeAllPages(totalPages = 799, maxMemoryBytes = 192L * 1024 * 1024),
        )
    }

    @Test
    fun `経路選択の境界がページ数に対して単調である`() {
        val heap = 192L * 1024 * 1024
        val budgetPages =
            (heap / 100 * PdfExtractor.MATERIALIZE_BUDGET_PERCENT / PdfExtractor.MATERIALIZED_BYTES_PER_PAGE).toInt()
        assertTrue(PdfExtractor.canMaterializeAllPages(budgetPages, heap))
        assertFalse(PdfExtractor.canMaterializeAllPages(budgetPages + 1, heap))
    }

    // ── ③実 PDF での両経路の完全一致（本文が 1 文字も変わらないこと） ─────────────

    @Test
    fun `実PDFで全ページ保持経路とストリーミング経路の本文が完全一致する`() {
        val pdf = File(resolveRepoRoot(), "sample_pdfs/N2959KI.pdf")
        assertTrue("テスト用 PDF が無い: ${pdf.absolutePath}", pdf.isFile)

        // 天井を注入して両経路を名指しで通す（実行環境のヒープに結果が左右されないようにする）。
        val materialized = PDDocument.load(pdf).use {
            PdfExtractor.runFinalEngine(it, null, maxMemoryBytes = Long.MAX_VALUE / 2)
        }
        val streamed = PDDocument.load(pdf).use {
            PdfExtractor.runFinalEngine(it, null, maxMemoryBytes = 1L)
        }

        assertTrue("段落が抽出できていない（テスト自体が無意味になる）", materialized.size > 100)
        assertEquals("経路が違っても段落数は同じ", materialized.size, streamed.size)
        assertEquals("経路が違っても本文が 1 文字も変わらない", materialized, streamed)
    }

    private fun resolveRepoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            if (File(d, "sample_pdfs").isDirectory) return d
            d = d.parentFile
        }
        throw IllegalStateException("sample_pdfs/ を含むリポジトリルートが見つからない")
    }
}
