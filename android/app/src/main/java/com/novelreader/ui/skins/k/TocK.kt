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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.ui.TocState
import com.novelreader.ui.skins.rememberTocEpLabelWidth
import com.novelreader.ui.skins.tocHereChipContentDescription
import com.novelreader.ui.skins.tocHereChipLabel
import com.novelreader.ui.theme.FontActionLabel
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontCaption
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
// 核は「いま自分がどこか」を常時明示: ①ヘッダに「目次」＋作品名サブ ②直下に現在地チップ「第N話」
//   （2026-08-07 の裁定で前置き「いま読んでいる: 」を削り、空いた幅を同じ行の進捗へ戻した。読み上げには残す）
//   ③各行の状態を語彙化（既読=題名を沈めて✓／現在=藍ルール＋地＋唯一の実アクション「ここから再開」／未読=通常）。
//   一画面の強調は現在話の再開チップ1つ（沈めて立てる・Design/10）。
//   そのチップは 2026-08-07 の裁定で**アイコンのみ（▶ の丸）**にした: 文字入りチップは非加重子として
//   実寸を先取りし、fontScale 2.0 で 156dp（=312px）を占めて章題の取り分を 45dp まで削っていた
//   （＝章題が1行1文字で縦に割れる）。名前は contentDescription が担う。
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
                // 話数ラベル列の整列幅は「この本で出る最長ラベル」から決める（機序＝rememberEpLabelWidth）。
                val epLabelWidth = rememberEpLabelWidth(total)

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
                            epLabelWidth = epLabelWidth,
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
                        lineHeight = TocKNormalLineHeight,
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
            // 見える文字は「第N話」だけ（裁定 2026-08-07・4スキン同型）。前置き「いま読んでいる: 」は
            // このチップが非加重子として幅を先取りする分の実質を占め、進捗の取り分を可視0文字まで潰していた。
            // 削った語は読み上げ（contentDescription）に残す＝機序と数値は tocHereChipContentDescription が正本。
            Text(
                tocHereChipLabel(currentIndex),
                fontSize = FontButtonLabel, // .herechip 12.5px
                lineHeight = TocKNormalLineHeight,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent.copy(alpha = 0.10f)) // --ai-pill（藍10%）
                    .clickable(onClick = onJumpToCurrent)
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8) // .herechip padding 6px 14px
                    .semantics { contentDescription = tocHereChipContentDescription(currentIndex) },
            )
        }
        val progress = buildString {
            append("全${total}話")
            // 読了率は現在章が既知（既読/現在の見当識が立つ）ときのみ。未読は分母だけ出す。
            if (currentIndex >= 0 && total > 0) {
                append("・読了率${(currentIndex * 100f / total).roundToInt()}%")
            }
        }
        // なぜ weight(1f)+maxLines=1 か（監査 2026-08-06 G-1・4スキン同型）: fontScale 2.0 でこの進捗文が
        // チップの余り幅へ折り返して縦5行に膨張し、Row の行高ごと現在地バーが伸びて下の章一覧
        //（LazyColumn weight(1f)）の残り高が 0＝目次のタップ対象が消えていた。余り幅の全量を進捗側へ渡して
        // 1行に固定し、入り切らない分は末尾省略で縮退させる（章一覧の消失より軽い縮退を選ぶ）。
        // 右寄せは旧 Spacer(weight(1f)) と同じ見た目を textAlign=End で保つ。
        Text(
            progress,
            fontSize = FontCaption, // .prog 12px
            lineHeight = TocKNormalLineHeight,
            color = colors.infoText,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 正本 toc-K.html で **line-height を書いていない**要素の行送り（`.htxt .work` / `.herechip` /
 * `.prog` / `.row .ep`）。CSS の `line-height:normal` はゴシックで実測 1.6。
 *
 * なぜ明示が要るか: 既定の LocalTextStyle（＝Typography.bodyLarge）が lineHeight=28.sp を持つため、
 * fontSize だけ落としても**行箱は 28sp のまま残る**（Compose の lineHeight は下限＝
 * docs/knowledge/compose-lineheight-is-a-floor-not-css-line-height.md）。この画面では
 * ①現在話チップ（.herechip＝ピル）が行箱の外周をなぞって太り、②章行（.row）と現在地バー（.here）と
 * ヘッダ（.top）の高さが、いちばん背の高い行箱＝28sp に引きずられていた。
 * em で持つ理由＝正本の比を、フォント token や fontScale が動いても保つため。
 * 実測（xhdpi・fontScale 1.0）: 12sp→19.5dp／12.5sp→20.0dp／13sp(明朝)→21.0dp（いずれも従来 28.0dp）。
 */
private val TocKNormalLineHeight = 1.6.em

/** モック toc-K.html `.row .ep{width:44px}`＝話数ラベル列の整列幅の下限（2桁までの実測値・スケール外の構造幅）。 */
private val EpLabelMinWidth = 44.dp

/**
 * 話数ラベル列（`.ep`）の整列幅。導出の機序・上界方式の理由は [rememberTocEpLabelWidth] の KDoc が正本
 *（2026-08-07 に M/J へ同じ破綻があったため K 専用実装から共有へ移した）。
 * 採寸スタイルは実際の描画と同一にする（Text は LocalTextStyle に fontSize だけ被せている）。
 */
@Composable
private fun rememberEpLabelWidth(total: Int): Dp = rememberTocEpLabelWidth(
    total = total,
    style = LocalTextStyle.current.merge(
        TextStyle(fontSize = FontCaption, lineHeight = TocKNormalLineHeight),
    ),
    minWidth = EpLabelMinWidth,
)

/**
 * 章行（モック .row）: 話数ラベル（ゴシック）＋題名（明朝）。左ルール分の幅を全行で確保して整列。
 * 既読=題名を沈めて行末に✓／現在=藍の左ルール＋藍10%地＋唯一の実アクション（▶ の丸チップ）／未読=通常。
 *
 * [epLabelWidth] はリスト全行で共有する整列幅（導出＝[rememberEpLabelWidth]）。行ごとに計算しない。
 */
@Composable
private fun ChapterRowK(
    epLabel: String,
    epLabelWidth: Dp,
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
                    lineHeight = TocKNormalLineHeight,
                    color = colors.infoText,
                    // .ep width 44px（整列用の構造幅）を下限に、桁数へ追従する整列幅（rememberEpLabelWidth）。
                    modifier = Modifier.width(epLabelWidth),
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
                // 行末: 既読=✓／現在=▶ の丸チップ（この画面唯一の強調）／未読=なし。
                when {
                    isCurrent -> Icon(
                        Icons.Filled.PlayArrow,
                        // アイコンだけでは何のボタンか分からない＝読み上げ用の名前は必須（裁定 2026-08-07 の条件）。
                        // タップは行全体の clickable が担うので、これは行の読み上げに載る名前として置く。
                        contentDescription = "ここから再開",
                        // 塗り藍ボタンの上の実グリフ＝対比保証の primary/onPrimary 対（K 既定＝accent と同値ゆえ見た目一致）。
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(Spacing.S4)
                            .size(16.dp), // 16dp グリフ＋S4 の縁＝24dp の丸（旧チップ文字は 2.0 で 156dp を占めていた）
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
