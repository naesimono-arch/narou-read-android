package com.novelreader.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.Closeable
import java.io.File
import java.util.Locale
import kotlin.math.max

/**
 * 抽出進捗の通知先。(step 0-3, stepLocal 0.0-1.0, phase メッセージ, 判明済みタイトル)。
 * BookRepository.ProgressListener.onProgress と同形（Phase 3 で直結するため）。
 */
typealias PdfProgress = (step: Int, stepLocal: Float, phase: String, title: String) -> Unit

/**
 * 開いた PDF に対する抽出操作。PDDocument のライフサイクルを隠すテスト継ぎ目。
 * 本番は PDFBox を包み、ユニットテストは fake を差し込む（実 PDF/実機フォント資産なしで facade を検証するため）。
 */
internal interface PdfHandle : Closeable {
    fun extractMeta(): BookMeta
    fun runEngine(onPageProgress: (processed: Int, bodyTotal: Int) -> Unit): List<String>
}

/** PDF を開くエンジン。 */
internal interface PdfEngine {
    /** PDF を開く。読込失敗（暗号化/破損）は生の例外を投げてよい（facade が classifyPdfError で分類する）。 */
    fun open(pdfFile: File): PdfHandle
}

/** 本番実装＝PDFBox 直結（移植元 submission-B loadDocument + extractBook のメタ/本文抽出部）。 */
internal object PdfBoxEngine : PdfEngine {
    override fun open(pdfFile: File): PdfHandle {
        // ⚠️ 実機で CID→Unicode グリフ解決を正しく行うには、この PDDocument.load の前に
        // PDFBoxResourceLoader.init(applicationContext) がアプリ起動時（Context 必須）に一度呼ばれている
        // 必要がある。Phase 3（BookRepository 切替）で MainActivity/Application へ配線する。
        // androidTest では @Before で instrumentation Context により init する（task_diary #31）。
        val doc = PDDocument.load(pdfFile)
        return object : PdfHandle {
            override fun extractMeta(): BookMeta = PdfExtractor.extractBookMeta(doc)

            override fun runEngine(onPageProgress: (Int, Int) -> Unit): List<String> =
                // PdfExtractor の (pct, processed, bodyTotal) から pct を捨て、facade が必要とする
                // (processed, bodyTotal) だけを前送りする。
                PdfExtractor.runFinalEngine(doc) { _, processed, bodyTotal -> onPageProgress(processed, bodyTotal) }

            override fun close() = doc.close()
        }
    }
}

/**
 * PDF 1 冊を HTML（index.html / chap_N.html）へ変換するオーケストレータ。
 * 移植元: python app.process_pdf（4ステップ進捗・例外分類）＋ submission-B Main.extractBook（meta→本文→章→前後書き）。
 *
 * 抽出コア（PdfExtractor/ChapterProcessor/HtmlExporter）は移植済みの純ロジックをそのまま呼ぶ。
 */
object PdfBookExtractor {

    /**
     * PDF を処理し書籍メタ(title/author)を返す。失敗は [PdfExtractionException] へ分類して投げる。
     * @param onProgress 4ステップ進捗の通知（step 0=タイトル/1=本文/2=章/3=HTML）。
     */
    fun process(
        pdfFile: File,
        bookId: String,
        outputDir: File,
        onProgress: PdfProgress = { _, _, _, _ -> },
    ): BookMeta = process(PdfBoxEngine, pdfFile, bookId, outputDir, onProgress)

    // engine を差し替え可能にした内部実装（ユニットテストが fake engine を注入し、進捗列と例外分類を被覆する）。
    internal fun process(
        engine: PdfEngine,
        pdfFile: File,
        bookId: String,
        outputDir: File,
        onProgress: PdfProgress,
    ): BookMeta {
        // 判明済みタイトル。step0 で確定するまでは空（UI の「変換中タイトル」表示用）。
        // クロージャは参照時に読むため、meta 確定後の代入が後続 step へ反映される（app.py current_title と同挙動）。
        var currentTitle = ""
        return try {
            engine.open(pdfFile).use { handle ->
                onProgress(0, 0f, "タイトルを読み取っています…", currentTitle)
                val meta = handle.extractMeta()
                currentTitle = meta.title

                onProgress(1, 0f, "本文を抽出しています…", currentTitle)
                val paragraphs = handle.runEngine { processed, bodyTotal ->
                    // app.py: _notify(1, cur / max(tot, 1), f"…({cur+1:,}/{tot:,}ページ)")
                    onProgress(
                        1,
                        processed.toFloat() / max(bodyTotal, 1),
                        "本文を抽出しています… (${grouped(processed + 1)}/${grouped(bodyTotal)}ページ)",
                        currentTitle,
                    )
                }

                onProgress(2, 0f, "章を分割しています…", currentTitle)
                val chaptersData = ChapterProcessor.splitIntoChapters(paragraphs)

                onProgress(2, 1f, "前書き・後書きを処理しています…", currentTitle)
                val finalChapters = ChapterProcessor.processForewordAfterword(chaptersData)

                onProgress(3, 0f, "HTMLを生成しています…", currentTitle)
                HtmlExporter.exportToPwa(finalChapters, bookId, meta.title, outputDir) { pct, phase ->
                    // app.py: _notify(3, (pct - 88) / 12, phase)。HtmlExporter は 88〜99 を出す。
                    onProgress(3, (pct - 88).toFloat() / 12, phase, currentTitle)
                }

                BookMeta(meta.title, meta.author)
            }
        } catch (e: Throwable) {
            // app.py process_pdf の except 節に相当。分類できない例外は型・トレースを保持したまま素通し。
            throw classifyPdfError(e)
        }
    }

    // Python f"{n:,}" 相当（3 桁区切りカンマ・ロケール非依存に US 固定）。
    private fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
