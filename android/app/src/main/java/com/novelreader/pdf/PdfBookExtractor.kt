package com.novelreader.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.Closeable
import java.io.File
import kotlin.math.max

/**
 * 抽出進捗の通知先。(step 0-3, stepLocal 0.0-1.0, phase メッセージ, 判明済みタイトル)。
 * BookRepository.ProgressListener.onProgress と同形（Phase 3 で直結するため）。
 */
typealias PdfProgress = (step: Int, stepLocal: Float, phase: String, title: String) -> Unit

/**
 * 本文抽出エンジンのフェーズ。LOAD=全ページのグリフ抽出(getText・超長編の支配的コスト)、PROCESS=段落化。
 * 進捗バーの step-1 local を両フェーズの重み合成で単調前進させるために区別する。
 * public なのは public な [PdfExtractor.runFinalEngine] のシグネチャに現れるため（PdfProgress と同流儀）。
 */
enum class EnginePhase { LOAD, PROCESS }

/**
 * 開いた PDF に対する抽出操作。PDDocument のライフサイクルを隠すテスト継ぎ目。
 * 本番は PDFBox を包み、ユニットテストは fake を差し込む（実 PDF/実機フォント資産なしで facade を検証するため）。
 */
internal interface PdfHandle : Closeable {
    fun extractMeta(): BookMeta
    fun runEngine(onProgress: (phase: EnginePhase, current: Int, total: Int) -> Unit): List<String>
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

            override fun runEngine(onProgress: (EnginePhase, Int, Int) -> Unit): List<String> =
                // runFinalEngine が LOAD/PROCESS を phase 付きで通知する。facade がそれを重み合成する。
                PdfExtractor.runFinalEngine(doc, onProgress)

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

                // 本文抽出は「読み込み(全ページのグリフ抽出)＋整形(段落化)」の2パスだが、UI では1つの連続進捗
                // として提示する。なぜ＝2パスを別ラベル・別カウンタで見せると、カウンタが 0→N を2度満ちて
                // 「一度終わって2周目に入った」ように誤認される（実機フィードバック）。表示とバーを常に一致させる。
                onProgress(1, 0f, "本文を処理しています… 0%", currentTitle)
                val paragraphs = handle.runEngine { phase, current, total ->
                    // step-1 local: LOAD(読み込み)中 0→LOAD_WEIGHT、PROCESS(整形)中 LOAD_WEIGHT→1.0 と単調前進。
                    // load を重み大に＝超長編では全ページ getText が支配的コストで、以前は load 中バーが 0f で固まって見えた。
                    val local = when (phase) {
                        EnginePhase.LOAD -> LOAD_WEIGHT * (current.toFloat() / max(total, 1))
                        EnginePhase.PROCESS -> LOAD_WEIGHT + (1f - LOAD_WEIGHT) * (current.toFloat() / max(total, 1))
                    }
                    // ラベルにも同じ % を載せ、下のバー(step-local)と数字を常に一致させる（リセット無しの通し進捗）。
                    onProgress(1, local, "本文を処理しています… ${(local * 100).toInt()}%", currentTitle)
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

    // 本文抽出 step-1 における load フェーズの進捗重み（0.0-1.0）。
    // なぜ大きく取るか＝超長編では全ページのグリフ抽出(getText)が支配的コストで、段落化(PROCESS)は
    // 相対的に軽い。実機(N6169DZ)で load 中にバーが 0 で固まって見えた UX の主因がここ。実機目視で調整可。
    private const val LOAD_WEIGHT = 0.75f
}
