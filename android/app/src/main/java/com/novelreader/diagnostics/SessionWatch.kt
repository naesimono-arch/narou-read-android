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
    /**
     * この prefs が「このインストールが書いたもの」かを見分ける徴（[InstallSentinel]）。
     * 既定値を置かないのは、忘れて省略した呼び出しが**復元由来の偽陽性を素通しする状態で
     * コンパイルできてしまう**ため（既定を付けた瞬間、この防御は付け忘れで黙って無効になる）。
     */
    private val installSentinel: InstallSentinel,
) {

    /**
     * プロセス起動時に1回。前回が開きっぱなしなら異常終了として記録する。
     * 記録時刻は「前回の最終確認時刻」＝落ちた瞬間に最も近い既知の時刻を使う（採取時刻ではない）。
     *
     * 復元直後だけは記録しない。理由と手段の選定は [InstallSentinel] の KDoc が正本
     * （要約: `app_prefs` は丸ごとバックアップされるので、prefs の中身だけでは
     * 「前回の自分」と「他のインストールから復元された残骸」を区別できない）。
     */
    fun onProcessStart() {
        val wasOpen = prefs.getBoolean(PrefKeys.DIAG_SESSION_OPEN, false)
        val lastSeenAt = prefs.getLong(PrefKeys.DIAG_LAST_SEEN_AT, 0L)
        // 徴の確認は記録の判定より**先**。ここで確定させておかないと、下の early return の経路で
        // 徴が立たないまま抜け、初回起動が何度も「初回」に見える。
        val installKnown = installSentinel.isPresent()
        if (!installKnown) {
            installSentinel.create()
            // ⚠️ 残骸をここで畳むのが要（畳まないと偽陽性が消えるのではなく**1回ずれる**だけ）。
            // 次の起動では徴が在る＝installKnown=true になるので、同じ DIAG_SESSION_OPEN=true が
            // そのとき「本物の異常終了」として通ってしまう。
            if (wasOpen) closeSession()
        }
        if (!shouldReportAbnormalExit(wasOpen, lastSeenAt, installKnown)) return
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
         *
         * @param installKnown このインストールで一度でも起動済みか（[InstallSentinel]）。false＝
         *   復元直後か初回起動＝**読んでいる `DIAG_*` がこのインストールの前回起動のものだと言えない**。
         *   ⚠️ この条件は「異常終了を数えにくくする」ためのものではない。徴が立つのは初回の1回だけで、
         *   2回目以降の起動は常に true＝本物の異常終了はこれまでどおり全件通る。
         */
        internal fun shouldReportAbnormalExit(
            wasOpen: Boolean,
            lastSeenAt: Long,
            installKnown: Boolean,
        ): Boolean = installKnown && wasOpen && lastSeenAt > 0L
    }
}
