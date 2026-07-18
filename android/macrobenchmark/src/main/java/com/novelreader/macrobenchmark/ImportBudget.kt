package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * 大PDF取込（pdf-import）のフェーズ別時間の予算判定ヘルパー（instrumentation 引数
 * `enableBudgetAssert` が true のときのみ使う）。判定の値源が benchmarkData.json である理由・
 * 残骸 JSON 検証の必要性は [StartupBudget] のクラス KDoc と同じ。
 *
 * 共通部（resolveBudget / collectSearchRoots / JSON 探索・残骸検証）を他 Budget と複製する理由も
 * 同じ（実機実証済みコードの無変更保全＝[ScrollBudget] の KDoc 参照）。
 *
 * なぜ判定は median のみで max を見ないのか（[StartupBudget] は median+max の2軸なのと対照的）:
 * 反復が3回しかない（1取込が分オーダーのため）＝max は1発の外乱（端末温度・ColorOS の背景動作）に
 * 過敏で flaky ゲート化する。一方 median は初回実測で分布が極めてタイト（Import#extract
 * 23.7〜24.7s）＝漸進劣化の回帰検知には median で十分。
 */
object ImportBudget {

    // 予算値の由来（実測 × 余裕係数）:
    //   OPPO PGEM10（Android 16 / ColorOS・N6169DZ 8.5MB/951章・3反復クリーン）の初回実測（2026-07-18）
    //     Import#extractSumMs median 24123ms（23.7〜24.7s）／Extract#engineSumMs median 22686ms
    //   に余裕係数 ×1.4〜1.5 を掛けて丸めた値（端末温度・ColorOS 外乱込み。起動予算の median 係数と同じ流儀）。
    //     Import#extract:  24.1s × 1.45 ≒ 35s
    //     Extract#engine:  22.7s × 1.45 ≒ 33s（extract の 94% を占める支配区間＝両方に予算を張ることで
    //       「engine 以外（コピー・SHA・章分割・DB）の劣化」と「engine 自体の劣化」を切り分けられる）。
    const val BUDGET_EXTRACT_MS = 35_000.0
    const val BUDGET_ENGINE_MS = 33_000.0

    // TraceSectionMetric(Mode.Sum) が benchmarkData.json の metrics マップに出すキー名。
    // 区間名そのままではなく「<区間名>SumMs」（併せて <区間名>Count も出る）——
    // ④初回実測の pull 済み JSON で実確認したスキーマ（例: "Import#extractSumMs": {median, minimum, maximum, …}）。
    private const val METRIC_EXTRACT = "Import#extractSumMs"
    private const val METRIC_ENGINE = "Extract#engineSumMs"

    /**
     * 適用する予算値を instrumentation 引数（`budgetExtractMs` / `budgetEngineMs`）で上書き可能にする
     * （FAIL 経路の実機実証・将来較正のため。パース不能な明示指定を既定へ黙って落とさず fail する理由＝
     * 較正事故防止は [ScrollBudget] と同じ）。
     */
    private fun resolveBudget(argKey: String, default: Double): Double {
        val raw = InstrumentationRegistry.getArguments().getString(argKey)
        if (raw == null || raw.isBlank()) return default
        return raw.trim().toDoubleOrNull()
            ?: throw AssertionError(
                "instrumentation 引数 $argKey='$raw' を Double として解釈できない。" +
                    "予算の指定ミスは既定へ黙って落とさず fail する（較正事故防止）。"
            )
    }

    /** instrumentation 引数 `enableBudgetAssert` を真偽解釈（未指定は false＝従来どおり計測のみ）。 */
    fun isBudgetAssertEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("enableBudgetAssert").toBoolean()

    /**
     * 大PDF取込の Import#extract / Extract#engine の median が予算内かを検証する。
     * 判定できない（JSON が見つからない・スキーマが期待と違う）場合は AssertionError で明示的に失敗させる
     * ——予算 assert を頼まれたのに判定できないのはサイレントスキップせず「失敗」として扱う方針。
     * 特に本ベンチは「完了検知の早発火で全メトリクス 0.0」の前科がある（PdfImportBenchmark の
     * measureBlock コメント参照）ため、値が取れないことを黙って緑にしない意味が大きい。
     *
     * @param notBeforeEpochMs 今回の measureRepeated 開始時刻（epoch ms）。採用した JSON の
     *   lastModified がこれ未満なら「今回の走行で書き出されていない残骸 JSON」と判断して fail する。
     */
    fun assertImportWithinBudget(notBeforeEpochMs: Long) {
        val roots = collectSearchRoots()
        val json = roots.asSequence()
            .filter { it.exists() }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.name.endsWith("-benchmarkData.json") }
            .maxByOrNull { it.lastModified() }
            ?: throw AssertionError(
                "予算 assert を要求されたが *-benchmarkData.json が見つからない。" +
                    "instrumentation 引数 androidx.benchmark.output.enable が true でない可能性が高い" +
                    "（この JSON は output.enable=true のときだけ書き出される）。探索したルート: " +
                    roots.joinToString(", ") { it.absolutePath }
            )

        // 残骸 JSON による偽 PASS 防止（lastModified 検証の機序は ScrollBudget の同名コメント参照）。
        val lastModified = json.lastModified()
        if (lastModified < notBeforeEpochMs) {
            throw AssertionError(
                "採用した *-benchmarkData.json が今回の走行より古い＝残骸 JSON の可能性が高く、判定には使わない。" +
                    "今回の走行で JSON が書き出されていない（instrumentation 引数 " +
                    "androidx.benchmark.output.enable が無効の可能性）。" +
                    "JSON lastModified=${lastModified}ms < 走行開始 notBefore=${notBeforeEpochMs}ms。" +
                    "JSON: ${json.absolutePath}"
            )
        }

        val benchmarks = JSONObject(json.readText()).optJSONArray("benchmarks")
            ?: throw AssertionError("benchmarkData.json に benchmarks 配列がない: ${json.absolutePath}")

        // importLargePdf を名前に含むテストエントリを採用（クラス名の違い等に頑健にするため部分一致）。
        var entry: JSONObject? = null
        for (i in 0 until benchmarks.length()) {
            val b = benchmarks.getJSONObject(i)
            if (b.optString("name").contains("importLargePdf")) {
                entry = b
                break
            }
        }
        val benchmark = entry
            ?: throw AssertionError("importLargePdf を名前に含むエントリが無い: ${json.absolutePath}")

        val metrics = benchmark.optJSONObject("metrics")
            ?: throw AssertionError("metrics マップが無い: ${json.absolutePath}")

        val extractMedian = metrics.optJSONObject(METRIC_EXTRACT)?.getDouble("median")
            ?: throw AssertionError("metrics.$METRIC_EXTRACT が無い: ${json.absolutePath}")
        val engineMedian = metrics.optJSONObject(METRIC_ENGINE)?.getDouble("median")
            ?: throw AssertionError("metrics.$METRIC_ENGINE が無い: ${json.absolutePath}")

        val budgetExtract = resolveBudget("budgetExtractMs", BUDGET_EXTRACT_MS)
        val budgetEngine = resolveBudget("budgetEngineMs", BUDGET_ENGINE_MS)

        // 0.0 は「計測不能（capture 早切れ等）」の症状であって高速化ではない——予算内判定に含めず fail する。
        // Count=1 の実区間が 0ms になることは物理的にない（初回実測の最小値でも exportHtml 246ms）ため、
        // 0.0 を緑にすると全メトリクス 0.0 の前科（完了検知の早発火）を回帰として検知できなくなる。
        if (extractMedian <= 0.0 || engineMedian <= 0.0) {
            throw AssertionError(
                "取込メトリクスが 0.0＝計測不能（trace capture の早切れ等）の疑い: " +
                    "$METRIC_EXTRACT=${extractMedian}ms / $METRIC_ENGINE=${engineMedian}ms " +
                    "(JSON: ${json.absolutePath})"
            )
        }

        val violations = buildList {
            if (extractMedian > budgetExtract) {
                add("$METRIC_EXTRACT median=${extractMedian}ms > 予算 ${budgetExtract}ms")
            }
            if (engineMedian > budgetEngine) {
                add("$METRIC_ENGINE median=${engineMedian}ms > 予算 ${budgetEngine}ms")
            }
        }
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "大PDF取込(importLargePdf)が時間予算を超過: ${violations.joinToString("; ")} " +
                    "(JSON: ${json.absolutePath})"
            )
        }

        // 予算内（PASS）でも実測値と適用予算を1行 logcat に残す（効かないゲートと区別する診断性＝
        // ScrollBudget の同名コメント参照）。
        android.util.Log.i(
            "ImportBudget",
            "PASS importLargePdf $METRIC_EXTRACT median=${extractMedian}ms " +
                "$METRIC_ENGINE median=${engineMedian}ms " +
                "(適用予算 extract<=${budgetExtract}ms engine<=${budgetEngine}ms)"
        )
    }

    /**
     * benchmarkData.json の探索ルート群を優先順に集める（重複排除）。
     *   1. instrumentation 引数 `additionalTestOutputDir`（明示指定があれば最優先）
     *   2. instrumentation / target 双方の context の external media dirs
     *      （前回実測では Android/media/com.novelreader.macrobenchmark/ 配下に出力された）
     */
    // externalMediaDirs は API 30 で deprecated だが、androidx.benchmark が実際に JSON を書き出す先が
    // ここ（前回実測で確認）なので意図的に使う。代替 API では benchmark の出力先を辿れない。
    @Suppress("DEPRECATION")
    private fun collectSearchRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        val args = InstrumentationRegistry.getArguments()
        args.getString("additionalTestOutputDir")?.takeIf { it.isNotBlank() }?.let {
            roots += File(it)
        }
        val instr = InstrumentationRegistry.getInstrumentation()
        instr.context.externalMediaDirs?.filterNotNull()?.let { roots += it }
        instr.targetContext.externalMediaDirs?.filterNotNull()?.let { roots += it }
        return roots.toList()
    }
}
