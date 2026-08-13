package com.novelreader.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 目次の話数ラベル列（モック `.ep`）の整列幅を、[total] と同桁のあらゆる話数を収める上界
 *（桁数×最大数字幅）から決める。K/M/J の目次が共有する（スキンごとに再実装しない）。
 *
 * なぜモックの固定幅（K 44px・M/J 52px）ではだめか（真因・2026-07-29 実機検証／2026-08-07 監査）:
 * モックの `width` は2桁ラベルを前提にした実測値で、3桁「第132話」は収まらず「第132」「話」で
 * 折り返して行高まで崩れる。M/J は `width(52.dp)` 固定のままだったため、4桁「第1240話」では
 * ラベル自体が3行に割れていた。実蔵書には 221 話・282 話・860 話の本があり通常利用で必ず踏む
 *（なろう系は4桁も普通）。
 *
 * なぜ「幅を広げる」ではなく「最長ラベルから決める」か: 固定幅は**整列用の構造幅**＝全行で題名の
 * 開始 x を揃えることが設計意図。単に定数を広げると2桁の本まで間延びし、行ごとに可変にすると
 * 整列そのものが壊れる。幅を [total]（＝リスト単位で不変）だけの関数にすれば、
 * 「1リスト内では全行同幅＝整列は不変」「本ごとに桁数へ追従」の両立になる。
 *
 * なぜ「第[total]話」1本の実採寸ではなく上界方式か（監査 2026-08-06 G-4）: 旧実装は total=1240 の
 * 1本だけを採寸したが、golden `TocK_ep4digits_light_1.0` では途中行「第1028話」だけが2行に割れて
 * いた＝採寸した文字列と実際に描く文字列の幅が食い違う。真因が採寸文字列の選び方（数字の字形差）か
 * Robolectric の丸め差かは未確定のため、どちらでも成立する上界＝「第」「話」の枠幅＋最大幅の数字×桁数
 * で採寸する（同桁のどの話数もこの幅を超えない。単字採寸の切り上げ合算は連字の字送り以上になる）。
 *
 * dp 単位で切り上げるのは、採寸 px → dp → `Modifier.width` の px 戻しで 1px 足りずに折り返す
 * 境界事故を避けるため（3桁はちょうど固定幅の前後＝境界そのものに乗る）。
 *
 * @param total 全話数（＝この本で出る最大の話数。桁数の決定にのみ使う）
 * @param style ラベルを実際に描くときと同一のテキストスタイル（fontSize・letterSpacing の差で幅が変わる）
 * @param minWidth モック由来の構造幅の下限。2桁以下は採寸値がこれを下回るためモック忠実のまま（間延びしない）
 */
@Composable
internal fun rememberTocEpLabelWidth(total: Int, style: TextStyle, minWidth: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(total, style, density, measurer, minWidth) {
        // 上界＝「第話」の枠幅＋（0〜9の最大幅×桁数）。数字ごとの字形幅差・丸め差があっても
        // 同桁のどの話数もこの幅に必ず収まる（KDoc「上界方式」参照）。
        val framePx = measurer.measure(text = "第話", style = style).size.width
        val maxDigitPx = ('0'..'9').maxOf { d -> measurer.measure(text = d.toString(), style = style).size.width }
        val widestPx = framePx + maxDigitPx * total.toString().length
        val measured = with(density) { widestPx.toDp() }
        ceil(measured.value).dp.coerceAtLeast(minWidth)
    }
}
