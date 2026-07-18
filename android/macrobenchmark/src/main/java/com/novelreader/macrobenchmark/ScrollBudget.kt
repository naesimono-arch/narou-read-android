package com.novelreader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * 本棚スクロールの frame timing 予算判定ヘルパー（instrumentation 引数 `enableBudgetAssert` が
 * true のときのみ使う）。判定の値源が benchmarkData.json である理由・残骸 JSON 検証の必要性は
 * [StartupBudget] のクラス KDoc と同じ（measureRepeated は void で結果を返さないため、完了直後に
 * 書き出し済みの JSON を読む）。
 *
 * なぜ StartupBudget と共通部（resolveBudget / collectSearchRoots / JSON 探索・残骸検証）を
 * あえて複製するのか:
 * StartupBudget は PASS/FAIL 両経路を実機実証済みのため無変更で保全し、共通化リファクタは
 * 意図的に見送る（再実証コストが共通化の利得を上回る）。ここで共通基底を切ると StartupBudget 側の
 * 挙動が実証済みの状態から動きうるため、複製の重複を承知で独立させる。
 */
object ScrollBudget {

    // 予算値の由来（実測 × 余裕係数・list/grid 両モード共通）:
    //   OPPO PGEM10（Android 16 / ColorOS・100冊シード・5反復）の初回実測
    //     list P50 8.6ms / P99 16.6ms・grid P50 10.4ms / P99 17.5ms
    //   を基に list/grid の遅い方（grid）に余裕を持たせて丸めた共通予算。
    //   なぜ係数を差別化するか:
    //     P50 は 60fps の1フレーム 16.7ms 未満に収める意図で実測（最遅 grid 10.4ms）に約1.5倍の
    //       余裕を掛けて 15.0ms に丸め（1フレーム内に確実に収める）。
    //     P99 はワースト外乱（ColorOS の SIGQUIT 除細動・端末温度ばらつき等）を吸収するため
    //       実測（最遅 grid 17.5ms）より厚めの係数を取り 30.0ms に丸め（尾を広く許容）。
    //       厚めが必要な裏付け＝assert 導入時の再走行（2026-07-17）で P99 は list 25.8 / grid 26.4ms
    //       を実測（初回比 +50%超）。尾は走行間で大きく揺れるため、P99 を絞ると flaky ゲート化する。
    //     P90 はその中間として 20.0ms（1フレーム 16.7ms をわずかに超える程度まで許容）。
    const val BUDGET_P50_MS = 15.0
    const val BUDGET_P90_MS = 20.0
    const val BUDGET_P99_MS = 30.0

    // FrameTimingMetric が benchmarkData.json に出すメトリクス名。
    // なぜ metrics ではなく sampledMetrics を読むのか:
    // StartupTimingMetric は集約統計を metrics.timeToInitialDisplayMs（median/maximum）に出すが、
    // FrameTimingMetric はフレーム単位のサンプル分位を sampledMetrics.frameDurationCpuMs
    // （P50/P90/P95/P99）に出す（tools/run_macrobenchmark.sh 末尾の Python 表示部が同スキーマを
    // 実機確認済み）。メトリクスの種別で出力先マップが異なるため、こちらは sampledMetrics を辿る。
    private const val METRIC_KEY = "frameDurationCpuMs"

    /**
     * 適用する予算値を instrumentation 引数（`budgetP50Ms` / `budgetP90Ms` / `budgetP99Ms`）で
     * 上書き可能にする。
     *
     * なぜ上書きを許すか:
     * FAIL 経路（予算超過）の実機実証と、将来の予算較正（端末更新・OS 更新での基準見直し）を
     * コード変更・再ビルドなしで行えるようにするため。既定は上の実測由来の定数のまま。
     *
     * 未指定（引数が無い／空白）なら既定へ落とす。ただし空白でない文字列が明示指定されて
     * かつ Double としてパース不能な場合は、サイレントに既定へ落とさず AssertionError で fail する
     * ——指定ミス（typo・単位付き等）を黙って既定で走らせると、意図した予算と違う値で緑になり
     *   較正事故（効かないゲート）の温床になるため。
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
     * 本棚スクロールの P50/P90/P99 が予算内かを検証する。
     * 判定できない（JSON が見つからない・スキーマが期待と違う）場合は AssertionError で明示的に失敗させる
     * ——予算 assert を頼まれたのに判定できないのはサイレントスキップせず「失敗」として扱う方針。
     *
     * @param testName 対象テストの識別子（"scrollList" / "scrollGrid"）。benchmarks[] の name に
     *   含まれるエントリを部分一致で採用する。
     * @param notBeforeEpochMs 今回の measureRepeated 開始時刻（epoch ms）。採用した JSON の
     *   lastModified がこれ未満なら「今回の走行で書き出されていない残骸 JSON」と判断して fail する。
     */
    fun assertScrollWithinBudget(testName: String, notBeforeEpochMs: Long) {
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

        // testName（"scrollList" / "scrollGrid"）を名前に含むテストエントリを採用
        // （クラス名の違い等に頑健にするため部分一致）。
        var entry: JSONObject? = null
        for (i in 0 until benchmarks.length()) {
            val b = benchmarks.getJSONObject(i)
            if (b.optString("name").contains(testName)) {
                entry = b
                break
            }
        }
        val benchmark = entry
            ?: throw AssertionError("$testName を名前に含むエントリが無い: ${json.absolutePath}")

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
                "本棚スクロール($testName)が jank 予算を超過: ${violations.joinToString("; ")} " +
                    "(JSON: ${json.absolutePath})"
            )
        }

        // 予算内（PASS）でも実測値と適用予算を1行 logcat に残す。
        // なぜ成功時も出すか: 無音だと「assert が本当に実行されたのか／どの予算で緑になったのか」を
        // logcat から確認できず、効かないゲート（永遠に緑）と区別できないため＝診断性の担保。
        android.util.Log.i(
            "ScrollBudget",
            "PASS $testName $METRIC_KEY P50=${p50}ms P90=${p90}ms P99=${p99}ms " +
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
