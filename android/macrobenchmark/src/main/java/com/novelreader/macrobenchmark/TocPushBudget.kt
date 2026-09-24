package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * 本棚→目次 push（[TocPushBenchmark]）の frame timing 予算判定ヘルパー。
 * 判定の値源が benchmarkData.json である理由・残骸 JSON 検証の必要性は [StartupBudget] と同一。
 *
 * 2026-08-21 に実機較正済み（[BUDGET_P50_MS] の由来コメント）。それまでは「引数なしの assert は
 * 未較正と fail する」設計で置いていた（推測値の既定は「緑なのに実機は破綻」か「常時赤」を必ず作るため
 * ＝[TabSwipeBudget] が較正前に採っていたのと同じ判断）。
 *
 * 予算上書き引数名は [ScrollBudget] / [FlipBudget] / [TabSwipeBudget] と共用する
 * （シナリオは `-e class` で排他実行のため衝突しない）。
 */
object TocPushBudget {

    // 予算値の由来（2026-08-21・OPPO PGEM10／Android 16 ColorOS・100冊シード・5反復×6開閉＝30窓・
    // benchmark ビルド〔R8 縮小済み〕・実測 frameDurationCpuMs P50 10.0 / P90 28.3 / P95 31.4 / P99 38.8ms）:
    //
    //   P50 15.0 ＝ 実測 10.0 ×1.5。1フレーム 16.7ms を跨がせない意図は既存3予算と同じ。
    //
    //   P90 35.0 ＝ 実測 28.3 ×1.24。**他の面の P90（18〜20ms）より意図的に厚い**。理由は緩めたいからでは
    //     なく、push 窓が構造的に重いから——遷移アニメ中は本棚と目次が同時に合成され、目次側は初回組み立てを
    //     並行して行う。実測の P90 がそこに座っている以上、20ms に絞れば健常状態が常時赤になる（＝ゲートが
    //     死ぬ）。⚠️ この 35.0 は「この重さでよい」という是認ではない。目次側の初回組み立てを軽くする改修が
    //     入ったら**下げ直すこと**（予算は現状追認でなく退行検知の天井）。
    //
    //   P99 60.0 ＝ 実測 38.8 ×1.55（[FlipBudget] が P99 に掛けた 1.65 とほぼ同じ係数。尾は走行間で
    //     ±50% 級に揺れるため絞ると flaky ゲート化する、という既存2予算の実測則に従う）。
    //     **60.0 という値には二重の意味がある**: 2026-08-19 の gfxinfo 計測が報告した症状は「12窓中11窓で
    //     60ms 超が必ず1枚出る」だった。30窓で同じことが起きれば 60ms 超が約30枚＝全1642フレームの 1.8%
    //     ＝**P99 が必ず 60 を超える**ので、この予算はその症状の再来をちょうど捕まえる高さに置いてある。
    //     ⚠️ 今回の実測では 60ms 超は**全5反復で0枚**（各反復の最大 46〜56ms）＝症状は再現していない。
    //     不一致の考察と、何を見れば決まるかは docs/knowledge/toc-push-tail-not-reproduced-in-macrobench.md。
    const val BUDGET_P50_MS = 15.0
    const val BUDGET_P90_MS = 35.0
    const val BUDGET_P99_MS = 60.0

    /** FrameTimingMetric が sampledMetrics へ出すメトリクス名（[ScrollBudget] と同じ）。 */
    private const val METRIC_KEY = "frameDurationCpuMs"

    /** instrumentation 引数 `enableBudgetAssert` を真偽解釈（未指定は false＝計測のみ）。 */
    fun isBudgetAssertEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("enableBudgetAssert").toBoolean()

    /**
     * 予算値の解決（instrumentation 引数で上書き可・未指定は上の実測由来の既定定数）。
     * パース不能な明示指定は既定へ黙って落とさず fail する（較正事故防止＝[ScrollBudget] と同じ）。
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
     * 本棚→目次 push の P50/P90/P99 が予算内かを検証する。
     * 判定できない（JSON 不在・スキーマ相違）場合はサイレントスキップせず AssertionError で落とす。
     *
     * @param notBeforeEpochMs 今回の measureRepeated 開始時刻（epoch ms）。採用 JSON の lastModified が
     *   これ未満なら残骸 JSON と判断して fail する。
     */
    fun assertTocPushWithinBudget(notBeforeEpochMs: Long) {
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
            if (b.optString("name").contains(TEST_NAME)) {
                entry = b
                break
            }
        }
        val benchmark = entry
            ?: throw AssertionError("$TEST_NAME に一致するエントリが無い: ${json.absolutePath}")

        val metric = benchmark.optJSONObject("sampledMetrics")?.optJSONObject(METRIC_KEY)
            ?: throw AssertionError("sampledMetrics.$METRIC_KEY メトリクスが無い: ${json.absolutePath}")

        val p50 = metric.getDouble("P50")
        val p90 = metric.getDouble("P90")
        val p99 = metric.getDouble("P99")

        val budgetP50 = resolveBudget("budgetP50Ms", BUDGET_P50_MS)
        val budgetP90 = resolveBudget("budgetP90Ms", BUDGET_P90_MS)
        val budgetP99 = resolveBudget("budgetP99Ms", BUDGET_P99_MS)

        val violations = buildList {
            if (p50 > budgetP50) add("P50=${p50}ms > 予算 ${budgetP50}ms")
            if (p90 > budgetP90) add("P90=${p90}ms > 予算 ${budgetP90}ms")
            if (p99 > budgetP99) add("P99=${p99}ms > 予算 ${budgetP99}ms")
        }
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "本棚→目次 push($TEST_NAME)が jank 予算を超過: ${violations.joinToString("; ")} " +
                    "(JSON: ${json.absolutePath})"
            )
        }

        // PASS でも実測値と適用予算を1行 logcat に残す（効かないゲートと区別する診断性）。
        android.util.Log.i(
            "TocPushBudget",
            "PASS $TEST_NAME $METRIC_KEY P50=${p50}ms P90=${p90}ms P99=${p99}ms " +
                "(適用予算 P50<=${budgetP50}ms P90<=${budgetP90}ms P99<=${budgetP99}ms)"
        )
    }

    /** 本オブジェクトが判定する唯一のテスト名（クラス内に兄弟テストが無いので境界判定は要らない）。 */
    private const val TEST_NAME = "pushToToc"

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
