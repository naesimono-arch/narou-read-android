package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * タブ横スワイプ／遷移（[TabSwipeBenchmark]）の frame timing 予算判定ヘルパー。
 *
 * ## ⚠️ このシナリオの予算は**まだ較正されていない**（既定定数を置いていない）
 * [ScrollBudget] / [FlipBudget] は「初回実測 × 余裕係数」で既定値を決めている。本シナリオはその初回実測が
 * 無い（実機実行が要る）ため、**推測値を既定として焼き込まない**——推測を回帰の基準線にすると
 * 「緑なのに実機は破綻」／「厳しすぎて常時赤」のどちらかを必ず作り、しかもその由来が後から辿れなくなる。
 *
 * したがって本オブジェクトは予算を**instrumentation 引数からしか受け取らない**。`enableBudgetAssert` が
 * true なのに引数が1つも無ければ**サイレントに素通しせず fail する**（「判定を頼まれたのに判定できない
 * ならスキップでなく失敗」＝[ScrollBudget] と同じ方針。ここで黙って緑にすると、較正されていないことを
 * 誰も知らないまま「予算ゲートがある」と誤認される＝効かないゲートの典型）。
 *
 * ## 較正の手順（実機が要る作業）
 *  1. `tools/run_macrobenchmark.sh --scenario tab-swipe`（`--assert` 無し）で実測する。
 *  2. 出力の `frameDurationCpuMs` P50/P90/P99 に [ScrollBudget] と同じ考え方で余裕係数を掛ける
 *     （P50＝1フレーム内に収める意図・P99＝走行間で大きく揺れるため厚めに）。
 *  3. その値を**由来のコメント付きで**本ファイルへ既定定数として書き、以後は引数なしでも assert が効く形にする。
 *
 * 予算上書き引数名（`budgetP50Ms` / `budgetP90Ms` / `budgetP99Ms`）は [ScrollBudget] / [FlipBudget] と共用する
 * ——シナリオは排他実行（`-e class` で1クラスだけ走らせる）なので衝突しない。既存2つと同じ流儀。
 *
 * JSON 探索・残骸検証（lastModified）・sampledMetrics を読む理由は [ScrollBudget] と同一。
 * 共通基底を切らずに複製するのも同じ理由（実機で実証済みのコードを無変更で保全する）。
 */
object TabSwipeBudget {

    /** FrameTimingMetric が benchmarkData.json の sampledMetrics へ出すメトリクス名（[ScrollBudget] と同じ）。 */
    private const val METRIC_KEY = "frameDurationCpuMs"

    /** instrumentation 引数 `enableBudgetAssert` を真偽解釈（未指定は false＝従来どおり計測のみ）。 */
    fun isBudgetAssertEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("enableBudgetAssert").toBoolean()

    /**
     * 明示指定された分位だけを検証する（未較正のため既定値は無い）。
     *
     * 空白でない文字列が指定されて Double としてパース不能なときは、既定へ黙って落とさず fail する
     * （指定ミスを黙殺すると意図と違う予算で緑になる＝ScrollBudget.resolveBudget と同じ判断）。
     */
    private fun explicitBudget(argKey: String): Double? {
        val raw = InstrumentationRegistry.getArguments().getString(argKey)
        if (raw == null || raw.isBlank()) return null
        return raw.trim().toDoubleOrNull()
            ?: throw AssertionError(
                "instrumentation 引数 $argKey='$raw' を Double として解釈できない。" +
                    "予算の指定ミスは黙って無視せず fail する（較正事故防止）。"
            )
    }

    /**
     * タブ横スワイプ／遷移の frame timing が予算内かを検証する。
     *
     * @param testName benchmarks[] の name に含まれる識別子（"swipeTabs" / "swipeTabsWithTransition"）。
     *   ⚠️ "swipeTabs" は "swipeTabsWithTransition" の接頭辞でもあるため、部分一致では取り違えうる。
     *   そこで JUnit のメソッド名区切り（`name` は "swipeTabs[...]" 等の形）を考慮し、
     *   「testName の直後が英数字でない」ことまで見て一致とする。
     * @param notBeforeEpochMs 今回の measureRepeated 開始時刻（epoch ms）。これより古い JSON は
     *   前回走行の残骸とみなして fail する（偽 PASS 防止）。
     */
    fun assertTabSwipeWithinBudget(testName: String, notBeforeEpochMs: Long) {
        val budgetP50 = explicitBudget("budgetP50Ms")
        val budgetP90 = explicitBudget("budgetP90Ms")
        val budgetP99 = explicitBudget("budgetP99Ms")
        if (budgetP50 == null && budgetP90 == null && budgetP99 == null) {
            throw AssertionError(
                "タブスワイプ／遷移の jank 予算は**未較正**（既定値を持たない）。" +
                    "--assert を使うなら --budget-p50 / --budget-p90 / --budget-p99 で明示するか、" +
                    "まず --assert 無しで実測して TabSwipeBudget へ由来付きの既定定数を入れること。" +
                    "推測値を既定に置かないのは意図的（TabSwipeBudget の KDoc 参照）。"
            )
        }

        val roots = collectSearchRoots()
        val json = roots.asSequence()
            .filter { it.exists() }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.name.endsWith("-benchmarkData.json") }
            .maxByOrNull { it.lastModified() }
            ?: throw AssertionError(
                "予算 assert を要求されたが *-benchmarkData.json が見つからない。" +
                    "instrumentation 引数 androidx.benchmark.output.enable が true でない可能性が高い。" +
                    "探索したルート: " + roots.joinToString(", ") { it.absolutePath }
            )

        val lastModified = json.lastModified()
        if (lastModified < notBeforeEpochMs) {
            throw AssertionError(
                "採用した *-benchmarkData.json が今回の走行より古い＝残骸 JSON の可能性が高く、判定には使わない。" +
                    "JSON lastModified=${lastModified}ms < 走行開始 notBefore=${notBeforeEpochMs}ms。JSON: ${json.absolutePath}"
            )
        }

        val benchmarks = JSONObject(json.readText()).optJSONArray("benchmarks")
            ?: throw AssertionError("benchmarkData.json に benchmarks 配列がない: ${json.absolutePath}")

        var entry: JSONObject? = null
        for (i in 0 until benchmarks.length()) {
            val b = benchmarks.getJSONObject(i)
            if (matchesTestName(b.optString("name"), testName)) {
                entry = b
                break
            }
        }
        val benchmark = entry
            ?: throw AssertionError("$testName に一致するエントリが無い: ${json.absolutePath}")

        val metric = benchmark.optJSONObject("sampledMetrics")?.optJSONObject(METRIC_KEY)
            ?: throw AssertionError("sampledMetrics.$METRIC_KEY メトリクスが無い: ${json.absolutePath}")

        val p50 = metric.getDouble("P50")
        val p90 = metric.getDouble("P90")
        val p99 = metric.getDouble("P99")

        val violations = buildList {
            if (budgetP50 != null && p50 > budgetP50) add("P50=${p50}ms > 予算 ${budgetP50}ms")
            if (budgetP90 != null && p90 > budgetP90) add("P90=${p90}ms > 予算 ${budgetP90}ms")
            if (budgetP99 != null && p99 > budgetP99) add("P99=${p99}ms > 予算 ${budgetP99}ms")
        }
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "タブスワイプ／遷移($testName)が jank 予算を超過: ${violations.joinToString("; ")} " +
                    "(JSON: ${json.absolutePath})"
            )
        }

        // PASS でも実測値と適用予算を1行残す（無音だと「効かないゲート」と区別できない＝診断性の担保）。
        android.util.Log.i(
            "TabSwipeBudget",
            "PASS $testName $METRIC_KEY P50=${p50}ms P90=${p90}ms P99=${p99}ms " +
                "(適用予算 P50<=${budgetP50 ?: "未指定"} P90<=${budgetP90 ?: "未指定"} P99<=${budgetP99 ?: "未指定"})"
        )
    }

    /**
     * benchmarks[].name が testName の指すテストかを判定する。
     * 単純な contains にしないのは "swipeTabs" が "swipeTabsWithTransition" の接頭辞で、
     * 前者を指定したときに後者のエントリを拾いうるため（＝別テストの数字で判定する取り違え）。
     */
    private fun matchesTestName(name: String, testName: String): Boolean {
        val at = name.indexOf(testName)
        if (at < 0) return false
        val after = at + testName.length
        return after >= name.length || !name[after].isLetterOrDigit()
    }

    /** benchmarkData.json の探索ルート群（ScrollBudget.collectSearchRoots と同一の根拠）。 */
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
