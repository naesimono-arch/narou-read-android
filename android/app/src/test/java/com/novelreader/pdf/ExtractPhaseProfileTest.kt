package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
     * 呼ばない [ParseOnlyStripper] との差分は、`normalizeGlyphUnicode` + [CharBox] 生成 +
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

            // --- A: 全ページの文字を1本のリストへ（数百万要素の新規リスト） ---
            val tA = System.nanoTime()
            val allChars = charListsByPage.flatten()
            val flattenMs = elapsedMs(tA)
            // ⚠ 件数だけ退避して以降 allChars / sizes を参照しない。参照を残すと本体には無い延命が起き、
            // GC 圧の差で後続ブロック(C/D/E)が過大に出る（初回実測で被覆率 108% を踏んだ真因がこれ）。
            // 本体では allChars は bodySize 算出後、map{} の結果は modeBucketKey 通過後に即ゴミになる。
            val glyphCount = allChars.size

            // --- B: サイズだけのリスト（Double の boxing が全要素で発生する） ---
            val tB = System.nanoTime()
            val sizeCount = allChars.map { it.size }.size
            val sizeListMs = elapsedMs(tB)

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

            val probeTotal = flattenMs + sizeListMs + comboMs + colsMs
            val report = StringBuilder()
            report.appendLine(
                "=== DetectBreakdown: $name at " +
                    "${java.time.Instant.ofEpochMilli(System.currentTimeMillis())} ==="
            )
            report.appendLine("detect 実測: ${fmt(detectMs)}ms (glyphs=$glyphCount sizes=$sizeCount)")
            report.appendLine(line("  A flatten()", flattenMs, detectMs))
            report.appendLine(line("  B map{size}", sizeListMs, detectMs))
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
}
