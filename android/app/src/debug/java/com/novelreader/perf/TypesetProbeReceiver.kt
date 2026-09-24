package com.novelreader.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * [TypesetWorkProbe] の ON/OFF・リセット・読み出しを adb から行う **debug 限定** の Receiver。
 *
 * ## なぜ src/debug 限定か（出荷物に載らない根拠）
 * このファイルは `src/debug` にのみ存在するため release/benchmark ビルドには一切コンパイルされない。
 * プローブ本体（[TypesetWorkProbe]）は main に居るが**既定 OFF**で、true にできる経路がこの Receiver
 * だけ＝出荷 APK では計測コードが動く手段が存在しない。既存の `SpikeActivity`（同じ debug manifest
 * overlay で exported=true 登録）と同じ隔離の型。
 *
 * ## なぜ logcat でなく ordered broadcast の resultData で返すか
 * `am broadcast` は ordered broadcast を送って `Broadcast completed: result=0, data="…"` を**標準出力に**
 * 印字する＝1行で確実に受け取れる。件数が数万に及ぶ計測点を毎回 logcat へ吐くと
 * ①ログ自体が計測対象のスレッドを遅くする ②ring buffer から溢れて**静かに欠測**する。
 * カウンタは端末内で加算し、読み出しは1回だけ、が正しい形。
 * （併せて logcat にも1行だけ残す＝スクリプトを介さず目視したいとき用。）
 *
 * ## 使い方
 * ```
 * adb -s <serial> shell am broadcast -a com.novelreader.debug.action.TYPESET_PROBE \
 *   -n com.novelreader/com.novelreader.perf.TypesetProbeReceiver --es cmd on
 * ```
 * `cmd` は `on`（有効化＋リセット）/ `off` / `reset` / `dump`。いずれも実行後のスナップショットを返す。
 * 定型シナリオの自動実行は `tools/measure_typeset_work.sh` が正本。
 */
class TypesetProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(EXTRA_CMD)) {
            CMD_ON -> {
                // 有効化と同時にリセットする: 「ON にしてから操作する」が唯一の正しい手順で、
                // ON 前に溜まった数（起動時の初回組版など）が混ざると測定区間が曖昧になるため。
                TypesetWorkProbe.reset()
                TypesetWorkProbe.enabled = true
            }
            CMD_OFF -> TypesetWorkProbe.enabled = false
            CMD_RESET -> TypesetWorkProbe.reset()
            CMD_DUMP -> Unit
            else -> {
                val snapshot = "error=unknown_cmd " + TypesetWorkProbe.snapshot()
                Log.i(TAG, snapshot)
                resultData = snapshot
                return
            }
        }
        val snapshot = TypesetWorkProbe.snapshot()
        Log.i(TAG, snapshot)
        resultData = snapshot
    }

    companion object {
        private const val TAG = "TypesetProbe"

        /** 計測操作の action（debug manifest overlay の intent-filter と同一文字列であること）。 */
        const val ACTION_PROBE = "com.novelreader.debug.action.TYPESET_PROBE"

        private const val EXTRA_CMD = "cmd"
        private const val CMD_ON = "on"
        private const val CMD_OFF = "off"
        private const val CMD_RESET = "reset"
        private const val CMD_DUMP = "dump"
    }
}
