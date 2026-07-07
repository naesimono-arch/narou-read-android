package com.novelreader.viewmodel

import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType

/**
 * 検索の絞り込み条件（条件シートの状態）。
 * range系（length/time/kaiwaritu/sasie）はなろうAPIの "min-max" 文字列をそのまま持つ
 * （UI は段階チップで選ぶため、値の組み立てはチップ定義側が担う）。
 */
data class SearchFilters(
    val types: Set<NarouNovelType> = emptySet(),       // D4 作品の形
    val lastups: Set<NarouLastup> = emptySet(),        // D5 期間
    val attrsInclude: Set<NarouAttr> = emptySet(),
    val attrsExclude: Set<NarouAttr> = emptySet(),
    val length: String? = null,             // D3 文字数
    val time: String? = null,               // D3 読了時間
    val kaiwaritu: String? = null,          // D3 会話率
    val sasie: String? = null,              // D3 挿絵
) {
    val isActive: Boolean get() = this != SearchFilters()

    /** 有効な条件の数（「条件を調整」ボタンのバッジ表示用）。 */
    fun activeCount(): Int {
        var n = 0
        n += types.size
        n += lastups.size
        n += attrsInclude.size
        n += attrsExclude.size
        if (length != null) n++
        if (time != null) n++
        if (kaiwaritu != null) n++
        if (sasie != null) n++
        return n
    }

    // なぜ time と文字数指定の併用不可（マニュアル§4.4）＝両方送ったときの挙動が未定義のため、モデル層で同時に立たないことを保証する。
    fun withLength(v: String?): SearchFilters {
        return copy(length = v, time = null)
    }

    // なぜ time と文字数指定の併用不可（マニュアル§4.4）＝両方送ったときの挙動が未定義のため、モデル層で同時に立たないことを保証する。
    fun withTime(v: String?): SearchFilters {
        return copy(time = v, length = null)
    }
}

enum class SearchRange {
    TITLE, STORY, KEYWORD, WRITER
}

/**
 * 検索画面の下書き状態（検索語＋範囲＋絞り込み）。
 * なぜ VM に持つか: 条件シートを閉じても・結果一覧から戻っても状態が残るように
 * （検索は「条件を練る」往復が多い操作のため、画面ローカル remember では失われて不便）。
 */
data class SearchDraft(
    val word: String = "",
    val inTitle: Boolean = true,
    val inStory: Boolean = false,
    val inKeyword: Boolean = false,
    val inWriter: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
) {
    /** 検索語が空でも絞り込み条件があれば実行できる（条件だけで探す使い方を許す）。 */
    val canSearch: Boolean get() = word.isNotBlank() || filters.isActive

    fun toQuery(): DiscoveryQuery = DiscoveryQuery(
        word = word.trim().takeIf { it.isNotBlank() },
        inTitle = inTitle,
        inStory = inStory,
        inKeyword = inKeyword,
        inWriter = inWriter,
        types = filters.types,
        lastups = filters.lastups,
        attrsInclude = filters.attrsInclude,
        attrsExclude = filters.attrsExclude,
        length = filters.length,
        time = filters.time,
        kaiwaritu = filters.kaiwaritu,
        sasie = filters.sasie,
    )

    /** 結果一覧の見出し。検索語があれば「「word」」、条件のみなら固定文言。 */
    fun resultTitle(): String =
        word.trim().takeIf { it.isNotBlank() }?.let { "「$it」" } ?: "条件で探す"
}

/**
 * カスタム範囲の文字列表現を組み立てる。
 * 数値化できない入力は無視。min>maxの場合は入れ替えて救済する。
 */
fun buildCustomRange(minText: String, maxText: String, unitMultiplier: Int): String? {
    val minVal = minText.trim().toIntOrNull()
    val maxVal = maxText.trim().toIntOrNull()

    if (minVal == null && maxVal == null) return null

    val resolvedMin = minVal?.let { it * unitMultiplier }
    val resolvedMax = maxVal?.let { it * unitMultiplier }

    return when {
        resolvedMin != null && resolvedMax != null -> {
            if (resolvedMin > resolvedMax) {
                "$resolvedMax-$resolvedMin"
            } else {
                "$resolvedMin-$resolvedMax"
            }
        }
        resolvedMin != null -> "$resolvedMin-"
        resolvedMax != null -> "-$resolvedMax"
        else -> null
    }
}

/**
 * 組み立てられた範囲文字列を元のUI用数値テキストのペアに分解する。
 */
fun parseCustomRange(raw: String?, unitDivisor: Int): Pair<String, String> {
    if (raw == null) return Pair("", "")
    val parts = raw.split("-")
    if (parts.size != 2) return Pair("", "")

    val minVal = parts[0].toIntOrNull()?.let { it / unitDivisor }
    val maxVal = parts[1].toIntOrNull()?.let { it / unitDivisor }

    val minText = minVal?.toString() ?: ""
    val maxText = maxVal?.toString() ?: ""
    return Pair(minText, maxText)
}

/**
 * 指定した検索範囲のトグルを適用した新しい [SearchDraft] を返す。
 * 最後の1つをオフにしようとした場合はトグルを無視して自身を返す。
 */
fun SearchDraft.withRangeToggled(range: SearchRange): SearchDraft {
    val currentOnCount = (if (inTitle) 1 else 0) +
            (if (inStory) 1 else 0) +
            (if (inKeyword) 1 else 0) +
            (if (inWriter) 1 else 0)

    val isTargetOn = when (range) {
        SearchRange.TITLE -> inTitle
        SearchRange.STORY -> inStory
        SearchRange.KEYWORD -> inKeyword
        SearchRange.WRITER -> inWriter
    }

    if (currentOnCount == 1 && isTargetOn) {
        // 全解除＝なろうAPI仕様で暗黙の全項目対象（あらすじ・キーワード含む）となり、「なぜこの作品が出たか分からない」不透明が再発するため、最後の1つは外せない（ADR 0007 原則2）。
        return this
    }

    return when (range) {
        SearchRange.TITLE -> copy(inTitle = !inTitle)
        SearchRange.STORY -> copy(inStory = !inStory)
        SearchRange.KEYWORD -> copy(inKeyword = !inKeyword)
        SearchRange.WRITER -> copy(inWriter = !inWriter)
    }
}

/**
 * 半角・全角スペース区切りのトークン集合として、指定したトークンが含まれているか判定する。
 */
fun containsWordToken(word: String, token: String): Boolean {
    val tokens = word.split(Regex("[\\s　]+")).filter { it.isNotEmpty() }
    return tokens.contains(token)
}

/**
 * 半角・全角スペース区切りのトークン集合から、指定したトークンをトグル（追加・除去）する。
 * 追加時は末尾に半角スペース区切りで追加し、除去時は余分な空白を正規化する。
 */
fun toggleWordToken(word: String, token: String): String {
    val tokens = word.split(Regex("[\\s　]+")).filter { it.isNotEmpty() }.toMutableList()
    if (tokens.contains(token)) {
        tokens.remove(token)
    } else {
        tokens.add(token)
    }
    return tokens.joinToString(" ")
}

/**
 * 全3種を選んだら空集合（=すべて）へ正規化。
 * why: 全選択と未指定は同義であり、チップ表示も「すべて」に畳む。
 */
fun toggleType(current: Set<NarouNovelType>, tapped: NarouNovelType): Set<NarouNovelType> {
    val next = if (current.contains(tapped)) {
        current - tapped
    } else {
        current + tapped
    }
    return if (next.size == 3) emptySet() else next
}

/**
 * 非連続な組（SEVENDAY+LASTMONTH）を作らない: 追加でギャップが生まれるなら THISMONTH も点灯。
 * THISMONTH の消灯で非連続になるなら LASTMONTH も消灯（直近側を残す方が「新しい作品を探す」文脈で自然）。
 * 全3種選択は（先月1日〜now の連続レンジとして意味があるので）空へは畳まない。
 */
fun toggleLastup(current: Set<NarouLastup>, tapped: NarouLastup): Set<NarouLastup> {
    val added = !current.contains(tapped)
    val next = if (added) current + tapped else current - tapped

    if (next.size <= 1 || next.size == 3) {
        return next
    }
    if (next.contains(NarouLastup.SEVENDAY) && next.contains(NarouLastup.LASTMONTH)) {
        return if (added) {
            next + NarouLastup.THISMONTH
        } else {
            next - NarouLastup.LASTMONTH
        }
    }
    return next
}

