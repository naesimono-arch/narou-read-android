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
 * 1字正立・2〜3字縦中横・4字以上各字回転の全処理を1度の走査で得るために使う。
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
 * なぜ極大ランか: 縦中横は「連続字数」で挙動が変わる（1字=正立・2〜3字=縦中横・4字以上=各字回転）。
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
 * 縦中横として組むべきラン（半角英数字 or 半角!? の連続が長さ2〜3のもの）を返す純関数。
 *
 * 規則（2026-07-17 裁定で英字へ拡張。根拠: 全角正規化は一般則でなく作者差が支配的＝
 * N3957FQ 等に半角略号 AW/MW/SIM/LDS が実在→「半角が来たら縦中横」を実装既定にする。
 * 裁定の記録＝親プラン確定済み裁定表・vertical-mode-p0-measurements-2026-07-17.md 追記）:
 * - 半角数字 [0-9]・半角英字 [A-Za-z]・半角 [!?] の同種連続: 長さ2〜3 → 縦中横（例 12・AW・SIM・!?）。
 * - 長さ1 → 縦中横にしない（1字は正立が慣行。E や α のような単独記号語も正立）。
 * - 長さ4+ → 縦中横にしない（1マスに収まらない＝各字回転の欧文横倒し）。
 * - 全角は一切対象外（極大ラン走査で拾わない）。
 *
 * 「1字は正立・4字以上は各字回転」という非縦中横側の最終向きは VerticalTypesetter が
 * maximalHalfWidthRuns を使って確定する。本関数の返り値はあくまで「縦中横にするラン」に限定する契約。
 */
fun detectTateChuYokoRuns(text: String): List<TcyRun> =
    maximalHalfWidthRuns(text)
        .filter { (it.endExclusive - it.start) in 2..3 }
        .map { TcyRun(it.start, it.endExclusive) }
