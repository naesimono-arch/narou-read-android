package com.novelreader.ui.skins.k

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.DeleteSourcePdfOption
import com.novelreader.ui.ListBookCard
import com.novelreader.ui.ProcessingBanner
import com.novelreader.ui.WebListBookCard
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.ShelfItem
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.filterShelfByStatus
import com.novelreader.viewmodel.mergeShelfItems
import com.novelreader.viewmodel.readingStatusFor

// ============================================================
// 明快K「本棚」（正本モック＝docs/design-candidates/skins/bookshelf-K.html）。
//
// 思想: 装飾でなく「構造の明快化」。ヘッダ＝画面名「本棚」＋冊数＋表示切替のみ（⋮/ハンガー/検索は
//   設定タブ・さがすタブへ移管済み＝K設計）。恒常ボトムナビは MainActivity が NavHost の外に静止表示するため
//   ここでは描かない（画面側の二重描画・下端 nav インセット加算はしない＝plan default-ui-clarity-K）。
//
// D 機能の全数引き継ぎ（M/P/J と同流儀・ADR 0022 §1）: 選択削除・Webカード（目次/続きから/取込/外す）・状態
//   フィルタ・PDF追加(FAB)・取込中バナー・スナックバー・空状態。選択モード状態（selectionMode/selectedIds と
//   各操作）は骨格 BookshelfContent が所有する単一の状態機械を引数で受けて共有する＝ここで再定義しない
//   （骨格側の BackHandler 1本が効く）。合成は D/J と同一の純関数 filterShelfByStatus＋mergeShelfItems（再実装なし）。
//
// 意匠の差（D からの写像でなく K モックへの忠実翻訳）:
//   ・グリッド＝3列固定（D の Adaptive 2列より密＝表示冊数増）。カード＝栞書影（ShioriCover 再利用）＋題名(明朝)
//     ＋「第N/M話」進捗＋状態（未読/読了/Web既読）＋可視⋮。D の GridBookCard（著者＋進捗バー＋朱印・⋮無し）とは
//     構造が別のため K 専用カードを新設する。朱印「了」は K では出さない＝モックが状態を文字「読了」で表すため。
//   ・状態フィルタチップ＝藍塗りピル（選択中）／アウトライン。D の FilterChipItem（角丸2dp・藍文字）とは意匠が
//     別（モック .chip は border-radius:999・選択で塗り）ゆえ K 専用チップを置く。
//   ・リストモードはモック未規定＝発明せず D の描画（ListBookCard/WebListBookCard）を流用する。
// 色/字/余白はトークン経由（hex 直書き禁止・ADR 0014）。メタ文字は AA の LocalShelfColors.infoText を使う。
// ============================================================

@Composable
internal fun BookshelfK(
    // 表示対象の蔵書（骨格 visibleBooks）と Web由来（未取込）。マージ純関数へそのまま渡す。
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    webReadingProgress: Map<String, Int>,
    webLastReadAt: Map<String, Long>,
    progressMap: Map<String, ProgressEntity>,
    chapterCountMap: Map<String, Int>,
    newEpisodeNovelMap: Map<String, WorkSummary>,
    processingState: ProcessingState,
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    onSelectStatus: (ReadingStatus?) -> Unit,
    // グリッド⇄リスト表示切替（永続はルート層の onToggleView に委譲＝D と同じ prefs 単一所有）。
    isGridView: Boolean,
    onToggleView: () -> Unit,
    // 選択モードは骨格（BookshelfContent）と共有する単一の状態機械（D/P/M/J と同じ）。ここでは所有せず引数で受ける。
    selectionMode: Boolean,
    selectedIds: List<String>,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onDeleteBooks: (List<BookEntity>, deleteSource: Boolean) -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onOpenWebNovel: (WebNovelEntity) -> Unit,
    onResumeWebNovel: (WebNovelEntity, Int) -> Unit,
    onImportWebNovel: (WebNovelEntity) -> Unit,
    onRemoveWebNovel: (WebNovelEntity) -> Unit,
    onOpenDiscovery: () -> Unit,
    onFabClick: () -> Unit,
    onCancelProcessing: () -> Unit,
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
) {
    // 蔵書＋Web由来を「最近の活動順」で1本にマージ＝D/J と同一の純関数（並び規則 ADR 0016 を共有・再実装なし）。
    val shelfItems = remember(books, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap, webReadingProgress)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }
    // 冊数（ヘッダ）＝ライブラリ総数（フィルタ非依存の「実データ件数」）。Web由来も棚の1点として数える。
    val libraryCount = books.size + webNovels.size
    val isProcessing = processingState.isProcessing

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            // statusBars のみ避ける（ボトムナビは NavHost の外＝下端 nav インセットは KBottomNav が持つ・二重加算しない）。
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ヘッダ（.head）: 「本棚」＋薄く冊数＋右端は表示切替のみ。
            KHeader(
                count = libraryCount,
                isGridView = isGridView,
                onToggleView = onToggleView,
            )

            // 取込中バナー（.proc 相当＝D の ProcessingBanner を流用）。出没のみ Motion スロット（reveal/dismiss）。
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                ProcessingBanner(
                    processingState = processingState,
                    onStop = onCancelProcessing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 状態フィルタチップ行（.chips）。棚が非空のときだけ意味を持つが、D と同じく常時出して「すべて」へ戻れる導線を保つ。
            KStatusChipRow(
                selectedStatus = selectedStatus,
                onSelect = onSelectStatus,
                statusCounts = statusCounts,
            )

            when {
                // 状態フィルタ絞り込みで0件（蔵書ゼロではない）＝ヘッダは残し静かな案内（D の StatusFilterEmptyText と同語）。
                selectedStatus != null && shelfItems.isEmpty() -> {
                    Text(
                        text = "この分類の本はありません",
                        fontSize = FontSubTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S16),
                    )
                }
                // 空状態は Loading 中は出さない（Content(空) 確定まで＝cold start の空フラッシュ回避・D の F-O と同思想）。
                !isLoading && shelfItems.isEmpty() && !isProcessing -> {
                    KEmptyState(
                        onFindWorks = onOpenDiscovery,
                        onAddPdf = onFabClick,
                        // Column 内で残り空間を占めて中央寄せする（fillMaxSize だと縦を過剰確保しヘッダを押し出すため weight）。
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                isGridView -> {
                    // グリッド3列固定（.grid grid-template-columns:repeat(3,1fr)）。
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        // Column 内で残り空間を占める（weight＝ヘッダ/チップの下の全域。fillMaxSize は縦過剰確保になる）。
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        // 上端はヘッダ（チップ行）が持つ。下端は FAB と最終行の重なり回避ぶん（D と同じ Insets 値）。
                        contentPadding = PaddingValues(
                            start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = Insets.ScrollBottomForFab,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.S16),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
                    ) {
                        items(shelfItems, key = { it.key }) { item ->
                            when (item) {
                                is ShelfItem.Book -> KGridBookCard(
                                    book = item.book,
                                    progress = progressMap[item.book.id],
                                    totalChaps = chapterCountMap[item.book.id] ?: 0,
                                    selectionMode = selectionMode,
                                    selected = item.book.id in selectedIds,
                                    onOpen = { onOpenBook(item.book) },
                                    onToggleSelect = { onToggleSelect(item.book.id) },
                                    onEnterSelection = { onEnterSelection(item.book.id) },
                                    modifier = Modifier.animateItem(),
                                )
                                // Web由来（未取込）。外す操作に確認を挟まないのは D/P/J と同じ判断（失う進捗が無く詳細から即戻せる）。
                                is ShelfItem.Web -> KWebGridBookCard(
                                    novel = item.novel,
                                    lastReadEpisode = item.lastReadEpisode,
                                    onOpen = { onOpenWebNovel(item.novel) },
                                    onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                    onImport = { onImportWebNovel(item.novel) },
                                    onRemove = { onRemoveWebNovel(item.novel) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
                else -> {
                    // リストモード＝モック未規定ゆえ D の目録描画（ListBookCard/WebListBookCard）を流用（発明しない）。
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = Insets.ScrollBottomForFab,
                        ),
                    ) {
                        items(shelfItems, key = { it.key }) { item ->
                            when (item) {
                                is ShelfItem.Book -> ListBookCard(
                                    book = item.book,
                                    progress = progressMap[item.book.id],
                                    novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                    totalChaps = chapterCountMap[item.book.id] ?: 0,
                                    onOpen = { onOpenBook(item.book) },
                                    modifier = Modifier.animateItem(),
                                    selectionMode = selectionMode,
                                    selected = item.book.id in selectedIds,
                                    onToggleSelect = { onToggleSelect(item.book.id) },
                                    onEnterSelection = { onEnterSelection(item.book.id) },
                                )
                                is ShelfItem.Web -> WebListBookCard(
                                    novel = item.novel,
                                    lastReadEpisode = item.lastReadEpisode,
                                    onOpen = { onOpenWebNovel(item.novel) },
                                    onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                    onImport = { onImportWebNovel(item.novel) },
                                    onRemove = { onRemoveWebNovel(item.novel) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            // 選択モードの下端アクションバー（残8・案B）。KBottomNav が nav インセットを持つため navigationBarsPadding は付けない
            // （付けると本バーとボトムナビの間に隙間が空く＝二重加算）。選択中はボトムナビの上に重なって出る。
            if (selectionMode) {
                KSelectionActionBar(
                    count = selectedIds.size,
                    onCancel = onExitSelection,
                    onSelectAll = {
                        // 全選択の対象は蔵書（Book）のみ＝Web未取込は選択削除の対象外（D/P/J と同一）。
                        onSelectAll(shelfItems.filterIsInstance<ShelfItem.Book>().map { it.book.id })
                    },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        }

        // 拡張FAB「＋ PDFを追加」（.fab 藍・ラベル付き）。選択モード中は下端の選択バーへ場を譲り隠す（D の Scaffold と同挙動）。
        if (!selectionMode) {
            ExtendedFloatingActionButton(
                text = { Text("PDFを追加") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onFabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.S16, bottom = Spacing.S16),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.S16),
        )
    }

    // 複数選択削除の確認（D/P/J と同語＝不可逆を本文で明示・取込元PDF削除オプションは共通 DeleteSourcePdfOption）。
    // K モックにダイアログ意匠は無いため OS 面の Material AlertDialog を使う（各スキンのダイアログ流儀と同じ）。
    if (showDeleteConfirm) {
        val targets = books.filter { it.id in selectedIds }
        val deletableCount = targets.count { it.sourceUri != null }
        var alsoDeleteSource by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("選択した${targets.size}冊を本棚から削除しますか？") },
            text = {
                Column {
                    Text("変換済みの本文データも削除されます。この操作は取り消せません。")
                    DeleteSourcePdfOption(deletableCount, alsoDeleteSource) { alsoDeleteSource = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteBooks(targets, alsoDeleteSource)
                    onExitSelection()
                }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("やめる") }
            },
        )
    }
}

// ============================================================
// ヘッダ（.head＝「本棚」＋薄く冊数＋右端 表示切替のみ）
// ============================================================
@Composable
private fun KHeader(
    count: Int,
    isGridView: Boolean,
    onToggleView: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.S24, end = Spacing.S8, top = Spacing.S8, bottom = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f)) {
            // 画面名＝タブと同語彙「本棚」（You Are Here の二重化）。SettingsScreenK の h1 と同じ字（headlineSmall bold）で揃える。
            // 色は明示 onSurface（ルート Surface 接地後も、見出しの意図を字面に残す）。
            Text(
                "本棚",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(Spacing.S8))
            // 冊数＝タイトルとベースラインを揃えた titleMedium（16sp）。旧 11sp+下端揃えは「小さくポツンと孤立」
            // とのユーザー指摘（2026-07-23）＝見出しの従属要素として大きさと基線で紐付ける。
            Text(
                "${count}冊",
                style = MaterialTheme.typography.titleMedium,
                color = LocalShelfColors.current.infoText,
                modifier = Modifier.alignByBaseline(),
            )
        }
        // グリッド⇄リスト表示切替（.view＝唯一のヘッダアクション）。図柄は D の本棚と同じ規則で入替。
        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                contentDescription = if (isGridView) "リスト表示" else "グリッド表示",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ============================================================
// 状態フィルタチップ行（.chips＝すべて/よみかけ/未読/読了。選択中＝藍塗りピル白字／他＝アウトライン）
// ============================================================
@Composable
private fun KStatusChipRow(
    selectedStatus: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    statusCounts: Map<ReadingStatus, Int>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        // 「すべて」＝選択なし（null）。棚が非空のときだけ出る行なので常に押せる。
        KStatusChip(label = "すべて", selected = selectedStatus == null, enabled = true) { onSelect(null) }
        // よみかけ／未読／読了。0件の分類は enabled=false で淡く＝押しても空表示になる分類を先に塞ぐ（D と同規則）。
        KStatusChip(
            label = "よみかけ",
            selected = selectedStatus == ReadingStatus.READING,
            enabled = (statusCounts[ReadingStatus.READING] ?: 0) > 0,
        ) { onSelect(ReadingStatus.READING) }
        KStatusChip(
            label = "未読",
            selected = selectedStatus == ReadingStatus.UNREAD,
            enabled = (statusCounts[ReadingStatus.UNREAD] ?: 0) > 0,
        ) { onSelect(ReadingStatus.UNREAD) }
        KStatusChip(
            label = "読了",
            selected = selectedStatus == ReadingStatus.FINISHED,
            enabled = (statusCounts[ReadingStatus.FINISHED] ?: 0) > 0,
        ) { onSelect(ReadingStatus.FINISHED) }
    }
}

/** フィルタチップ1個（.chip＝角丸ピル）。選択＝藍塗り＋白字／非選択＝ヘアライン枠＋補助色／0件＝淡く不活性。 */
@Composable
private fun KStatusChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape // border-radius:999px＝完全な丸ピル
    val base = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .then(base)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
    ) {
        Text(
            label,
            fontSize = FontChipLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ============================================================
// グリッド書籍カード（.bk＝栞書影＋題名(明朝)＋「第N/M話」/状態＋可視⋮）
// ShioriCover（栞書影の描画）を再利用し、下段の題名・状態は K モックの版面で組む（D の GridBookCard とは別構造）。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KGridBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = readingStatusFor(progress, totalChaps)
    val chapNum = chapterNumberOf(progress?.lastReadFilename)

    Column(
        modifier = modifier
            // 1冊=1トラバーサル単位に束ねる（D カードと同流儀）。
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                // 通常＝タップで開く/長押しで選択モードへ。選択モード中はタップ/長押しで選択トグル（D と同挙動）。
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ShioriCover(
                title = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(3.dp))
                    // 書影の輪郭線。栞書影の紙色はライト=Surface・セピア=Background と地色同値（SkinD.shiori）＝
                    // 枠が無いと本と地が一体化する（2026-07-23 ユーザー指摘の真因）。outline の半透明で全テーマ一律に縁取る。
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                // 取込時に抽選・永続した先端種/棒長（旧蔵書は null＝title 由来へフォールバックで見た目不変・D と同じ）。
                persistedTipIndex = book.shioriTipIndex,
                persistedLenFrac = book.shioriLenFrac,
            )
            // 選択中は書影へ藍の細縁取り＋淡い藍かぶせ（D の .bk.sel と同じ）。
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
            // 選択モード中のみ書影右上に選択マーク（⋮は書影上に置かない＝下のキャプション行へ。
            // なぜ: 栞書影の縦組み題字は右端上起点＝TopEnd の⋮と必ず衝突する。実機検分 2026-07-23 で確認）。
            if (selectionMode) {
                KSelectionCheck(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.S8),
                )
            }
        }

        Spacer(Modifier.height(Spacing.S8))
        // キャプション行＝題名・状態（左）＋可視⋮（右端）。Play Books 等と同型の標準配置＝書影と衝突しない。
        Row {
            Column(Modifier.weight(1f)) {
                // 題名（.t＝明朝・2行clamp）。表紙内(ShioriCover)の縦組み題字とは別に、下段へ横組みで添える（モック .cvt＋.t の二重表示）。
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontSubTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.S4))
                KBookStatusLine(status = status, chapNum = chapNum, totalChaps = totalChaps)
            }
            if (!selectionMode) {
                Box {
                    KCardMenuButton(onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // 単一削除の専用配線は無く、削除は「選択→下端バー→確認」の共有フローが担う（新機能・VM変更は作らない）。
                        // ゆえに⋮は複数選択の入口「選択」を露出して長押し操作を発見可能にする（plan 確定6の「入口説明」）。
                        DropdownMenuItem(
                            text = { Text("選択") },
                            onClick = { menuOpen = false; onEnterSelection() },
                        )
                    }
                }
            }
        }
    }
}

/** 蔵書カードの状態行（.st）。読了＝「読了」／未読＝藍ドット＋「未読」／よみかけ＝「第N/M話」。 */
@Composable
private fun KBookStatusLine(status: ReadingStatus, chapNum: Int?, totalChaps: Int) {
    when (status) {
        ReadingStatus.FINISHED -> Text(
            "読了",
            fontSize = FontMicroLabel,
            color = LocalShelfColors.current.infoText,
        )
        ReadingStatus.UNREAD -> Row(verticalAlignment = Alignment.CenterVertically) {
            // 藍ドット（.st .dot）＝未読の徴。意味は隣接の文字が運ぶため、ドット自体は装飾アクセント＝primary で可。
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(Spacing.S4))
            Text("未読", fontSize = FontMicroLabel, color = LocalShelfColors.current.infoText)
        }
        // よみかけ＝読んだ章/全章（進捗バーでなく到達話数を数字で示すモック流儀）。chapNum は READING では非 null。
        ReadingStatus.READING -> Text(
            "第${chapNum ?: 1}/${totalChaps}話",
            fontSize = FontMicroLabel,
            color = LocalShelfColors.current.infoText,
        )
    }
}

// ============================================================
// Web由来（未取込）グリッドカード（.bk＋.web マーカー。主タップ＝続きから/目次・⋮＝目次/取込/外す）
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KWebGridBookCard(
    novel: WebNovelEntity,
    lastReadEpisode: Int,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasProgress = lastReadEpisode > 0

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                // 進捗あれば主タップ＝続きから（D/J と統一）／未読は目次。長押しは⋮を開く。
                onClick = if (hasProgress) onResume else onOpen,
                onLongClick = { menuOpen = true },
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ShioriCover(
                title = novel.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(3.dp))
                    // 書影の輪郭線（KGridBookCard と同理由＝紙色が地色同値で一体化するため）。
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
            )
            // Web マーカー（.web＝左上・任意の識別）。半透明白地に藍字＝画像可読の固定色（後述 KCardMenuButton と同扱い）。
            Text(
                "Web",
                fontSize = FontMicroLabel,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.S4)
                    .clip(RoundedCornerShape(6.dp))
                    .background(WebCardOverlayScrim)
                    .padding(horizontal = Spacing.S4, vertical = Spacing.S4),
            )
        }

        Spacer(Modifier.height(Spacing.S8))
        // キャプション行＝題名・状態（左）＋可視⋮（右端）。書影上に置かない理由は KGridBookCard と同じ（縦題字衝突）。
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = novel.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontSubTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.S4))
                // 状態（.st）: 進捗あれば「第N話まで既読」（モック文言）／無ければ「なろう・未取込」（D と同じ未取込の徴）。
                if (hasProgress) {
                    Text(
                        "第${lastReadEpisode}話まで既読",
                        fontSize = FontMicroLabel,
                        color = LocalShelfColors.current.infoText,
                    )
                } else {
                    Text(
                        "なろう・未取込",
                        fontSize = FontMicroLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box {
                KCardMenuButton(onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // 進捗ありのとき主タップは続きから＝目次導線を⋮へ降格して残す（D/J と同判断）。
                    if (hasProgress) {
                        DropdownMenuItem(text = { Text("なろうの目次を開く") }, onClick = { menuOpen = false; onOpen() })
                    }
                    DropdownMenuItem(text = { Text("縦書きPDFを取り込む") }, onClick = { menuOpen = false; onImport() })
                    DropdownMenuItem(text = { Text("本棚から外す") }, onClick = { menuOpen = false; onRemove() })
                }
            }
        }
    }
}

// 書影上に載る Web マーカーの固定スクリム（半透明白）。任意の書影色の上で読ませる画像可読用途の固定色＝
// テーマ配色に紐づかない（D の SelectionCheck 白リング・朱印バッジと同じ ADR 0014 の固定色スロット）。
private val WebCardOverlayScrim = Color.White.copy(alpha = 0.85f)

/**
 * キャプション行右端の可視⋮（32dpタップ面）。書影上でなく通常面に載るためスクリム不要＝トークン色で描く
 * （書影右上案は栞書影の縦題字と衝突するため移設＝実機検分 2026-07-23）。
 */
@Composable
private fun KCardMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = "メニュー",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 選択マーク（D の SelectionCheck を K へ再掲＝画像可読の固定色。選択＝藍塗り＋白✓／非選択＝白リング＋暗スクリム）。 */
@Composable
private fun KSelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) primary else Color.Black.copy(alpha = 0.26f))
            .border(1.5.dp, if (selected) primary else Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ============================================================
// 選択モードの下端アクションバー（D の SelectionActionBar を K へ再掲）。
// navigationBarsPadding は付けない＝KBottomNav が nav インセットを持つため（付けると二重加算で隙間が空く）。
// ============================================================
@Composable
private fun KSelectionActionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("キャンセル", fontSize = FontLabel) }
                Text(
                    text = "${count}冊選択中",
                    fontSize = FontLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.S8),
                )
                TextButton(onClick = onSelectAll) { Text("全選択", fontSize = FontLabel) }
                Spacer(Modifier.width(Spacing.S8))
                Button(
                    onClick = onDelete,
                    enabled = count > 0,
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Text("削除", fontSize = FontLabel)
                }
            }
        }
    }
}

// ============================================================
// 空状態（.empty＝「まだ本がありません」＋説明＋CTA2つ〈作品をさがす〉〈PDFを追加〉）
// 〈作品をさがす〉はさがすタブ（発見ホーム）へ＝K は発見帯を本棚に置かず、さがすタブへ一本化した（plan 確定5）。
// ============================================================
@Composable
private fun KEmptyState(
    onFindWorks: () -> Unit,
    onAddPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.S40),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "まだ本がありません",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.S12))
        Text(
            "読みたい作品をさがすか、お手元のPDFを追加して読み始めましょう。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.S32))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S12)) {
            // 一画面一強調＝新規ユーザーの主導線「作品をさがす」を藍の実塗り、PDF追加を輪郭ボタンに沈める。
            Button(onClick = onFindWorks) { Text("作品をさがす") }
            OutlinedButton(onClick = onAddPdf) { Text("PDFを追加") }
        }
    }
}
