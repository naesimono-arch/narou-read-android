package com.novelreader.ui.skins.k

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.DeleteSourcePdfOption
import com.novelreader.ui.MissingContentBadge
import com.novelreader.ui.NewChaptersBadge
import com.novelreader.ui.ProcessingBanner
import com.novelreader.ui.ReimportScanBanner
import com.novelreader.ui.ReimportSweepBanner
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.shioriAccentFor
import com.novelreader.ui.components.shioriHue
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfChrome
import com.novelreader.ui.skins.ShelfData
import com.novelreader.ui.skins.ShelfSelection
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.rememberShelfViewToggle
import com.novelreader.ui.theme.FontCardTitle
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.LocalShioriColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.domain.ReadingStatus
import com.novelreader.domain.ScanProgress
import com.novelreader.domain.ShelfItem
import com.novelreader.domain.chapterNumberOf
import com.novelreader.domain.deleteConfirmBody
import com.novelreader.domain.filterShelfByStatus
import com.novelreader.domain.mergeShelfItems
import com.novelreader.domain.readingStatusFor
import com.novelreader.domain.reimportStatusLabel
import com.novelreader.domain.webNcodesInSelection

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
//   ・グリッド＝2列固定（2列改A・書影≈140dp・2026-07-24 ユーザー裁定＝3列6冊は小さすぎ→2列約5冊へ拡大）。
//     カード＝栞書影（ShioriCover 再利用）＋題名(明朝・1行)＋「第N/M話」進捗＋状態（未読/読了/Web既読）＋可視⋮。
//     D の GridBookCard（著者＋進捗バー＋朱印・⋮無し）とは構造が別のため K 専用カードを新設する。
//     朱印「了」は K では出さない＝モックが状態を文字「読了」で表すため。
//   ・状態フィルタチップ＝藍塗りピル（選択中）／アウトライン。D の FilterChipItem（角丸2dp・藍文字）とは意匠が
//     別（モック .chip は border-radius:999・選択で塗り）ゆえ K 専用チップを置く。
//   ・リストモード＝K 専用の案A 題字1行（KListBookCard/KWebListBookCard）。旧・D 流用は 2026-07-24 裁定で
//     圧縮S へ置換し、2026-07-26 裁定で案A（題字1行 ellipsis・行高≈71dp・約8.6行/画面）へ再圧縮
//     （正本モック bookshelf-list-K.html。Web未取込行は field 沈め＋青磁破線の行フレーム＝.web）。
// 色/字/余白はトークン経由（hex 直書き禁止・ADR 0014）。メタ文字は AA の LocalShelfColors.infoText を使う。
// ============================================================

@Composable
internal fun BookshelfK(
    // 引数の束（2026-07-27 純構造リファクタ）: 一覧面＝編集操作あり＝選択状態機械と Web 操作の束も受ける。
    // K は theme 束を受けない（テーマUIは設定タブ SettingsScreenK へ移管済み）。actions.onOpenWardrobe も
    // 意匠上未使用（装いの間へは設定タブから入る＝K設計）＝束の契約は全面共通のまま、表出はスキンが選ぶ。
    data: ShelfData,
    chrome: ShelfChrome,
    actions: ShelfActions,
    // 選択モードは骨格（BookshelfContent）と共有する単一の状態機械（D/P/M/J と同じ）。ここでは所有せず束で受ける。
    selection: ShelfSelection,
    webActions: ShelfWebActions,
    snackbarHostState: SnackbarHostState,
) {
    // ── 束の展開（本体の参照名を変えない局所別名＝挙動・描画とも既存と同一） ──
    val books = data.books
    val webNovels = data.webNovels
    val webReadingProgress = data.webReadingProgress
    val webLastReadAt = data.webLastReadAt
    val progressMap = data.progressMap
    val chapterCountMap = data.chapterCountMap
    val newEpisodeNovelMap = data.newEpisodeNovelMap
    val processingState = chrome.processingState
    val selectedStatus = chrome.selectedStatus
    val statusCounts = chrome.statusCounts
    val onSelectStatus = chrome.onSelectStatus
    val isLoading = chrome.isLoading
    val selectionMode = selection.selectionMode
    val selectedIds = selection.selectedIds
    val onToggleSelect = selection.onToggleSelect
    val onEnterSelection = selection.onEnterSelection
    val onExitSelection = selection.onExitSelection
    val onSelectAll = selection.onSelectAll
    val onDeleteBooks = selection.onDeleteBooks
    val onOpenBook = actions.onOpenBook
    val onOpenWebNovel = webActions.onOpenWebNovel
    val onResumeWebNovel = webActions.onResumeWebNovel
    val onImportWebNovel = webActions.onImportWebNovel
    val onRemoveWebNovel = webActions.onRemoveWebNovel
    val onOpenDiscovery = actions.onOpenDiscovery
    val onFabClick = actions.onFabClick
    val onCancelProcessing = actions.onCancelProcessing
    // 蔵書＋Web由来を「最近の活動順」で1本にマージ＝D/J と同一の純関数（並び規則 ADR 0016 を共有・再実装なし）。
    val shelfItems = remember(books, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap, webReadingProgress)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }
    // 冊数（ヘッダ）＝ライブラリ総数（フィルタ非依存の「実データ件数」）。Web由来も棚の1点として数える。
    val libraryCount = books.size + webNovels.size
    val isProcessing = processingState.isProcessing

    // グリッド⇄リスト表示状態（旧 k_grid_view＝route 所有）は K 自身が所有する（skins/ShelfViewToggle・
    // p_hinge_detent と同じ prefs 直参照の流儀）。既定 true＝K 装着時はグリッドで開く（モック正本
    // bookshelf-K.html の既定形）。共有 is_grid_view を流用しない理由: K でトグルした値が D の
    // 目録既定（false）を汚す＝スキンを跨いだ状態漏れを避けるため（キー分離は従来どおり）。
    val gridToggle = rememberShelfViewToggle(PrefKeys.K_GRID_VIEW, default = true)
    val isGridView = gridToggle.value

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
                onToggleView = gridToggle::toggle,
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

            // 本文欠落の一括検出バナー（案C・正本 bookshelf-reimport-sweep-D .alert＝ヘッダ直下スロット）。
            // 表示可否（新規検出の指紋）は VM が判定・内訳ダイアログは route 層所有＝ここは知らせを描くだけ。
            AnimatedVisibility(
                visible = chrome.sweepBannerVisible,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                ReimportSweepBanner(
                    missingCount = data.reimportPlans.size,
                    onLater = chrome.onSweepLater,
                    onReimport = chrome.onSweepConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // PDF フォルダ走査の進捗バナー（案X・正本 .proc）。検出バナーと同じスロット・同じ Motion 出没で、
            // 表示は排他（VM が走査中は sweepBannerVisible を false にする）。
            // 退場アニメの間 folderScan は既に null になっているため直前の非 null 値を保持して描く
            // （保持箱をスナップショット状態にしない理由＝BookshelfScreen の同処理コメント参照）。
            val lastScan = remember { arrayOfNulls<ScanProgress>(1) }
            chrome.folderScan?.let { lastScan[0] = it }
            AnimatedVisibility(
                visible = chrome.folderScan != null,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                lastScan[0]?.let { progress ->
                    ReimportScanBanner(
                        progress = progress,
                        onStop = chrome.onScanStop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                    // 列数のみ向き応答（2026-07-26 ユーザー裁定・案L5）: 縦=2列（2列改A・書影≈140dp＝
                    // 360−48(左右S24)−32(列間S32)=280/2）／横=5列（書影≈131dp級・可視域約162dpに書影約93%）。
                    // なぜ横だけ列数を変えるか: 縦と同じ2列だと横800dp級で書影が364dpへ肥大し1画面の収納数が
                    // 激減する（正本モック skins/bookshelf-K-landscape.html）。余白・アスペクト比・キャプション
                    // 構成は縦横同値＝裁定の変数は列数のみ。判定は既存流儀の LocalConfiguration.orientation
                    //（回転で Configuration が変われば自動で再コンポーズされる）。
                    val isLandscape =
                        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isLandscape) 5 else 2),
                        state = gridState,
                        // Column 内で残り空間を占める（weight＝ヘッダ/チップの下の全域。fillMaxSize は縦過剰確保になる）。
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        // 上端はヘッダ（チップ行）が持つ。下端は FAB と最終行の重なり回避ぶん（D と同じ Insets 値）。
                        contentPadding = PaddingValues(
                            start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = Insets.ScrollBottomForFab,
                        ),
                        // 行間は S16 維持。列間は 2列改A で S32 へ拡大（書影を大きく見せるための余白拡大）。
                        verticalArrangement = Arrangement.spacedBy(Spacing.S16),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S32),
                    ) {
                        // contentType=型: 蔵書/Web はカード構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                        items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
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
                                    // 本文欠落（案B）: バッジ＋状態行の差し替え。文言は domain が正本。
                                    missingLabel = data.reimportPlans[item.book.id]?.let { reimportStatusLabel(it) },
                                )
                                // Web由来（未取込）。⋮単体の「本棚から外す」は確認を挟まない（失う進捗が無く即戻せる）。
                                // 複数選択削除（系3）は確認ダイアログを挟む＝内訳文言で Web の可逆性を明示する。
                                is ShelfItem.Web -> KWebGridBookCard(
                                    novel = item.novel,
                                    lastReadEpisode = item.lastReadEpisode,
                                    onOpen = { onOpenWebNovel(item.novel) },
                                    onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                    onImport = { onImportWebNovel(item.novel) },
                                    onRemove = { onRemoveWebNovel(item.novel) },
                                    modifier = Modifier.animateItem(),
                                    // 選択キーは ShelfItem.Web.key="web:<ncode>"（蔵書は bare id）。
                                    selectionMode = selectionMode,
                                    selected = item.key in selectedIds,
                                    onToggleSelect = { onToggleSelect(item.key) },
                                    onEnterSelection = { onEnterSelection(item.key) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    // リストモード＝K 専用の案A 題字1行（KListBookCard/KWebListBookCard・正本モック bookshelf-list-K.html）。
                    // 旧・D 流用（ListBookCard/WebListBookCard）は 2026-07-24 裁定で圧縮Sへ置換し、2026-07-26 裁定で案Aへ再圧縮。
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = Insets.ScrollBottomForFab,
                        ),
                    ) {
                        // contentType=型: 蔵書/Web はカード構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                        items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
                            when (item) {
                                is ShelfItem.Book -> KListBookCard(
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
                                    // 本文欠落（案B）: 状態部の差し替え（目録行は書影なし＝バッジは出ない）。
                                    missingLabel = data.reimportPlans[item.book.id]?.let { reimportStatusLabel(it) },
                                )
                                is ShelfItem.Web -> KWebListBookCard(
                                    novel = item.novel,
                                    lastReadEpisode = item.lastReadEpisode,
                                    onOpen = { onOpenWebNovel(item.novel) },
                                    onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                    onImport = { onImportWebNovel(item.novel) },
                                    onRemove = { onRemoveWebNovel(item.novel) },
                                    modifier = Modifier.animateItem(),
                                    // 複数選択削除（系3）: 選択キーは ShelfItem.Web.key="web:<ncode>"。
                                    selectionMode = selectionMode,
                                    selected = item.key in selectedIds,
                                    onToggleSelect = { onToggleSelect(item.key) },
                                    onEnterSelection = { onEnterSelection(item.key) },
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
                        // 全選択に Web由来カードも含める（系3）。選択キーは蔵書=bare book.id・Web=ShelfItem.Web.key("web:<ncode>")。
                        onSelectAll(
                            shelfItems.map { item ->
                                when (item) {
                                    is ShelfItem.Book -> item.book.id
                                    is ShelfItem.Web -> item.key
                                }
                            }
                        )
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

    // 複数選択削除の確認（D と同語＝内訳ごとに正しい不可逆性を本文で明示・取込元PDF削除オプションは共通 DeleteSourcePdfOption）。
    // K モックにダイアログ意匠は無いため OS 面の Material AlertDialog を使う（各スキンのダイアログ流儀と同じ）。
    if (showDeleteConfirm) {
        val bookTargets = books.filter { it.id in selectedIds }
        // Web由来（未取込）カードも選択削除の対象（系3）。選択キー "web:<ncode>" を ncode へ分解し webNovels と突合する。
        val webNcodes = webNcodesInSelection(selectedIds).toSet()
        val webTargets = webNovels.filter { it.ncode in webNcodes }
        val deletableCount = bookTargets.count { it.sourceUri != null }
        val total = bookTargets.size + webTargets.size
        var alsoDeleteSource by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            // 蔵書とWebが混じり得るため中立の「件」で数える。
            title = { Text("選択した${total}件を本棚から削除しますか？") },
            text = {
                Column {
                    // 選択内訳（蔵書数・Web数）で本文を出し分け（系3）＝Web に「本文データも削除」の虚偽を出さない。
                    Text(deleteConfirmBody(bookTargets.size, webTargets.size))
                    DeleteSourcePdfOption(deletableCount, alsoDeleteSource) { alsoDeleteSource = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    // 蔵書は本文データごと削除／Web は本棚から外す（既存 removeWebNovel を一括適用）。空側は呼ばない。
                    if (bookTargets.isNotEmpty()) onDeleteBooks(bookTargets, alsoDeleteSource)
                    webTargets.forEach { onRemoveWebNovel(it) }
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
    // 本文欠落（案B・正本 bookshelf-reimport-badge-D）: 非 null なら書影左下に「本文なし」バッジ＋
    // 状態行をこの文言で置き換える（文言は domain.reimportStatusLabel が正本）。タップは onOpen のまま
    // ＝route 層が欠落本を復旧ダイアログへ差し替える（カードは知らない＝結線を一点に保つ）。
    missingLabel: String? = null,
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
                    .aspectRatio(3f / 4f)
                    // 書影の輪郭＝影（2026-07-24 ユーザー裁定＝モックは box-shadow・旧・線 border は誤訳だった）。
                    // shadow は clip より前＝影を要素の外周へ落としてから角丸で本体をクリップする。
                    // elevation はトークン供給（ShioriColors.coverShadowElevation）: 明面 2dp／ダーク 6dp
                    //（案(a) 2026-07-26 ユーザー裁定＝旧 2dp 暫定は暗面で影が沈み視認不能のため増強）。
                    .shadow(
                        elevation = LocalShioriColors.current.coverShadowElevation,
                        shape = RoundedCornerShape(3.dp),
                    )
                    .clip(RoundedCornerShape(3.dp)),
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
            // 欠落バッジ（案B・.miss）: 書影左下＝栞棒（上辺起点）と縦題字（右辺）のどちらとも重ならない静かな隅。
            if (missingLabel != null) {
                MissingContentBadge(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.S8, bottom = Spacing.S8),
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
                // 題名（.t＝明朝・1行clamp）。表紙内(ShioriCover)の縦組み題字とは別に、下段へ横組みで添える（モック .cvt＋.t の二重表示）。
                // 2列改A で書影を大きく取るぶんキャプションは1行へ圧縮（2026-07-24 裁定）。
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontSubTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.S4))
                if (missingLabel != null) {
                    // 欠落本の状態行（案B・.st）: 進捗の徴を欠落文言に置き換える（本文が無い本に話数を出すと嘘になる）。
                    Text(missingLabel, fontSize = FontMicroLabel, color = LocalShelfColors.current.infoText)
                } else {
                    KBookStatusLine(status = status, chapNum = chapNum, totalChaps = totalChaps)
                }
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
    // 複数選択削除（系3）: Web由来カードも長押しで選択モードに参加する（選択キー "web:<ncode>" は呼び出し側が扱う）。
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasProgress = lastReadEpisode > 0
    // 破線フレーム色（青磁＝secondary）。DrawScope 内では @Composable の MaterialTheme を読めないため事前に捕捉する。
    val seiji = MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                // 選択モード中はタップ/長押しで選択トグル。通常時は進捗あれば主タップ=続きから／未読は目次、長押しで選択モードへ（系3）。
                // 旧・長押し＝⋮は、キャプション行右端の可視⋮（KCardMenuButton）が代替導線になったため選択入口へ譲る。
                onClick = { if (selectionMode) onToggleSelect() else if (hasProgress) onResume() else onOpen() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 未取込＝D改（2026-07-24 ユーザー裁定）: 影は付けない＝「まだ実体がない一冊」を浮かせない
            // （実体のある蔵書カードだけ手順2の影を持つ）。輪郭は下の青磁破線が担う。
            ShioriCover(
                title = novel.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(3.dp)),
            )
            // 紙地一段沈め＝取込前の「仮置き」感（正本モック D改: 紙地を field 系へ）。ShioriCover の上へ
            // onSurface 5% を薄く被せてテーマ非依存で一段くすませる（alpha は実機検分で調整）。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            )
            // 青磁の破線フレーム（D改＝旧・白ピルの代替）。輪郭が未確定＝「仮置き＝まだ手元にない」の比喩。
            // 角丸3dp＝書影 clip と整合。描画本体は共有 narouDashedOutline（リスト帯と1定義を共用）。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .narouDashedOutline(color = seiji, cornerRadius = 3.dp),
            )
            // 選択中は書影へ藍の細縁取り＋淡い藍かぶせ（KGridBookCard と同じ .bk.sel）。
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
            // 選択モード中は書影右上に選択マーク（蔵書カードと共有の KSelectionCheck）。
            if (selectionMode) {
                KSelectionCheck(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.S8),
                )
            }
        }

        Spacer(Modifier.height(Spacing.S8))
        // キャプション行＝題名・状態（左）＋可視⋮（右端）。書影上に置かない理由は KGridBookCard と同じ（縦題字衝突）。
        Row {
            Column(Modifier.weight(1f)) {
                // 題名（.t＝明朝・1行clamp）。2列改A で書影を大きく取るぶんキャプションは1行へ圧縮（2026-07-24 裁定・蔵書カードと同じ）。
                Text(
                    text = novel.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontSubTitle,
                    maxLines = 1,
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
            // 選択モード中は書影上の選択マークへ場を譲り⋮を隠す（KGridBookCard と同じ）。
            if (!selectionMode) {
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
}

// ============================================================
// リスト（目録）書籍カード＝案A 題字1行（2026-07-26 ユーザー裁定・mockview 目視＝旧・圧縮S をさらに縦圧縮）。
// 正本モック＝bookshelf-list-K.html（案A: 行上下 S12・題字明朝1行 ellipsis・メタ上 S4）。
// なぜ題字1行か: 圧縮S（行高≈130dp・≈4.7冊/画面）から削るのは題字2行目だけで行高≈71dp・≈8.6冊/画面
//   （実機実効360dp幅・リスト可視領域≈616dpの実寸算出）に届き、著者・メタ行・⋮・状態は温存できるため。
//   行高≈71dp＞48dp＝タップ標的の下限（UX05-C）も維持。
// 様式: 左端4dp色帯（作品識別色＝書架の栞と同じ title 由来 accent で「1冊=1色相」を保つ・ListBookCard と同一導出）
//   ＋題字（明朝・1行 ellipsis・FontCardTitle）＋メタ1行（ゴシック・著者名と状態を中黒で連結）＋下ヘアライン。
//   進捗バー・状態の独立行は持たない（圧縮の系譜＝縦だけ詰める）。
// 機能パリティは D の ListBookCard から全数移植（タップ=開く／長押し=選択入口／選択モード・選択マーク／
//   新着「続きN話」バッジ／可視⋮=選択の入口）。⋮はモック各行に .dots があるため K グリッドと同じく常設する。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KListBookCard(
    book: BookEntity,
    progress: ProgressEntity?,
    // 続き（新着）バッジ用の作品要約（VM が一括照会し配布・null=未紐付け/未取得/失敗）。D の ListBookCard と同じ。
    novelDetail: WorkSummary?,
    totalChaps: Int,
    onOpen: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    modifier: Modifier = Modifier,
    // 本文欠落（案B）: 非 null ならメタ行の状態部をこの文言で置き換える（KGridBookCard と同契約。
    // 目録行は書影を持たないため状態文言だけが欠落を運ぶ）。
    missingLabel: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = readingStatusFor(progress, totalChaps)
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val newCount = newEpisodeCountFor(novelDetail, totalChaps)
    // 作品識別色（左端の色帯）。書架の栞と同じ title 由来 accent で「1冊=1色相」を保つ（ListBookCard と同一導出＝再実装なし）。
    val accentLightness = LocalShioriColors.current.accentLightness
    val barColor = remember(book.title, accentLightness) { shioriAccentFor(shioriHue(book.title), accentLightness) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                // 1冊=1トラバーサル単位に束ねる（行末⋮は別フォーカスとして残る＝D の目録行と同流儀）。
                .semantics(mergeDescendants = true) {}
                // 選択中は行全体に淡い藍かぶせ（D の ListBookCard と同じ・目録は色帯があるため控えめ）。
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else Color.Transparent,
                )
                .combinedClickable(
                    // 通常＝タップで開く／長押しで選択モードへ。選択モード中はタップ/長押しで選択トグル（D と同挙動）。
                    onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                    onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
                )
                // 色帯を行の高さいっぱいに伸ばすため内容の最小内在高さに合わせる（D の目録行と同骨格）。
                .height(IntrinsicSize.Min)
                // 上下 S12＝案A（2026-07-26 裁定・旧 S24 の半減。行高≈71dpで≈8.6冊/画面に届く圧縮の主因）。
                .padding(top = Spacing.S12, bottom = Spacing.S12),
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
            Spacer(Modifier.width(Spacing.S16))
            Column(modifier = Modifier.weight(1f)) {
                // 題字（明朝・1行 ellipsis＝案A 2026-07-26 裁定。長題は…で省く＝削るのは題字2行目のみ）。
                // 書影のない目録では題字が主役＝FontCardTitle（グリッドのキャプション FontSubTitle とは役割が別）。
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = FontCardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 題字→メタの詰め S4＝案A（旧 S8。正本 .m の margin-top:4px）。
                Spacer(Modifier.height(Spacing.S4))
                // メタ1行（ゴシック）: 著者名・状態を中黒で連結（著者が空なら状態のみ）。新着があれば末尾に「続きN話」。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (book.author.isNotBlank()) {
                        Text(
                            text = book.author,
                            fontSize = FontMicroLabel,
                            color = LocalShelfColors.current.infoText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 著者が長くても状態・バッジを押し出さない（D の目録行と同じ収縮）。
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("・", fontSize = FontMicroLabel, color = LocalShelfColors.current.infoText)
                    }
                    // 状態部＝グリッドの KBookStatusLine を再利用（読了/未読(藍ドット)/第N/M話）＝徴を1箇所に集約。
                    // 本文欠落（案B）はグリッドと同じ置き換え（進捗の徴を出さず欠落文言のみ）。
                    if (missingLabel != null) {
                        Text(missingLabel, fontSize = FontMicroLabel, color = LocalShelfColors.current.infoText)
                    } else {
                        KBookStatusLine(status = status, chapNum = chapNum, totalChaps = totalChaps)
                    }
                    // 続き（新着）バッジ＝D の ListBookCard と同じ NewChaptersBadge を共有（internal 昇格）。メタ行末尾へ。
                    newCount?.let {
                        Spacer(Modifier.width(Spacing.S8))
                        NewChaptersBadge(newCount = it)
                    }
                }
            }
            // 行末＝選択モード中は選択マーク／通常は可視⋮（選択入口）。書影の縦題字衝突が無い行だが K グリッドと導線を揃える。
            if (selectionMode) {
                Spacer(Modifier.width(Spacing.S8))
                KSelectionCheck(selected = selected)
            } else {
                Box {
                    KCardMenuButton(onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // 単一削除の専用配線は無く、⋮は複数選択の入口「選択」を露出する（KGridBookCard と同じ回答＝新機能/VM 変更なし）。
                        DropdownMenuItem(
                            text = { Text("選択") },
                            onClick = { menuOpen = false; onEnterSelection() },
                        )
                    }
                }
            }
        }
        // 行下のヘアライン区切り（モック .lc の border-bottom 1px・本棚系 --hl）。
        HorizontalDivider(thickness = 1.dp, color = LocalShelfColors.current.hairline)
    }
}

// ============================================================
// リスト（目録）Web由来カード＝案A（2026-07-26 ユーザー裁定・正本 bookshelf-list-K.html の .web）。KWebGridBookCard の目録版。
// 未取込の徴＝行全体を field 沈め＋青磁1.5dp破線（角丸6dp）の中空フレームで括る。グリッド .cv.narou が書影（実体）の
//   輪郭を「未確定＝仮置き」に描くのと同じ言葉を、書影のない目録では行そのものに掛ける（旧・帯だけの破線化は
//   2026-07-26 mockview 目視でドラフト案A のフレーム意匠へ差し替え裁定）。色帯は蔵書行と同じ title 由来色に戻す
//   （正本 .web は --band を保持＝破線枠が「未取込」を語り、帯は「1冊=1色相」の識別に専念する役割分担）。
// 機能パリティは D の WebListBookCard から全数移植（タップ=進捗あれば再開/無ければ目次・長押し=選択入口・
//   選択マーク・⋮=目次(進捗時)/取込/外す・resume 分岐）。⋮メニューは KWebGridBookCard と同じ項目を inline で持つ。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KWebListBookCard(
    novel: WebNovelEntity,
    lastReadEpisode: Int,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasProgress = lastReadEpisode > 0
    // 破線署名色（青磁＝secondary）。DrawScope 内では @Composable の MaterialTheme を読めないため事前に捕捉する。
    val seiji = MaterialTheme.colorScheme.secondary
    // 帯の作品識別色（蔵書行 KListBookCard と同一導出＝「1冊=1色相」を Web由来でも保つ）。
    val accentLightness = LocalShioriColors.current.accentLightness
    val bandColor = remember(novel.title, accentLightness) { shioriAccentFor(shioriHue(novel.title), accentLightness) }

    // 蔵書行と違い下ヘアラインを持たない＝正本 .web が border-bottom を破線フレームへ置換しているため
    // （残すと破線と実線の二重区切りになる）。Column 包みも不要になり Row 単体で組む。
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            // 角丸6dp＝正本 .web の border-radius。clip が field 地・選択かぶせ・リップルを枠形に収める。
            .clip(RoundedCornerShape(6.dp))
            // 紙地一段沈め（--field）＝KWebGridBookCard と同じ onSurface 5% かぶせのテーマ非依存翻訳（alpha は実機検分で調整）。
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            // 選択中は field の上へ淡い藍かぶせ（蔵書行と同値）。
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent,
            )
            // 青磁破線フレーム（線幅1.5dp・dash 4dp/3dp＝グリッド書影と同値の共有 narouDashedOutline）。
            .narouDashedOutline(color = seiji, cornerRadius = 6.dp)
            .combinedClickable(
                // 選択モード中はトグル。通常は進捗あれば主タップ=続きから／無ければ目次、長押しで選択モードへ（系3）。
                onClick = { if (selectionMode) onToggleSelect() else if (hasProgress) onResume() else onOpen() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            )
            .height(IntrinsicSize.Min)
            // 上下 S12＝案A（蔵書行と同値。行高≈71dpで蔵書行とリズムを揃える）。
            .padding(top = Spacing.S12, bottom = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 色帯（蔵書行と同寸: 幅4dp・角丸2dp）。破線フレームの左角丸6dpと重ならないよう枠内へ 6dp インセットし
        // （正本 .web::before left:6px）、題字の左位置は蔵書行と揃える＝後続ギャップを S16−インセットで相殺する。
        Box(
            modifier = Modifier
                .padding(start = Insets.NarouListBandInset)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(bandColor),
        )
        Spacer(Modifier.width(Spacing.S16 - Insets.NarouListBandInset))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = novel.title,
                fontFamily = MinchoFamily,
                // 目録の題字は行の主役＝FontCardTitle・1行 ellipsis（案A＝KListBookCard と同じ）。
                fontSize = FontCardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 題字→メタの詰め S4＝案A（正本 .m の margin-top:4px）。
            Spacer(Modifier.height(Spacing.S4))
            // メタ1行: 著者＋状態を中黒で連結（蔵書行と同構造＝正本 .web の .m）。未取込署名の枠内にある行のため
            // 文字は著者ごと青磁で統一する（正本 .web .m,.web .m span＝seiji-ink）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (novel.writer.isNotBlank()) {
                    Text(
                        text = novel.writer,
                        fontSize = FontMicroLabel,
                        color = seiji,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 著者が長くても状態を押し出さない（蔵書行と同じ収縮）。
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("・", fontSize = FontMicroLabel, color = seiji)
                }
                // 進捗あれば「第N話まで既読」（K グリッド Web と同文言）／無ければ「なろう・未取込」（Medium＝未取込の徴）。
                if (hasProgress) {
                    Text(
                        "第${lastReadEpisode}話まで既読",
                        fontSize = FontMicroLabel,
                        color = seiji,
                    )
                } else {
                    Text(
                        "なろう・未取込",
                        fontSize = FontMicroLabel,
                        fontWeight = FontWeight.Medium,
                        color = seiji,
                    )
                }
            }
        }
        if (selectionMode) {
            Spacer(Modifier.width(Spacing.S8))
            KSelectionCheck(selected = selected)
        } else {
            Box {
                KCardMenuButton(onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // 進捗ありのとき主タップは続きから＝目次導線を⋮へ降格して残す（KWebGridBookCard と同判断）。
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

/**
 * 未取込Webカードの青磁破線輪郭（D改署名・2026-07-24 裁定）: 線幅1.5dp・破線間隔4dp/3dp。
 * なぜ共有 Modifier に集約するか: グリッド書影とリスト行フレーム（案A・2026-07-26 裁定で帯の破線化から
 * 行全体のフレームへ移行）は別 Composable で、破線値を各所へ写経すると
 * リスト新設時の署名脱落（2026-07-26 是正の真因＝圧縮S 新設時にグリッド inline 描画が持ち込まれなかった）
 * が再発する＝「未取込の破線署名」を1定義に束ねて構造的に防ぐ。
 * dashPathEffect の破線間隔はレイアウト余白でなくストローク模様の構造値＝Spacing 尺の対象外（実機で調整）。
 */
private fun Modifier.narouDashedOutline(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        // 半ストローク内側へ寄せ、線全体を領域内に収める（clip の角丸と整合）。
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
        ),
    )
}

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
