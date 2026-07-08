package com.novelreader.ui.discovery

import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouNovelType
import com.novelreader.narou.model.NarouLastup
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
 * 条件チップの種別。
 *
 * なぜ enum を導入したか: 以前は結果画面（[DiscoveryResultScreen]）側が「表示文字列と
 * 一致するか」「末尾の位置か」でチップ種別を推測していた（例: ラベルが biggenreLabel と
 * 一致すれば大ジャンルチップ、index==lastIndex なら並び順チップ）。文言や並び順を変えると
 * 種別判定が静かに壊れる脆い設計だったため、生成時点で種別を型として持たせる。
 *
 * 値は消費側（[DiscoveryResultScreen]）の分岐から実際に使われている区別を逆抽出したもの:
 * - [ORDER] / [BIG_GENRE] / [GENRE] / [GENRE_PLACEHOLDER] はクリックでドロップダウンを開く
 * - [CONDITION] は表示のみ（クリック不可）
 */
enum class ChipKind {
    /** 通常の条件チップ（作品種別・検索範囲・属性・期間・文字数など）。表示のみ。 */
    CONDITION,
    /** 大ジャンル。1件のみ選択時はクリックでジャンル変更ドロップダウンを開く。 */
    BIG_GENRE,
    /** 小ジャンル。1件のみ選択時はクリックでジャンル変更ドロップダウンを開く。 */
    GENRE,
    /** ジャンル未指定時に消費側が差し込むプレースホルダ「ジャンル」チップ。クリック可。 */
    GENRE_PLACEHOLDER,
    /** 末尾の並び順チップ。クリックで並び順ドロップダウンを開く。 */
    ORDER,
}

/** 結果一覧ヘッダの条件チップ1枚（表示文言＋種別）。 */
data class ConditionChip(val label: String, val kind: ChipKind)

/**
 * 結果一覧ヘッダの条件チップを組み立てる。
 * word そのもの（見出しに出る）とジャンル単独指定（見出しがジャンル名になる）以外の
 * 有効条件を、人が読める短い言葉で並べる。末尾は常に並び順。
 */
fun conditionChipLabels(query: DiscoveryQuery): List<ConditionChip> {
    val chips = mutableListOf<ConditionChip>()
    // なぜ addChip 命名か: `add` にすると下の buildList ラムダ内でレシーバの MutableList.add を
    // ローカル関数がシャドウし（ローカル関数優先）、型推論不能＋チップ誤追加の罠になるため。
    fun addChip(label: String, kind: ChipKind = ChipKind.CONDITION) = chips.add(ConditionChip(label, kind))

    if (query.types.isNotEmpty()) {
        val label = NarouNovelType.values().filter { it in query.types }.joinToString("・") { it.uiLabel }
        addChip(label)
    }

    // 検索範囲（word があるときのみ意味を持つ）
    if (!query.word.isNullOrBlank()) {
        val ranges = buildList {
            if (query.inTitle) add("タイトル")
            if (query.inStory) add("あらすじ")
            if (query.inKeyword) add("キーワード")
            if (query.inWriter) add("作者名")
        }
        if (ranges.isNotEmpty()) addChip(ranges.joinToString("・"))
    }
    if (!query.notWord.isNullOrBlank()) addChip("除外: ${query.notWord}")

    query.biggenres.forEach { code -> NarouGenres.biggenreLabel(code)?.let { addChip(it, ChipKind.BIG_GENRE) } }
    query.genres.forEach { code -> NarouGenres.genreLabel(code)?.let { addChip(it, ChipKind.GENRE) } }

    val hasTensei = NarouAttr.TENSEI in query.attrsInclude
    val hasTenni = NarouAttr.TENNI in query.attrsInclude
    if (hasTensei && hasTenni) {
        addChip("転生・転移")
    } else if (hasTensei) {
        addChip("異世界転生")
    } else if (hasTenni) {
        addChip("異世界転移")
    }

    NarouAttr.values().forEach { attr ->
        if (attr != NarouAttr.TENSEI && attr != NarouAttr.TENNI && attr in query.attrsInclude) {
            addChip(attr.uiLabel)
        }
    }

    NarouAttr.values().forEach { attr ->
        if (attr in query.attrsExclude) {
            addChip("${attr.uiLabel}を除く")
        }
    }

    if (query.lastups.isNotEmpty()) {
        val label = NarouLastup.values().filter { it in query.lastups }.joinToString("・") { it.uiLabel }
        addChip("${label}に更新")
    }

    rangeText(query.length, "字", ::charCountText)?.let { addChip(it) }
    rangeText(query.time, "分")?.let { addChip("読了$it") }
    rangeText(query.kaiwaritu, "%")?.let { addChip("会話率$it") }
    // 挿絵は「1枚以上」指定が実質「挿絵あり」なので特例で言い換える
    if (query.sasie == "1-") addChip("挿絵あり")
    else rangeText(query.sasie, "枚")?.let { addChip("挿絵$it") }

    addChip("${query.order.uiLabel}順", ChipKind.ORDER)
    return chips
}
