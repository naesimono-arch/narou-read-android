package com.novelreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.computeContinuation
import com.novelreader.narou.model.NarouNovel
import com.novelreader.ui.components.BookCover
import com.novelreader.ui.theme.MinchoFamily
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = (if (flexBar) Modifier.weight(1f) else Modifier.width(80.dp))
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$percent%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        // 未読は青磁。素地上で低コントラスト（モック意図の「静かに沈める」）＝完全準拠のトレードオフ。
        Text(
            text = "未読",
            modifier = modifier,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ============================================================
// 本棚カードの進捗割合を、章位置＋（最終章のみ）章内スクロール位置から算出する。
//
// なぜ単純な chapNum/totalChaps を使わないか（F-N）:
// それだと最終章のファイルを開いた瞬間、章内を1行も読んでいなくても progress=1.0 になり
// 「100%」と嘘表示していた（章 index 単独算出でスクロール実位置を無視していたのが根因）。
// 章の総量（総アイテム数・総高さ）は DB に保存しておらず（ProgressEntity が持つのは
// LazyList の firstVisibleItemIndex/Offset だけ）、厳密な章内% は原理的に出せない。
// そこで最終章に限り、確実に判る「先頭か否か」だけを使って過大表示を避ける:
//   ・先頭（未スクロール）＝まだ最終章を読み始めていない → (N-1)/N（あと1章ぶん未読）
//   ・少しでもスクロール済み＝読み進めている → 1.0（読了間近とみなす）
// 中間章は従来どおり chapNum/totalChaps（そこは嘘にならないため挙動を変えない）。
// 進捗の書込側は一切変更せず、表示計算のみで嘘を消す。
// ============================================================
internal fun progressFractionFor(
    chapNum: Int?,
    totalChaps: Int,
    scrollIndex: Int,
    scrollOffset: Int,
): Float? {
    if (chapNum == null || totalChaps <= 0) return null
    return if (chapNum >= totalChaps) {
        // 最終章。章内スクロールを加味する。
        val atTop = scrollIndex == 0 && scrollOffset == 0
        if (atTop) (totalChaps - 1).toFloat() / totalChaps else 1f
    } else {
        chapNum.toFloat() / totalChaps
    }
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
            fontSize = 10.5.sp,
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
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteUiMode: Int,
    modifier: Modifier = Modifier,
) {
    // 削除メニューの開閉状態（⋮タップ または 長押しで開く）
    var menuExpanded by remember { mutableStateOf(false) }

    val totalChaps by produceState(initialValue = 0, key1 = book.id) {
        value = withContext(Dispatchers.IO) {
            File(book.htmlDirPath)
                .listFiles { f -> f.name.matches(Regex("chap_\\d+\\.html")) }
                ?.size ?: 0
        }
    }

    val chapNum = progress?.lastReadFilename
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

    // 最終章の章内スクロールを加味して「開いた瞬間100%」の嘘を消す（F-N・詳細は progressFractionFor）。
    val progressFraction = progressFractionFor(
        chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0,
    )

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "gridCardScale",
    )

    // モック .bk: カード地・影・角丸チップを廃したフラット構図。書影＋メタを地に直接置く。
    // クリック=開く / 長押し=削除メニュー。
    // 長押しは方式に依らず常時有効にする（M5: ⋮に気づかない層のフォールバックとして必ず残す）。
    // deleteUiMode は「可視の⋮を出すか」だけを制御する（既定1で⋮を出す）。
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onOpen,
                onLongClick = { menuExpanded = true },
            ),
    ) {
        // 書影（縦横比 2:3・角丸2px・下部に明朝タイトル焼き込み）
        Box(modifier = Modifier.fillMaxWidth()) {
            BookCover(
                bookId = book.id,
                title = book.title,
                showTitle = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(2.dp)),
            )
            // ⋮方式(1・既定)のみ書影右上に削除ボタンを出す（M5: 削除の可視手がかり）。0は長押しのみ。
            // Box は方式に関わらず DropdownMenu のアンカーとして常設する。
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                if (deleteUiMode == 1) {
                    // なぜスクリム背景を敷くか: 書影はタイトルハッシュ由来の任意の色相のため、
                    // アイコン単体では明色カバー上で視認できない。半透明黒＋白でコントラストを確保。
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .padding(6.dp)
                            .size(30.dp)
                            .background(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "メニュー",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp),
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

        Spacer(Modifier.height(11.dp))
        // メタ題字（明朝）。著者はモックのグリッドでは表示しない（リストのみ）。
        // 書影内タイトルと本欄タイトルが重複する点は完全準拠ゆえのトレードオフ（後日検証・調整）。
        Text(
            text = book.title,
            fontFamily = MinchoFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(9.dp))
        BookProgressRow(
            totalChaps = totalChaps,
            progressFraction = progressFraction,
            flexBar = true,
        )
        // 続きあり（モックは進捗行の下・上4px）
        newEpisodeCountFor(novelDetail, totalChaps)?.let { newCount ->
            NewChaptersBadge(newCount = newCount, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ============================================================
// リスト用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    // 続きありバッジ用のなろう詳細（VM が一括照会し配布。null=未紐付け/未取得/失敗）。
    novelDetail: NarouNovel?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteUiMode: Int,
    modifier: Modifier = Modifier,
) {
    // 削除メニューの開閉状態（⋮タップ または 長押しで開く）
    var menuExpanded by remember { mutableStateOf(false) }

    val totalChaps by produceState(initialValue = 0, key1 = book.id) {
        value = withContext(Dispatchers.IO) {
            File(book.htmlDirPath)
                .listFiles { f -> f.name.matches(Regex("chap_\\d+\\.html")) }
                ?.size ?: 0
        }
    }

    val chapNum = progress?.lastReadFilename
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

    // 最終章の章内スクロールを加味して「開いた瞬間100%」の嘘を消す（F-N・詳細は progressFractionFor）。
    val progressFraction = progressFractionFor(
        chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0,
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listCardScale",
    )

    // モック .li: カード地・影を廃し、上下余白＋下ヘアラインで区切る静かな行。
    // 外側 Column が行本体(Row)と区切り線(HorizontalDivider)を束ねる。
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                    // 長押しは方式に依らず常時有効（M5: ⋮のフォールバック）。deleteUiMode は⋮の可視のみ制御。
                    onLongClick = { menuExpanded = true },
                )
                .padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 小さい書影（46×69・角丸2px・文字なしの色面のみ）
            BookCover(
                bookId = book.id,
                title = book.title,
                showTitle = false,
                modifier = Modifier
                    .width(46.dp)
                    .height(69.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = book.author,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                // モック fusion のリストは進捗行の右に続きありバッジを並べる（margin-left:10px）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookProgressRow(
                        totalChaps = totalChaps,
                        progressFraction = progressFraction,
                        flexBar = false,
                    )
                    newEpisodeCountFor(novelDetail, totalChaps)?.let { newCount ->
                        NewChaptersBadge(newCount = newCount, modifier = Modifier.padding(start = 10.dp))
                    }
                }
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
        // 行下のヘアライン区切り（モック .li の border-bottom 1px）
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// ============================================================
// 削除メニュー（⋮タップ・長押し共通のドロップダウン）
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
