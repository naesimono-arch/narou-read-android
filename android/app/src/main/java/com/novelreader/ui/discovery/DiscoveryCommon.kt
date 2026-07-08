package com.novelreader.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import com.novelreader.ui.theme.MinchoFamily
import java.util.Locale

// ============================================================
// 発見系画面の共通部品（モック docs/design-candidates/discovery/*.html の翻訳）。
// 一覧行 .rk ＝ 順位（明朝数字・上位3位のみ藍）＋タイトル明朝＋作者・ジャンル＋メタ1行。
// 「公式より丁寧」の実体: pt生数字に読了目安（time）を人の言葉で併記する。
// 色は MaterialTheme.colorScheme（Color.kt トークン）経由・直書き禁止（ADR 0005）。
// ============================================================

/** 「連載中 127話」「完結 88話」「短編」。end の意味は仕様書どおり直感と逆（0=短編・完結済）。 */
fun novelStatusLabel(novel: NarouNovel): String {
    if (novel.novelType == 2) return "短編"
    val episodes = novel.generalAllNo ?: 1
    return if (novel.end == 0) "完結 ${episodes}話" else "連載中 ${episodes}話"
}

/** 読了時間（分）→「約12分」「約8時間」。time 欠損時は length から導出（読了時間＝文字数÷500 切り上げ）。 */
fun readTimeLabel(novel: NarouNovel): String? {
    val minutes = novel.time
        ?: novel.length?.let { (it + 499) / 500 }
        ?: return null
    return if (minutes < 60) {
        "約${minutes}分"
    } else {
        // なぜ四捨五入か: 一覧の目安表示に分単位の精度は不要で、「約8時間」の丸い言葉が D の静けさに合うため。
        val hours = (minutes + 30) / 60
        "約${hours}時間"
    }
}

/** 現在の order タブに対応するポイントラベル（「週間 12,345pt」）。値が無ければ null。 */
fun pointLabel(order: NarouOrder, novel: NarouNovel): String? {
    val (prefix, value) = when (order) {
        NarouOrder.DAILY -> "日間" to novel.dailyPoint
        NarouOrder.WEEKLY -> "週間" to novel.weeklyPoint
        NarouOrder.MONTHLY -> "月間" to novel.monthlyPoint
        NarouOrder.QUARTER -> "四半期" to novel.quarterPoint
        NarouOrder.TOTAL, NarouOrder.NEW -> "累計" to novel.globalPoint
    }
    if (value == null) return null
    return "$prefix ${String.format(Locale.JAPAN, "%,d", value)}pt"
}

/**
 * 一覧の1行（モック .rk）。
 * @param rank 1始まりの表示順位。上位3位のみ藍（primary）、以降は補助色。
 * @param order pt ラベルの種別決定に使う（結果一覧では基本 order を渡す）。
 */
@Composable
fun NovelListRow(
    rank: Int,
    novel: NarouNovel,
    order: NarouOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = rank.toString(),
            fontFamily = MinchoFamily,
            fontSize = 20.sp,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(34.dp)
                .padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = novel.title ?: "（無題）",
                fontFamily = MinchoFamily,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = novel.writer ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NarouGenres.genreLabel(novel.genre)?.let { genre ->
                    Text(
                        text = genre,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = novelStatusLabel(novel),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                readTimeLabel(novel)?.let {
                    Text(
                        text = it,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pointLabel(order, novel)?.let {
                    Text(
                        text = it,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 発見系リスト領域の「本体でない」表示状態（読込中／空／エラー）。
 * なぜ専用 sealed 型か: DiscoveryStatusBox は DiscoveryUiState（ホーム・結果一覧）と
 * NovelDetailUiState（作品詳細）の2つの状態型から共有され、いずれも Content 相当（一覧本体・
 * 詳細本体）は各画面が自前で描く。box が受け持つのはこの3状態だけなので、両状態型の共通部分を
 * ここへ切り出す。旧 API の bool＋nullable 併用（isLoading/emptyMessage/errorMessage）だと
 * 「読込中かつエラー」のような不正な組合せが型上表現できてしまうため、排他を型で保証する。
 */
sealed interface DiscoveryStatus {
    object Loading : DiscoveryStatus
    data class Empty(val message: String) : DiscoveryStatus
    data class Error(val message: String, val onRetry: (() -> Unit)? = null) : DiscoveryStatus
}

/** Loading / Empty / Error の共通表示（発見系画面のリスト領域用）。 */
@Composable
fun DiscoveryStatusBox(
    status: DiscoveryStatus,
    modifier: Modifier = Modifier,
) {
    // なぜ fillMaxSize を内部固定しないか: 呼び出し側でサイズ要求が実際に分岐する
    // （結果一覧・詳細は領域いっぱい＝fillMaxSize、ホームは LazyColumn item 内で半分の高さ）。
    // サイズは配置を決める親の責務なので modifier で受け取り、ここでは素の Box に適用する。
    Box(modifier = modifier) {
        when (status) {
            is DiscoveryStatus.Loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is DiscoveryStatus.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    status.onRetry?.let { onRetry ->
                        Button(onClick = onRetry) { Text("再試行") }
                    }
                }
            }
            is DiscoveryStatus.Empty -> Text(
                text = status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// ── Preview ──────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun DiscoveryStatusBoxPreview_Loading() {
    MaterialTheme {
        DiscoveryStatusBox(
            status = DiscoveryStatus.Loading,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryStatusBoxPreview_Empty() {
    MaterialTheme {
        DiscoveryStatusBox(
            status = DiscoveryStatus.Empty("該当する作品がありません"),
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryStatusBoxPreview_Error() {
    MaterialTheme {
        DiscoveryStatusBox(
            status = DiscoveryStatus.Error("通信に失敗しました", onRetry = {}),
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}
