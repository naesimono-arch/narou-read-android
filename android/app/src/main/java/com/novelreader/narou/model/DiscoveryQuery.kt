package com.novelreader.narou.model

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
    THISWEEK("thisweek", "今週"),
    THISMONTH("thismonth", "今月"),
    LASTMONTH("lastmonth", "先月"),
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
    val tensei: Boolean = false,       // istensei=1
    val tenni: Boolean = false,        // istenni=1
    val excludeZankoku: Boolean = false, // notzankoku=1
    val type: NarouNovelType? = null,
    val lastup: NarouLastup? = null,
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
        parts.add("tensei:$tensei")
        parts.add("tenni:$tenni")
        parts.add("excludeZankoku:$excludeZankoku")
        parts.add("type:${type?.name.orEmpty()}")
        parts.add("lastup:${lastup?.name.orEmpty()}")
        parts.add("time:${time.orEmpty()}")
        parts.add("length:${length.orEmpty()}")
        parts.add("kaiwaritu:${kaiwaritu.orEmpty()}")
        parts.add("sasie:${sasie.orEmpty()}")
        parts.add("limit:$limit")
        return parts.joinToString("|")
    }
}
