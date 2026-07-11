package com.novelreader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.viewmodel.ProcessingState

// ============================================================
// 空状態（本が1冊もないとき）
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
        Spacer(Modifier.height(24.dp))
        Text(
            "本棚はまだ空です",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "右下の＋からPDFを追加してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FilledTonalButton(onClick = onAddClick) {
            Text("PDFを追加する")
        }
    }
}

// ============================================================
// 処理中バナー（TopAppBar直下からスライドイン）
// ============================================================
@Composable
internal fun ProcessingBanner(
    processingState: ProcessingState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 幅（fillMaxWidth）は親が決める配置の責務のため呼び出し側から渡す。root では固定しない。
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 主見出し: 停止中は「停止しています…」、通常は変換中タイトル（未判明時は汎用文言）。
                    // 件数(n/m)は連結せず別要素にする。連結すると長いタイトルの省略(...)で
                    // 件数まで切り捨てられ、件数が見えなくなるため。
                    Text(
                        text = if (processingState.isStopping) "停止しています…"
                               else processingState.title.ifEmpty { "PDF処理中…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 補助行: 現在のフェーズ詳細（ページ進捗など）。
                    if (processingState.phase.isNotEmpty()) {
                        Text(
                            text = processingState.phase,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 件数バッジ: 複数件キューイング時のみ右端に常時表示（タイトル省略の影響を受けない）
                if (processingState.queueTotal > 1) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${processingState.queueCurrent}/${processingState.queueTotal}件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                // 停止ボタン: 停止中は連打防止のため非表示にする。
                if (!processingState.isStopping) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("停止", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // ステッパーインジケーター
            val stepLabels = listOf("タイトル", "本文", "分割", "HTML")
            StepperIndicator(
                stepIndex = processingState.stepIndex,
                stepTotal = processingState.stepTotal,
                labels = stepLabels,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // プログレスバー（ステップ切替時は瞬時リセット、通常時はtweenでアニメーション）
            val progress = remember { Animatable(0f) }
            var lastStep by remember { mutableIntStateOf(-1) }
            LaunchedEffect(processingState.stepIndex, processingState.stepLocalPercent) {
                if (processingState.stepIndex != lastStep) {
                    progress.snapTo(0f)
                    lastStep = processingState.stepIndex
                }
                progress.animateTo(
                    targetValue = processingState.stepLocalPercent,
                    animationSpec = tween(durationMillis = 400),
                )
            }
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ステップ ${processingState.stepIndex + 1}/${processingState.stepTotal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

// ============================================================
// ステッパーインジケーター
// ============================================================
@Composable
private fun StepperIndicator(
    stepIndex: Int,
    stepTotal: Int,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(stepTotal) { i ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (i <= stepIndex) primary else outline),
                )
                if (i < stepTotal - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (i < stepIndex) primary else outline),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == stepIndex) primary else outline,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
