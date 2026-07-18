package com.novelreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.data.WebNovelEntity
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.shioriAccentFor
import com.novelreader.ui.components.shioriHue
import com.novelreader.ui.theme.FontCardTitle
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.LocalShioriColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionSpringCard
import com.novelreader.ui.theme.Spacing

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
    modifier: Modifier = Modifier,
    // 機能②: WebView 読書位置（最後に開いた話。0＝未読）。>0 でメタ行を「続きから 第N話」導線へ差し替える。
    lastReadEpisode: Int = 0,
    onResume: () -> Unit = {},  // 「続きから 第N話」タップ＝記録した話へ WebView で直接（カード本体タップは目次のまま）
) {
    // なぜ expanded を Card 内で閉じるか: 各カードの ⋮ ドロップダウンメニューの開閉は独立しており、他カードと共有しないため
    var menuExpanded by remember { mutableStateOf(false) }

    // 進捗あり＝主タップを「続きから読む」に統一する（continuity Major 2026-07-12）。
    // なぜ: 同型の PDF 蔵書カードは主タップ＝続きから再開なのに、Web 由来カードだけ主タップが
    // 「なろう目次」着地で身振りの意味が割れていた。進捗があれば PDF と揃えて主タップ＝再開にし、
    // 目次は⋮メニューへ降格する（＝旧「続きから」小リンクの <48dp タップ標的も同時に解消）。
    val hasProgress = lastReadEpisode > 0

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感、BookCard.kt と共通）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = MotionSpringCard,
        label = "webGridCardScale",
    )

    Column(
        modifier = modifier
            // 1冊=1トラバーサル単位に束ねる（critic Major）。題字/メタ行が別ノードに割れないようにする。
            .semantics(mergeDescendants = true) {}
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                // 進捗あれば主タップ=再開（PDFと統一）／未読は従来どおり目次(onOpen)。
                onClick = if (hasProgress) onResume else onOpen,
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
                    // 進捗ありのとき主タップは再開に統一したため、目次導線を⋮へ降格して残す。
                    onOpenIndex = if (hasProgress) ({ menuExpanded = false; onOpen() }) else null,
                    onImport = { menuExpanded = false; onImport() },
                    onRemove = { menuExpanded = false; onRemove() },
                )
            }
        }

        Spacer(Modifier.height(Spacing.S12))
        // メタ題字（明朝）
        Text(
            text = novel.title,
            fontFamily = MinchoFamily,
            fontSize = FontSubTitle,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.S8))
        // メタ行: 機能②の読書記録があれば「続きから 第N話」を静かに添える（藍＝primary）。
        // なぜ非クリックにするか（continuity Major）: 主タップ（カード本体）が再開に統一されたため、
        // 旧・小リンク（<48dp タップ標的）を廃し、ここは状態表示だけの静かなラベルに落とす。
        // 無ければ従来の「なろう・未取込」（青磁＝secondary）＝主タップは目次(onOpen)。
        if (lastReadEpisode > 0) {
            Text(
                text = "続きから 第${lastReadEpisode}話",
                fontSize = FontLabel,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "なろう・未取込",
                fontSize = FontMicroLabel,
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
    modifier: Modifier = Modifier,
    // 機能②: WebView 読書位置（最後に開いた話。0＝未読）。>0 でメタ行を「続きから 第N話」導線へ差し替える。
    lastReadEpisode: Int = 0,
    onResume: () -> Unit = {},
) {
    // なぜ expanded を Card 内で閉じるか: 各リスト行の ⋮ ドロップダウンメニューの開閉は独立しており、他カードと共有しないため
    var menuExpanded by remember { mutableStateOf(false) }

    // 進捗あり＝主タップを「続きから読む」に統一（continuity Major・grid と同じ判断）。
    val hasProgress = lastReadEpisode > 0

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = MotionSpringCard,
        label = "webListCardScale",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                // 1冊=1トラバーサル単位に束ねる（critic Major）。行末の⋮は別フォーカスとして残る。
                .semantics(mergeDescendants = true) {}
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    // 進捗あれば主タップ=再開（PDFと統一）／未読は目次(onOpen)。
                    onClick = if (hasProgress) onResume else onOpen,
                    onLongClick = { menuExpanded = true },
                )
                // 色帯を行の高さいっぱいに伸ばすため（PDF 蔵書の目録行と同じ骨格）。
                .height(IntrinsicSize.Min)
                .padding(top = Spacing.S16, bottom = Spacing.S16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左端の色帯（本の小口メタファ）。整合ルール「1冊=1色相」で書架の栞と同じ title 由来 accent に一本化。
            // なぜ青磁でなく題字由来色か: 未取込署名はモードB（accent 上書きをせず、下の「なろう・未取込」青磁
            // テキストで署名する）＝正本 consistency-D 既定。帯自体は取込済み蔵書と同じ色相ルールで揃える。
            val accentLightness = LocalShioriColors.current.accentLightness
            val barColor = remember(novel.title, accentLightness) { shioriAccentFor(shioriHue(novel.title), accentLightness) }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
            Spacer(Modifier.width(Spacing.S16))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontCardTitle,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (novel.writer.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.S8))
                    Text(
                        text = novel.writer,
                        fontSize = FontLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Spacing.S12))
                // メタ行: 機能②の読書記録があれば「続きから 第N話」を静かに添える（藍＝primary・非クリック）。
                // 主タップ（行本体）が再開に統一されたため小リンクは廃止（continuity Major・<48dp 解消）。
                // 無ければ従来の「なろう・未取込」（青磁＝secondary）。行本体タップは目次(onOpen)。
                // Spacer は mokuroku 意匠（ui/design-canon 後継）側を正とする（10→拡張7段 S12）。
                if (lastReadEpisode > 0) {
                    Text(
                        text = "続きから 第${lastReadEpisode}話",
                        fontSize = FontLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "なろう・未取込",
                        fontSize = FontMicroLabel,
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
                    // 進捗ありのとき目次導線を⋮へ降格して残す（grid と同じ判断）。
                    onOpenIndex = if (hasProgress) ({ menuExpanded = false; onOpen() }) else null,
                    onImport = { menuExpanded = false; onImport() },
                    onRemove = { menuExpanded = false; onRemove() },
                )
            }
        }
        // 行下のヘアライン区切り（モック .li の border-bottom 1px、BookCard.kt と共通・本棚系 --hl）
        HorizontalDivider(
            thickness = 1.dp,
            color = LocalShelfColors.current.hairline,
        )
    }
}

// なぜ WebBookDropdownMenu を private にするか: WebBookCard.kt 内の Composable からのみ呼び出すことを想定し、不要な露出を防ぐため
// onOpenIndex: 進捗ありカードで主タップを再開へ譲ったとき、なろう目次を開く導線を⋮へ降格して残す（null＝出さない）。
@Composable
private fun WebBookDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenIndex: (() -> Unit)? = null,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // 進捗あり時のみ: 主タップは再開なので、目次はここから開く（continuity Major）。
        onOpenIndex?.let { openIndex ->
            DropdownMenuItem(
                text = { Text("なろうの目次を開く") },
                onClick = openIndex,
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
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
