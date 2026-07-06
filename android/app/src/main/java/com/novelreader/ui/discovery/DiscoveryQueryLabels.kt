package com.novelreader.ui.discovery

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouGenres
import java.util.Locale

// ============================================================
// DiscoveryQuery → 結果一覧の条件チップ文言（モック .conds）への派生。
// Compose 非依存の純関数（testDebugUnitTest でロジック担保するため分離）。
// ============================================================

/** "30-" → "30分〜" / "-30" → "〜30分" / "30-100" → "30〜100分" / "30" → "30分"。 */
internal fun rangeText(raw: String?, unitSuffix: String, format: (Int) -> String = { it.toString() }): String? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split("-")
    return when {
        // "30"（単一値）
        parts.size == 1 -> "${format(parts[0].toIntOrNull() ?: return null)}$unitSuffix"
        parts.size != 2 -> null
        // "-30"（以下）
        parts[0].isEmpty() -> "〜${format(parts[1].toIntOrNull() ?: return null)}$unitSuffix"
        // "30-"（以上）
        parts[1].isEmpty() -> "${format(parts[0].toIntOrNull() ?: return null)}$unitSuffix〜"
        else -> {
            val lo = parts[0].toIntOrNull() ?: return null
            val hi = parts[1].toIntOrNull() ?: return null
            "${format(lo)}〜${format(hi)}$unitSuffix"
        }
    }
}

/** 文字数の人向け表記: 10万 → "10万"、8,500 → "8,500"。 */
internal fun charCountText(n: Int): String =
    if (n >= 10000 && n % 10000 == 0) "${n / 10000}万"
    else String.format(Locale.JAPAN, "%,d", n)

/**
 * 結果一覧ヘッダの条件チップ文言を組み立てる。
 * word そのもの（見出しに出る）とジャンル単独指定（見出しがジャンル名になる）以外の
 * 有効条件を、人が読める短い言葉で並べる。末尾は常に並び順。
 */
fun conditionChipLabels(query: DiscoveryQuery): List<String> {
    val labels = mutableListOf<String>()

    query.type?.let { labels.add(it.uiLabel) }

    // 検索範囲（word があるときのみ意味を持つ）
    if (!query.word.isNullOrBlank()) {
        val ranges = buildList {
            if (query.inTitle) add("タイトル")
            if (query.inStory) add("あらすじ")
            if (query.inKeyword) add("キーワード")
            if (query.inWriter) add("作者名")
        }
        if (ranges.isNotEmpty()) labels.add(ranges.joinToString("・"))
    }
    if (!query.notWord.isNullOrBlank()) labels.add("除外: ${query.notWord}")

    query.biggenres.forEach { code -> NarouGenres.biggenreLabel(code)?.let(labels::add) }
    query.genres.forEach { code -> NarouGenres.genreLabel(code)?.let(labels::add) }

    when {
        query.tensei && query.tenni -> labels.add("転生・転移")
        query.tensei -> labels.add("異世界転生")
        query.tenni -> labels.add("異世界転移")
    }
    if (query.excludeZankoku) labels.add("残酷描写を除く")

    query.lastup?.let { labels.add("${it.uiLabel}更新") }

    rangeText(query.length, "字", ::charCountText)?.let(labels::add)
    rangeText(query.time, "分")?.let { labels.add("読了$it") }
    rangeText(query.kaiwaritu, "%")?.let { labels.add("会話率$it") }
    // 挿絵は「1枚以上」指定が実質「挿絵あり」なので特例で言い換える
    if (query.sasie == "1-") labels.add("挿絵あり")
    else rangeText(query.sasie, "枚")?.let { labels.add("挿絵$it") }

    labels.add("${query.order.uiLabel}順")
    return labels
}
