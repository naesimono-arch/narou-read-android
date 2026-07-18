package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * 章送り（chapter-flip）の frame timing 予算判定ヘルパー（instrumentation 引数 `enableBudgetAssert` が
 * true のときのみ使う）。判定の値源が benchmarkData.json である理由・残骸 JSON 検証の必要性は
 * [StartupBudget] のクラス KDoc と同じ（measureRepeated は void で結果を返さないため、完了直後に
 * 書き出し済みの JSON を読む）。
 *
 * なぜ [ScrollBudget] と共通部（resolveBudget / collectSearchRoots / JSON 探索・残骸検証）を
 * あえて複製するのか:
 * StartupBudget / ScrollBudget は PASS/FAIL 両経路を実機実証済みのため無変更で保全し、共通化
 * リファクタは意図的に見送る（再実証コストが共通化の利得を上回る）。同じ理由で本オブジェクトも
 * 独立させ、予算値の由来コメントをシナリオ固有の文脈ごと局所化する。
 *
 * 予算上書きの instrumentation 引数（`budgetP50Ms`/`budgetP90Ms`/`budgetP99Ms`）は ScrollBudget と
 * 同名を意図的に共用する——同一走行で動くシナリオは常に1つ（スクリプトの --scenario で排他）のため
 * 衝突せず、スクリプト側の透過オプション（--budget-p50 等）を増やさずに済む。
 */
object FlipBudget {

    // 予算値の由来（実測 × 余裕係数）:
    //   OPPO PGEM10（Android 16 / ColorOS・50章シード・30章送り×5反復）の初回実測（2026-07-18）
    //     frameDurationCpuMs P50 7.1 / P90 11.6 / P95 16.9 / P99 30.3ms
    //   なぜ P50/P90 は本棚スクロール（ScrollBudget）と同値で P99 だけ厚いのか:
    //     P50〜P90 は実測が本棚スクロールより軽い（定常スクロール中の描画は同種）ため同じ
    //       15.0 / 20.0ms を共用する（1フレーム 16.7ms 基準の意図も同じ）。
    //     P99 は章切替時の新章パース＋初回レイアウトのスパイクが分位の尾に乗る（実測 30.3ms＝
    //       本棚スクロールの尾 17.5ms より重く、これは jank ではなく章送り固有の構造コスト）。
    //       本棚と同じ 30ms に絞ると健常状態が即 FAIL する＝スパイクを許容する係数（実測×1.65）で
    //       50.0ms に丸める。尾が走行間で大きく揺れる性質（ScrollBudget の再走行実測 +50%超）も同根。
    const val BUDGET_P50_MS = 15.0
    const val BUDGET_P90_MS = 20.0
    const val BUDGET_P99_MS = 50.0

    // FrameTimingMetric の出力先が sampledMetrics.frameDurationCpuMs（P50/P90/P95/P99）である根拠は
    // ScrollBudget の同名コメント参照（メトリクス種別で出力先マップが異なる・実機確認済み）。
    private const val METRIC_KEY = "frameDurationCpuMs"

    /**
     * 適用する予算値を instrumentation 引数で上書き可能にする（FAIL 経路の実機実証・将来較正のため。
     * パース不能な明示指定を既定へ黙って落とさず fail する理由＝較正事故防止は [ScrollBudget] と同じ）。
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
     * 章送りの P50/P90/P99 が予算内かを検証する。
     * 判定できない（JSON が見つからない・スキーマが期待と違う）場合は AssertionError で明示的に失敗させる
     * ——予算 assert を頼まれたのに判定できないのはサイレントスキップせず「失敗」として扱う方針。
     *
     * @param notBeforeEpochMs 今回の measureRepeated 開始時刻（epoch ms）。採用した JSON の
     *   lastModified がこれ未満なら「今回の走行で書き出されていない残骸 JSON」と判断して fail する。
     */
    fun assertFlipWithinBudget(notBeforeEpochMs: Long) {
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

        // flipChapters を名前に含むテストエントリを採用（クラス名の違い等に頑健にするため部分一致）。
        var entry: JSONObject? = null
        for (i in 0 until benchmarks.length()) {
            val b = benchmarks.getJSONObject(i)
            if (b.optString("name").contains("flipChapters")) {
                entry = b
                break
            }
        }
        val benchmark = entry
            ?: throw AssertionError("flipChapters を名前に含むエントリが無い: ${json.absolutePath}")

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
                "章送り(flipChapters)が jank 予算を超過: ${violations.joinToString("; ")} " +
                    "(JSON: ${json.absolutePath})"
            )
        }

        // 予算内（PASS）でも実測値と適用予算を1行 logcat に残す（効かないゲートと区別する診断性＝
        // ScrollBudget の同名コメント参照）。
        android.util.Log.i(
            "FlipBudget",
            "PASS flipChapters $METRIC_KEY P50=${p50}ms P90=${p90}ms P99=${p99}ms " +
                "(適用予算 P50<=${budgetP50}ms P90<=${budgetP90}ms P99<=${budgetP99}ms)"
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
