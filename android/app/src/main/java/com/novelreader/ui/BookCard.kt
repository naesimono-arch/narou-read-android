package com.novelreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.computeContinuation
import com.novelreader.narou.model.NarouNovel
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.shioriAccentFor
import com.novelreader.ui.components.shioriHue
import com.novelreader.ui.theme.FontCardTitle
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSealBadge
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionSpringCard
import com.novelreader.ui.theme.ShioriSealScrimDark
import com.novelreader.ui.theme.ShioriSealVermilion
import com.novelreader.ui.theme.ShioriSealVermilionDark
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.progressFractionFor
import com.novelreader.viewmodel.readingStatusFor
import com.novelreader.viewmodel.relativeReadLabel

// ============================================================
// 進捗行（モック .pr）: 「N話 + 細い藍バー + N%」を藍で、未読は青磁で表示。
// グリッド=バー伸縮(flexBar=true) / リスト=バー80dp固定(false)。
// 色は token 経由（primary=藍 #1C3D5A / secondary=青磁 #9CB3A8 / track=outlineVariant）＝直書き回避。
// ============================================================
@Composable
private fun BookProgressRow(
    totalChaps: Int,
    progressFraction: Float?,
    flexBar: Boolean,
    modifier: Modifier = Modifier,
) {
    // 進捗の有無で描画ルート（Row/Text）が分岐するため、呼び出し側の modifier は
    // 実際に描画される側のルートへ適用する（どちらが出ても配置指定が効くように）。
    if (progressFraction != null) {
        val percent = (progressFraction * 100).toInt()
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${totalChaps}話",
                fontSize = FontLabel,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = (if (flexBar) Modifier.weight(1f) else Modifier.width(80.dp))
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = LocalShelfColors.current.hairline,   // 本棚系 --track
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$percent%",
                fontSize = FontLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        // 未読は濃青磁 UnreadSeiji。意味を運ぶ文字は 4.5:1 が最低線（ADR 0014-D 裁定＝旧・完全準拠トレードオフを上書き）。
        Text(
            text = "未読",
            modifier = modifier,
            fontSize = FontLabel,
            letterSpacing = 0.8.sp,
            color = LocalShelfColors.current.unreadLabel,
        )
    }
}

// ============================================================
// 最後に読んだ相対時刻「◯日前」（continuity Minor・モック未表現＝最終ユーザー確認バッチ対象）。
// よみかけ（progressFraction != null＝進捗あり）のカードにだけ、補助色で静かに1行添える。
// なぜカード内で now を都度取るか: 表示専用の粗い日粒度ラベルで、再コンポーズ時の微小なズレは無害。
// ============================================================
@Composable
private fun RelativeReadLabel(
    progressFraction: Float?,
    lastReadAt: Long,
    modifier: Modifier = Modifier,
) {
    if (progressFraction == null) return
    val label = relativeReadLabel(lastReadAt, System.currentTimeMillis()) ?: return
    Text(
        text = label,
        modifier = modifier,
        fontSize = FontMicroLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ============================================================
// 続きありバッジ（モック fusion .new-chapters）: 青磁ドット＋藍文字「続き N話」。
// PDF↔Web継続読書の「新着の気配」を本棚でも静かに知らせる。
// ============================================================
@Composable
private fun NewChaptersBadge(newCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "続き ${newCount}話",
            fontSize = FontMicroLabel,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 手元PDFの章数（totalChaps）となろう詳細（novelDetail）を突き合わせ、新着話数を返す。null=バッジ非表示。
 *
 * なぜ Repository を直接叩かず引数の novelDetail を使うか（アーキ監査残課題1）:
 * 以前はカードごとに produceState で novelApiRepository.novelDetail を直撃しており、カード枚数ぶん
 * Repository を直撃＝テスト不能・本棚を開くたび並列発火だった。照会は BookshelfViewModel へ一括で吊り上げ、
 * カードは配布された詳細を受け取って突き合わせるだけの純粋関数に落とした（通信・キャッシュ・失敗握り潰しは VM 側）。
 * これは純 Kotlin の純粋計算なので Compose 非依存で単体テストできる。
 */
internal fun newEpisodeCountFor(novelDetail: NarouNovel?, totalChaps: Int): Int? {
    if (novelDetail == null || totalChaps <= 0) return null
    return (computeContinuation(totalChaps, novelDetail) as? ContinuationInfo.NewEpisodes)?.newCount
}

// ============================================================
// グリッド用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    // 続きありバッジ用のなろう詳細（VM が一括照会し配布。null=未紐付け/未取得/失敗）。
    novelDetail: NarouNovel?,
    // 章数（chap_N.html の枚数）。VM の chapterCountMap から渡す＝カード毎の重複IOを廃し、
    // 状態フィルタ（readingStatusFor）と同じ値を共有する。
    totalChaps: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 削除メニューの開閉状態（長押しで開く）
    var menuExpanded by remember { mutableStateOf(false) }

    val chapNum = chapterNumberOf(progress?.lastReadFilename)

    // 最終章の章内スクロールを加味して「開いた瞬間100%」の嘘を消す（F-N・詳細は progressFractionFor）。
    val progressFraction = progressFractionFor(
        chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0,
    )

    // 読了なら書影右下に朱印「了」を出す（正本 bookshelf-shiori-grid-D.html の .seal）。
    // 判定はカード表示・状態フィルタと同一の単一真実源 readingStatusFor を使い、進捗行(BookProgressRow)や
    // 「読了」フィルタと徴（しるし）がズレないようにする。
    val isFinished = readingStatusFor(progress, totalChaps) == ReadingStatus.FINISHED

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = MotionSpringCard,
        label = "gridCardScale",
    )

    // モック .bk: カード地・影・角丸チップを廃したフラット構図。書影＋メタを地に直接置く。
    // クリック=開く / 長押し=削除メニュー。
    // 栞系モックはフラット構図＝可視の⋮を持たない。削除は長押しメニューへ一本化する。
    Column(
        modifier = modifier
            // TalkBack で1冊=1トラバーサル単位に束ねる（critic Major 2026-07-12）。題字/著者/進捗/続きバッジが
            // 個別ノードに割れて何度もスワイプさせる問題を解消する。長押し削除は onLongClick に残る。
            .semantics(mergeDescendants = true) {}
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onOpen,
                onLongClick = { menuExpanded = true },
            ),
    ) {
        // 書影＝栞（紙地＋色の棒＋先端＋表紙内の縦組み明朝題字）。角丸3px（モック .cv）。
        // 題字は表紙内で1度だけ（正本 bookshelf-shiori-final-D.html）＝本欄の横題字を廃し二重表示を解消。
        // モック .cv の box-shadow＝表紙が棚から浮く影。ダークは背景が暗く影が沈むため強め
        // （モック: ライト 0 6px 16px rgba(...,.12) ／ ダーク 0 8px 20px rgba(0,0,0,.5)）。
        val coverIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        Box(modifier = Modifier.fillMaxWidth()) {
            ShioriCover(
                title = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    // shadow は clip より前＝影を要素の外周に落としてから角丸で本体をクリップする。
                    .shadow(
                        elevation = if (coverIsDark) 8.dp else 6.dp,
                        shape = RoundedCornerShape(3.dp),
                    )
                    .clip(RoundedCornerShape(3.dp)),
            )
            // 朱印「了」（読了バッジ）。正本 grid-D .seal: 19dp角・角丸2dp・右下9dp・枠1dp・明朝 SemiBold 9.5sp。
            // 朱色は accent（title 由来色相）と無関係の固定「読了の徴」＝専用トークン。背景は紙地へ半透過で溶かす
            // （ライトは surface 50%／ダークは正本の固定暗色トークン 50%）。coverIsDark は上で算出済みを再利用。
            if (isFinished) {
                val sealColor = if (coverIsDark) ShioriSealVermilionDark else ShioriSealVermilion
                val sealBg = if (coverIsDark) ShioriSealScrimDark.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 9.dp, bottom = 9.dp)
                        .size(19.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(sealBg)
                        .border(1.dp, sealColor, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "了",
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = FontSealBadge,
                        color = sealColor,
                    )
                }
            }
            // 削除メニューのアンカー（可視の⋮は持たず長押しで開く。栞モックはフラット構図）。
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DeleteDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }

        // 表紙下は著者＋状態のみ（モック .au → .pr）。題字は表紙内で描くため本欄には出さない。
        // 著者はゴシック（既定）・補助色。栞表紙が作品の識別子なので下段は静かに添えるだけ。
        Spacer(Modifier.height(9.dp))
        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                fontSize = FontLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        BookProgressRow(
            totalChaps = totalChaps,
            progressFraction = progressFraction,
            flexBar = true,
        )
        // よみかけ（進捗あり）に限り「いつぶりか」を静かに添える（continuity Minor・モック未表現）。
        RelativeReadLabel(
            progressFraction = progressFraction,
            lastReadAt = progress?.lastReadAt ?: 0L,
            modifier = Modifier.padding(top = 4.dp),
        )
        // 続きあり（モックは進捗行の下・上4px）
        newEpisodeCountFor(novelDetail, totalChaps)?.let { newCount ->
            NewChaptersBadge(newCount = newCount, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ============================================================
// 文字目録の行（骨格3）: 表紙を排し、明朝の題字を主役に縦へ連ねる。
// 作品の識別は左端 4dp の色帯（本の小口メタファ・作品識別色）だけで行う
// ＝生成書影を捨てて存在しない装画を捏造しない（モック bookshelf-mokuroku-D.html）。
// ※関数名は呼び出し側（BookshelfContent のリストモード）との互換のため ListBookCard を維持する。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    // 続きありバッジ用のなろう詳細（VM が一括照会し配布。null=未紐付け/未取得/失敗）。
    novelDetail: NarouNovel?,
    // 章数（chap_N.html の枚数）。VM の chapterCountMap から渡す＝カード毎の重複IOを廃し、
    // 状態フィルタ（readingStatusFor）と同じ値を共有する。
    totalChaps: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteUiMode: Int,
    modifier: Modifier = Modifier,
) {
    // 削除メニューの開閉状態（⋮タップ または 長押しで開く）
    var menuExpanded by remember { mutableStateOf(false) }

    val chapNum = chapterNumberOf(progress?.lastReadFilename)

    // 最終章の章内スクロールを加味して「開いた瞬間100%」の嘘を消す（F-N・詳細は progressFractionFor）。
    val progressFraction = progressFractionFor(
        chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0,
    )

    // 作品識別色（左端の色帯）。表紙を持たない目録では、この色帯だけが作品の視覚的な手がかり。
    // 書架の栞（棒・先端）と同じ shiori accent に一本化＝seed は book.id でなく title。
    // なぜ title か: 書架グリッドの栞は title→色相で描くため、目録も title 由来にしないと同じ本が
    // 書架と目録で違う色になる（整合ルール「1冊=1色相」の核心・正本 consistency-D）。
    val surface = MaterialTheme.colorScheme.surface
    val barColor = remember(book.title, surface) { shioriAccentFor(shioriHue(book.title), surface) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = MotionSpringCard,
        label = "listCardScale",
    )

    // モック 文字目録 .li: カード地・影・書影を廃し、上下余白＋下ヘアラインで区切る静かな行。
    // 外側 Column が行本体(Row)と区切り線(HorizontalDivider)を束ねる。
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                // 1冊=1トラバーサル単位に束ねる（critic Major）。行末の⋮ IconButton は自身が併合ノード境界の
                // ため別フォーカスとして残る＝「本1ノード＋⋮1ノード」の2単位になり、題字/著者/進捗の分割読みを解消。
                .semantics(mergeDescendants = true) {}
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                    // 長押しは方式に依らず常時有効（M5: ⋮のフォールバック）。deleteUiMode は⋮の可視のみ制御。
                    onLongClick = { menuExpanded = true },
                )
                // 色帯を行の高さいっぱいに伸ばすため、行の高さを内容の最小内在高さに合わせる。
                .height(IntrinsicSize.Min)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左端の色帯（本の小口メタファ・作品識別色）。行の高さに合わせて stretch。
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 明朝の題字（目録の主役）。2行まで。
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontCardTitle,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 著者＋続きあり（青磁）。モックの目録は著者脇に「続き N話」を寄せる。
                val newCount = newEpisodeCountFor(novelDetail, totalChaps)
                if (book.author.isNotBlank() || newCount != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (book.author.isNotBlank()) {
                            Text(
                                text = book.author,
                                fontSize = FontLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // 続きバッジの領域を確保するため、著者が長くてもバッジを押し出さない。
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        newCount?.let {
                            NewChaptersBadge(
                                newCount = it,
                                modifier = Modifier.padding(start = if (book.author.isNotBlank()) 10.dp else 0.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                BookProgressRow(
                    totalChaps = totalChaps,
                    progressFraction = progressFraction,
                    flexBar = false,
                )
                // よみかけに限り「いつぶりか」を静かに添える（continuity Minor・モック未表現）。
                RelativeReadLabel(
                    progressFraction = progressFraction,
                    lastReadAt = progress?.lastReadAt ?: 0L,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 削除アフォーダンス。⋮方式(1・既定)のみ行末にボタン（M5: 削除の可視手がかり）。0は長押しのみ。
            // Box は方式に関わらず DropdownMenu のアンカーとして常設する。
            Box {
                if (deleteUiMode == 1) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "メニュー",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DeleteDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }
        // 行下のヘアライン区切り（モック .li の border-bottom 1px、本棚系 --hl）
        HorizontalDivider(
            thickness = 1.dp,
            color = LocalShelfColors.current.hairline,
        )
    }
}

// ============================================================
// カードメニュー（⋮タップ・長押し共通のドロップダウン）
// 一時機構：削除UIの採用方式が確定したら呼び出し側の分岐ごと整理する。
// ============================================================
@Composable
private fun DeleteDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("削除") },
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}
