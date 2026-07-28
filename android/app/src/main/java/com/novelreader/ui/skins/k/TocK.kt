package com.novelreader.ui.skins.k

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.TocState
import com.novelreader.ui.theme.FontActionLabel
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontCaption
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSheetTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.tocInitialFirstVisibleIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ============================================================
// 明快K: 目次＝正本モック toc-K.html の忠実翻訳（深い画面＝ボトムナビは出さない・没入優先。plan 確定事項2/3）。
//
// 核は「いま自分がどこか」を常時明示: ①ヘッダに「目次」＋作品名サブ ②直下に現在地チップ「いま読んでいる」
//   ③各行の状態を語彙化（既読=題名を沈めて✓／現在=藍ルール＋地＋唯一の実アクション「ここから再開」／未読=通常）。
//   一画面の強調は現在話の「ここから再開」チップ1つ（沈めて立てる・Design/10）。
//
// 色は D の読書テーマ（ReadingColors）追従（K=SkinD・D の目次が ReadingColors を使うのと同型＝ライト/セピア/ダーク）。
//   base→background・ink→text・藍→accent・line→divider・メタ（作品名/進捗/話数ラベル）→infoText（AA 意味テキスト）。
//   既読題名の沈め＝textSecondary（D の目次と同じ裁定）。話数=ゴシック・題名=明朝（既存 Typography 踏襲）。
//
// 既読/現在の判定は D 実装 TocList と同じデータ（currentChapterFile と一致する行＝現在／それより前＝既読）を
//   そのまま使う（新しい既読管理を発明しない）。初期スクロール位置も共通の tocInitialFirstVisibleIndex を再利用。
// ============================================================

@Composable
internal fun TocK(
    tocState: TocState,
    colors: ReadingColors,
    workTitle: String?,
    currentChapterFile: String?,
    onSelectChapter: (fileName: String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            // nav バー inset を root で処理する（M/J の目次・D/C の Scaffold 既定と同じ hard-cut 流儀）。
            // これが無いとリスト末尾が物理下端まで届き、最終行がジェスチャーバーと重なる（2026-07-29 実機）。
            // background の後に置くことで地色は nav 帯まで塗られたまま内容だけ持ち上がる。
            .navigationBarsPadding(),
    ) {
        TocHeaderK(workTitle, colors, onNavigateToBookshelf)

        when (tocState) {
            is TocState.Content -> {
                val entries = tocState.entries
                val total = entries.size
                // 現在章 index（D 実装 tocInitialFirstVisibleIndex/TocList と同じ突合＝fileName 一致）。未読/不一致は -1。
                val currentIndex = entries.indexOfFirst { it.fileName == currentChapterFile }
                val listState = rememberLazyListState(
                    // 開いた瞬間から現在章付近を表示（D/M/P と同じ導出＝現在章の1つ手前・未読は先頭）。
                    initialFirstVisibleItemIndex = tocInitialFirstVisibleIndex(entries, currentChapterFile),
                )
                val scope = rememberCoroutineScope()

                HereBarK(
                    currentIndex = currentIndex,
                    total = total,
                    colors = colors,
                    // チップタップで現在章行へスクロール（現在章の1つ手前を見せて前後の文脈を残す＝初期位置と同じ導出）。
                    onJumpToCurrent = {
                        scope.launch { listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) }
                    },
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                ) {
                    // key に fileName（章ごとに一意で安定＝非同期差し替え・現在章スクロール時も同一性を保つ・D と同方針）。
                    itemsIndexed(entries, key = { _, entry -> entry.fileName }) { index, entry ->
                        ChapterRowK(
                            epLabel = "第${index + 1}話",
                            title = entry.title.ifEmpty { "第${index + 1}話" },
                            isCurrent = index == currentIndex,
                            // 既読＝現在章より前（currentIndex<0＝未読なら既読は無い）。D TocList と同じ導出。
                            isRead = currentIndex >= 0 && index < currentIndex,
                            colors = colors,
                            onClick = { onSelectChapter(entry.fileName) },
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.S32)) }
                }
            }

            is TocState.Loading -> TocSkeletonK(colors, Modifier.fillMaxWidth().weight(1f))

            is TocState.Empty -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("章が見つかりません", color = colors.textSecondary, fontFamily = MinchoFamily)
            }

            is TocState.Error -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.S32),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "目次の読み込みに失敗しました",
                    color = colors.textSecondary,
                    fontFamily = MinchoFamily,
                    fontSize = FontSectionTitle,
                )
                Text(
                    tocState.message,
                    // 失敗理由＝意味を運ぶ文字ゆえ infoText（textSecondary の alpha 沈めは AA 割れ・D エラー画面と同裁定）。
                    color = colors.infoText,
                    fontFamily = MinchoFamily,
                    fontSize = FontCaption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.S4, bottom = Spacing.S16),
                )
                Text(
                    "再試行",
                    fontSize = FontSubTitle,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
                )
            }
        }
    }
}

/** ヘッダ（モック .top）: ←戻る（44dp タップ面）＋「目次」＋作品名サブ（1行省略）。 */
@Composable
private fun TocHeaderK(workTitle: String?, colors: ReadingColors, onBack: () -> Unit) {
    Column {
        Row(
            // .top padding 2px 12px 12px → 上 S4 / 横 S12 / 下 S12。gap 4px → S4。
            modifier = Modifier.padding(start = Spacing.S12, end = Spacing.S12, top = Spacing.S4, bottom = Spacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 44dp タップ面（Material 既定 48dp より詰めモック .back 44px に合わせる）。
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "本棚に戻る",
                    tint = colors.topBarIcon,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.S4)) {
                Text(
                    "目次",
                    fontSize = FontSheetTitle, // .htxt h1 18px（ゴシック bold）
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
                // 作品名は与えられた場合のみ出す（捏造禁止＝未紐付け等で欠落するなら行ごと出さない）。
                if (!workTitle.isNullOrBlank()) {
                    Text(
                        workTitle,
                        fontFamily = MinchoFamily,
                        fontSize = FontSubTitle, // .work 13px（明朝）
                        color = colors.infoText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.S4), // .work margin-top 2px → S4
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider) // .top border-bottom
    }
}

/**
 * 現在地バー（モック .here）: 左＝現在話チップ（藍10%地・藍字・タップで該当行へ）／右＝進捗。
 * 読了率は既存データ（現在章 index と全話数）から計算できるときのみ併記する（捏造禁止＝未読は出さない）。
 * 読了率＝既読話数（現在話より前＝✓ を付ける行）currentIndex ÷ 全話数。現在話は読了に数えないため画面の
 * ✓ 数と一致する（P の CLEAR% と同式・可視の✓を単一真実源にして捏造を避ける）。
 */
@Composable
private fun HereBarK(currentIndex: Int, total: Int, colors: ReadingColors, onJumpToCurrent: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentIndex >= 0) {
            Text(
                "いま読んでいる: 第${currentIndex + 1}話",
                fontSize = FontButtonLabel, // .herechip 12.5px
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent.copy(alpha = 0.10f)) // --ai-pill（藍10%）
                    .clickable(onClick = onJumpToCurrent)
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8), // .herechip padding 6px 14px
            )
        }
        Spacer(Modifier.weight(1f))
        val progress = buildString {
            append("全${total}話")
            // 読了率は現在章が既知（既読/現在の見当識が立つ）ときのみ。未読は分母だけ出す。
            if (currentIndex >= 0 && total > 0) {
                append("・読了率${(currentIndex * 100f / total).roundToInt()}%")
            }
        }
        Text(progress, fontSize = FontCaption, color = colors.infoText) // .prog 12px
    }
}

/**
 * 章行（モック .row）: 話数ラベル（ゴシック）＋題名（明朝）。左ルール分の幅を全行で確保して整列。
 * 既読=題名を沈めて行末に✓／現在=藍の左ルール＋藍10%地＋唯一の実アクション「ここから再開」／未読=通常。
 */
@Composable
private fun ChapterRowK(
    epLabel: String,
    title: String,
    isCurrent: Boolean,
    isRead: Boolean,
    colors: ReadingColors,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 現在章＝藍10%の面（--ai-pill）で「いまここ」を面として示す。
                .then(if (isCurrent) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
                .clickable(onClick = onClick)
                .height(IntrinsicSize.Min), // 左ルールの fillMaxHeight を行高へ一致させる
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 現在章の左ルール（モック .row.cur border-left 3px 藍）。非現在は透明の同幅で全行のテキスト開始位置を揃える。
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (isCurrent) colors.accent else Color.Transparent),
            )
            Row(
                // .row padding 15px 18px 15px 15px（左ルール後の内側）。左右とも 15/18px→S16・縦 S16。gap 12px→S12。
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S16, bottom = Spacing.S16),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
            ) {
                Text(
                    epLabel,
                    fontSize = FontCaption, // .row .ep 12px（ゴシック）
                    color = colors.infoText,
                    modifier = Modifier.width(44.dp), // .ep width 44px（整列用の構造幅＝スケール外）
                )
                Text(
                    title,
                    fontFamily = MinchoFamily,
                    fontSize = FontActionLabel, // .row .tt 15px（明朝）
                    lineHeight = 22.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    // 現在=本文色（太字）／既読=沈める（textSecondary・D TocList と同じ既読色）／未読=本文色。
                    color = when {
                        isCurrent -> colors.text
                        isRead -> colors.textSecondary
                        else -> colors.text
                    },
                    modifier = Modifier.weight(1f),
                )
                // 行末: 既読=✓／現在=「ここから再開」チップ（この画面唯一の強調）／未読=なし。
                when {
                    isCurrent -> Text(
                        "ここから再開",
                        fontSize = FontLabel, // .resume 11px
                        fontWeight = FontWeight.Bold,
                        // 塗り藍ボタンの実文字＝対比保証の primary/onPrimary 対（K 既定＝accent と同値ゆえ見た目一致）。
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = Spacing.S12, vertical = Spacing.S4), // .resume padding 5px 11px
                    )
                    isRead -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null, // 「既読」の意味は行全体の見え方が担う（装飾アイコン）
                        tint = colors.infoText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider) // .row border-bottom
    }
}

/**
 * ロード中スケルトン（章リストと同じ左ルール幅＋テキスト行）。充足後のレイアウト飛びを抑える。
 * モックはロード状態を定義しないため D の TocSkeleton と同じ静的プレースホルダ（アニメ無し＝過剰演出を避ける）。
 */
@Composable
private fun TocSkeletonK(colors: ReadingColors, modifier: Modifier = Modifier) {
    val barColor = colors.text.copy(alpha = 0.07f)
    Column(modifier = modifier) {
        // 章題長に揺らぎを持たせて「文章の目次」らしく見せる。
        listOf(0.85f, 0.6f, 0.9f, 0.7f, 0.8f, 0.55f, 0.88f, 0.65f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S16, bottom = Spacing.S16)
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
            HorizontalDivider(color = colors.divider)
        }
    }
}
