package com.novelreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import com.novelreader.data.WebNovelEntity
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.shioriAccentFor
import com.novelreader.ui.components.shioriHue
import com.novelreader.ui.theme.MinchoFamily

// ============================================================
// WebGridBookCard / WebListBookCard
//
// Web由来・未取り込み小説を本棚でカード表示するための Composable。
// (b) Web由来・未取込カードはモック正本で縦ルールが青磁（MaterialTheme.colorScheme.secondary）となる。
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebGridBookCard(
    novel: WebNovelEntity,
    onOpen: () -> Unit,      // カードタップ＝なろうを WebView で開く（初回＝目次。呼び出し側が処理）
    onImport: () -> Unit,    // ⋮メニュー「縦書きPDFを取り込む」
    onRemove: () -> Unit,    // ⋮メニュー「本棚から外す」
    // 機能②: WebView 読書位置（最後に開いた話。0＝未読）。>0 でメタ行を「続きから 第N話」導線へ差し替える。
    lastReadEpisode: Int = 0,
    onResume: () -> Unit = {},  // 「続きから 第N話」タップ＝記録した話へ WebView で直接（カード本体タップは目次のまま）
    modifier: Modifier = Modifier,
) {
    // なぜ expanded を Card 内で閉じるか: 各カードの ⋮ ドロップダウンメニューの開閉は独立しており、他カードと共有しないため
    var menuExpanded by remember { mutableStateOf(false) }

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感、BookCard.kt と共通）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "webGridCardScale",
    )

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
        // 書影＝栞（紙地＋色の棒＋先端＋表紙内の縦組み明朝題字）。縦横比 2:3・角丸2px。
        // なぜ Web由来カードも栞にするか: 整合ルール「1冊=1色相」（正本 consistency-D）で書架の全書影を
        // 栞に統一する。未取込の署名は accent の上書き（モードA）ではなく、棒／帯は通常の題字由来色のまま
        // カード下の「なろう・未取込」青磁テキストで行う（モードB＝正本 UNTAKEN_MODE 既定）。
        Box(modifier = Modifier.fillMaxWidth()) {
            ShioriCover(
                title = novel.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(2.dp)),
            )

            // 操作メニューのアンカー（可視の⋮は持たず長押しで開く。栞モックはフラット構図＝⋮無し）。
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                WebBookDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onImport = { menuExpanded = false; onImport() },
                    onRemove = { menuExpanded = false; onRemove() },
                )
            }
        }

        Spacer(Modifier.height(11.dp))
        // メタ題字（明朝）
        Text(
            text = novel.title,
            fontFamily = MinchoFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(9.dp))
        // メタ行: 機能②の読書記録があれば「続きから 第N話」導線（藍＝primary・タップで記録話へ WebView 直着地）。
        // 無ければ従来の「なろう・未取込」（青磁＝secondary）。カード本体タップは常に目次(onOpen)＝ユーザー想定
        // 「最初は目次・二度目以降は続きから読むボタン」に沿う。
        if (lastReadEpisode > 0) {
            Text(
                text = "続きから 第${lastReadEpisode}話",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onResume)
                    .padding(vertical = 4.dp),
            )
        } else {
            Text(
                text = "なろう・未取込",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebListBookCard(
    novel: WebNovelEntity,
    onOpen: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    // 機能②: WebView 読書位置（最後に開いた話。0＝未読）。>0 でメタ行を「続きから 第N話」導線へ差し替える。
    lastReadEpisode: Int = 0,
    onResume: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // なぜ expanded を Card 内で閉じるか: 各リスト行の ⋮ ドロップダウンメニューの開閉は独立しており、他カードと共有しないため
    var menuExpanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "webListCardScale",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true },
                )
                // 色帯を行の高さいっぱいに伸ばすため（PDF 蔵書の目録行と同じ骨格）。
                .height(IntrinsicSize.Min)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左端の色帯（本の小口メタファ）。整合ルール「1冊=1色相」で書架の栞と同じ title 由来 accent に一本化。
            // なぜ青磁でなく題字由来色か: 未取込署名はモードB（accent 上書きをせず、下の「なろう・未取込」青磁
            // テキストで署名する）＝正本 consistency-D 既定。帯自体は取込済み蔵書と同じ色相ルールで揃える。
            val surface = MaterialTheme.colorScheme.surface
            val barColor = remember(novel.title, surface) { shioriAccentFor(shioriHue(novel.title), surface) }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.title,
                    fontFamily = MinchoFamily,
                    fontSize = 16.5.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (novel.writer.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = novel.writer,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // メタ行: 機能②の読書記録があれば「続きから 第N話」導線（藍＝primary・タップで記録話へ WebView 直着地）。
                // 無ければ従来の「なろう・未取込」（青磁＝secondary）。行本体タップは常に目次(onOpen)。
                // Spacer 10dp は mokuroku 意匠（ui/design-canon 後継）側を正とする。
                if (lastReadEpisode > 0) {
                    Text(
                        text = "続きから 第${lastReadEpisode}話",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onResume)
                            .padding(vertical = 4.dp),
                    )
                } else {
                    Text(
                        text = "なろう・未取込",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "メニュー",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WebBookDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onImport = { menuExpanded = false; onImport() },
                    onRemove = { menuExpanded = false; onRemove() },
                )
            }
        }
        // 行下のヘアライン区切り（モック .li の border-bottom 1px、BookCard.kt と共通）
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// なぜ WebBookDropdownMenu を private にするか: WebBookCard.kt 内の Composable からのみ呼び出すことを想定し、不要な露出を防ぐため
@Composable
private fun WebBookDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("縦書きPDFを取り込む") },
            onClick = onImport,
            leadingIcon = {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        DropdownMenuItem(
            text = { Text("本棚から外す") },
            onClick = onRemove,
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
