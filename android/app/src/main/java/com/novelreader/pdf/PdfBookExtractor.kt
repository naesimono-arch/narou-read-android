package com.novelreader.pdf

import com.novelreader.trace.Sections
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
 * 移植元: app.process_pdf（4ステップ進捗・例外分類）＋ submission-B Main.extractBook（meta→本文→章→前後書き）。
 *
 * 抽出コア（PdfExtractor/ChapterProcessor/HtmlExporter）は移植済みの純ロジックをそのまま呼ぶ。
 *
 * ---
 * **※ この pdf パッケージ全体に関わる注記（代表箇所として本ファイルに集約）**
 *
 * 本パッケージの各所にある「移植元 python/…」「Python と同一」等のコメントは、**git 履歴上の出自**を
 * 指すだけである。PDF 抽出は 2026-07-05 に Chaquopy(Python) から純 Kotlin(PDFBox-Android) へ完全移植され、
 * **`python/` ディレクトリはリポジトリに実在しない**。したがって「Python 実装に合わせる」ことを
 * 現在の受入条件と読んではならない（追従すべき現物が無い＝照合不能）。
 *
 * 出力の受入条件は現在は次の2つが正本:
 * - HTML の書式 … `src/test/resources/golden_html/` ＋ `HtmlExporterGoldenTest`
 * - 本文抽出の結果 … `ab-review/golden_regression/` 配下の `.pdf.json` ＋ `JvmGoldenRegressionTest`
 *   （ここでワイルドカードを書かないのは、Kotlin のブロックコメントがネスト可能で
 *    パス中の `/` と `*` の並びがコメント開始として解釈され、KDoc が閉じなくなるため）
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
                // trace 区間（ロジック不変・計測のためだけの挿入）: 表紙1ページのメタ抽出。
                val meta = Sections.trace("Extract#meta") { handle.extractMeta() }
                currentTitle = meta.title

                // 本文抽出は「読み込み(全ページのグリフ抽出)＋整形(段落化)」の2パスだが、% は 0.75/0.25 の
                // 重み合成で通し単調前進として1つの連続進捗として提示する（%が巻き戻らない主表示）。
                // 副表示のページ n/m はフェーズ内カウントのため PROCESS 開始時に 0 へ戻るが、なぜそれで
                // 錯覚しないか＝フェーズ語を「読み込んでいます」「整形しています」と変えることで「別工程に
                // 入った」と読ませ、同じ語のままカウンタだけ 0→N を2度満ちる「2周目に入った」誤認
                // （過去に実機で発生）を構造的に解消する。開始時（tick 前）は総ページ未確定のためページ数を付けない。
                onProgress(1, 0f, "本文を読み込んでいます…", currentTitle)
                // trace 区間: 本文抽出エンジン（全ページのグリフ抽出＋段落化＝超長編の支配的コスト）。
                // 全て同期実行（runEngine 内に suspend 境界は無い）ため begin/end は同一スレッドで閉じ、
                // TraceSectionMetric が単一の slice として拾える。進捗コールバックの意味は不変。
                val paragraphs = Sections.trace("Extract#engine") {
                    handle.runEngine { phase, current, total ->
                    // step-1 local: LOAD(読み込み)中 0→LOAD_WEIGHT、PROCESS(整形)中 LOAD_WEIGHT→1.0 と単調前進。
                    // load を重み大に＝超長編では全ページ getText が支配的コストで、以前は load 中バーが 0f で固まって見えた。
                    // % 計算は一切変えない。フェーズ語だけをここで切り替える（副表示の巻き戻り錯覚回避）。
                    val local = when (phase) {
                        EnginePhase.LOAD -> LOAD_WEIGHT * (current.toFloat() / max(total, 1))
                        EnginePhase.PROCESS -> LOAD_WEIGHT + (1f - LOAD_WEIGHT) * (current.toFloat() / max(total, 1))
                    }
                    // フェーズ語（LOAD=読み込み/PROCESS=整形）。同語のままカウンタが 0→N を2度満ちる誤認を避ける。
                    val phaseWord = when (phase) {
                        EnginePhase.LOAD -> "本文を読み込んでいます"
                        EnginePhase.PROCESS -> "本文を整形しています"
                    }
                    // ラベルはページ n/m（フェーズ内カウント）のみ。通し進捗の数値% はラベルから外し下のバー(step-local)へ
                    // 一本化した（%とページ数の二重表示を解消し、狭幅でも末尾のページ数が省略で消えないように・ユーザー所見 2026-07-15）。
                    onProgress(1, local, "$phaseWord…（$current/${total}ページ）", currentTitle)
                    }
                }

                onProgress(2, 0f, "章を分割しています…", currentTitle)
                // 単話（【題名】マーカー皆無）では嘘見出し「作品情報・プロローグ」の代わりに
                // 表紙由来の作品タイトル meta.title を単一章タイトルへ流用する（裁定済み仕様）。
                // trace 区間: 章分割（段落列→章構造への分解）。
                val chaptersData = Sections.trace("Extract#splitChapters") {
                    ChapterProcessor.splitIntoChapters(paragraphs, meta.title)
                }

                onProgress(2, 1f, "前書き・後書きを処理しています…", currentTitle)
                val finalChapters = ChapterProcessor.processForewordAfterword(chaptersData)

                onProgress(3, 0f, "HTMLを生成しています…", currentTitle)
                // trace 区間: HTML 書き出し（章ごとの chap_N.html／index.html 生成）。
                Sections.trace("Extract#exportHtml") {
                    HtmlExporter.exportToPwa(finalChapters, meta.title, outputDir) { pct, phase ->
                        // app.py: _notify(3, (pct - 88) / 12, phase)。HtmlExporter は 88〜99 を出す。
                        onProgress(3, (pct - 88).toFloat() / 12, phase, currentTitle)
                    }
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
