// なぜ domain/ か: 本ファイルは ViewModel でない純ロジック（検索下書きのデータ型と純関数のみ・
// Android 依存は Parcelable/Immutable 注釈だけ）のため、ViewModel 実装が住む viewmodel/ から
// 「UI 状態管理を持たない業務ロジックの置き場」として独立させた（2026-07-27 構造リファクタ）。
package com.novelreader.domain

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.novelreader.narou.model.NarouAttr
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouLastup
import com.novelreader.narou.model.NarouNovelType
import kotlinx.parcelize.Parcelize

/**
 * 検索の絞り込み条件（条件シートの状態）。
 * range系（length/time/kaiwaritu/sasie）はなろうAPIの "min-max" 文字列をそのまま持つ
 * （UI は段階チップで選ぶため、値の組み立てはチップ定義側が担う）。
 */
// なぜ Parcelable か: SearchDraft ごと SavedStateHandle へ退避して process death 復帰させるため（F-E）。
// なぜ @Immutable か: 全フィールドが val で、Set 群は copy() ベース更新のみ（emptySet/setOf/集合演算で
// 生成した read-only Set を差し替える運用＝要素の in-place 変更をしない）ため実質不変。強い skipping 下で
// 本型を「不安定な Set を含む型」として === 比較させず equals 比較の stable 型に固定し、検索条件チップ由来の
// 頻繁な再コンポーズで skippable を効かせる（kotlinx-immutable の List<TextSegment> と同じ stability 狙い）。
@Immutable
@Parcelize
data class SearchFilters(
    val types: Set<NarouNovelType> = emptySet(),       // D4 作品の形
    val lastups: Set<NarouLastup> = emptySet(),        // D5 期間
    // ジャンル: 大ジャンル（すべて）選択＝biggenres、詳細ジャンル選択＝genres。両者は相互排他で同時に非空にしない
    // （理由は withBiggenreToggled/withGenreToggled のコメント参照）。
    val biggenres: Set<Int> = emptySet(),
    val genres: Set<Int> = emptySet(),
    val attrsInclude: Set<NarouAttr> = emptySet(),
    val attrsExclude: Set<NarouAttr> = emptySet(),
    val length: String? = null,             // D3 文字数
    val time: String? = null,               // D3 読了時間
    val kaiwaritu: String? = null,          // D3 会話率
    val sasie: String? = null,              // D3 挿絵
) : Parcelable {
    val isActive: Boolean get() = this != SearchFilters()

    /** 有効な条件の数（「条件を調整」ボタンのバッジ表示用）。 */
    fun activeCount(): Int {
        var n = 0
        n += types.size
        n += lastups.size
        // ジャンルは既存の集合項目（types/lastups/attrs）と同じく「選択チップ1枚＝1件」で数える。
        // biggenres と genres は相互排他で同時に非空にならないため、単純な和で二重計上は起きない。
        n += biggenres.size
        n += genres.size
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

    // 大ジャンル（すべて）チップのトグル。tapped を biggenres に足し引きし、詳細ジャンル(genres)は常に空へ掃く。
    // なぜ biggenres と genres を同時に非空にしないか（相互排他）: なろうAPIは biggenre と genre を別パラメータの
    // AND で合成する（マニュアル§4.2＝ハイフンはパラメータ内の OR、パラメータ間は AND）。大ジャンルと、その配下でない
    // 詳細ジャンルを同時送信すると「biggenre=1 かつ genre=201」のように交わりが空になり、条件は見えているのに結果が
    // 消える（istensei/nottensei 同時指定＝空集合と同族の欠陥。ADR 0007 原則2「見えている条件と実送信の一致」を破る）。
    // 結果画面の changeResultGenreFilter も常に一方のみを設定しており、この二択モードが既存アプリ内セマンティクスと一致する。
    fun withBiggenreToggled(code: Int): SearchFilters {
        val next = if (code in biggenres) biggenres - code else biggenres + code
        return copy(biggenres = next, genres = emptySet())
    }

    // 詳細ジャンルチップのトグル。tapped を genres に足し引きし、大ジャンル(biggenres)は常に空へ掃く（相互排他の理由は withBiggenreToggled 参照）。
    fun withGenreToggled(code: Int): SearchFilters {
        val next = if (code in genres) genres - code else genres + code
        return copy(genres = next, biggenres = emptySet())
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
// なぜ @Immutable か: 全フィールドが val（String/Boolean と @Immutable な SearchFilters）で、更新は copy() で
// 新インスタンスを作る運用のため実質不変。SearchFilters を @Immutable にしても、それを内包する本型に注釈が無いと
// 推論上は不安定なままになり DiscoverySearchContent が equals で skip できない。両方に付けて draft を stable にする。
@Immutable
@Parcelize
data class SearchDraft(
    val word: String = "",
    // 除外語（なろうAPI notword）。空白のみは未指定として扱い、送出時に trim→空なら null 化する（word と同じ流儀）。
    val notWord: String = "",
    val inTitle: Boolean = true,
    val inStory: Boolean = false,
    val inKeyword: Boolean = false,
    val inWriter: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    // なぜ カスタム文字数/読了時間の「生入力テキスト」を draft に持つか（単一真実源＝SSOT）:
    // これらの欄はユーザーが打った生の数値文字列（例 "1"）で、filters.length/time はそれを
    // 万字/分へスケールした送出用レンジ文字列（例 "10000-"）＝別表現。以前は画面側の remember に
    // 生テキストを持ち LaunchedEffect で filters.length/time と双方向同期していたが、length からの
    // 逆算（parseCustomRange）は "01"→"1" 等で桁を落とし IME 入力中の値を壊し得る二重真実源だった。
    // 生テキストを draft 側の唯一の保持先へ畳むことで逆算同期を廃し、process death 復帰でも
    // filters と一緒に生入力が復元される（型は String のまま＝現状の入力体験を保つ）。
    // なぜ SearchFilters ではなく SearchDraft へ置くか: SearchFilters.isActive（全等値判定）/
    // activeCount（length!=null 判定）の意味を変えないため（生テキストの残留が「条件あり」を誤検知
    // し canSearch を誤って true にするのを避ける。生テキストは送出条件ではなく UI 入力の途中状態）。
    val lengthCustomMin: String = "",
    val lengthCustomMax: String = "",
    val timeCustomMin: String = "",
    val timeCustomMax: String = "",
) : Parcelable {
    /** 検索語が空でも絞り込み条件があれば実行できる（条件だけで探す使い方を許す）。 */
    val canSearch: Boolean get() = word.isNotBlank() || filters.isActive

    fun toQuery(): DiscoveryQuery = DiscoveryQuery(
        word = word.trim().takeIf { it.isNotBlank() },
        // 未指定（空白のみ）は notword を送らない＝word と同じ trim→空→null 変換で一貫させる。
        notWord = notWord.trim().takeIf { it.isNotBlank() },
        inTitle = inTitle,
        inStory = inStory,
        inKeyword = inKeyword,
        inWriter = inWriter,
        biggenres = filters.biggenres,
        genres = filters.genres,
        types = filters.types,
        lastups = filters.lastups,
        attrsInclude = filters.attrsInclude,
        attrsExclude = filters.attrsExclude,
        length = filters.length,
        time = filters.time,
        kaiwaritu = filters.kaiwaritu,
        sasie = filters.sasie,
    )

    /** 結果一覧の見出し。検索語1語なら「「word」」、複数語なら件数表記、条件のみなら固定文言。 */
    fun resultTitle(): String {
        val tokens = wordTokens(word)
        return when {
            tokens.isEmpty() -> "条件で探す"
            // なぜ件数表記か: 複数語を鉤括弧のまま並べると見出しが1行に収まらず見切れる（実機
            // フィードバック）。各語は結果一覧の KEYWORD チップに個別表示されるため、見出しは
            // 件数の要約で足りる（作品詳細からの複数キーワード検索の title とも同形式）。
            tokens.size >= 2 -> "キーワード${tokens.size}件"
            else -> "「${tokens.first()}」"
        }
    }
}

/**
 * カスタム範囲の文字列表現を組み立てる。
 * 数値化できない入力は無視。min>maxの場合は入れ替えて救済する。
 */
/**
 * カスタム範囲入力欄のテキストを「半角数字のみ」へ正規化する（全角数字は半角へ写像して受け入れる）。
 * なぜ入力層で正規化するか: IME・貼り付け経由で全角数字や記号が入ると、欄には値が見えているのに
 * toIntOrNull が null になり「見えている条件が送出されない」サイレント無効になるため
 * （ADR 0007 原則2違反。nottensei 送出欠落と同族の欠陥クラス）、数字以外を構造的に入れない。
 */
fun normalizeCustomRangeInput(text: String): String =
    buildString {
        for (ch in text) {
            when (ch) {
                in '0'..'9' -> append(ch)
                in '０'..'９' -> append('0' + (ch - '０'))
            }
        }
    }

fun buildCustomRange(minText: String, maxText: String, unitMultiplier: Int): String? {
    // なぜ負数を弾き Long で乗算するか: 文字数・分数は非負が定義域。貼り付け等で負数が入ると
    // "-50000-50000" のようなハイフン3連の不正レンジ文字列を API 挙動未定義のまま送出してしまう。
    // また Int 乗算は 30万(万字)=3×10^9 で桁あふれして負数化する（同じ不正形の別入口）ため、
    // Long で計算して Int 上限へ丸める。
    val minVal = minText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val maxVal = maxText.trim().toIntOrNull()?.takeIf { it >= 0 }

    if (minVal == null && maxVal == null) return null

    fun scale(v: Int): Int = (v.toLong() * unitMultiplier).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val resolvedMin = minVal?.let { scale(it) }
    val resolvedMax = maxVal?.let { scale(it) }

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
 * 検索語を半角・全角スペース区切りのトークン列へ分解する。
 * なぜ独立関数か: 選択キーワードは独立した Set ではなく word（単一String）へ畳み込む方式のため、
 * 「今どのトークンが選択中か」を UI が列挙する必要がある（選択中キーワードバー）。分割規則は
 * containsWordToken / toggleWordToken と一致させねばならず、規則の二重定義を避けるため両者もこれを介す。
 */
// なぜトップレベル定数化: この区切り正規表現は wordTokens 呼び出しの度にコンパイルされていた。
// キーワードチップの選択判定（containsWordToken）は展開カテゴリの最大115チップぶん毎キーストロークで走るため、
// 呼び出し毎の Regex 再コンパイルが検索画面の再コンポーズ負荷になっていた。パターンは不変なので一度だけコンパイルする。
private val WORD_TOKEN_SEPARATOR = Regex("[\\s　]+")

fun wordTokens(word: String): List<String> =
    word.split(WORD_TOKEN_SEPARATOR).filter { it.isNotEmpty() }

/**
 * 半角・全角スペース区切りのトークン集合として、指定したトークンが含まれているか判定する。
 */
fun containsWordToken(word: String, token: String): Boolean {
    return wordTokens(word).contains(token)
}

/**
 * 半角・全角スペース区切りのトークン集合から、指定したトークンをトグル（追加・除去）する。
 * 追加時は末尾に半角スペース区切りで追加し、除去時は余分な空白を正規化する。
 */
fun toggleWordToken(word: String, token: String): String {
    val tokens = wordTokens(word).toMutableList()
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

/**
 * 段階チップ1つ分＝なろうAPIへ送るレンジ文字列と、UIに出す表示ラベルの対。
 * なぜ対で持つか: 以前はレンジ列（LENGTH_STEPS/TIME_STEPS）とチップ文言が別ファイルで平行定義され、
 * 段の増減や境界変更で片方だけ直すと値とラベルがずれる事故を招いていたため、単一真実源として1箇所で対にする。
 */
data class RangeStep(val range: String, val label: String)

// 連続階段（境界値が隣接段と一致していることが selectedStepIndices の分解可能性の前提）
val LENGTH_STEPS_DEF = listOf(
    RangeStep("-10000", "〜1万字"),
    RangeStep("10000-100000", "1万〜10万字"),
    RangeStep("100000-500000", "10万〜50万字"),
    RangeStep("500000-1000000", "50万〜100万字"),
    RangeStep("1000000-", "100万字〜"),
)
val TIME_STEPS_DEF = listOf(
    RangeStep("-30", "〜30分"),
    RangeStep("30-120", "30分〜2時間"),
    RangeStep("120-600", "2時間〜10時間"),
    RangeStep("600-", "10時間〜"),
)
// 分解ロジック（selectedStepIndices/toggleRangeStep）が使うレンジ文字列列は対定義から射影して単一真実源を保つ。
val LENGTH_STEPS: List<String> = LENGTH_STEPS_DEF.map { it.range }
val TIME_STEPS: List<String> = TIME_STEPS_DEF.map { it.range }

/** ステップ文字列 "min-max"（開端は "-max"/"min-"）を (min, max) に分解する。 */
private fun parseStepBounds(step: String): Pair<String?, String?> {
    val parts = step.split("-")
    return when {
        step.startsWith("-") -> Pair(null, parts.getOrNull(1))
        step.endsWith("-") -> Pair(parts.getOrNull(0), null)
        else -> Pair(parts.getOrNull(0), parts.getOrNull(1))
    }
}

/**
 * 合成レンジ文字列 → 選択中ステップの添字集合。ステップ列の連続部分列 i..j の外周
 * （i の下端〜j の上端。開端は "-"）と一致しない文字列（カスタム入力値）は空集合。
 * なぜ: なろうAPIの length/time は単一レンジしか受けない（minlen/maxlen 併用不可・マニュアル§4.4）ため、複数選択は連続区間の結合として表現する。非隣接選択は間の段を自動点灯し、点灯チップ＝実際に送る範囲を一致させる（ADR 0007 原則2）。
 */
fun selectedStepIndices(raw: String?, steps: List<String>): Set<Int> {
    if (raw == null || steps.isEmpty()) return emptySet()

    val n = steps.size
    for (i in 0 until n) {
        for (j in i until n) {
            // 全選択は除外する（全選択のときは raw は null になり、この関数には raw!=null で入るため）
            if (i == 0 && j == n - 1) continue

            val min = if (i == 0) null else parseStepBounds(steps[i]).first
            val max = if (j == n - 1) null else parseStepBounds(steps[j]).second

            val target = when {
                min == null && max != null -> "-$max"
                min != null && max == null -> "$min-"
                min != null && max != null -> "$min-$max"
                else -> null
            }

            if (raw == target) {
                return (i..j).toSet()
            }
        }
    }
    return emptySet()
}

/**
 * 添字 index をトグルし、非隣接になったら間の段を全て点灯してから外周を合成。
 * 全段点灯は null（=すべて）へ正規化。空も null。
 * なぜ: なろうAPIの length/time は単一レンジしか受けない（minlen/maxlen 併用不可・マニュアル§4.4）ため、複数選択は連続区間の結合として表現する。非隣接選択は間の段を自動点灯し、点灯チップ＝実際に送る範囲を一致させる（ADR 0007 原則2）。
 */
fun toggleRangeStep(raw: String?, index: Int, steps: List<String>): String? {
    if (steps.isEmpty()) return null

    val currentIndices = selectedStepIndices(raw, steps)

    val nextIndices = if (currentIndices.contains(index)) {
        val i = currentIndices.minOrNull() ?: 0
        val j = currentIndices.maxOrNull() ?: 0
        when {
            // 下端の消灯: 残る上側 [index+1..j] を保つ（下端を外して選択全体が消えるのは期待に反する）
            index == i && index < j -> ((index + 1)..j).toSet()
            // 上端・中抜きの消灯: 下側 [i..index-1] を残す
            // なぜ: 中抜きは連続制約上どちらかを捨てるしかなく、決定的で予測可能な挙動にする
            else -> (i until index).toSet()
        }
    } else {
        // 点灯処理: 非隣接になったら間の段を全て点灯
        if (currentIndices.isEmpty()) {
            setOf(index)
        } else {
            val i = currentIndices.min()
            val j = currentIndices.max()
            val newMin = minOf(i, index)
            val newMax = maxOf(j, index)
            (newMin..newMax).toSet()
        }
    }

    if (nextIndices.isEmpty() || nextIndices.size == steps.size) {
        return null
    }

    val newI = nextIndices.min()
    val newJ = nextIndices.max()
    val min = if (newI == 0) null else parseStepBounds(steps[newI]).first
    val max = if (newJ == steps.size - 1) null else parseStepBounds(steps[newJ]).second

    return when {
        min == null && max != null -> "-$max"
        min != null && max == null -> "$min-"
        min != null && max != null -> "$min-$max"
        else -> null
    }
}

