package com.novelreader.diagnostics

import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * 未捕捉例外を端末内へ書き残す [Thread.UncaughtExceptionHandler]。
 *
 * **既定ハンドラを置き換えるのではなく前段に挟む**（記録後に必ず委譲する）。Android の既定ハンドラは
 * ANR ダイアログの表示・logcat への出力・プロセス終了を担っており、これを奪うと「例外が起きたのに
 * アプリが半死のまま残る」最悪の状態になる。診断は本番挙動へ介入しない、が鉄則。
 */
object CrashReporter {

    private val installed = AtomicBoolean(false)

    /**
     * @param onRecorded 記録が終わった直後に呼ばれる（[SessionWatch] のセッション閉じに使う＝
     *   クラッシュとして記録済みのものを、次回起動で「異常終了」として二重に数えないため）。
     *   ここでの例外も握りつぶす＝既定ハンドラへの委譲を止めないため。
     */
    fun install(recorder: DiagnosticsRecorder, onRecorded: () -> Unit = {}) {
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                recorder.record(
                    kind = DiagnosticEvent.Kind.CRASH,
                    throwable = throwable,
                    threadName = thread.name,
                )
            }
            runCatching { onRecorded() }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // 実機では RuntimeInit が既定ハンドラを入れているのでこの枝には来ない。
                // それでも自前で終了させるのは、万一 null だった場合に例外を握りつぶしたまま
                // プロセスを生かしてしまう（＝上記「半死」の状態）ことだけは避けるため。
                Process.killProcess(Process.myPid())
                exitProcess(CRASH_EXIT_CODE)
            }
        }
    }

    private const val CRASH_EXIT_CODE = 10
}
