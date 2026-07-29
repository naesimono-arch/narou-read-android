package com.novelreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.MotionSpringProgressFollow
import com.novelreader.viewmodel.ProcessingSource
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.ui.theme.Spacing

// EmptyBookshelf（空状態）は ui/EmptyBookshelf.kt へ純移動した（2026-07-27・役割が別物のため同居を解消）。

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
            modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S16),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.S12))
                Column(modifier = Modifier.weight(1f)) {
                    // 主見出し: 停止中は「停止しています…」、通常は変換中タイトル（未判明時は汎用文言）。
                    // 件数(n/m)は連結せず別要素にする。連結すると長いタイトルの省略(...)で
                    // 件数まで切り捨てられ、件数が見えなくなるため。
                    // 題名は1行で十分（最初の数文字で作品の判別はつく＝ユーザー所見 2026-07-15）。
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
                    // 2行許容する（実使用フィードバック 2026-07-15＝本質的な不満は「読み込み中のページ数が読めない」）。
                    // phase 文字列は「本文を読み込んでいます… 45%（3/12ページ）」のようにページ数が文末に付くため
                    // （生成元 PdfBookExtractor.kt）、1行 Ellipsis だと末尾のページ数が真っ先に切り捨てられていた。
                    if (processingState.phase.isNotEmpty()) {
                        Text(
                            text = processingState.phase,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 件数バッジ: 複数件キューイング時のみ右端に常時表示（タイトル省略の影響を受けない）
                if (processingState.queueTotal > 1) {
                    Spacer(Modifier.width(Spacing.S8))
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
                    Spacer(Modifier.width(Spacing.S4))
                    TextButton(
                        onClick = onStop,
                        contentPadding = PaddingValues(horizontal = Spacing.S12, vertical = Spacing.S4),
                    ) {
                        Text("停止", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // ステップ駆動の3要素（4段ステッパー・spring進捗バー・「ステップ n/4」計数）は PDF 供給元のみ。
            // Web 取込は章単位取得でステップ概念を持たず、出すと「ステップ 1/4」「タイトル」で凍結表示になる
            // （2026-07-29 裁定②の真因＝PDF 専用の器を Web にも無条件描画していた）。Web は既存の章進行
            // 「章 i/N 取得中」（phase 行）へ一本化する＝既存バナー語彙の出し分けのみで新しい意匠は足さない。
            if (processingState.source == ProcessingSource.PDF) {
                Spacer(Modifier.height(Spacing.S12))
                // ステッパーインジケーター
                val stepLabels = listOf("タイトル", "本文", "分割", "HTML")
                StepperIndicator(
                    stepIndex = processingState.stepIndex,
                    stepTotal = processingState.stepTotal,
                    labels = stepLabels,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.S8))
                // プログレスバー。ステップ切替は key(stepIndex) の状態再構成で瞬時リセット（旧 snapTo 相当。
                // 新ステップは 0f 発行から始まるため再構成の初期値も実質 0）。
                // なぜ LaunchedEffect＋Animatable＋tween 張り直しをやめたか（2026-07-16 実機計測・残7⑥の真因）:
                // stepLocalPercent はページ単位で高頻度（実測10〜25ms間隔）に更新され、更新のたび effect が再起動して
                // tween(400ms) が毎回キャンセルされ、easing 序盤の平坦区間だけを反復＝表示値が最大約1%で張り付いた
                // （animateTo 完了0回を実測）。spring 追従（animateFloatAsState）は retarget で現在速度を引き継ぐため
                // 高頻度更新でも前進が途切れない（spring の選定理由はトークン側 Motion.kt のコメント参照）。
                key(processingState.stepIndex) {
                    val progress by animateFloatAsState(
                        targetValue = processingState.stepLocalPercent,
                        animationSpec = MotionSpringProgressFollow,
                        label = "processingProgress",
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                }
                Spacer(Modifier.height(Spacing.S4))
                Text(
                    text = "ステップ ${processingState.stepIndex + 1}/${processingState.stepTotal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
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
        Spacer(Modifier.height(Spacing.S4))
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
