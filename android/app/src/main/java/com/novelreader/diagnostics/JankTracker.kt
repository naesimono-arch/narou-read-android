package com.novelreader.diagnostics

import android.view.View
import android.view.Window
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 実利用中のフレーム落ちを画面別に自己計測する（JankStats の薄いラッパ）。
 *
 * なぜ画面名を JankStats の state 機構で持つか（自前の変数に覚えないか）:
 * フレームは「開始時点の状態」に属する。画面遷移中のフレームをコールバック時点の画面名で数えると、
 * 遷移で重いフレームが**遷移先の画面のせい**に見えてしまい、犯人を取り違える。
 * JankStats はフレーム開始時の state を [androidx.metrics.performance.FrameData] に添えて返すので、
 * それをそのまま集計キーにする。
 *
 * 収集は前面にいる間だけ（[setTrackingEnabled]）。背面で回し続ける意味が無く、電池を無駄に食うため。
 */
class JankTracker(val aggregator: JankAggregator = JankAggregator()) {

    private var jankStats: JankStats? = null
    private var stateHolder: PerformanceMetricsState.Holder? = null

    /**
     * Activity の window へ接続する。二重接続は前の購読を捨ててから張り直す
     * （Activity 再生成のたびに購読が積み上がると同じフレームを多重計上するため）。
     */
    fun attach(window: Window, contentView: View) {
        jankStats?.isTrackingEnabled = false
        jankStats = JankStats.createAndTrack(window) { frameData ->
            val screen = frameData.states
                .firstOrNull { it.key == KEY_SCREEN }?.value
                ?: UNKNOWN_SCREEN
            aggregator.record(screen, frameData.frameDurationUiNanos, frameData.isJank)
        }
        stateHolder = PerformanceMetricsState.getHolderForHierarchy(contentView)
    }

    /** 現在の画面名を差し替える（以後のフレームがこの名前で数えられる）。 */
    fun setScreen(screen: String) {
        stateHolder?.state?.putState(KEY_SCREEN, screen)
    }

    fun setTrackingEnabled(enabled: Boolean) {
        jankStats?.isTrackingEnabled = enabled
    }

    /**
     * 集計を要約テキストとして書き出し、次の区間のために捨てる。
     * 記録がゼロのときは何も書かない（空ブロックでファイルを埋めない）。
     */
    fun flushTo(store: DiagnosticsStore, nowMillis: Long = System.currentTimeMillis()) {
        val stats = aggregator.snapshot()
        if (stats.isEmpty()) return
        store.appendJank(formatReport(stats, nowMillis))
        aggregator.reset()
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val UNKNOWN_SCREEN = "(unknown)"

        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

        /**
         * 1セッション分のブロック。フレーム数の多い画面から並べる＝実際に長く使われた画面が上に来る
         * （数フレームしか描いていない画面の jank 率は分母が小さく誤読を招くため、下へ送る）。
         */
        internal fun formatReport(
            stats: List<ScreenJank>,
            nowMillis: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): String {
            val time = TIME_FORMAT.withZone(zone).format(Instant.ofEpochMilli(nowMillis))
            return buildString {
                appendLine("--- session end $time ---")
                stats.sortedByDescending { it.frameCount }.forEach { appendLine(it.formatLine()) }
            }
        }
    }
}
