package com.novelreader.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/**
 * 診断イベントの採取係。端末情報の収集と [DiagnosticsStore] への書き込みを1箇所に集約する。
 *
 * なぜ「現在画面」をここに @Volatile で持つか: クラッシュは任意のスレッドで起き、その瞬間に
 * Compose の状態や NavController を触ることはできない（別スレッドから読めない・すでに壊れている
 * 可能性がある）。UI 側が遷移のたびに素の文字列を書き込み、採取側はそれを読むだけにする。
 */
class DiagnosticsRecorder(
    private val appContext: Context,
    val store: DiagnosticsStore,
) {

    /** 直近に表示していた画面（NavHost の route 等）。UI 層が遷移のたびに更新する。 */
    @Volatile
    var currentScreen: String? = null

    /**
     * 1件採取して保管する。
     *
     * epochMillis を引数に取るのは、異常終了の推定（[SessionWatch]）が「前回セッションの
     * 最終確認時刻」＝過去の時刻で記録する必要があるため（採取時刻ではない）。
     */
    fun record(
        kind: DiagnosticEvent.Kind,
        throwable: Throwable? = null,
        threadName: String? = null,
        screen: String? = currentScreen,
        epochMillis: Long = System.currentTimeMillis(),
    ) {
        val mem = runCatching {
            ActivityManager.MemoryInfo().also { info ->
                appContext.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            }
        }.getOrNull()
        val event = DiagnosticEvent(
            kind = kind,
            epochMillis = epochMillis,
            screen = screen,
            threadName = threadName,
            stackTrace = throwable?.let { DiagnosticEvent.stackTraceOf(it) },
            appVersionName = versionName,
            appVersionCode = versionCode,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidRelease = Build.VERSION.RELEASE ?: "?",
            androidSdk = Build.VERSION.SDK_INT,
            availableMemMb = mem?.availMem?.let { it / BYTES_PER_MB },
            totalMemMb = mem?.totalMem?.let { it / BYTES_PER_MB },
            lowMemory = mem?.lowMemory,
        )
        store.write(event)
    }

    // 版数はプロセス内で不変なので一度だけ解決する（クラッシュ経路で PackageManager を叩く回数を減らす）。
    private val packageInfo by lazy {
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0) }.getOrNull()
    }
    private val versionName: String get() = packageInfo?.versionName ?: "?"
    private val versionCode: Long
        get() = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
