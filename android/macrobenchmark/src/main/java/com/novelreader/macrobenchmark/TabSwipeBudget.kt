package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * タブ横スワイプ／遷移（[TabSwipeBenchmark]）の frame timing 予算判定ヘルパー。
 *
 * ## 予算の由来（2026-08-06 実機較正＝初回実測 × 余裕係数。それまでは意図的に未較正だった）
 * OPPO PGEM10（Android 16 / ColorOS・100冊＋実HTML50章シード・5反復・`tools/run_macrobenchmark.sh
 * --scenario tab-swipe`）の初回実測 `frameDurationCpuMs`:
 *   swipeTabs               P50 6.33 / P90 10.99 / P99 19.70ms（同日の別走行2回の P99 は 18.06 / 23.60ms）
 *   swipeTabsWithTransition P50 7.18 / P90 12.42 / P99 31.28ms
 * 2テスト共通予算のため**重い方（swipeTabsWithTransition）を基準に**丸める（[ScrollBudget] が
 * list/grid の遅い方へ合わせたのと同じ判断）:
 *   P50 11.0ms＝実測 7.18 × ≈1.5（60fps の1フレーム 16.7ms 内に確実に収める意図）
 *   P90 18.0ms＝実測 12.42 × ≈1.45（1フレーム 16.7ms をわずかに超える程度まで許容）
 *   P99 50.0ms＝実測 31.28 × ≈1.6（遷移窓は push/pop アニメ・目次経由の2段 pop・隣ページ実体化の
 *     スパイクが尾に乗る構造コスト。尾は走行間で大きく揺れる——swipeTabs の P99 が走行間で
 *     18.1→23.6ms（＋30%）を実測——ため、絞ると flaky ゲート化する。[FlipBudget] が P99 だけ
 *     厚い 50.0ms を取ったのと同根・同値）
 *
 * 較正前の本オブジェクトは「引数なしの assert は未較正と fail する」設計だった（推測値を既定に
 * 焼き込むと『緑なのに実機は破綻』か『常時赤』を必ず作るため）。既定定数が入った現在も、
 * その方針の残骸として**引数のパース不能は黙って既定へ落とさず fail する**（[ScrollBudget] と同じ）。
 *
 * 予算上書き引数名（`budgetP50Ms` / `budgetP90Ms` / `budgetP99Ms`）は [ScrollBudget] / [FlipBudget] と共用する
 * ——シナリオは排他実行（`-e class` で1クラスだけ走らせる）なので衝突しない。既存2つと同じ流儀。
 *
 * JSON 探索・残骸検証（lastModified）・sampledMetrics を読む理由は [ScrollBudget] と同一。
 * 共通基底を切らずに複製するのも同じ理由（実機で実証済みのコードを無変更で保全する）。
 */
object TabSwipeBudget {

    // 由来は上の KDoc「予算の由来」参照（2026-08-06 PGEM10 初回実測 × 余裕係数・重い方基準）。
    const val BUDGET_P50_MS = 11.0
    const val BUDGET_P90_MS = 18.0
    const val BUDGET_P99_MS = 50.0

    /** FrameTimingMetric が benchmarkData.json の sampledMetrics へ出すメトリクス名（[ScrollBudget] と同じ）。 */
    private const val METRIC_KEY = "frameDurationCpuMs"

    /** instrumentation 引数 `enableBudgetAssert` を真偽解釈（未指定は false＝従来どおり計測のみ）。 */
    fun isBudgetAssertEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("enableBudgetAssert").toBoolean()

    /**
     * 適用予算の解決（instrumentation 引数で上書き可・未指定は上の実測由来の既定定数）。
     *
     * 空白でない文字列が指定されて Double としてパース不能なときは、既定へ黙って落とさず fail する
     * （指定ミスを黙殺すると意図と違う予算で緑になる＝ScrollBudget.resolveBudget と同じ判断）。
     */
    private fun resolveBudget(argKey: String, default: Double): Double {
        val raw = InstrumentationRegistry.getArguments().getString(argKey)
        if (raw == null || raw.isBlank()) return default
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
        val budgetP50 = resolveBudget("budgetP50Ms", BUDGET_P50_MS)
        val budgetP90 = resolveBudget("budgetP90Ms", BUDGET_P90_MS)
        val budgetP99 = resolveBudget("budgetP99Ms", BUDGET_P99_MS)

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
            if (p50 > budgetP50) add("P50=${p50}ms > 予算 ${budgetP50}ms")
            if (p90 > budgetP90) add("P90=${p90}ms > 予算 ${budgetP90}ms")
            if (p99 > budgetP99) add("P99=${p99}ms > 予算 ${budgetP99}ms")
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
                "(適用予算 P50<=${budgetP50}ms P90<=${budgetP90}ms P99<=${budgetP99}ms)"
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
