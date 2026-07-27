package com.novelreader.diagnostics

/**
 * 画面ごとのフレーム時間を集計する（フレーム落ちの実利用計測）。
 *
 * **なぜ全フレームを保存せずヒストグラムか**: 60Hz で1分＝3600フレーム、日常利用なら1日で数十万件になる。
 * 生の列を持てばメモリもファイルも破綻するし、逆に「平均だけ」では最も知りたい裾（たまに来る長いフレーム）が
 * 平均に埋もれて消える。バケット分布なら固定メモリで P90/P99 の近似と最悪値が取れる。
 *
 * **なぜ画面別か**: ユーザー報告は「特定の画面でもたつく」という形で来る（本棚スクロール・タブスワイプ等）。
 * 全体の平均では犯人が特定できず、対処が当て推量になる。
 *
 * スレッド安全性: JankStats のコールバックは実装依存の executor から来るため、集計はロックで守る
 * （呼び出し元のスレッドを仮定しない）。1フレームあたりの処理は加算のみで競合は軽い。
 */
class JankAggregator(
    private val bucketUpperBoundsMs: List<Int> = DEFAULT_BUCKET_UPPER_BOUNDS_MS,
) {

    private val lock = Any()
    private val byScreen = LinkedHashMap<String, MutableStat>()

    fun record(screen: String, frameDurationNanos: Long, isJank: Boolean) {
        val ms = frameDurationNanos / NANOS_PER_MS
        synchronized(lock) {
            val stat = byScreen.getOrPut(screen) { MutableStat(LongArray(bucketUpperBoundsMs.size + 1)) }
            stat.frameCount++
            if (isJank) stat.jankCount++
            if (ms > stat.worstMs) stat.worstMs = ms
            stat.buckets[bucketIndexOf(ms)]++
        }
    }

    /** 現在までの集計（記録が無ければ空）。書き出し・表示はこの不変スナップショットに対して行う。 */
    fun snapshot(): List<ScreenJank> = synchronized(lock) {
        byScreen.map { (screen, stat) ->
            ScreenJank(
                screen = screen,
                frameCount = stat.frameCount,
                jankCount = stat.jankCount,
                worstMs = stat.worstMs,
                bucketCounts = stat.buckets.toList(),
                bucketUpperBoundsMs = bucketUpperBoundsMs,
            )
        }
    }

    /** 書き出し済みの分を捨てる（次の区間の集計へ）。 */
    fun reset() = synchronized(lock) { byScreen.clear() }

    private fun bucketIndexOf(ms: Double): Int {
        bucketUpperBoundsMs.forEachIndexed { i, bound -> if (ms <= bound) return i }
        return bucketUpperBoundsMs.size // 最終バケット＝上限なし（外れ値の受け皿）
    }

    private class MutableStat(val buckets: LongArray) {
        var frameCount: Long = 0
        var jankCount: Long = 0
        var worstMs: Double = 0.0
    }

    companion object {
        private const val NANOS_PER_MS = 1_000_000.0

        /**
         * バケット上限（ms）。60Hz の予算 16.7ms を跨ぐ前後を細かく刻み、そこから先は粗くする
         * ＝「予算内か・軽い超過か・体感に出る大遅延か」を見分けるのが目的で、大遅延側の分解能は要らない。
         */
        val DEFAULT_BUCKET_UPPER_BOUNDS_MS = listOf(8, 16, 24, 32, 48, 64, 100, 200, 500)
    }
}

/** 1画面分の集計結果（不変スナップショット）。 */
data class ScreenJank(
    val screen: String,
    val frameCount: Long,
    val jankCount: Long,
    val worstMs: Double,
    val bucketCounts: List<Long>,
    val bucketUpperBoundsMs: List<Int>,
) {

    /** jank と判定されたフレームの割合（%）。フレームが無ければ 0。 */
    val jankPercent: Double
        get() = if (frameCount == 0L) 0.0 else jankCount * 100.0 / frameCount

    /**
     * パーセンタイルの**近似**（ms）。ヒストグラムなのでバケット上限までしか分からない＝
     * 「P90 は 24ms 以内」のような上界として読む値で、実測値そのものではない。
     * 最終バケット（上限なし）に落ちた場合だけは実測の最悪値を返す（上界が無限大では読めないため）。
     */
    fun percentileMs(p: Double): Double {
        if (frameCount == 0L) return 0.0
        val target = frameCount * p
        var cumulative = 0L
        bucketCounts.forEachIndexed { i, count ->
            cumulative += count
            if (cumulative >= target) {
                return if (i < bucketUpperBoundsMs.size) bucketUpperBoundsMs[i].toDouble() else worstMs
            }
        }
        return worstMs
    }

    /**
     * 1行の要約（回収したテキストをそのまま読む前提）。
     * 例: `bookshelf  frames=12000 jank=3.4% p90<=24ms p99<=100ms worst=312.5ms`
     */
    fun formatLine(): String = "%s  frames=%d jank=%.1f%% p90<=%.0fms p99<=%.0fms worst=%.1fms".format(
        screen, frameCount, jankPercent, percentileMs(0.90), percentileMs(0.99), worstMs,
    )
}
