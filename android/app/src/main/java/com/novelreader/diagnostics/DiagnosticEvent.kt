package com.novelreader.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 端末内に残す診断イベント1件（クラッシュ／異常終了の推定）。
 *
 * なぜ端末内完結か: 本アプリは「外部送信ゼロ・端末内完結」を守る（Play 公開準備の方針＝handover の
 * プライバシーポリシー節）。そのため Crashlytics 等の送信型 SDK は入れず、実利用で拾った異常は
 * この形でファイルへ落とし、開発者が回収する（debug ビルドなら `adb shell run-as` で読める）。
 *
 * なぜ [Kind.ABNORMAL_EXIT] が要るか: 「前面で使っている最中にアプリが消えた」は ANR・OEM の
 * 強制終了・OOM のいずれでも起こるが、[Thread.UncaughtExceptionHandler] はそのどれも捕まえられない
 * （例外を投げずにプロセスごと落ちるため）。API 30+ なら ApplicationExitInfo が終了理由を教えてくれるが、
 * 検証機の Huawei P30 は **API 29** で使えない。そこで「前面セッションが閉じられないまま次の起動が来た」
 * ことをもって異常終了と推定する（推定である旨は [Kind] の説明どおりで、確定した原因ではない）。
 */
data class DiagnosticEvent(
    val kind: Kind,
    /** 発生時刻（クラッシュ＝その瞬間、異常終了＝**前回セッションの最終確認時刻**）。 */
    val epochMillis: Long,
    /** 発生時に表示していた画面（NavHost の route など）。不明なら null。 */
    val screen: String?,
    val threadName: String?,
    val stackTrace: String?,
    val appVersionName: String,
    val appVersionCode: Long,
    val deviceModel: String,
    val androidRelease: String,
    val androidSdk: Int,
    /** 発生時点の空きメモリ MB（取得できなければ null）。OOM 由来の異常終了を見分ける材料。 */
    val availableMemMb: Long?,
    val totalMemMb: Long?,
    val lowMemory: Boolean?,
) {
    enum class Kind {
        /** 未捕捉例外＝スタックトレースが残る確定情報。 */
        CRASH,

        /** 前面のまま消えた＝ANR / OEM kill / OOM の**いずれか**（区別はつかない）。 */
        ABNORMAL_EXIT,
    }

    /**
     * 人間が読む1件分のテキスト。回収した開発者がそのまま読む前提で、機械パースはしない
     * （パースが要るほど溜まるなら JSON 化を検討する。現状は「たまに数件」を想定）。
     *
     * zone を引数に取るのは、テストで端末のタイムゾーンに依存しない固定出力を得るため。
     */
    fun format(zone: ZoneId = ZoneId.systemDefault()): String {
        val time = TIME_FORMAT.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
        return buildString {
            appendLine("=== novel-reader diagnostics ===")
            appendLine("kind: $kind")
            appendLine("time: $time")
            appendLine("app: $appVersionName ($appVersionCode)")
            appendLine("device: $deviceModel / Android $androidRelease (API $androidSdk)")
            appendLine("screen: ${screen ?: "(unknown)"}")
            appendLine("thread: ${threadName ?: "(n/a)"}")
            val mem = if (availableMemMb == null || totalMemMb == null) "(n/a)"
            else "avail ${availableMemMb}MB / total ${totalMemMb}MB / lowMemory=${lowMemory ?: "?"}"
            appendLine("memory: $mem")
            if (stackTrace != null) {
                appendLine("--- stack trace ---")
                append(stackTrace)
                if (!stackTrace.endsWith("\n")) appendLine()
            }
        }
    }

    companion object {
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        /**
         * 例外をテキスト化する（cause の連鎖・suppressed 込み＝[Throwable.printStackTrace] と同じ形）。
         * 自前で再帰せず printStackTrace に委ねるのは、連鎖の書式を標準と一致させて読み違えを防ぐため。
         */
        fun stackTraceOf(t: Throwable): String = StringWriter().use { sw ->
            PrintWriter(sw).use { t.printStackTrace(it) }
            sw.toString()
        }
    }
}
