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
) {
    if (progressFraction != null) {
        val percent = (progressFraction * 100).toInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ============================================================
// グリッド用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridBookCard(
    book: BookEntity,
    lastRead: String?,
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

    val chapNum = lastRead
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

    val progressFraction = if (chapNum != null && totalChaps > 0) {
        chapNum.toFloat() / totalChaps.toFloat()
    } else null

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "gridCardScale",
    )

    // モック .bk: カード地・影・角丸チップを廃したフラット構図。書影＋メタを地に直接置く。
    // クリック=開く / 長押し=削除メニュー（モックは削除アフォーダンスを持たないため既定は長押しに集約）。
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onOpen,
                // 既定(0)は長押しで削除メニュー。⋮方式(1)を選んだ場合は書影上の⋮で開くため長押しは無効。
                onLongClick = if (deleteUiMode == 0) ({ menuExpanded = true }) else null,
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
            // ⋮方式(1)のみ書影右上に削除ボタン（既定0では非表示＝モック準拠のフラット）。
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
    }
}

// ============================================================
// リスト用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListBookCard(
    book: BookEntity,
    lastRead: String?,
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

    val chapNum = lastRead
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

    val progressFraction = if (chapNum != null && totalChaps > 0) {
        chapNum.toFloat() / totalChaps.toFloat()
    } else null

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
                    onLongClick = if (deleteUiMode == 0) ({ menuExpanded = true }) else null,
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
                BookProgressRow(
                    totalChaps = totalChaps,
                    progressFraction = progressFraction,
                    flexBar = false,
                )
            }
            // 削除アフォーダンス。⋮方式(1)のみ行末にボタン。既定0は非表示（長押しで開く）。
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
