package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [ParserRules.COLUMN_CAPACITY] の**前提そのもの**を実 PDF で全数検証するゲート。
 *
 * ## なぜ検出でなくテストで守るか
 * 列の容量は行復元の土台で、外すと**行境界が全部壊れる**（しかも本文の字は 1 文字も落ちないので、
 * 字数系のゲートは全部緑のまま静かに壊れる）。一方でこの寸法は「揺れた実測が無い」項目なので、
 * [DetectedRules] へ足すと**検出が外れる面**を新設するだけになる（同 KDoc＝予防的検出が固定値より
 * 悪化した実例あり・ADR 0041 決定3）。よって固定前提＋ここでの全数検証という形にしてある。
 *
 * ## 何を確かめるか
 * 列の文字数分布に対し、
 * - 最長列＝容量+1（行頭禁則のぶら下がり・widow 回避だけが容量を 1 字超える）
 * - 最頻列長＝容量（折り返しは容量ちょうどでしか起きないので、そこに鋭い峰が立つ）
 * の 2 点。生成器が列高を変えれば両方同時にずれるので、片方だけの偶然一致では通らない。
 *
 * N6169DZ（8,668 ページ）は本テストの対象から外す＝同じ前提を確かめるのに 1 桁多い時間が要り、
 * かつ [WebAnchoredOracleTest] の S3-line が同文書の行構造そのものを見張っている（容量が外れれば
 * そちらが先に赤くなる）ため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextProcessorColumnCapacityTest {

    @Before
    fun initResourceLoader() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    @Test fun n2959ki_columnCapacityPremise() = assertPremise("sample_pdfs/N2959KI.pdf")

    @Test fun n1453lw_columnCapacityPremise() = assertPremise("sample_pdfs/N1453LW.pdf")

    @Test fun n5368ml_columnCapacityPremise() = assertPremise("sample_pdfs/N5368ML.pdf")

    @Test fun n0833hi_ep57_columnCapacityPremise() =
        assertPremise("android/app/src/test/resources/pdf_oracle/N0833HI_ep57.pdf")

    private fun assertPremise(relPath: String) {
        val pdf = File(repoRoot(), relPath)
        assertTrue("PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        val hist = PDDocument.load(pdf).use { doc ->
            val pages = PdfExtractor.loadPages(doc)
            val rules = DetectedRules.detect(pages)
            val counts = HashMap<Int, Int>()
            for ((pageNum, chars) in pages.withIndex()) {
                // 本文処理と同じ除外（先頭 3 ページ・最終ページ）と同じ列復元を通す。
                if (pageNum < 3 || pageNum >= pages.size - 1) continue
                val bodies = chars.filter {
                    !ParserRules.checkIsTitle(it.fontName, it.size, rules.bodySize) &&
                        ParserRules.isClose(it.size, rules.bodySize)
                }
                val cols = TextProcessor.groupCharsByLine(
                    bodies.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top }),
                    rules.lineStepX / 2.0,
                )
                for ((_, members) in cols) {
                    val n = members.size
                    if (n > 0) counts[n] = (counts[n] ?: 0) + 1
                }
            }
            counts
        }
        assertTrue("本文列が 1 本も取れていない（前提の検証が空回りする）: $relPath", hist.isNotEmpty())
        val longest = hist.keys.max()
        val mode = hist.maxByOrNull { it.value }!!.key
        val total = hist.values.sum()
        println("  [容量前提] $relPath 列数=$total 最長=$longest 最頻=$mode（${hist[mode]}本）")
        assertEquals(
            "最長列が容量+1 でない＝ぶら下がり以外で容量を超えている（列高が変わった疑い）: $relPath",
            ParserRules.COLUMN_CAPACITY + 1,
            longest,
        )
        assertEquals(
            "最頻列長が容量と違う＝折り返し位置が変わった（列高が変わった疑い）: $relPath",
            ParserRules.COLUMN_CAPACITY,
            mode,
        )
    }

    private fun repoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            if (File(d, "sample_pdfs").isDirectory && File(d, "android").isDirectory) return d
            d = d.parentFile
        }
        throw IllegalStateException("リポジトリルートが user.dir=${System.getProperty("user.dir")} から辿れない")
    }
}
