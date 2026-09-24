package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * 抽出エンジン（`Extract#engine`）の**内訳**を JVM 上で計測するプロファイラ。ゲートではない。
 *
 * なぜ要るか: 実機の総時間は既に取れている（`ImportBudget` に OPPO PGEM10 実測＝
 * Import#extract median 24.1s / Extract#engine median 22.7s＝extract の 94%）が、
 * **engine 22.7 秒の内訳が無い**。どのフェーズが支配的かを知らずに形状を変えるのは
 * 当て推量になるため、まず「コストの正体」を測る道具としてこれを置く。
 *
 * なぜ本番コードに計測を挿さないか: `Sections.trace` は JVM では完全 no-op（`Sections.available=false`）
 * で内訳を出せない。かといって本番へ JVM 用の計測経路を足すと、ゴールデン回帰が踏む生産コードに
 * 計測専用の分岐が住み着く。[PdfExtractor.runFinalEngine] が呼ぶ3段はいずれも object の関数として
 * 外から個別に呼べるので、**本番を一切変更せず**テスト側から同じ順序で叩いて測る。
 *
 * ⚠ 絶対値の正本ではない: Robolectric/JVM は実機と CPU・JIT・GC が異なるため、ここで出るミリ秒は
 * 実機値とは一致しない。**読むべきは総和に対する各フェーズの比率**で、最適化の効果判定の正本は
 * 実機 Macrobenchmark（`PdfImportBenchmark` + `ImportBudget`）のまま。
 *
 * ⚠ [phaseProbe] は [TextProcessor.processPages] の内部と同じ呼び方を**再現したプローブ**であり、
 * 本体のコピーである。本体（TextProcessor.kt の分類ループ〜列テキスト化）を変更したら、この再現も
 * 追従させないと内訳がズレる。
 *
 * プローブ合計は processPages 実測に**届かないのが正常**（初回実測で被覆率 46.6%）。理由は2つとも
 * 設計どおりで、異常ではない:
 * - プローブは段落縫合（isNewParagraph 判定・currentParagraph.append・allParagraphs.add）を
 *   測っていない＝processPages の一部しか覆っていない。
 * - プローブは processPages の**後**に走るため同じコードが JIT で温まっており、各段が速く出る。
 * よって自己検証は「下回ったら警告」ではなく「**上回ったら警告**」で行う（プローブが本体より遅いのは、
 * 本体の変更に追従できていないか計測自体が壊れている兆候）。
 *
 * 実行方法（既定では走らない＝CI と日常の testDebugUnitTest を遅くしないため）:
 * ```
 * EXTRACT_PROFILE=N2959KI gw … :app:testDebugUnitTest --tests "*ExtractPhaseProfileTest*" --rerun
 * ```
 * ⚠ `--rerun` は必須。Gradle は**環境変数を test タスクの入力として見ない**ため、フィクスチャ名だけを
 * 変えて再実行すると `testDebugUnitTest UP-TO-DATE` でスキップされ、BUILD SUCCESSFUL のまま
 * **前回のフィクスチャの XML が残り続ける**（実際に踏んだ＝N2959KI の結果を N6169DZ のものと
 * 誤読しかけた）。付け忘れても気づけるよう、レポート冒頭に対象名と実行時刻を必ず出す。
 *
 * 環境変数（システムプロパティではなく）で切り替えるのは、Gradle の test タスクが環境変数を
 * そのまま子へ渡す一方、`-D` はビルドスクリプト側の明示的な転送設定が要るため（build.gradle を
 * 計測都合で変更したくない）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtractPhaseProfileTest {

    @Before
    fun initResourceLoader() {
        // 本番 NovelReaderApplication.onCreate と同じく PDDocument.load の前に一度だけ init
        // （JvmGoldenRegressionTest と同一理由＝CMap/glyphlist を実機と揃える）。
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun profileEnginePhases() {
        val fixture = System.getenv("EXTRACT_PROFILE")
        // 未指定はスキップ。計測は分オーダー（大PDF）になり得るため既定では走らせない。
        // assumeTrue の理由をメッセージに残す＝「なぜ緑なのに何も出ないのか」を後から追える。
        assumeTrue(
            "EXTRACT_PROFILE 未設定のためスキップ（例: EXTRACT_PROFILE=N2959KI）。" +
                "これはゲートではなく内訳計測用のプロファイラ。",
            !fixture.isNullOrBlank(),
        )
        val name = fixture!!.trim()

        val repoRoot = resolveRepoRoot()
        val pdf = File(repoRoot, "sample_pdfs/$name.pdf")
        check(pdf.isFile) { "PDF が無い: ${pdf.absolutePath}" }

        // 反復回数（既定1）。JIT が温まると比率が変わるため、比率を精査したいときだけ増やす。
        val repeats = System.getenv("EXTRACT_PROFILE_REPEATS")?.trim()?.toIntOrNull() ?: 1

        val report = StringBuilder()
        // 対象名と実行時刻を必ず出す＝`--rerun` 付け忘れで古い XML を読んだときに気づけるようにする
        // （Gradle が環境変数を入力と見ない罠＝クラス KDoc の ⚠。ImportBudget が benchmarkData.json の
        // lastModified で残骸 JSON を弾くのと同じ発想を、こちらは出力側の自己申告で行う）。
        report.appendLine(
            "=== ExtractPhaseProfile: $name (${pdf.length() / 1024}KB) repeats=$repeats " +
                "at ${java.time.Instant.ofEpochMilli(System.currentTimeMillis())} ==="
        )

        repeat(repeats) { iteration ->
            PDDocument.load(pdf).use { doc ->
                val totalPages = doc.numberOfPages

                // ---- 段① グリフ収集（processTextPosition が1グリフごとに走る） ----
                val loadStart = System.nanoTime()
                val charListsByPage = PdfExtractor.loadPages(doc)
                val loadMs = elapsedMs(loadStart)

                // ---- 段② 解析パラメータ検出（内部で groupCharsByLine を本処理とは別に再実行する） ----
                val detectStart = System.nanoTime()
                val rules = DetectedRules.detect(charListsByPage)
                val detectMs = elapsedMs(detectStart)

                // ---- 段③ 本処理 ----
                val processStart = System.nanoTime()
                val paragraphs = TextProcessor.processPages(charListsByPage, totalPages, rules)
                val processMs = elapsedMs(processStart)

                val engineMs = loadMs + detectMs + processMs
                val glyphCount = charListsByPage.sumOf { it.size }

                report.appendLine("--- iteration ${iteration + 1} ---")
                report.appendLine("pages=$totalPages glyphs=$glyphCount paragraphs=${paragraphs.size}")
                report.appendLine(line("① loadPages", loadMs, engineMs))
                report.appendLine(line("② DetectedRules.detect", detectMs, engineMs))
                report.appendLine(line("③ processPages", processMs, engineMs))
                report.appendLine("engine 合計: ${fmt(engineMs)}ms")

                // ---- 段③の内訳プローブ ----
                val probe = phaseProbe(charListsByPage, totalPages, rules)
                report.appendLine("  [processPages 内訳プローブ]")
                report.appendLine(line("  ③a 文字分類ループ", probe.classifyMs, processMs))
                report.appendLine(line("  ③b bodies ソート", probe.sortMs, processMs))
                report.appendLine(line("  ③c groupCharsByLine", probe.groupMs, processMs))
                report.appendLine(line("  ③d associateRuby", probe.rubyMs, processMs))
                report.appendLine(line("  ③e buildLineStr(列)", probe.buildMs, processMs))
                report.appendLine(
                    "  プローブ合計: ${fmt(probe.totalMs)}ms / processPages 実測 ${fmt(processMs)}ms" +
                        " (被覆率 ${fmt(probe.totalMs / processMs * 100)}%＝100%未満は正常)"
                )
                // 被覆率が 100% に届かないのは設計どおり（段落縫合を測らない＋JIT 差＝クラス KDoc）。
                // 逆に本体を上回ったら、プローブが本体から乖離したか計測が壊れた兆候として警告する。
                if (probe.totalMs > processMs) {
                    report.appendLine(
                        "  ⚠ プローブ合計が processPages 実測を上回った＝本体への追従漏れか計測破損の疑い。" +
                            "内訳を信用せず phaseProbe を TextProcessor.processPages と突き合わせること。"
                    )
                }
            }
        }

        // Gradle の test XML（build/test-results/**/*.xml）に system-out として必ず残るため、
        // testLogging の設定に依存せず後から回収できる。
        println(report)
    }

    /**
     * 支配区間 [PdfExtractor.loadPages]（engine の 66〜69%）を「PDFBox 側のコスト」と
     * 「こちらが足しているコスト」に分離する。前者が大半なら、この層でできることは無い
     * （＝最適化の上限がライブラリで決まる）ことが確定する。
     *
     * 分離が成立する根拠: [GlyphStripper.processTextPosition] は `super` を呼んでいない＝
     * PDFTextStripper 既定のテキストバッファ書き込みは元から走らない。よって同じく super を
     * 呼ばない [ParseOnlyStripper] との差分は、[GlyphDecoder] による字の決定 + [CharBox] 生成 +
     * リスト追加だけになる。
     */
    @Test
    fun profileLoadPagesBreakdown() {
        val fixture = System.getenv("EXTRACT_PROFILE")
        assumeTrue(
            "EXTRACT_PROFILE 未設定のためスキップ（例: EXTRACT_PROFILE=N2959KI）。",
            !fixture.isNullOrBlank(),
        )
        val name = fixture!!.trim()
        val pdf = File(resolveRepoRoot(), "sample_pdfs/$name.pdf")
        check(pdf.isFile) { "PDF が無い: ${pdf.absolutePath}" }

        // 先に走る方が JIT で不利になるのを避けるため、両者を1往復ウォームアップしてから計測する。
        runStripper(pdf, collectCharBoxes = false)
        runStripper(pdf, collectCharBoxes = true)

        val parseOnlyMs = runStripper(pdf, collectCharBoxes = false)
        val fullMs = runStripper(pdf, collectCharBoxes = true)
        val ownMs = fullMs - parseOnlyMs

        val report = StringBuilder()
        report.appendLine(
            "=== LoadPagesBreakdown: $name at " +
                "${java.time.Instant.ofEpochMilli(System.currentTimeMillis())} ==="
        )
        report.appendLine("PDFBox パース+TextPosition のみ: ${fmt(parseOnlyMs)}ms")
        report.appendLine("GlyphStripper（本番）:           ${fmt(fullMs)}ms")
        report.appendLine(
            "差分＝自前(normalize+CharBox生成): ${fmt(ownMs)}ms " +
                "(loadPages の ${fmt(ownMs / fullMs * 100)}%)"
        )
        // 差分が負になるのは計測ノイズがコスト差を上回った証拠＝自前コストは誤差以下と読む
        // （0 と報告して「無い」と断定せず、測れなかったことを明示する）。
        if (ownMs < 0) {
            report.appendLine("⚠ 差分が負＝自前コストは計測ノイズ以下。有意差なしと読むこと。")
        }
        println(report)
    }

    /**
     * engine の 22% を占める [DetectedRules.detect] の内訳。可動域のうち費用対効果が最も良い候補が
     * どのブロックかを決めるために測る。
     *
     * ⚠ 被覆外: `modeBucketKey` / `bucketModeRefined`（private のため呼べない）。どちらも
     * **構築済みリストに対する集計**で、重いのはリスト構築側（ここで測る A/B/D/E）と見込んでいるが、
     * その見込み自体は未検証なので被覆率の残差に含まれる。
     *
     * プローブの `filter` は [DetectedRules.detect] の実出力 `bodySize` / `rubySize` を使うため、
     * 本体と厳密に同じ集合を対象にする（自前で閾値を再現すると乖離するため）。
     */
    @Test
    fun profileDetectBreakdown() {
        val fixture = System.getenv("EXTRACT_PROFILE")
        assumeTrue(
            "EXTRACT_PROFILE 未設定のためスキップ（例: EXTRACT_PROFILE=N2959KI）。",
            !fixture.isNullOrBlank(),
        )
        val name = fixture!!.trim()
        val pdf = File(resolveRepoRoot(), "sample_pdfs/$name.pdf")
        check(pdf.isFile) { "PDF が無い: ${pdf.absolutePath}" }

        PDDocument.load(pdf).use { doc ->
            val charListsByPage = PdfExtractor.loadPages(doc)

            // 本体を1回ウォームアップしてから実測する（プローブ側だけ JIT で温まる不公平を避ける）。
            DetectedRules.detect(charListsByPage)
            val detectStart = System.nanoTime()
            val rules = DetectedRules.detect(charListsByPage)
            val detectMs = elapsedMs(detectStart)

            val bodySize = rules.bodySize
            val rubySize = rules.rubySize

            // --- A: bodySize ヒストグラム。畳み込み「前」と「後」の両形状を同一走行で測って比で読む
            //     （理由は下の D と同じ＝走行をまたいだ絶対値比較は機械側の振れと区別できない）。
            //     旧形状は flatten() で全グリフのコピー、さらに map{ it.size } で boxed Double の
            //     リストを作ってからカウントする＝ヒストグラム1本のために一時リストを2本確保していた。
            //     ⚠ 両プローブとも一時リストを関数スコープに閉じ込めてある。参照を外へ残すと本体には
            //     無い延命が起き、GC 圧の差で後続ブロック(C/D)が過大に出る（初回実測で被覆率 108% を
            //     踏んだ真因がこれ）。件数は charListsByPage から別に数える。
            val glyphCount = charListsByPage.sumOf { it.size }
            probeBodyHistogramOldShape(charListsByPage) // 両形状をウォームアップしてから測る
            probeBodyHistogramMergedShape(charListsByPage)
            val histOldMs = probeBodyHistogramOldShape(charListsByPage)
            val histMs = probeBodyHistogramMergedShape(charListsByPage)

            // --- C: ページ番号シグネチャの収集（全ページ×全文字・Pair キーの HashMap） ---
            val tC = System.nanoTime()
            val comboPages = HashMap<Pair<Double, Double>, MutableSet<Int>>()
            for ((pi, page) in charListsByPage.withIndex()) {
                for (c in page) {
                    if (ParserRules.isClose(c.size, bodySize)) continue
                    // private な bucket01 と同一式（時間計測用の写し。値の一致が目的ではない）。
                    val key = (Math.round(c.size * 10.0) / 10.0) to Math.round(c.top).toDouble()
                    comboPages.getOrPut(key) { mutableSetOf() }.add(pi)
                }
            }
            val comboMs = elapsedMs(tC)

            // --- D: 列復元ループ。畳み込み「前」と「後」の両形状を同一走行で測って比で読む。
            //     なぜ同一走行での対比が要るか: 走行ごとに機械側の速度が大きく振れ、**変更していない**
            //     loadPages が同じコードのまま 3.5s→5.8s、processPages が 0.7s→1.0s に振れた走行を実測した。
            //     走行をまたいだ絶対値比較では変更の効果と機械の振れを分離できないため、旧形状と新形状を
            //     同じ JVM・同じ入力で並べて測る（比なら機械の速度で割り戻される）。
            //     旧形状は本体からは既に消えているので、この対照群がその唯一の記録でもある。
            probeColsOldShape(charListsByPage, bodySize, rubySize) // 両形状をウォームアップしてから測る
            probeColsMergedShape(charListsByPage, bodySize, rubySize)
            val oldShapeMs = probeColsOldShape(charListsByPage, bodySize, rubySize)
            val colsMs = probeColsMergedShape(charListsByPage, bodySize, rubySize)

            val probeTotal = histMs + comboMs + colsMs
            val report = StringBuilder()
            report.appendLine(
                "=== DetectBreakdown: $name at " +
                    "${java.time.Instant.ofEpochMilli(System.currentTimeMillis())} ==="
            )
            report.appendLine("detect 実測: ${fmt(detectMs)}ms (glyphs=$glyphCount)")
            report.appendLine(line("  A bodySize ヒストグラム(畳み込み後＝本体と同形)", histMs, detectMs))
            report.appendLine(
                "  A' 同(畳み込み前＝flatten+map{size} 経由・対照群): ${fmt(histOldMs)}ms" +
                    " → 同一走行比で ${fmt((1 - histMs / histOldMs) * 100)}% 削減"
            )
            report.appendLine(line("  C comboPages ループ", comboMs, detectMs))
            report.appendLine(line("  D 列復元(畳み込み後＝本体と同形)", colsMs, detectMs))
            report.appendLine(
                "  D' 列復元(畳み込み前の旧形状・対照群): ${fmt(oldShapeMs)}ms" +
                    " → 同一走行比で ${fmt((1 - colsMs / oldShapeMs) * 100)}% 削減"
            )
            report.appendLine(
                "  プローブ合計: ${fmt(probeTotal)}ms / detect 実測 ${fmt(detectMs)}ms" +
                    " (被覆率 ${fmt(probeTotal / detectMs * 100)}%＝100%未満は正常)"
            )
            if (probeTotal > detectMs) {
                report.appendLine("  ⚠ プローブ合計が detect 実測を上回った＝本体への追従漏れか計測破損の疑い。")
            }
            println(report)
        }
    }

    /**
     * 表紙メタ抽出（[PdfExtractor.extractBookMeta] が呼ぶ 1 ページ抽出）が、`endPage=1` を設定しても
     * **全ページを反復しているか**を実測で確定させ、1 ページだけを処理する形との差を同一走行で測る。
     *
     * 疑いの根拠（pdfbox-android 2.0.27.0 を `javap -c` で読んだ実装位置）:
     * `PDFTextStripper.processPages` は PDPageTree を**最後まで** iterate し、ページごとに
     * `currentPageNo++` と `hasContents()` を呼んで `processPage(page)` を呼ぶ。ページ範囲
     * `startPage..endPage` の判定は `processPage` の**先頭**にあり、範囲外はそこで return するだけ＝
     * ページ辞書の解決と `hasContents()` のコストは全ページぶん払っている
     * （⚠ 「範囲判定は processPages 側にある」という下馬評は誤り。バイトコードで確認した）。
     *
     * `visitedPages` はその直接証拠（`processPage` の呼び出し回数）。
     * **結論は「差はあるが桁が違う」**＝報告の数値どおり本番へは入れない。この計測を残すのは、
     * 同じ疑いが再燃したときに再測せずに済ませるため。
     */
    @Test
    fun profileFirstPageMeta() {
        val fixture = System.getenv("EXTRACT_PROFILE")
        assumeTrue(
            "EXTRACT_PROFILE 未設定のためスキップ（例: EXTRACT_PROFILE=N2959KI）。",
            !fixture.isNullOrBlank(),
        )
        val name = fixture!!.trim()
        val pdf = File(resolveRepoRoot(), "sample_pdfs/$name.pdf")
        check(pdf.isFile) { "PDF が無い: ${pdf.absolutePath}" }

        val report = StringBuilder()
        report.appendLine(
            "=== FirstPageMeta: $name at " +
                "${java.time.Instant.ofEpochMilli(System.currentTimeMillis())} ==="
        )

        PDDocument.load(pdf).use { doc ->
            // 両形状を1往復ウォームアップ（先に走る方が JIT で不利になるのを避ける＝他プローブと同作法）。
            probeFirstPageWholeTree(doc)
            probeFirstPageRangeOnly(doc)

            val counting = CountingStripper().apply {
                sortByPosition = false
                startPage = 1
                endPage = 1
            }
            counting.getText(doc)

            val (oldChars, oldMs) = probeFirstPageWholeTree(doc)
            val (newChars, newMs) = probeFirstPageRangeOnly(doc)

            report.appendLine(
                "pages=${doc.numberOfPages} / processPage が呼ばれた回数=${counting.visitedPages}" +
                    "（総ページ数と一致するなら全ページ反復が確定）"
            )
            report.appendLine("旧: getText(endPage=1) 全ページ反復: ${fmt(oldMs)}ms (chars=${oldChars.size})")
            report.appendLine("新: 1ページ処理で打ち切り:          ${fmt(newMs)}ms (chars=${newChars.size})")
            report.appendLine(
                "差分 ${fmt(oldMs - newMs)}ms（同一走行比 ${fmt((1 - newMs / oldMs) * 100)}% 削減）" +
                    "＝engine 秒オーダーに対する寄与を見て採否を決めること"
            )
            report.appendLine(
                if (oldChars == newChars) "等価性: CharBox 列が完全一致（data class equals＝全フィールド）"
                else "⚠ 等価性: 不一致＝この畳み込みは採用不可"
            )
        }
        println(report)
    }

    /** 現行 [PdfExtractor] の 1 ページ抽出と同形（`endPage=1` + `getText`）。CharBox 列と所要ミリ秒を返す。 */
    private fun probeFirstPageWholeTree(doc: PDDocument): Pair<List<CharBox>, Double> {
        val stripper = GlyphStripper().apply {
            sortByPosition = false
            startPage = 1
            endPage = 1
        }
        val start = System.nanoTime()
        stripper.getText(doc)
        val ms = elapsedMs(start)
        return (stripper.pages.firstOrNull() ?: emptyList<CharBox>()) to ms
    }

    /** ページツリーの反復を 1 ページ目で打ち切る形（[RangeGlyphProbeStripper]）。 */
    private fun probeFirstPageRangeOnly(doc: PDDocument): Pair<List<CharBox>, Double> {
        val stripper = RangeGlyphProbeStripper(0, 0)
        val start = System.nanoTime()
        stripper.getText(doc)
        val ms = elapsedMs(start)
        return (stripper.pages.firstOrNull() ?: emptyList<CharBox>()) to ms
    }

    /** `processPage` の呼び出し回数を数えるだけの対照（全ページ反復の直接証拠を取る）。 */
    private class CountingStripper : com.tom_roush.pdfbox.text.PDFTextStripper() {
        var visitedPages = 0
        override fun processPage(page: PDPage) {
            visitedPages++
            super.processPage(page)
        }

        override fun processTextPosition(text: TextPosition) = Unit
    }

    /** [GlyphStripper] と設定を揃えた走査を1回行い、`getText` の所要ミリ秒を返す（PDF ロードは計測外）。 */
    private fun runStripper(pdf: File, collectCharBoxes: Boolean): Double {
        PDDocument.load(pdf).use { doc ->
            val stripper = if (collectCharBoxes) GlyphStripper() else ParseOnlyStripper()
            stripper.sortByPosition = false
            stripper.startPage = 1
            stripper.endPage = Int.MAX_VALUE
            val start = System.nanoTime()
            stripper.getText(doc)
            return elapsedMs(start)
        }
    }

    /**
     * [GlyphStripper] から「CharBox を作って貯める」処理だけを抜いた対照群。
     * `text.unicode` の取得と空判定までは本番と揃える（そこまでは PDFBox 側のコストのため）。
     */
    private class ParseOnlyStripper : com.tom_roush.pdfbox.text.PDFTextStripper() {
        override fun processTextPosition(text: com.tom_roush.pdfbox.text.TextPosition) {
            val raw = text.unicode
            if (raw.isNullOrEmpty()) return
        }
    }

    private class ProbeResult(
        val classifyMs: Double,
        val sortMs: Double,
        val groupMs: Double,
        val rubyMs: Double,
        val buildMs: Double,
    ) {
        val totalMs: Double get() = classifyMs + sortMs + groupMs + rubyMs + buildMs
    }

    /**
     * [TextProcessor.processPages] の内部と同じ順序・同じ入力で各段を呼び直し、段ごとの合計時間を得る。
     * 本体のコピーであることの危険はクラス KDoc の ⚠ を参照（被覆率で自己検証している）。
     */
    private fun phaseProbe(
        charListsByPage: List<List<CharBox>>,
        totalPages: Int,
        rules: DetectedRules,
    ): ProbeResult {
        var classifyNs = 0L
        var sortNs = 0L
        var groupNs = 0L
        var rubyNs = 0L
        var buildNs = 0L

        for ((pageNum, chars) in charListsByPage.withIndex()) {
            // 本体 TextProcessor.kt:147 と同一の除外条件（先頭3ページと最終ページ）。
            if (pageNum < 3 || pageNum >= totalPages - 1) continue

            val t0 = System.nanoTime()
            val titlesAll = mutableListOf<CharBox>()
            val bodiesAll = mutableListOf<CharBox>()
            val rubiesAll = mutableListOf<CharBox>()
            for (c in chars) {
                val fontSize = c.size
                val yPos = c.top
                if (ParserRules.isClose(fontSize, rules.pageNumSize)) {
                    if (ParserRules.isClose(yPos, rules.pageNumY, absTol = 5.0) ||
                        ParserRules.isClose(c.bottom, rules.pageNumY, absTol = 5.0)
                    ) {
                        continue
                    }
                }
                if (ParserRules.checkIsTitle(c.fontName, fontSize, rules.bodySize)) {
                    titlesAll.add(c)
                } else if (ParserRules.isClose(fontSize, rules.bodySize)) {
                    bodiesAll.add(c)
                } else if (ParserRules.isClose(fontSize, rules.rubySize)) {
                    rubiesAll.add(c)
                }
            }
            classifyNs += System.nanoTime() - t0

            val t1 = System.nanoTime()
            val bodiesSorted = bodiesAll.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top })
            sortNs += System.nanoTime() - t1

            val t2 = System.nanoTime()
            val linesDict = TextProcessor.groupCharsByLine(bodiesSorted)
            groupNs += System.nanoTime() - t2

            val t3 = System.nanoTime()
            TextProcessor.associateRuby(linesDict, rubiesAll, rules.rubyOffsetX)
            rubyNs += System.nanoTime() - t3

            val t4 = System.nanoTime()
            for (x in linesDict.keys.sortedDescending()) {
                TextProcessor.buildLineStr(linesDict[x]!!.sortedBy { it.top })
            }
            buildNs += System.nanoTime() - t4
        }

        return ProbeResult(
            classifyMs = classifyNs / 1_000_000.0,
            sortMs = sortNs / 1_000_000.0,
            groupMs = groupNs / 1_000_000.0,
            rubyMs = rubyNs / 1_000_000.0,
            buildMs = buildNs / 1_000_000.0,
        )
    }

    /**
     * bodySize ヒストグラムの畳み込み**前**（対照群）。全グリフのコピー（flatten）と boxed Double の
     * リスト（map{size}）を経由してから数える、当時の形をそのまま保つ。一時リストをこの関数の
     * スコープに閉じ込め、呼び出し側へ参照を漏らさない（本体に無い延命を作らないため）。
     * 本体からは既に消えた形状なので、ここが唯一の記録になる。
     */
    private fun probeBodyHistogramOldShape(charListsByPage: List<List<CharBox>>): Double {
        val start = System.nanoTime()
        val allChars = charListsByPage.flatten()
        val sizes = allChars.map { it.size }
        val counts = HashMap<Double, Int>()
        for (v in sizes) {
            val b = Math.round(v * 10.0) / 10.0
            counts[b] = (counts[b] ?: 0) + 1
        }
        return elapsedMs(start)
    }

    /** 畳み込み**後**（[DetectedRules.detect] の現行と同形＝ページ配列を直接走査してカウンタへ積む）。 */
    private fun probeBodyHistogramMergedShape(charListsByPage: List<List<CharBox>>): Double {
        val start = System.nanoTime()
        val counts = HashMap<Double, Int>()
        for (page in charListsByPage) {
            for (c in page) {
                val b = Math.round(c.size * 10.0) / 10.0
                counts[b] = (counts[b] ?: 0) + 1
            }
        }
        return elapsedMs(start)
    }

    /**
     * 畳み込み**前**の列復元（対照群）。ページごとに同じ filter+groupCharsByLine を2周し、
     * ルビ1個ごとに `bodyCols.filter{}.maxOrNull()` で新規リストを作る、という当時の形をそのまま保つ。
     * 本体からは既に消えた形状なので、ここが唯一の記録になる（変更の効果を後から再現・検証できるように残す）。
     */
    private fun probeColsOldShape(
        charListsByPage: List<List<CharBox>>,
        bodySize: Double,
        rubySize: Double,
    ): Double {
        val start = System.nanoTime()
        for (page in charListsByPage) {
            TextProcessor.groupCharsByLine(
                page.filter { ParserRules.isClose(it.size, bodySize) }
            ).keys.sortedDescending()
        }
        for (page in charListsByPage) {
            val bodyCols = TextProcessor.groupCharsByLine(
                page.filter { ParserRules.isClose(it.size, bodySize) }
            ).keys.toList()
            if (bodyCols.isEmpty()) continue
            for (r in page.filter { ParserRules.isClose(it.size, rubySize) }) {
                bodyCols.filter { it < r.x0 }.maxOrNull() ?: continue
            }
        }
        return elapsedMs(start)
    }

    /** 畳み込み**後**の列復元（[DetectedRules.detect] の現行と同形）。 */
    private fun probeColsMergedShape(
        charListsByPage: List<List<CharBox>>,
        bodySize: Double,
        rubySize: Double,
    ): Double {
        val start = System.nanoTime()
        for (page in charListsByPage) {
            val bodyColKeys = TextProcessor.groupCharsByLine(
                page.filter { ParserRules.isClose(it.size, bodySize) }
            ).keys
            bodyColKeys.sortedDescending()
            if (bodyColKeys.isEmpty()) continue
            for (r in page) {
                if (!ParserRules.isClose(r.size, rubySize)) continue
                var parent: Double? = null
                for (x in bodyColKeys) {
                    if (x < r.x0 && (parent == null || x > parent)) parent = x
                }
            }
        }
        return elapsedMs(start)
    }

    /**
     * 支配区間 [PdfExtractor.loadPages]（engine の 66〜70%・うち 85〜91% が PDFBox 内部）を
     * **ページ範囲で分割して並列に走らせた**ときの効果と等価性を測るスパイク。本番は変更しない
     * （採否は監督が決める。ここは材料を出すだけ）。
     *
     * 単一スレッドでは PDFBox 内部を動かせない以上、支配区間そのものを分割できる手段は並列化しかない。
     * 逆に言えば**等価でなければ意味がない**ので、比より先に CharBox 列の一致を見る。
     *
     * 計測の作法:
     * - 走行をまたいだ絶対値比較はしない。同一走行内で単一スレッド版と K=2/3/4 を並べて比だけを読む。
     * - 並列版は各スレッドが**自分の [PDDocument] を開く**（PDFBox の COSDocument はスレッド安全でない）。
     *   よって PDF の再オープン K 回ぶんも並列版のコストに含めて測る＝実装したときの実コストに合わせる。
     * - 先に単一スレッドで 1 ページ処理して静的キャッシュ（PDFBoxResourceLoader・CMap・glyphlist）を
     *   温めてから並列区間に入る。それでも競合で落ちる／値が化けるなら、それ自体が結論。
     */
    @Test
    fun profileParallelLoadPages() {
        val fixture = System.getenv("EXTRACT_PROFILE")
        assumeTrue(
            "EXTRACT_PROFILE 未設定のためスキップ（例: EXTRACT_PROFILE=N2959KI）。",
            !fixture.isNullOrBlank(),
        )
        val name = fixture!!.trim()
        val pdf = File(resolveRepoRoot(), "sample_pdfs/$name.pdf")
        check(pdf.isFile) { "PDF が無い: ${pdf.absolutePath}" }

        val cores = Runtime.getRuntime().availableProcessors()
        val report = StringBuilder()
        report.appendLine(
            "=== ParallelLoadPages: $name at " +
                "${java.time.Instant.ofEpochMilli(System.currentTimeMillis())} ==="
        )
        report.appendLine("availableProcessors=$cores")

        // 静的キャッシュのウォームアップ（1ページだけ・単一スレッド）。
        PDDocument.load(pdf).use { doc -> RangeGlyphProbeStripper(0, 0).getText(doc) }

        // 単一スレッド基準。ウォームアップ2回を捨ててから採用する（1回だけだと JIT が温まりきらず、
        // 走行末尾の再測が 0.8x まで下がった＝基準が高止まりして並列の比が過大に出る）。
        measureBaseline(pdf)
        measureBaseline(pdf)
        val base = measureBaseline(pdf)
        report.appendLine(
            "単一スレッド基準: ${fmt(base.ms)}ms (pages=${base.pageCount} glyphs=${base.glyphCount}" +
                " 照合=${if (base.strictPages != null) "全フィールド厳密" else "ページ単位フィンガープリント"})"
        )

        // 並列経路も一度ウォームアップしてから測る。しないと最初に測る K だけ JIT で不利になり、
        // 「K が大きいほど速い」という**測り方由来の**傾きが出る（N2959KI の初回計測で実際に出た）。
        parallelLoad(pdf, 4, base.pageCount)

        for (k in listOf(2, 3, 4)) {
            // 同じ K を2回測る。1回目は直前の走行が残したゴミの GC を被りやすく、K 間の比較が
            // 「測った順番」の関数になってしまうため（K=2 だけ 1.1x に沈む走行を実測した）。
            // ⚠ 2回ぶんの結果を同時に抱えない（大文書ではそれだけでヒープが尽きる）。
            //   1回ずつ照合して時間と判定だけ残し、CharBox 列はループの各回で捨てる。
            val times = ArrayList<Double>(2)
            val verdicts = LinkedHashSet<String>()
            var error: Throwable? = null
            while (times.size < 2 && error == null) {
                val r = parallelLoad(pdf, k, base.pageCount)
                if (r.error != null) {
                    error = r.error
                    break
                }
                times.add(r.ms)
                verdicts.add(
                    if (base.strictPages != null) strictVerdict(base.strictPages, r.pages!!)
                    else fingerprintVerdict(base.counts, base.fingerprint, r.pages!!)
                )
            }
            if (error != null) {
                // 並列化の可否そのものが計測対象＝失敗を握り潰さず型と内容をそのまま出す。
                report.appendLine("K=$k: ⚠ 失敗 ${error!!::class.java.name}: ${error!!.message}")
                continue
            }
            val best = times.min()
            report.appendLine(
                "K=$k: ${times.joinToString(" / ") { fmt(it) + "ms" }}" +
                    "（速い方で 単一比 ${fmt(base.ms / best)}x・短縮 ${fmt((1 - best / base.ms) * 100)}%）" +
                    " 等価性: ${verdicts.joinToString(" ＋ ")}"
            )
        }

        // 計測の最後に基準をもう一度取り、走行中に機械側が振れていないかを見る（振れていれば比も疑う）。
        val baseAgain = measureBaseline(pdf)
        report.appendLine(
            "単一スレッド基準（再測）: ${fmt(baseAgain.ms)}ms" +
                "（初回比 ${fmt(baseAgain.ms / base.ms)}x＝1.0 から離れるほど走行中の振れが大きい）"
        )
        println(report)
    }

    /**
     * 単一スレッド基準の1回ぶん。CharBox 列そのものは**返さない**（大文書で2セット同時に抱えると
     * ヒープが持たないため、照合材料へ畳んでからスコープを抜ける）。小さい文書だけ厳密比較用に保持する。
     */
    private class BaselineResult(
        val ms: Double,
        val pageCount: Int,
        val glyphCount: Int,
        val counts: List<Int>,
        val fingerprint: List<Long>,
        val strictPages: List<List<CharBox>>?,
    )

    /** 本番 [PdfExtractor.loadPages] を PDF オープン込みで1回走らせる（並列版と同じ土俵にするため）。 */
    private fun measureBaseline(pdf: File): BaselineResult {
        val start = System.nanoTime()
        PDDocument.load(pdf).use { doc ->
            val pages = PdfExtractor.loadPages(doc)
            val ms = elapsedMs(start)
            val glyphCount = pages.sumOf { it.size }
            return BaselineResult(
                ms = ms,
                pageCount = pages.size,
                glyphCount = glyphCount,
                counts = pages.map { it.size },
                fingerprint = fingerprintPages(pages),
                strictPages = if (glyphCount <= STRICT_COMPARE_GLYPH_LIMIT) pages else null,
            )
        }
    }

    private class LoadResult(val pages: List<List<CharBox>>?, val ms: Double, val error: Throwable? = null)

    /**
     * ページ範囲を K 分割し、各スレッドが自分の [PDDocument] を開いて担当範囲だけ収集 → ページ順に連結。
     * 時間には PDF の K 回オープンと連結も含める（実装したときに実際に払うコストのため）。
     */
    private fun parallelLoad(pdf: File, k: Int, totalPages: Int): LoadResult {
        val bounds = splitRanges(totalPages, k)
        val pool = Executors.newFixedThreadPool(k)
        try {
            val start = System.nanoTime()
            val futures = bounds.map { (from, to) ->
                pool.submit(
                    Callable {
                        PDDocument.load(pdf).use { doc ->
                            val stripper = RangeGlyphProbeStripper(from, to)
                            stripper.getText(doc)
                            stripper.pages.toList()
                        }
                    }
                )
            }
            val merged = ArrayList<List<CharBox>>(totalPages)
            for (f in futures) merged.addAll(f.get())
            return LoadResult(merged, elapsedMs(start))
        } catch (t: Throwable) {
            // ExecutionException は原因を剥がして返す（スレッド安全性の観測がこのスパイクの主目的）。
            return LoadResult(null, Double.NaN, (t as? java.util.concurrent.ExecutionException)?.cause ?: t)
        } finally {
            pool.shutdownNow()
        }
    }

    /** [0, totalPages) を先頭から順に K 個の連続範囲（両端含む・0始まり）へ分ける。 */
    private fun splitRanges(totalPages: Int, k: Int): List<Pair<Int, Int>> {
        val per = totalPages / k
        val rem = totalPages % k
        var from = 0
        return (0 until k).map { i ->
            val size = per + if (i < rem) 1 else 0
            val range = from to (from + size - 1)
            from += size
            range
        }
    }

    /** 全 CharBox を全フィールドで突き合わせ、最初の食い違いを返す（一致なら "完全一致"）。 */
    private fun strictVerdict(expected: List<List<CharBox>>, actual: List<List<CharBox>>): String {
        if (expected.size != actual.size) return "⚠ ページ数不一致 ${expected.size} vs ${actual.size}"
        for (p in expected.indices) {
            val e = expected[p]
            val a = actual[p]
            if (e.size != a.size) return "⚠ page $p の文字数不一致 ${e.size} vs ${a.size}"
            for (i in e.indices) {
                if (e[i] != a[i]) return "⚠ page $p char $i 不一致 ${e[i]} vs ${a[i]}"
            }
        }
        return "完全一致（${expected.size} ページ・全フィールド）"
    }

    /** ページ単位フィンガープリントでの照合（大文書用。2セット同時保持を避けるため）。 */
    private fun fingerprintVerdict(
        expectedCounts: List<Int>,
        expectedFingerprint: List<Long>,
        actual: List<List<CharBox>>,
    ): String {
        if (expectedFingerprint.size != actual.size) {
            return "⚠ ページ数不一致 ${expectedFingerprint.size} vs ${actual.size}"
        }
        val actualFingerprint = fingerprintPages(actual)
        for (p in expectedFingerprint.indices) {
            if (expectedCounts[p] != actual[p].size) {
                return "⚠ page $p の文字数不一致 ${expectedCounts[p]} vs ${actual[p].size}"
            }
            if (expectedFingerprint[p] != actualFingerprint[p]) return "⚠ page $p のフィンガープリント不一致"
        }
        return "完全一致（${actual.size} ページ・全フィールドを畳んだ 64bit 照合）"
    }

    /**
     * ページごとに text/fontName/size/x0/top/bottom を順序込みで 64bit へ畳む。
     * double は `doubleToRawLongBits` で**ビット等価**を見る（0.1 の丸めを挟まない＝ズレを見逃さない）。
     */
    private fun fingerprintPages(pages: List<List<CharBox>>): List<Long> = pages.map { page ->
        var h = 1125899906842597L
        for (c in page) {
            h = 31 * h + c.text.hashCode()
            h = 31 * h + (c.fontName?.hashCode() ?: 0)
            h = 31 * h + java.lang.Double.doubleToRawLongBits(c.size)
            h = 31 * h + java.lang.Double.doubleToRawLongBits(c.x0)
            h = 31 * h + java.lang.Double.doubleToRawLongBits(c.top)
            h = 31 * h + java.lang.Double.doubleToRawLongBits(c.bottom)
        }
        31 * h + page.size
    }

    /**
     * [GlyphStripper] の収集ロジックの**コピー**に、ページ範囲 [fromIndex]..[toIndex]
     * （0 始まり・両端含む）だけを処理して打ち切る `processPages` を組み合わせたプローブ。
     *
     * なぜ本体を継承しないか: 採否が決まっていない形状のために本番クラスを `open` にしない。
     * プローブは本体のコピーという既存作法に合わせる（本体を変えたらここも追従させること）。
     *
     * なぜ `startPage`/`endPage` を 0..0 にするか: `processPages` を差し替えると `currentPageNo`
     * （private・setter 無し）が 0 のままになり、`processPage` 先頭の範囲判定
     * `currentPageNo in startPage..endPage` を通せなくなるため。`startBookmarkPageNumber` /
     * `endBookmarkPageNumber` は `processPages` を通らないので既定の 0 のままだが、判定は
     * 「-1 でなければ currentPageNo と比較」で 0 同士なら両方通る（javap -c で確認済み）。
     */
    private class RangeGlyphProbeStripper(
        private val fromIndex: Int,
        private val toIndex: Int,
    ) : PDFTextStripper() {

        val pages: MutableList<MutableList<CharBox>> = mutableListOf()
        private var current: MutableList<CharBox> = mutableListOf()

        init {
            sortByPosition = false
            startPage = 0
            endPage = 0
        }

        override fun processPages(tree: PDPageTree) {
            var index = 0
            for (page in tree) {
                if (index > toIndex) break
                if (index >= fromIndex) processPage(page)
                index++
            }
        }

        override fun startPage(page: PDPage) {
            current = mutableListOf()
            pages.add(current)
            super.startPage(page)
        }

        // 本番 [GlyphStripper] と同じ復号器を使う（プローブが本番と別の字を作らないようにするため）。
        private val decoder = GlyphDecoder()

        override fun processTextPosition(text: TextPosition) {
            val s = decoder.decode(text)
            if (s.isNullOrEmpty()) return
            val bottom = text.yDirAdj.toDouble()
            current.add(
                CharBox(
                    text = s,
                    fontName = text.font?.name,
                    size = text.fontSizeInPt.toDouble(),
                    x0 = text.xDirAdj.toDouble(),
                    top = bottom - text.heightDir.toDouble(),
                    bottom = bottom,
                )
            )
        }
    }

    private fun elapsedMs(startNs: Long): Double = (System.nanoTime() - startNs) / 1_000_000.0

    private fun line(label: String, ms: Double, totalMs: Double): String {
        val pct = if (totalMs > 0) ms / totalMs * 100 else 0.0
        return "$label: ${fmt(ms)}ms (${fmt(pct)}%)"
    }

    private fun fmt(v: Double): String = String.format("%.1f", v)

    /** user.dir から sample_pdfs/ を持つ祖先ディレクトリを探す（JvmGoldenRegressionTest と同一作法）。 */
    private fun resolveRepoRoot(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            if (File(d, "sample_pdfs").isDirectory && File(d, "ab-review").isDirectory) return d
            d = d.parentFile
        }
        throw IllegalStateException(
            "sample_pdfs/ を含むリポジトリルートが user.dir=${System.getProperty("user.dir")} から見つからない"
        )
    }

    private companion object {
        /**
         * これを超える文書では厳密比較（CharBox 列を2セット同時保持）を諦め、ページ単位
         * フィンガープリント照合へ落とす閾値。N6169DZ の 338 万グリフは 2 セットでテストワーカーの
         * ヒープに収まらないため（N2959KI の 38 万は厳密比較のまま通る）。
         */
        const val STRICT_COMPARE_GLYPH_LIMIT = 1_000_000
    }
}
