package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 「[PdfExtractor.loadPages] のリスト位置＝実ページ番号」という契約を、**内容ストリームを持たない
 * ページを含む PDF** で見張る。
 *
 * ## 真因（この網が無いと黙って壊れる）
 * PDFBox の `PDFTextStripper.processPages` は `if (page.hasContents())` でページを走査対象から**外す**。
 * 素の実装ではそのページの記録ごと落ちるので、返り値の件数が文書のページ数より短くなり、
 * **以降のページが 1 つずつ手前へ詰まる**。下流の [PdfExtractor.runFinalEngine] はリスト位置を
 * ページ番号として扱う（「先頭 3 ページ＝表紙・最終ページ＝クレジットを捨てる」、および
 * ページ跨ぎの段落縫合）ため、空ページが 1 枚在るだけで**本文の先頭が削られる**
 * （実測: S3 fixture を白紙 padding で作った際、本文 11p のうち先頭 3p が消えた）。
 * 生成系 PDF に空ページが出ない前提に依存していた潜在欠陥で、症状は出力の欠落として現れる。
 *
 * fixture は S3 用のページ抜き PDF を**読むだけ**で、空ページを差し込んだ複製を一時ファイルへ書いて使う。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfLoadPagesIndexTest {

    @Before
    fun initResourceLoader() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun 内容ストリームの無いページを含んでもリスト位置がページ番号と一致する() {
        val src = fixturePdf()
        val base = PDDocument.load(src).use { PdfExtractor.loadPages(it).map { p -> p.size } }
        assertTrue("fixture の本文ページが少なすぎて検証にならない", base.count { it > 0 } >= 5)

        // 先頭・中間・末尾の 3 か所へ「内容ストリームを持たないページ」を差し込む。
        // 素の PDPage は /Contents を持たない＝PDFBox が走査対象から外す条件そのもの。
        val padded = File.createTempFile("loadpages-index", ".pdf")
        try {
            PDDocument.load(src).use { doc ->
                doc.pages.insertBefore(PDPage(), doc.getPage(0))
                doc.pages.insertBefore(PDPage(), doc.getPage(5)) // 挿入後の 5 番＝元の 4 番の手前
                doc.addPage(PDPage())
                doc.save(padded)
            }
            // 空ページのぶんだけ 0 グリフのページが挟まり、実ページのグリフ数は順序ごと保存される。
            val expected = listOf(0) + base.take(4) + listOf(0) + base.drop(4) + listOf(0)

            PDDocument.load(padded).use { doc ->
                assertEquals("差し込んだページ数が想定と違う", base.size + 3, doc.numberOfPages)
                val single = PdfExtractor.loadPages(doc)
                assertEquals(
                    "loadPages の件数がページ数と一致しない＝リスト位置がページ番号からズレている",
                    doc.numberOfPages,
                    single.size,
                )
                assertEquals("ページごとのグリフ数の並びが崩れている", expected, single.map { it.size })

                // 並列経路（ページ範囲分割）も同じ契約を満たすこと。範囲の切れ目に空ページが来る形も含む。
                for (k in 2..3) {
                    assertEquals(
                        "並列 loadPages(K=$k) が単一経路と一致しない",
                        expected,
                        PdfExtractor.loadPagesParallel(doc, padded, k).map { it.size },
                    )
                }

                // 低ヒープ経路（走査のたびに読み直す供給源）も同じ並びで供給すること。
                val streamed = mutableListOf<Pair<Int, Int>>()
                ReparsingPageSource(doc).forEachPage { i, chars -> streamed.add(i to chars.size) }
                assertEquals(
                    "ReparsingPageSource のページ番号が実ページ番号とズレている",
                    expected.indices.map { it to expected[it] },
                    streamed,
                )
            }
        } finally {
            padded.delete()
        }
    }

    private fun fixturePdf(): File {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            val f = File(d, "android/app/src/test/resources/pdf_oracle/N0833HI_ep57.pdf")
            if (f.isFile) return f
            d = d.parentFile
        }
        throw IllegalStateException("fixture PDF が user.dir=${System.getProperty("user.dir")} から辿れない")
    }
}
