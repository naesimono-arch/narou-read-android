package com.novelreader.narou.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class NarouOrder(val apiValue: String, val uiLabel: String) {
    DAILY("dailypoint", "日間"),
    WEEKLY("weeklypoint", "週間"),
    MONTHLY("monthlypoint", "月間"),
    QUARTER("quarterpoint", "四半期"),
    TOTAL("hyoka", "累計"),
    NEW("new", "新着"),
}

enum class NarouNovelType(val apiValue: String, val uiLabel: String) {
    SHORT("t", "短編"),
    RENSAI("r", "連載中"),
    KANKETSU("er", "完結済"),
}

enum class NarouLastup(val apiValue: String, val uiLabel: String) {
    SEVENDAY("sevenday", "7日以内"),
    THISMONTH("thismonth", "今月"),
    LASTMONTH("lastmonth", "先月"),
}

enum class NarouAttr(val isParam: String, val notParam: String, val uiLabel: String) {
    TENSEI("istensei", "nottensei", "異世界転生"),
    TENNI("istenni", "nottenni", "異世界転移"),
    R15("isr15", "notr15", "R15"),
    BL("isbl", "notbl", "ボーイズラブ"),
    GL("isgl", "notgl", "ガールズラブ"),
    ZANKOKU("iszankoku", "notzankoku", "残酷な描写"),
}

data class DiscoveryQuery(
    val order: NarouOrder = NarouOrder.WEEKLY,
    val word: String? = null,
    val notWord: String? = null,
    // 検索範囲。全て false のときは API に付けず全項目対象（なろうAPI仕様）
    val inTitle: Boolean = false,
    val inStory: Boolean = false,
    val inKeyword: Boolean = false,
    val inWriter: Boolean = false,
    val biggenres: Set<Int> = emptySet(),
    val genres: Set<Int> = emptySet(),
    val attrsInclude: Set<NarouAttr> = emptySet(),
    val attrsExclude: Set<NarouAttr> = emptySet(),
    val types: Set<NarouNovelType> = emptySet(),
    val lastups: Set<NarouLastup> = emptySet(),
    val time: String? = null,       // 読了時間(分)。"30-100"/"−30"は"-30"/"30-" 形式
    val length: String? = null,     // 文字数。同形式
    val kaiwaritu: String? = null,  // 会話率%。同形式
    val sasie: String? = null,      // 挿絵数。同形式
    val limit: Int = 30,
) {
    /**
     * キャッシュキー。全フィールドを正規化した文字列（Set はソートして連結）。
     */
    fun cacheKey(): String {
        // なぜここで正規化した文字列を結合してキャッシュキーにするか:
        // クエリの全フィールドを一意かつ再現可能な形で文字列化することで、同一のパラメータを持つ
        // リクエストに対してインメモリキャッシュを正確に効かせるため。
        val parts = mutableListOf<String>()
        parts.add("order:${order.name}")
        parts.add("word:${word?.trim().orEmpty()}")
        parts.add("notWord:${notWord?.trim().orEmpty()}")
        parts.add("inTitle:$inTitle")
        parts.add("inStory:$inStory")
        parts.add("inKeyword:$inKeyword")
        parts.add("inWriter:$inWriter")
        parts.add("biggenres:${biggenres.sorted().joinToString(",")}")
        parts.add("genres:${genres.sorted().joinToString(",")}")
        parts.add("attrsInclude:${attrsInclude.map { it.name }.sorted().joinToString(",")}")
        parts.add("attrsExclude:${attrsExclude.map { it.name }.sorted().joinToString(",")}")
        parts.add("types:${types.map { it.name }.sorted().joinToString(",")}")
        parts.add("lastups:${lastups.map { it.name }.sorted().joinToString(",")}")
        parts.add("time:${time.orEmpty()}")
        parts.add("length:${length.orEmpty()}")
        parts.add("kaiwaritu:${kaiwaritu.orEmpty()}")
        parts.add("sasie:${sasie.orEmpty()}")
        parts.add("limit:$limit")
        return parts.joinToString("|")
    }
}

/**
 * types → API type パラメータ。null は「パラメータ無し」。SHORT+RENSAI だけは API に複合値が無いため
 * 特別扱いが要る（NovelApiRepository 側で2クエリマージ）。ここでは null を返す。
 */
fun typeApiParam(types: Set<NarouNovelType>): String? {
    // なぜ: type にハイフンOR は使えない＝ type=t-r は無効値として無視され全件が返る（2026-07-07 実測: allcount が無指定と一致）。
    // 公式複合値 re/ter で表現できない SHORT+RENSAI のみ、呼び出し側で2クエリに分けてマージする。
    if (types.isEmpty() || types.size == 3) return null
    if (types.size == 1) {
        return types.first().apiValue
    }
    // size == 2
    return when {
        types.contains(NarouNovelType.SHORT) && types.contains(NarouNovelType.KANKETSU) -> "ter"
        types.contains(NarouNovelType.RENSAI) && types.contains(NarouNovelType.KANKETSU) -> "re"
        else -> null // SHORT + RENSAI の組み合わせ
    }
}

/**
 * lastups → API lastup パラメータ。単一はプリセット文字列、複数は UNIX 秒の "start-end" 連続レンジ。
 */
fun lastupApiParam(
    lastups: Set<NarouLastup>,
    nowMs: Long,
    zone: ZoneId = ZoneId.of("Asia/Tokyo")
): String? {
    // なぜ: zone を Asia/Tokyo 固定にするのは、なろうのプリセット（thismonth 等）がサーバ＝日本時間の暦で解釈されるため。端末のタイムゾーンに依らず意味を揃える。
    // なぜ: 複数時期の OR はプリセット文字列では表現できないが、lastup は UNIXタイムスタンプのハイフン区切りを受ける（マニュアル§4.5）ため連続レンジへ合成する。
    // 非連続な組（7日以内+先月）は UI 側で間（今月）を自動点灯して構造的に防ぐが、万一漏れても min-max の広い側に倒す（絞りすぎて作品が消えるより害が小さい）。
    if (lastups.isEmpty()) return null
    if (lastups.size == 1) return lastups.first().apiValue

    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
    val intervals = lastups.map { lastup ->
        when (lastup) {
            NarouLastup.SEVENDAY -> {
                val start = now.minusDays(7).toEpochSecond()
                val end = now.toEpochSecond()
                start to end
            }
            NarouLastup.THISMONTH -> {
                val start = now.with(TemporalAdjusters.firstDayOfMonth())
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochSecond()
                val end = now.toEpochSecond()
                start to end
            }
            NarouLastup.LASTMONTH -> {
                val start = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth())
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochSecond()
                val end = now.with(TemporalAdjusters.firstDayOfMonth())
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS).minusSeconds(1).toEpochSecond()
                start to end
            }
        }
    }
    val minStart = intervals.minOf { it.first }
    val maxEnd = intervals.maxOf { it.second }
    return "$minStart-$maxEnd"
}
