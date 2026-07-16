package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * コールド起動の予算判定ヘルパー（instrumentation 引数 `enableBudgetAssert` が true のときのみ使う）。
 *
 * なぜ JSON 経由で判定するのか:
 * androidx.benchmark 1.4.1 の実バイナリで確認したとおり [androidx.benchmark.macro.junit4.MacrobenchmarkRule]
 * の `measureRepeated` は全オーバーロードが void で、計測値をテストコードへ返さない。一方
 * `androidx.benchmark.ResultWriter#appendTestResult` が measureRepeated の毎回リターン直前に
 * `<パッケージ名>-benchmarkData.json` を蓄積分全件で書き出す（`Arguments.outputEnable`＝
 * instrumentation 引数 `androidx.benchmark.output.enable` が true のときのみ）。
 * したがって measureRepeated 完了直後にこの JSON を読めば、当該テストの結果は必ず書き出し済みで参照できる。
 */
object StartupBudget {

    // 予算値の由来（実測 × 余裕係数）:
    //   OPPO PGEM10（Android 16 / ColorOS・コールド起動5反復）の初回実測
    //     timeToInitialDisplayMs median 252.9ms / max 274.5ms
    //   に余裕係数 median×1.4 / max×1.8 を掛けて丸めた値。
    //   なぜ係数を差別化するか＝中央値は分布がタイトで安定だが、最大値は初回反復で伸びやすく
    //   ColorOS の SIGQUIT 除細動由来の外乱・端末温度ばらつきを受けやすいため max 側を厚めに取る。
    //     median: 252.9 × 1.4 ≒ 354 → 350 に丸め（やや厳しめ）
    //     max:    274.5 × 1.8 ≒ 494 → 500 に丸め（やや緩め）
    const val BUDGET_MEDIAN_MS = 350.0
    const val BUDGET_MAX_MS = 500.0

    // StartupTimingMetric が benchmarkData.json の metrics マップに出すキー名
    private const val METRIC_KEY = "timeToInitialDisplayMs"

    /** instrumentation 引数 `enableBudgetAssert` を真偽解釈（未指定は false＝従来どおり計測のみ）。 */
    fun isBudgetAssertEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("enableBudgetAssert").toBoolean()

    /**
     * コールド起動の median/max が予算内かを検証する。
     * 判定できない（JSON が見つからない・スキーマが期待と違う）場合は AssertionError で明示的に失敗させる
     * ——予算 assert を頼まれたのに判定できないのはサイレントスキップせず「失敗」として扱う方針。
     *
     * @param notBeforeEpochMs 今回の measureRepeated 開始時刻（epoch ms）。採用した JSON の
     *   lastModified がこれ未満なら「今回の走行で書き出されていない残骸 JSON」と判断して fail する。
     */
    fun assertColdStartupWithinBudget(notBeforeEpochMs: Long) {
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

        // 採用した JSON が「今回の走行」で書き出されたものかを lastModified で検証する。
        // なぜ必要か: JSON が今回書き出されなかった場合（output.enable 未指定でスクリプト外から
        // 実行した等）でも前回走行の残骸 JSON が端末に残っていると maxByOrNull がそれを拾い、
        // 古い値で偽 PASS してしまう。measureRepeated 開始前の時刻より古い JSON は残骸とみなし fail。
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

        // coldStartup を名前に含むテストエントリを採用（クラス名の違い等に頑健にするため部分一致）
        var entry: JSONObject? = null
        for (i in 0 until benchmarks.length()) {
            val b = benchmarks.getJSONObject(i)
            if (b.optString("name").contains("coldStartup")) {
                entry = b
                break
            }
        }
        val benchmark = entry
            ?: throw AssertionError("coldStartup を名前に含むエントリが無い: ${json.absolutePath}")

        val metric = benchmark.optJSONObject("metrics")?.optJSONObject(METRIC_KEY)
            ?: throw AssertionError("$METRIC_KEY メトリクスが無い: ${json.absolutePath}")

        val median = metric.getDouble("median")
        val max = metric.getDouble("maximum")

        val violations = buildList {
            if (median > BUDGET_MEDIAN_MS) add("median=${median}ms > 予算 ${BUDGET_MEDIAN_MS}ms")
            if (max > BUDGET_MAX_MS) add("maximum=${max}ms > 予算 ${BUDGET_MAX_MS}ms")
        }
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "コールド起動が起動予算を超過: ${violations.joinToString("; ")} " +
                    "(JSON: ${json.absolutePath})"
            )
        }
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
