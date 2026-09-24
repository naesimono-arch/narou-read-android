package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * ページ範囲並列 [PdfExtractor.loadPagesParallel] の**等価性・進捗・キャンセル・並列度決定**を
 * 実 PDF で縛る恒久ゲート。
 *
 * なぜ実機ではなく JVM に置くか: 並列化の採否根拠（短縮比・ヒープのピーク）は実機でしか測れないが、
 * ここで守りたいのは**結果が単一走行と 1 グリフも違わないこと**で、それは CPU が実機か JVM かに依存しない。
 * 実機 [PdfExtractorDeviceSpikeTest] / androidTest のスパイクは端末が要り毎回は回せないので、
 * `testDebugUnitTest` で毎回踏める形をここに置く（[JvmGoldenRegressionTest] と同じ思想）。
 *
 * ⚠ K は本番では [PdfExtractor.parallelDegree] がヒープから決めるが、**このテストは K を固定して**呼ぶ。
 * 等価性の網が実行機のヒープ次第で緩む（余裕が無い機械では K=1 に落ちて何も検証しない）のを避けるため。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfParallelLoadPagesTest {

    @Before
    fun initResourceLoader() {
        // PDDocument.load の前に一度だけ（AAR 同梱 CMap/glyphlist のロード。欠くと CID→Unicode 解決がズレる）。
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    /**
     * 15 ページの短編で K=2..4 の全分割を単一走行と全フィールド比較する。
     * 短いので余り（15=8+7 / 5+5+5 / 4+4+4+3）のパターンを安く全部踏める。
     */
    @Test
    fun shortWork_parallelResultIsIdenticalToSingle_forEveryDegree() {
        val pdf = fixture("N1453LW")
        val single = PDDocument.load(pdf).use { PdfExtractor.loadPages(it) }
        for (degree in 2..4) {
            val parallel = PDDocument.load(pdf).use { PdfExtractor.loadPagesParallel(it, pdf, degree) }
            assertPagesIdentical("N1453LW K=$degree", single, parallel)
        }
    }

    /**
     * 中編 799 ページ・K=3（実機で 2.7x が出た組み合わせ）を単一走行と全フィールド比較する。
     * 799÷3 は割り切れない＝余りを先頭スレッドへ配る分割が境界ページを重複/欠落させないことも同時に縛る。
     */
    @Test
    fun mediumWork_parallelResultIsIdenticalToSingle() {
        val pdf = fixture("N2959KI")
        val single = PDDocument.load(pdf).use { PdfExtractor.loadPages(it) }
        val parallel = PDDocument.load(pdf).use { PdfExtractor.loadPagesParallel(it, pdf, 3) }
        assertPagesIdentical("N2959KI K=3", single, parallel)
    }

    /**
     * 並列でも進捗は「1度に1スレッドから・単調増加で・最後は総ページ数」で届く。
     * 単一走行前提で書かれた下流（PdfProcessingService の通知間引き）の契約がここで守られる。
     */
    @Test
    fun progress_isSerializedMonotonicAndReachesTotal() {
        val pdf = fixture("N1453LW")
        val seen = ArrayList<Int>()
        val totals = HashSet<Int>()
        val concurrent = java.util.concurrent.atomic.AtomicBoolean(false)
        val inCallback = java.util.concurrent.atomic.AtomicInteger(0)

        val pages = PDDocument.load(pdf).use { doc ->
            PdfExtractor.loadPagesParallel(doc, pdf, 3) { loaded, total ->
                // 同時進入の検出（直列化が壊れたらここが true になる）。
                if (inCallback.incrementAndGet() != 1) concurrent.set(true)
                seen.add(loaded)
                totals.add(total)
                inCallback.decrementAndGet()
            }
        }

        assertTrue("進捗コールバックが同時に2スレッドから呼ばれた（直列化が壊れている）", !concurrent.get())
        assertEquals("total は常に総ページ数", setOf(pages.size), totals)
        assertTrue("進捗が1件も出ていない", seen.isNotEmpty())
        assertEquals("最後の通知は総ページ数でなければならない", pages.size, seen.last())
        for (i in 1 until seen.size) {
            assertTrue("進捗が後退した: ${seen[i - 1]} → ${seen[i]}（全体=$seen）", seen[i] > seen[i - 1])
        }
    }

    /**
     * 進捗コールバックからの中断（本番では `ensureActive()` の CancellationException）が
     * **そのままの例外オブジェクトで**呼び出し元へ届き、かつ全ワーカーが停止していること。
     *
     * なぜ「そのまま」が要るか: 呼び出し元 PdfBookImporter は `if (e is CancellationException) throw e` で
     * 停止と失敗を見分ける。ExecutionException に包んだまま返すと「停止」が Unknown エラーに化ける。
     * なぜスレッド停止まで見るか: 各ワーカーの PDDocument は `use` で閉じるので、**ワーカーが終了している
     * ことが閉じ終えたことの観測可能な証跡**になる（開いたままなら数十〜数百MBを掴んだままになる）。
     */
    @Test
    fun cancellation_propagatesOriginalThrowable_andLeavesNoRunningWorker() {
        val pdf = fixture("N2959KI") // 799ページ＝5ページ目で止めた後に走り続ける余地が十分ある
        val stop = RuntimeException("テスト用の中断（本番の CancellationException 相当）")

        val thrown = try {
            PDDocument.load(pdf).use { doc ->
                PdfExtractor.loadPagesParallel(doc, pdf, 3) { loaded, _ -> if (loaded >= 5) throw stop }
            }
            null
        } catch (t: Throwable) {
            t
        }

        assertSame("中断例外が包まれず同一オブジェクトで伝播すること", stop, thrown)
        assertEquals(
            "打ち切りの理由（他スレッドの失敗）で本来の例外が差し替わっていないこと",
            0,
            thrown!!.suppressedExceptions.size,
        )
        assertNoLoaderThreadAlive()
    }

    /**
     * 並列度の決定式を PGEM10 実測の数値で固定する。ここが緩むと 192〜256MB 上限の端末で
     * K を許してしまい、実機では OutOfMemoryError すら出ずに OEM がプロセスごと殺す（o-kill(109)）。
     */
    @Test
    fun parallelDegree_matchesMeasuredHeapCeiling() {
        // PGEM10（heapgrowthlimit=384m・8コア）: 長編は単一 1 セットで 276MB を使うので上乗せは1本ぶんだけ。
        assertEquals("PGEM10 長編 8,668ページ", 2, degree(8_668, mb = 384, cores = 8))
        // 192〜256MB が上限の端末では長編は単一ですら危うい＝並列化しない。
        assertEquals("256MB 端末 長編", 1, degree(8_668, mb = 256, cores = 8))
        assertEquals("192MB 端末 長編", 1, degree(8_668, mb = 192, cores = 8))
        // 中編は 1 セットが小さいので上限の低い端末でも K=3（実機 2.7x）。
        assertEquals("PGEM10 中編 799ページ", 3, degree(799, mb = 384, cores = 8))
        assertEquals("128MB 端末 中編", 3, degree(799, mb = 128, cores = 8))
        // それでも足りない端末では単一へ落ちる。
        assertEquals("64MB 端末 中編", 1, degree(799, mb = 64, cores = 8))
        assertEquals("PGEM10 4,000ページ", 3, degree(4_000, mb = 384, cores = 8))
        // 上限は3つ: 実機最良の K=3・コア数・1スレッドあたりの最小持ち分（256ページ）。
        assertEquals("ヒープが潤沢でも K は 3 まで", 3, degree(4_000, mb = 2_048, cores = 8))
        assertEquals("コア数が上限になる", 2, degree(8_668, mb = 1_024, cores = 2))
        assertEquals("511ページは 1 スレッドぶんしか持ち分が無い", 1, degree(511, mb = 384, cores = 8))
        assertEquals("512ページで初めて 2 分割できる", 2, degree(512, mb = 384, cores = 8))
        assertEquals("短編は並列化しない", 1, degree(15, mb = 384, cores = 8))
        assertEquals("0ページでも 1 を返す（単一経路）", 1, degree(0, mb = 384, cores = 8))
    }

    // ==========================================================
    // ヘルパ
    // ==========================================================

    private fun degree(pages: Int, mb: Int, cores: Int): Int =
        PdfExtractor.parallelDegree(pages, mb.toLong() * 1024L * 1024L, cores)

    /** ページ数・ページ毎の文字数・全 CharBox（全フィールド）を突き合わせる。最初の食い違いで落とす。 */
    private fun assertPagesIdentical(
        label: String,
        expected: List<List<CharBox>>,
        actual: List<List<CharBox>>,
    ) {
        assertEquals("$label: ページ数", expected.size, actual.size)
        for (p in expected.indices) {
            val e = expected[p]
            val a = actual[p]
            assertEquals("$label: page $p の文字数", e.size, a.size)
            for (i in e.indices) {
                // CharBox は data class＝text/fontName/size/x0/top/bottom の全フィールド比較になる。
                assertEquals("$label: page $p char $i", e[i], a[i])
            }
        }
    }

    /**
     * 並列ワーカー（[PdfExtractor.LOAD_THREAD_PREFIX] 接頭辞）が 1 本も生きていないことを確認する。
     * スレッドの消滅は awaitTermination の直後でも数ミリ秒遅れうるので、短くポーリングして判定する。
     */
    private fun assertNoLoaderThreadAlive() {
        val deadline = System.nanoTime() + 3_000_000_000L
        var alive = liveLoaderThreads()
        while (alive.isNotEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20)
            alive = liveLoaderThreads()
        }
        assertTrue("並列ワーカーが停止していない（PDDocument が開いたままの疑い）: $alive", alive.isEmpty())
    }

    private fun liveLoaderThreads(): List<String> =
        Thread.getAllStackTraces().keys
            .filter { it.isAlive && it.name.startsWith(PdfExtractor.LOAD_THREAD_PREFIX) }
            .map { it.name }

    private fun fixture(name: String): File {
        val pdf = File(resolveRepoRoot(), "sample_pdfs/$name.pdf")
        assertTrue("PDF が無い: ${pdf.absolutePath}", pdf.isFile)
        return pdf
    }

    /** gradle の cwd がモジュールでもルートでも解決できるよう user.dir から遡る（JvmGoldenRegressionTest と同一）。 */
    private fun resolveRepoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            if (File(d, "sample_pdfs").isDirectory) return d
            d = d.parentFile
        }
        throw IllegalStateException("sample_pdfs/ を含むリポジトリルートが user.dir=${System.getProperty("user.dir")} から見つからない")
    }
}
