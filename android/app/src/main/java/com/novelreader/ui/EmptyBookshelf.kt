package com.novelreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
//
// 文言は案B「操作と結果の明示」＝ADR 0037 追記（2026-09-03 確定）。正本モックは
// docs/design-candidates/skins/bookshelf-D.html の候補枠「案B ／ 操作と結果の明示（迷わせない優先）」。
// 旧文言「右下の＋からPDFを追加してください」を捨てたのは、同じ裁定で空棚の拡張FAB を引っ込めたため
// ＝本文が名指しする対象が画面に無くなる（伝播を見送っていた唯一の理由が、文言改稿で消えた）。
// ============================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyBookshelf(
    onAddClick: () -> Unit,
    // 検索画面（さがすタブ＝発見ホーム）への動線。ADR 0037 追記 2026-09-03 で
    // 「CTA は1本」を覆して併置した＝行き先の違う2つ（手元のPDFを入れる／これから探す）は
    // 二重出しではない。蔵書ゼロの人から後者を消すと初見が行き止まりになる。
    onFindWorks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // サイズ（fillMaxSize 等）は配置を決める親の責務のため呼び出し側から渡す。
    // 内部で固定すると別の余白・配置で再利用できなくなるため root では固定しない。
    //
    // なぜ Box(中央寄せ)＋内側 Column(verticalScroll) の二段構えか（K の同型監査 G-6 と同じ機序）:
    // 導線が2本になって空棚の全高が伸び、fontScale を上げると内容が器を超えて下端の CTA が画面外へ切れる。
    // Column へ直接 verticalScroll を足すと、内容が可視域より低いとき Column が内容高で wrap して上詰めに
    // なり 1.0 の中央寄せが崩れるため、中央寄せは外の Box・あふれ時のスクロールは内側 Column へ分ける
    //（1.0 の見た目は不変・大きい fontScale だけスクロール可能になる）。
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.S40),
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
                "まだ一冊もありません",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.S8))
            Text(
                "お手元のPDFを取り込むと、ふりがな付きで読めるようになります。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.S32))
            // なぜ FlowRow か（K の空棚 KEmptyState と同じ監査 G-6 由来）: 素の Row は幅を分け合わず、
            // 大きい fontScale では先行ボタンが実寸を取り切って後続が残り幅へ1文字ずつ縦積みになる。
            // 入り切らないボタンは次行へ折り返して両導線の判読を保つ。
            // spacedBy の第2引数 CenterHorizontally は折返し後の各行を中央へ揃える（1.0 の1行時は見た目不変）。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.S12),
            ) {
                // 主導線＝消した FAB の役を引き継ぐ「PDFを追加する」（案B の CTA 文言そのまま）。
                // 併置する「作品をさがす」は輪郭ボタンへ沈める＝一画面一強調（ADR 0014 原則）。
                FilledTonalButton(onClick = onAddClick) {
                    Text("PDFを追加する")
                }
                OutlinedButton(onClick = onFindWorks) {
                    Text("作品をさがす")
                }
            }
        }
    }
}
