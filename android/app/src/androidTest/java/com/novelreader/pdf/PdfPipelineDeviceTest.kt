package com.novelreader.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Task 9 実機フル疎通（穴3 の全経路 KILL の土台＝抽出→HTML→本棚→リーダー描画）。
 *
 * full facade [PdfBookExtractor.process]（meta→本文→章→前後書き→HtmlExporter）を実機で回し、
 * 3PDF の Kotlin 生成 HTML を **実アプリの本棚**（`filesDir/novels/<id>` ＋ `books` テーブル）へシードする。
 * これは BookRepository が Phase 3 で行う経路と同一（Chaquopy 呼出を PdfBookExtractor へ差し替えたもの）ゆえ、
 * 「Kotlin パイプラインが実機で通り、既存リーダーがその出力を描画できる」ことを Phase 3 配線前に検証できる。
 *
 * テスト自身が assert するのは疎通（index.html/chap_1.html が生成される）まで。
 * **リーダー目視関門**（plan Task9 ③：ふりがな位置・章送り・前後書き囲み・シーン区切り）は人間が行う：
 * `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` でアプリを残し、
 * テスト後に本棚へ並ぶ3冊を開いて目視する（[[workflow-notify-each-step-visual-check]]）。
 *
 * 実PDF資産は gitignore（bring-your-own）。正本は sample_pdfs/ と ab-review/golden_regression/。
 */
@RunWith(AndroidJUnit4::class)
class PdfPipelineDeviceTest {

    @Before
    fun initResourceLoader() {
        // facade 内の PDDocument.load より前に一度必要（task_diary #31）。
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    // (アセット名, 本棚に出す安定 bookId)。bookId 固定＝REPLACE で再実行のたびに上書きし本棚を汚さない。
    private val fixtures = listOf(
        "N1453LW.pdf" to "spike-N1453LW",
        "N2959KI.pdf" to "spike-N2959KI",
        "N6169DZ.pdf" to "spike-N6169DZ",
    )

    @Test
    fun seedBookshelfWithKotlinPipeline() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        Assume.assumeTrue(
            "androidTest/assets/spike/ に実PDFが無いためスキップ（配置は sample_pdfs/ と ab-review/golden_regression/ から）",
            testAssets.list("spike")?.isNotEmpty() == true,
        )
        val dao = AppDatabase.getDatabase(ctx).bookDao()

        for ((asset, bookId) in fixtures) {
            val pdf = File.createTempFile(bookId, ".pdf", ctx.cacheDir)
            testAssets.open("spike/$asset").use { input -> pdf.outputStream().use { input.copyTo(it) } }

            // BookRepository:76 と同一の出力先レイアウト。
            val outputDir = File(ctx.filesDir, "novels/$bookId").apply { mkdirs() }
            val meta = try {
                PdfBookExtractor.process(pdf, bookId, outputDir)
            } finally {
                pdf.delete()
            }

            // HtmlExporter が実機で走り生成物が出たことの疎通確認。
            val index = File(outputDir, "index.html")
            val chap1 = File(outputDir, "chap_1.html")
            assertTrue("$asset: index.html 未生成", index.exists() && index.length() > 0)
            assertTrue("$asset: chap_1.html 未生成", chap1.exists() && chap1.length() > 0)

            // 実本棚へ登録（BookRepository:98 と同形）。目視のため addedAt を大きな固定値にして最近クラスタの先頭へ。
            dao.insertBook(
                BookEntity(bookId, meta.title, outputDir.absolutePath, meta.author, addedAt = SEED_ADDED_AT)
            )
        }
    }

    companion object {
        // 目視用シードを本棚先頭へ出すための固定 addedAt（実 addedAt より十分大きい未来値・Date 非依存で決定的）。
        private const val SEED_ADDED_AT = 4_000_000_000_000L
    }
}
