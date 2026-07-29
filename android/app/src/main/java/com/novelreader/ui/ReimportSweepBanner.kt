package com.novelreader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.Spacing

// ============================================================
// 本文欠落の一括検出バナー（案C・正本モック bookshelf-reimport-sweep-D.html .alert）。
// ヘッダ直下のヘアライン枠・回転体なし（進行中でなく「知らせ」の状態）。
// 藍（primary）は CTA「まとめて再取込」に1点集中＝K の一画面一強調を守る（本文・副文は ink/ink-soft）。
// ProcessingBanner と同じく K/D 両面から共有される（意匠はトークン経由＝スキンの色で染まる）。
// ============================================================
@Composable
internal fun ReimportSweepBanner(
    missingCount: Int,
    onLater: () -> Unit,
    onReimport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            // モック .alert margin 0 20px 12px → 離散スケールでは横 S24（本棚の版面インセットと同値）・下 S12。
            .padding(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S12)
            // モック .alert: border 1px line・radius 12px。塗りは地と同じ（--base）＝枠だけで浮かせる。
            .border(1.dp, LocalShelfColors.current.hairline, RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S12, end = Spacing.S16, bottom = Spacing.S4)) {
            // .t: 事実の一文（12.5px 相当・w600・ink）
            Text(
                text = "本文データが見つからない本が ${missingCount}冊あります",
                fontSize = FontButtonLabel,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.S4))
            // .s: 機序の説明と「失っていないもの」の明示（10.5px 相当・ink-soft）
            Text(
                text = "端末の変更やバックアップ復元のあとに起きることがあります。本の情報と読書位置は残っています。",
                fontSize = FontMicroLabel,
                lineHeight = FontLabel * 1.6,
                color = LocalShelfColors.current.infoText,
            )
            // .acts: 右寄せ2ボタン。「あとで」は ink-soft・「まとめて再取込」は藍 bold（一画面一強調の一点）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onLater) {
                    Text("あとで", fontSize = FontButtonLabel, color = LocalShelfColors.current.infoText)
                }
                TextButton(onClick = onReimport) {
                    Text(
                        "まとめて再取込",
                        fontSize = FontButtonLabel,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
