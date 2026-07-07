package com.novelreader.viewmodel

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType

/**
 * 検索の絞り込み条件（条件シートの状態）。
 * range系（length/time/kaiwaritu/sasie）はなろうAPIの "min-max" 文字列をそのまま持つ
 * （UI は段階チップで選ぶため、値の組み立てはチップ定義側が担う）。
 */
data class SearchFilters(
    val type: NarouNovelType? = null,       // D4 作品の形
    val lastup: NarouLastup? = null,        // D5 期間
    val tensei: Boolean = false,            // D2 異世界転生
    val tenni: Boolean = false,             // D2 異世界転移
    val excludeZankoku: Boolean = false,    // D2 残酷描写を除く
    val length: String? = null,             // D3 文字数
    val time: String? = null,               // D3 読了時間
    val kaiwaritu: String? = null,          // D3 会話率
    val sasie: String? = null,              // D3 挿絵
) {
    val isActive: Boolean get() = this != SearchFilters()

    /** 有効な条件の数（「条件を調整」ボタンのバッジ表示用）。 */
    fun activeCount(): Int {
        var n = 0
        if (type != null) n++
        if (lastup != null) n++
        if (tensei) n++
        if (tenni) n++
        if (excludeZankoku) n++
        if (length != null) n++
        if (time != null) n++
        if (kaiwaritu != null) n++
        if (sasie != null) n++
        return n
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
        type = filters.type,
        lastup = filters.lastup,
        tensei = filters.tensei,
        tenni = filters.tenni,
        excludeZankoku = filters.excludeZankoku,
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
