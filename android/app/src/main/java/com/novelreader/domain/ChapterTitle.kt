package com.novelreader.domain

/**
 * 章題を「話数ラベル」と「題そのもの」へ分けた結果（[splitChapterTitle] の出力）。
 *
 * なぜこの型が要るか（2026-07-31 のデータ整備）: 章見出しの正本モック（reading-D / reading-vertical-scroll-D
 * の `.chap-h`）は **`.num`（話数ラベル・ゴシック小・アクセント色）と `.t`（題・明朝）を別要素**として持つが、
 * アプリの [com.novelreader.model.ChapterContent.title] は PDF/Web 取込の見出し行をそのまま1本の文字列で
 * 抱えており、両者を分けて扱う手段が存在しなかった（`ui/VerticalChapterContent.VerticalChapterHeader` の
 * KDoc が「分離したデータを持たない」と明記していたのがこれ）。本ファイルはその分離だけを提供する。
 *
 * **描画はまだどこもこの型を読んでいない**＝現状の見た目は1pxも変わらない。ラベルをどう組むか
 * （生の接頭辞のまま出すのか・M/P/J のように index から「第 百二十七 話」と漢数字で組み直すのか・
 * ゴシック化するのか・そもそも出すのか）は意匠＝design 裁定の領分なので、ここでは決めない。
 */
data class ChapterTitleParts(
    /** 切り出した話数（半角/全角数字を10進解釈）。null＝ラベルを見つけられなかった。 */
    val episodeNumber: Int?,
    /** ラベルを取り除いた題本体。ラベルが無ければ原文そのまま（＝切り出しに失敗しても情報を失わない）。 */
    val body: String,
    /** 実際に取り除いた生の接頭辞（例 `"０１．"`）。null＝取り除いていない。
     *  なぜ生も返すか: 「原文の見え方を保ちたい」意匠になったとき、[episodeNumber] から組み直すのでなく
     *  この文字列をそのまま `.num` へ置ける。どちらを採るかは裁定側の選択肢として残す。 */
    val rawLabel: String?,
)

// 半角/全角の算用数字。漢数字は**意図的に対象外**——「一人の少年」「三度まで」のような題の
// 先頭語と機械的に区別できず、題を削る誤爆の方が実害が大きいため（切り出さなければ現状維持で無害）。
private const val DIGITS = "0-9０-９"

// 形A: 助数詞つき（`第127話 …`・`12話　…`・`第3章．…`）。助数詞のあとに**区切り（約物か空白）を要求する**のは
// 「12話目の冒険」のような題を `12話` ＋ `目の冒険` に割ってしまわないため（区切りが無ければ切らない＝現状維持）。
private val COUNTER_FORM = Regex("^第?[$DIGITS]+[話章](?:[．.、:：]|[　 ])[　 ]*")

// 形B: 数字＋約物区切り（`０１．…`・`12.…`・`3、…`）。実蔵書 N1453LW の全章がこの形
// （`０１．婚約の継続をされたいのですか？`）。約物の直後が数字なら切らない＝「2.5次元の…」のような
// 小数・章節番号を `2.` ＋ `5次元の…` に割る誤爆を塞ぐ。
private val PUNCT_FORM = Regex("^[$DIGITS]+[．.、:：][　 ]*(?![$DIGITS])")

// 形C: 数字＋空白だけ（`１　嵐の夕暮れ`＝実蔵書 N2959KI の全章）。**単独では曖昧**——
// 「100　万回生きた猫」のような題と字面が同一で、機械には区別できない。よって目次順の話数
// （expectedNumber）が一致したときだけ採る（後述の why）。
private val SPACE_FORM = Regex("^[$DIGITS]+[　 ]+")

/**
 * 章題の先頭にある話数ラベルを切り出す。切り出せなければ [ChapterTitleParts.body] は原文のまま。
 *
 * @param title 章題（[com.novelreader.model.ChapterContent.title] / [com.novelreader.model.TocEntry.title]）。
 * @param expectedNumber その章の目次順の話数（1始まり。UI が既に持つ `chapterNumber` と同じ値）。null＝不明。
 *   なぜ照合材料を受けるか: 「数字＋空白」始まりの題は**ラベルなのか題の一部なのか字面では決まらない**
 *   （`１　嵐の夕暮れ` と `100　万回生きた猫` は同型）。目次順と一致する数字なら「作者が振った通し番号」と
 *   見なしてよく、一致しない/不明なら曖昧なまま切らない＝**題を削る誤爆より、ラベルを出さない現状維持を選ぶ**。
 *   助数詞つき（形A）・約物区切り（形B）は字面だけで確定できるので照合を要求しない。
 */
fun splitChapterTitle(title: String, expectedNumber: Int? = null): ChapterTitleParts {
    val noLabel = ChapterTitleParts(episodeNumber = null, body = title, rawLabel = null)

    val match = COUNTER_FORM.find(title)
        ?: PUNCT_FORM.find(title)
        ?: SPACE_FORM.find(title)?.takeIf { spaceForm ->
            // 曖昧形は目次順と一致したときだけ採用（上の @param why）。
            expectedNumber != null && parseEpisodeNumber(spaceForm.value) == expectedNumber
        }
        ?: return noLabel

    val body = title.substring(match.range.last + 1)
    // ラベルだけで題が空になる（`第12話` のみ等）ときは切らない: 見出しから題が消えて
    // 「話数しか出ない章」になる方が情報を失う。原文をそのまま題として扱う。
    if (body.isBlank()) return noLabel

    val number = parseEpisodeNumber(match.value) ?: return noLabel
    return ChapterTitleParts(episodeNumber = number, body = body, rawLabel = match.value)
}

/** 接頭辞の中の数字列を 10 進で読む（全角数字は半角へ寄せる）。桁あふれ・数字なしは null。 */
private fun parseEpisodeNumber(rawLabel: String): Int? {
    val digits = rawLabel.mapNotNull { ch ->
        when (ch) {
            in '0'..'9' -> ch
            // 全角数字（U+FF10-U+FF19）を半角へ。Char.digitToInt は全角も受けるが、
            // ここは「連続した数字列だけを拾う」ため明示的に写像する。
            in '０'..'９' -> '0' + (ch - '０')
            else -> null
        }
    }.joinToString("")
    // toIntOrNull は桁あふれで null＝異常に長い数字列を話数として捏造しない。
    return digits.toIntOrNull()?.takeIf { it > 0 }
}
