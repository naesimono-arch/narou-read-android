package com.novelreader.typeset

/**
 * 縦中横ラン（プレーン文字列内の連続範囲・[start, endExclusive)）。
 * この範囲を組版は1ユニット（CharClass.TATE_CHU_YOKO）として扱う。
 */
data class TcyRun(val start: Int, val endExclusive: Int)

/**
 * 半角の「ラン種別」。縦中横判定はラン長で分岐するため、種別ごとに極大ランを取る。
 */
internal enum class HalfWidthKind { DIGIT, EXCLAM_QUEST, ALPHA }

/**
 * 極大ラン（同種の半角文字が連続する最長範囲）。VerticalTypesetter が
 * 1桁正立・2〜3桁縦中横・4桁以上各字回転・英字回転の全処理を1度の走査で得るために使う。
 */
internal data class HalfWidthRun(val start: Int, val endExclusive: Int, val kind: HalfWidthKind)

private fun halfWidthKindOf(c: Char): HalfWidthKind? = when (c) {
    in '0'..'9' -> HalfWidthKind.DIGIT
    '!', '?' -> HalfWidthKind.EXCLAM_QUEST
    in 'A'..'Z', in 'a'..'z' -> HalfWidthKind.ALPHA
    else -> null
}

/**
 * 半角英数字・半角!? の極大ランを左から列挙する（純関数）。
 * なぜ極大ランか: 縦中横は「桁数」で挙動が変わる（1桁=正立・2〜3桁=縦中横・4桁以上=各字回転）。
 * 極大ランの長さを見て初めて分岐できるため、detectTateChuYokoRuns も Typesetter もこの走査を共有する。
 */
internal fun maximalHalfWidthRuns(text: String): List<HalfWidthRun> {
    val runs = ArrayList<HalfWidthRun>()
    var i = 0
    while (i < text.length) {
        val kind = halfWidthKindOf(text[i])
        if (kind == null) {
            i++
            continue
        }
        var j = i + 1
        // 同種が続く限り伸ばす（DIGIT と EXCLAM_QUEST は混ぜない＝別ラン。例 "1!" は2ラン）。
        while (j < text.length && halfWidthKindOf(text[j]) == kind) j++
        runs.add(HalfWidthRun(i, j, kind))
        i = j
    }
    return runs
}

/**
 * 縦中横として組むべきラン（半角数字 or 半角!? の連続が長さ2〜3のもの）だけを返す純関数。
 *
 * 規則（v1・合成テスト担保が目的＝P0-2 で実蔵書に半角対象ゼロを確認済み）:
 * - 半角数字 [0-9] の連続: 長さ2〜3 → 縦中横。長さ1 → 縦中横にしない（1桁は正立が慣行）。長さ4+ → 縦中横にしない（各字回転）。
 * - 半角 [!?] の連続: 長さ2〜3 → 縦中横（例 !? !! !!?）。長さ1 → しない。4+ → しない（各字回転）。
 * - 半角英字は縦中横対象外（常に回転）。全角は一切対象外（極大ラン走査で拾わない）。
 *
 * 「1桁は縦中横にしない・4桁以上は各字回転」という非縦中横側の最終向きは VerticalTypesetter が
 * maximalHalfWidthRuns を使って確定する。本関数の返り値はあくまで「縦中横にするラン」に限定する契約。
 */
fun detectTateChuYokoRuns(text: String): List<TcyRun> =
    maximalHalfWidthRuns(text)
        .filter { run ->
            (run.kind == HalfWidthKind.DIGIT || run.kind == HalfWidthKind.EXCLAM_QUEST) &&
                (run.endExclusive - run.start) in 2..3
        }
        .map { TcyRun(it.start, it.endExclusive) }
