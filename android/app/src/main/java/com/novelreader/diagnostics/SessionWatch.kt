package com.novelreader.diagnostics

import android.content.SharedPreferences
import com.novelreader.PrefKeys

/**
 * 「前面で使っている最中にアプリが消えた」を次の起動時に検出する仕掛け。
 *
 * なぜ必要か: ANR・OEM の強制終了（EMUI/ColorOS の省電力 kill）・OOM はいずれも例外を投げずに
 * プロセスごと落ちるため、[CrashReporter] では一切捕まらない。終了理由を後から引ける
 * `ApplicationExitInfo` は **API 30+** で、日常利用してもらっている検証機（Huawei P30）は API 29＝使えない。
 *
 * そこで前面セッションの開閉を永続フラグで持ち、「開いたまま次の起動が来た」＝前面のまま消えた、と推定する。
 * 背面へ回ってから殺されたケースは Android の正常動作なので**異常として数えない**（onBackground で閉じる）。
 * クラッシュで落ちた場合も [onCrashRecorded] で閉じ、CRASH と ABNORMAL_EXIT の二重計上を避ける。
 *
 * 精度の限界（承知のうえの割り切り）: 停電・電池切れ・端末再起動も「開いたまま」になるため
 * ABNORMAL_EXIT に混ざる。区別する手立てが API 29 には無いので、件数はやや過大に出る前提で読む。
 */
class SessionWatch(
    private val prefs: SharedPreferences,
    private val recorder: DiagnosticsRecorder,
) {

    /**
     * プロセス起動時に1回。前回が開きっぱなしなら異常終了として記録する。
     * 記録時刻は「前回の最終確認時刻」＝落ちた瞬間に最も近い既知の時刻を使う（採取時刻ではない）。
     */
    fun onProcessStart() {
        val wasOpen = prefs.getBoolean(PrefKeys.DIAG_SESSION_OPEN, false)
        val lastSeenAt = prefs.getLong(PrefKeys.DIAG_LAST_SEEN_AT, 0L)
        if (!shouldReportAbnormalExit(wasOpen, lastSeenAt)) return
        recorder.record(
            kind = DiagnosticEvent.Kind.ABNORMAL_EXIT,
            screen = prefs.getString(PrefKeys.DIAG_LAST_SCREEN, null),
            epochMillis = lastSeenAt,
        )
        closeSession()
    }

    /** アプリが前面に出た（ProcessLifecycleOwner の onStart）。 */
    fun onForeground(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(PrefKeys.DIAG_SESSION_OPEN, true)
            .putLong(PrefKeys.DIAG_LAST_SEEN_AT, nowMillis)
            .apply()
    }

    /** アプリが背面へ回った（＝以後の kill は正常動作なので数えない）。 */
    fun onBackground() = closeSession()

    /** クラッシュとして記録済み＝異常終了として二重に数えない。 */
    fun onCrashRecorded() = closeSession()

    /**
     * 前面での生存確認を更新する（画面遷移のたびに呼ぶ）。異常終了の「時刻」と「画面」の
     * 精度はこの更新頻度で決まる＝どの画面で消えたかが分かるのはここを通しているため。
     */
    fun noteScreen(screen: String, nowMillis: Long = System.currentTimeMillis()) {
        recorder.currentScreen = screen
        prefs.edit()
            .putString(PrefKeys.DIAG_LAST_SCREEN, screen)
            .putLong(PrefKeys.DIAG_LAST_SEEN_AT, nowMillis)
            .apply()
    }

    private fun closeSession() {
        prefs.edit().putBoolean(PrefKeys.DIAG_SESSION_OPEN, false).apply()
    }

    companion object {
        /**
         * 異常終了として記録すべきか（純関数＝この判定だけを JVM テストで固定する）。
         *
         * lastSeenAt==0 を除外するのは、フラグだけ立って時刻が無い状態（旧版からの移行途中や
         * 書き込みの途中終了）で「1970-01-01 に落ちた」という無意味な記録を作らないため。
         */
        internal fun shouldReportAbnormalExit(wasOpen: Boolean, lastSeenAt: Long): Boolean =
            wasOpen && lastSeenAt > 0L
    }
}
