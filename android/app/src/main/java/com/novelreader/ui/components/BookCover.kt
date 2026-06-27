package com.novelreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ============================================================
// BookCover — タイトルハッシュから書影を動的生成するコンポーザブル
//
// 画像データなしで書影を表現する。book.id のハッシュから色相を決定するため、
// 同じ書籍は常に同じ色になる（ランダムではなく決定的）。
//
// UI-n: 視覚言語 D「和モダン・余白」へ作り替え（モック ui-n-phase0/bookshelf-D.html）。
// なぜ低彩度・暗色スラブにするか: D は寒色×藍ヘアラインの静謐な構図で、
// 旧 HSL の高彩度カラフル書影は世界観に合わないため。色相は残しつつ彩度・明度を抑え、
// 左に藍の縦ルールを引いて「実画像を捏造しない静かな書影」という D の署名を与える。
// ============================================================

/** HSL → RGB 変換（Jetpack Compose は HSL を直接持たないため手動実装） */
private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = hue / 360f
    val s = saturation
    val l = lightness

    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q

    fun hue2rgb(t: Float): Float {
        var t2 = t
        if (t2 < 0f) t2 += 1f
        if (t2 > 1f) t2 -= 1f
        return when {
            t2 < 1f / 6f -> p + (q - p) * 6f * t2
            t2 < 1f / 2f -> q
            t2 < 2f / 3f -> p + (q - p) * (2f / 3f - t2) * 6f
            else          -> p
        }
    }

    return Color(
        red   = hue2rgb(h + 1f / 3f),
        green = hue2rgb(h),
        blue  = hue2rgb(h - 1f / 3f),
    )
}

/**
 * @param bookId 書籍ID（ハッシュのシードに使用）
 * @param title  表紙中央に大きく表示する1〜2文字の抽出元
 * @param modifier Modifier（縦横比はこのModifierで制御する）
 */
@Composable
fun BookCover(
    bookId: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    // ハッシュから色相を決定（同じIDなら毎回同じ色になる）
    val colors = remember(bookId) {
        val hash = bookId.hashCode()
        val hue  = abs(hash) % 360f
        // D 様式: 色相だけ振り、彩度・明度は低く抑えた暗色スラブにする。
        // 彩度 12〜21%（旧 38〜49%）／明度 上 26〜34%・下はさらに 8% 暗く（旧 46〜61%）。
        // これで全書影が「静かな寒色寄りの暗い面」に揃い、藍の縦ルールと文字が映える。
        val saturation = 0.12f + (abs(hash / 360) % 10) / 100f    // 12〜21%
        val lightnessTop = 0.26f + (abs(hash / 8640) % 9) / 100f  // 26〜34%
        val lightnessBot = lightnessTop - 0.08f

        val topColor    = hslToColor(hue, saturation, lightnessTop)
        val bottomColor = hslToColor(hue, saturation, lightnessBot)
        // 暗色スラブに固定したため文字は常に白で読める（旧のしきい値分岐は不要）。
        val textColor   = Color(0xFFFFFFFF)

        Triple(topColor, bottomColor, textColor)
    }

    val (topColor, bottomColor, textColor) = colors

    // 表紙の最初の1文字（絵文字や記号でも1文字として取得）
    val displayChar = title.take(1).ifEmpty { "？" }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor),
                ),
            ),
    ) {
        // ────── 左の藍の縦ルール（D の署名要素）──────
        // なぜ start を固定 dp にするか: グリッド(幅大)・リスト(幅60dp)の双方で
        // おおむね左端 7〜16% に収まり、サイズ非依存で破綻しないため。
        // 暗色スラブ上で沈まないよう、トークン藍より明るめの藍を使う。
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .width(2.dp)
                .fillMaxHeight(0.82f)
                .background(Color(0xFF6E96B8)),
        )
        // ────── 中央の大きな1文字 ──────
        Text(
            text = displayChar,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.90f),
            modifier = Modifier.align(Alignment.Center),
        )
        // 著者名はカード本文（書影の外）に一本化したため、カバー内には描画しない
    }
}
