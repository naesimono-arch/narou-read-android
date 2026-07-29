package com.novelreader.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.domain.ScanProgress
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
//
// 同ファイルの [ReimportScanBanner]（案X の走査中バナー・正本モックの .proc）は同じスロットに出る
// 排他の相方＝器（ヘアライン枠・radius 12・同じ内側余白）を共有するためここに同居させる。
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

// ============================================================
// PDF フォルダ走査の進捗バナー（案X・正本モック bookshelf-reimport-sweep-D.html .proc）。
// 検出バナーと同じヘッダ直下スロットに排他で出る（VM の sweepBannerVisible が走査中は false を返す）。
//
// なぜ既存 ProcessingBanner を流用しないか: あちらは ProcessingState（供給元 PDF/WEB のスロット）駆動で、
// 取込キューの表示という別の責務を持つ。走査は取込より前段の「照合」で、キューにも FGS にも乗らない
// （ProcessingSource を増やすと停止ディスパッチ・ステッパー出し分けの分岐が全部増える）。
// 見た目の器はモック .proc をそのまま写す＝新しい意匠は発明していない（ステッパーだけ出さない＝
// PDF 変換の4段とは別物という Web 取込と同じ判断）。
// ============================================================
@Composable
internal fun ReimportScanBanner(
    progress: ScanProgress,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S12)
            .border(1.dp, LocalShelfColors.current.hairline, RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.S16, vertical = Spacing.S12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // .proc .sp（18px・2px・藍トップ）。列挙中は総数未確定なので不定回転のままで正しい。
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.S12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PDFを照合しています",
                        fontSize = FontButtonLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // 副行は「当たった冊数」。列挙中（total=0）は件数を語れないので何を待っているかを言う。
                    Text(
                        text = if (progress.total == 0) "フォルダの中を調べています…"
                               else "${progress.matched}冊 見つかりました",
                        fontSize = FontMicroLabel,
                        color = LocalShelfColors.current.infoText,
                    )
                }
                // .proc .cnt: 照合済み / フォルダ内のPDF総数。総数確定前は出さない（0/0 は無情報）。
                if (progress.total > 0) {
                    Spacer(Modifier.width(Spacing.S8))
                    Text(
                        text = "${progress.hashed} / ${progress.total}",
                        fontSize = FontLabel,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(Spacing.S4))
                TextButton(onClick = onStop) {
                    Text(
                        "停止",
                        fontSize = FontButtonLabel,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.S8))
            // .proc .bar（2px）。総数未確定のうちは 0 のまま＝嘘の前進を描かない。
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.hashed.toFloat() / progress.total else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = LocalShelfColors.current.hairline,
            )
        }
    }
}
