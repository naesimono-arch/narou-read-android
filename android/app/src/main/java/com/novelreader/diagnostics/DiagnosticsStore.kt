package com.novelreader.diagnostics

import java.io.File

/**
 * 診断イベントの端末内保管庫（`filesDir/diagnostics/` 配下）。
 *
 * 置き場を内部ストレージにするのは、権限なしで書けて他アプリから読めず、アンインストールで
 * 確実に消えるため（診断のために外部ストレージ権限を要求するのは本末転倒）。
 *
 * **書き込みは同期**。クラッシュ経路（[CrashReporter]）から呼ばれ、その直後にプロセスが死ぬため、
 * ワーカーへ投げると書き終わる前に消える。1件が数KBなので同期でも体感に影響しない。
 */
class DiagnosticsStore(private val rootDir: File) {

    private val eventsDir = File(rootDir, DIR_EVENTS)

    /**
     * 1件を書き、古い分を [KEEP_EVENTS] 件まで間引く。
     *
     * 失敗しても例外を投げない: これは診断の付帯機能で、ここでの失敗（ストレージ満杯・権限異常）が
     * アプリ本体の動作やクラッシュハンドラの後続処理（既定ハンドラへの委譲）を巻き添えにしてはならない。
     * 失敗が黙って消えることの代償より、診断が本番挙動を壊す危険の方が大きい。
     */
    fun write(event: DiagnosticEvent) {
        runCatching {
            eventsDir.mkdirs()
            val name = fileNameOf(event.epochMillis, event.kind)
            File(eventsDir, name).writeText(event.format())
            prune()
        }
    }

    /** 保管中の全イベントを新しい順に連結して返す（回収用。無ければ空文字）。 */
    fun dumpAll(): String = runCatching {
        listEventFiles().sortedDescending()
            .joinToString("\n") { File(eventsDir, it).readText() }
    }.getOrDefault("")

    /** 保管件数（回収導線やテストの確認用）。 */
    fun count(): Int = listEventFiles().size

    /**
     * フレーム落ちの要約を1ファイルへ追記する（1回の前面セッション＝背面へ回るたびに1ブロック）。
     *
     * イベントのような1件1ファイルにしないのは、知りたいのが「日をまたいだ傾向」で、
     * 時系列に並んだ1本のテキストの方が読みやすいため。無限成長は [MAX_JANK_BYTES] で頭打ちにし、
     * 溢れたら**古い側を捨てる**（新しい方が知りたい情報なので、末尾を残す）。
     */
    fun appendJank(text: String) {
        runCatching {
            rootDir.mkdirs()
            val file = File(rootDir, FILE_JANK)
            file.appendText(text)
            if (file.length() > MAX_JANK_BYTES) {
                file.writeText(trimToTail(file.readText(), MAX_JANK_BYTES / 2))
            }
        }
    }

    /** 追記済みのフレーム落ち要約（回収用。無ければ空文字）。 */
    fun dumpJank(): String =
        runCatching { File(rootDir, FILE_JANK).readText() }.getOrDefault("")

    private fun listEventFiles(): List<String> =
        eventsDir.list()?.filter { it.startsWith(PREFIX) }?.toList() ?: emptyList()

    private fun prune() {
        expired(listEventFiles(), KEEP_EVENTS).forEach { File(eventsDir, it).delete() }
    }

    companion object {
        private const val DIR_EVENTS = "events"
        private const val PREFIX = "event-"

        /**
         * 保管する最大件数。長期の日常利用で無制限に溜めないための上限で、「直近の異常が読めれば
         * 足りる」用途に対して十分に多い側へ倒してある（1件数KB＝30件でも数百KB）。
         */
        const val KEEP_EVENTS = 30

        /**
         * ファイル名。epochMillis を先頭に置くのは、名前の辞書順＝発生時刻順にして
         * 一覧・間引きをソートだけで済ませるため（13桁の間は桁揃えが保たれる）。
         */
        internal fun fileNameOf(epochMillis: Long, kind: DiagnosticEvent.Kind): String =
            "$PREFIX$epochMillis-${kind.name.lowercase()}.txt"

        /**
         * 間引き対象（＝新しい方から [keep] 件を残し、それ以外）を返す純関数。
         * ファイル I/O を混ぜないのは、この選定規則だけを JVM テストで固定するため。
         */
        internal fun expired(names: List<String>, keep: Int): List<String> =
            names.sortedDescending().drop(keep)

        private const val FILE_JANK = "jank.txt"

        /** フレーム落ち要約ファイルの上限（約256KB）。行数にして数千セッション分＝傾向を見るには十分。 */
        internal const val MAX_JANK_BYTES = 256L * 1024L

        /**
         * 末尾 [maxBytes] 相当を残して古い側を捨てる純関数。**行の途中では切らない**
         * （切ると壊れた1行が先頭に残り、読む側が数値を誤読する）。
         * 改行が1つも無い＝1行が上限を超える異常な入力のときは、丸ごと捨てずそのまま返す
         * （捨てると症状の唯一の記録が消えるため、多少溢れても残す方を選ぶ）。
         */
        internal fun trimToTail(text: String, maxBytes: Long): String {
            if (text.length <= maxBytes) return text
            val tail = text.takeLast(maxBytes.toInt())
            val cut = tail.indexOf('\n')
            return if (cut < 0) text else tail.substring(cut + 1)
        }
    }
}
