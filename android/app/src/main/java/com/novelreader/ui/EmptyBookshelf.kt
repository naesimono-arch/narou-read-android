package com.novelreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.Spacing

// ============================================================
// 空状態（本が1冊もないとき）。ProcessingBanner.kt からの純移動（2026-07-27・役割が別物のため同居を解消）。
// ============================================================
@Composable
internal fun EmptyBookshelf(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    // サイズ（fillMaxSize 等）は配置を決める親の責務のため呼び出し側から渡す。
    // 内部で固定すると別の余白・配置で再利用できなくなるため root では固定しない。
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 空状態イラストの線色。旧『紙と墨』暖色 #D7C6BF の取り残し＝D パレット外（ADR 0014 原則3）。
        // テーマ追従の outline へ。outlineVariant(#ECEAE4) は素地比 1.08:1 でイラストが消えるため、
        // 静かだが見える outline(#9CA0A8) を採る。
        // Canvas(DrawScope) 内では colorScheme を読めないため composition で読んで渡す。
        val illustColor = MaterialTheme.colorScheme.outline
        // Canvas で描く空の本棚イラスト
        Canvas(
            modifier = Modifier.size(140.dp),
        ) {
            val w = size.width
            val h = size.height
            val color = illustColor

            // 棚板（上下2本）
            drawLine(color, start = Offset(0f, h * 0.30f), end = Offset(w, h * 0.30f), strokeWidth = 3.dp.toPx())
            drawLine(color, start = Offset(0f, h * 0.72f), end = Offset(w, h * 0.72f), strokeWidth = 3.dp.toPx())

            // 縦柱（左右）
            drawLine(color, start = Offset(w * 0.05f, h * 0.20f), end = Offset(w * 0.05f, h * 0.80f), strokeWidth = 3.dp.toPx())
            drawLine(color, start = Offset(w * 0.95f, h * 0.20f), end = Offset(w * 0.95f, h * 0.80f), strokeWidth = 3.dp.toPx())

            // 中央に小さな本シルエット3冊（薄い）
            val bookColor = color.copy(alpha = 0.4f)
            val bw = w * 0.12f
            val bh = h * 0.30f
            val by = h * 0.35f
            listOf(0.30f, 0.46f, 0.62f).forEach { cx ->
                drawRect(bookColor, topLeft = Offset(w * cx - bw / 2, by), size = Size(bw, bh))
            }
        }
        Spacer(Modifier.height(Spacing.S24))
        Text(
            "本棚はまだ空です",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.S8))
        Text(
            "右下の＋からPDFを追加してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S32))
        FilledTonalButton(onClick = onAddClick) {
            Text("PDFを追加する")
        }
    }
}
