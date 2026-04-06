package com.novelreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
// 画像データなしでも Apple Books 風の美しい書影を実現する。
// book.id のハッシュから HSL 色相を決定することで、
// 同じ書籍は常に同じ色になる（ランダムではなく決定的）。
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
 * @param author 表紙下部に小さく表示する著者名
 * @param modifier Modifier（縦横比はこのModifierで制御する）
 */
@Composable
fun BookCover(
    bookId: String,
    title: String,
    author: String,
    modifier: Modifier = Modifier,
) {
    // ハッシュから色相を決定（同じIDなら毎回同じ色になる）
    val colors = remember(bookId) {
        val hash = bookId.hashCode()
        val hue  = abs(hash) % 360f
        // 計画仕様値: 彩度 35〜60%、明度 38〜50%（落ち着いた印象で書影らしく）
        val saturation = 0.35f + (abs(hash / 360) % 25) / 100f   // 35〜60%
        val lightnessTop = 0.38f + (abs(hash / 8640) % 12) / 100f // 38〜50%
        val lightnessBot = lightnessTop - 0.10f

        val topColor    = hslToColor(hue, saturation, lightnessTop)
        val bottomColor = hslToColor(hue, saturation, lightnessBot)
        // テキスト色: 背景が明るければ暗色、暗ければ白
        val textColor   = if (lightnessTop > 0.55f) Color(0xFF1C1916) else Color(0xFFFFFFFF)

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
        // ────── 装飾: 右上に薄い半透明の大文字でテクスチャ感を演出 ──────
        Text(
            text = displayChar,
            fontSize = 80.sp,
            fontWeight = FontWeight.Black,
            color = textColor.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp),
        )

        // ────── 中央の大きな1文字（計画仕様: 36sp Bold, alpha=0.9f） ──────
        Text(
            text = displayChar,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.90f),
            modifier = Modifier.align(Alignment.Center),
        )

        // ────── 下部: 著者名（計画仕様: 11sp, alpha=0.7f） ──────
        if (author.isNotBlank()) {
            Text(
                text = author,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.70f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}
