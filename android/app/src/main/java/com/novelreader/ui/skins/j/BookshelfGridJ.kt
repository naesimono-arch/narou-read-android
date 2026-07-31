package com.novelreader.ui.skins.j

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.DeleteSourcePdfOption
import com.novelreader.ui.MissingContentDeleteWarningText
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfChrome
import com.novelreader.ui.skins.ShelfData
import com.novelreader.ui.skins.ShelfSelection
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.GlyphDarkPortal
import com.novelreader.ui.theme.GoldPortal
import com.novelreader.ui.theme.GreenPortal
import com.novelreader.ui.theme.InkPortal
import com.novelreader.ui.theme.LinePortal
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.AmbGridBackdropPortal
import com.novelreader.ui.theme.NovelReaderAlertDialog
import com.novelreader.ui.theme.PagePortal
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.ResumeInkPortal
import com.novelreader.ui.theme.ResumeSurfacePortal
import com.novelreader.ui.theme.SoftPortal
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.domain.ReadingStatus
import com.novelreader.domain.ShelfItem
import com.novelreader.domain.chapterNumberOf
import com.novelreader.domain.countMissingContentTargets
import com.novelreader.domain.deleteConfirmLabel
import com.novelreader.domain.filterShelfByStatus
import com.novelreader.domain.mergeShelfItems
import com.novelreader.domain.missingContentDeleteWarning
import com.novelreader.domain.progressFractionFor
import com.novelreader.domain.readingStatusFor
import java.time.LocalTime
import kotlin.math.roundToInt

// ============================================================
// スキンJ「ポータル」の本棚＝グリッド一覧面（正本 bookshelf-J.html の「③ グリッド一覧」＝`.g-*`/`.grid`/`.bk` 系）。
//
// 是正の背景（ADR 0022 追記その2・ユーザー最上位原則「各スキンは全く別のアプリ＝Dの見た目の型を引き継がない」）:
//   デッキ⇄一覧トグルの一覧側を D構造フォールバック（栞書影＝別UIの型）から J自身の意匠へ差し替える。J の一覧は
//   「1作=1画面の没入扉」を升目へ降格した面で、各セルもまた小さな扉（cover=扉固有 ambient ＋象徴文字＋題名）。
//   世界（森の回廊）が切れないよう、扉パレット選択（portalDoorPaletteFor）・絞り込みチップ・取込中バナー・
//   ⋮メニューはデッキ面の internal 部品を再利用する（BookshelfPortalJ の PortalIconButton/PortalChips/
//   PortalProcessingBanner/PortalThemeMenuSection＝二重実装なし）。
//
// D 機能の全数引き継ぎ（欠落ゼロが合格条件）: 選択削除・Webカード（未取込 目次/続きから/取込/外す）・読書状態
//   フィルタ・PDF追加（⋮へ移植）・取込中バナー・スナックバー・空状態・デッキ⇄一覧トグル。選択モード状態
//   （selectionMode/selectedIds と各操作）は骨格 BookshelfContent が所有する単一の状態機械を引数で受けて共有する
//   ＝ここで再定義しない（二重管理を避け、骨格側の BackHandler 1本が効く）。合成は D else 経路と同一の純関数
//   filterShelfByStatus＋mergeShelfItems（並び規則 ADR 0016 を共有＝再実装なし）。
//
// タイポ: J 流儀＝題名/象徴＝明朝（MinchoFamily・mock var(--mincho)）・メタ/UI＝既定ゴシック。px 値はモックの
//   font-size を 1:1 で sp へ写す（各行にモック由来コメント）。
// モーション: J はモックにモーション無し（ADR 0022 §3）＝静止。取込中バナーの出没だけ既存 Motion スロット流用。
//
// 〈遊び心〉の直交2軸（デッキと同じ機序をグリッドでも維持）:
//   J1「開く扉」= 各セルの読了率 open で升の大気（cover のグロー）が明るむ。既定=夕（open=読進）で扉の奥が灯る。
//   J3「時を映す扉」= 起動時刻の相（朝/夕/夜）でセル大気の温度・明るさを揃える。既定=夕はモック値を厳密再現し、
//     朝/夜はデッキと同一の portalAmbientParamsFor を共有して整合させる（＝時刻でデッキと不連続にならない）。
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfGridJ(
    // 引数の束（2026-07-27 純構造リファクタ）: 一覧面＝編集操作あり＝選択状態機械と Web 操作の束も受ける。
    data: ShelfData,
    chrome: ShelfChrome,
    actions: ShelfActions,
    theme: ThemeControl,
    // 選択モードは骨格（BookshelfContent）と共有する単一の状態機械（D/P/M と同じ）。ここでは所有せず束で受ける。
    selection: ShelfSelection,
    webActions: ShelfWebActions,
    snackbarHostState: SnackbarHostState,
    // 一覧⇄デッキの面切替（旧 onToggleDeck）。状態は rememberShelfFace が所有し閉包で結線される。
    onToggleFace: () -> Unit,
) {
    // ── 束の展開（本体の参照名を変えない局所別名＝挙動・描画とも既存と同一） ──
    val books = data.books
    val webNovels = data.webNovels
    val webReadingProgress = data.webReadingProgress
    val webLastReadAt = data.webLastReadAt
    val progressMap = data.progressMap
    val chapterCountMap = data.chapterCountMap
    val newEpisodeNovelMap = data.newEpisodeNovelMap
    val webNewEpisodeTotals = data.webNewEpisodeTotals
    val processingState = chrome.processingState
    val selectedStatus = chrome.selectedStatus
    val statusCounts = chrome.statusCounts
    val onSelectStatus = chrome.onSelectStatus
    val isLoading = chrome.isLoading
    val appTheme = theme.appTheme
    val onThemeChange = theme.onThemeChange
    val followingSystem = theme.followingSystem
    val onFollowSystem = theme.onFollowSystem
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
    // onOpenDiscovery/onOpenWardrobe は撤去済み（2026-07-29 K形正本追従＝発見は「さがす」タブ・装いは設定タブへ移管。
    // デッキ面 BookshelfPortalJ のクローム導線は K形モック対象外＝残置）。
    val onFabClick = actions.onFabClick
    val onCancelProcessing = actions.onCancelProcessing
    // 蔵書＋Web由来を「最近の活動順」で1本にマージ＝D else 経路と同一の純関数（再実装なし）。
    val shelfItems = remember(books, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap, webReadingProgress)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }

    // 〈遊び心〉J3: 起動時に1回だけ時刻相を固定（フレーム毎に Clock を読むと再コンポーズが暴れるため・デッキと同機序）。
    val timePhase = remember { portalTimePhaseFor(LocalTime.now().hour) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ── グリッド面の外殻の地（mock `.portal` の style＝#0C0E0B＝PagePortal）＋上端の淡い森グロー
            //   （mock radial-gradient(90% 40% at 50% -6%, rgba(31,52,38,.6), transparent)＝AmbGridBackdropPortal 中央追加済み）──
            .background(PagePortal)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AmbGridBackdropPortal.copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.06f),
                        radius = size.width * 0.9f,
                    ),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // ── g-top（本棚題字＋見つける/装いの間/デッキへ戻る/メニュー）＝スワイプで動かない固定クローム ──
            GridTopBar(
                // 冊数（K形の明示冊数）＝ライブラリ総数（蔵書＋Web由来）。D/K/M の libraryCount と同一定義。
                count = books.size + webNovels.size,
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                followingSystem = followingSystem,
                onFollowSystem = onFollowSystem,
                onToggleDeck = onToggleFace,
                onFabClick = onFabClick,
            )

            // 取込中バナー（.proc＝扉を仕立てている＝デッキ面と同一部品）。出没のみ既存 Motion スロット。
            AnimatedVisibility(
                visible = processingState.isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                PortalProcessingBanner(processingState, onCancelProcessing)
            }

            // ── g-scroll（見つける導線＋絞り込みチップ＋升目グリッド）──
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // .grid grid-template-columns:1fr 1fr（モックは2列固定）
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // .g-scroll padding 0 22px 32px（左右22px→S24・下32px→S32）。上端はヘッダが持つ。
                contentPadding = PaddingValues(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S32),
                verticalArrangement = Arrangement.spacedBy(Spacing.S24),   // .grid row-gap 22px
                horizontalArrangement = Arrangement.spacedBy(Spacing.S16), // .grid col-gap 16px
            ) {
                // 見つける導線（FindGuideBandJ）は撤去した（2026-07-29 ユーザー裁定＝K形正本 bookshelf-J.html 追従。
                // 発見は恒常ナビ「さがす」タブへ完全分離）。
                // 絞り込みチップ（.chipbar＝読書状態フィルタ・デッキ面と同一部品）＝全幅。
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PortalChips(selectedStatus, statusCounts, onSelectStatus)
                    Spacer(Modifier.height(Spacing.S12))
                }

                // contentType=型: 蔵書/Web はカード構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is ShelfItem.Book -> {
                            val progress = progressMap[item.book.id]
                            val totalChaps = chapterCountMap[item.book.id] ?: 0
                            GridDoorCell(
                                book = item.book,
                                progress = progress,
                                totalChaps = totalChaps,
                                novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                webSiteTotal = webNewEpisodeTotals[item.book.id],
                                timePhase = timePhase,
                                selectionMode = selectionMode,
                                selected = item.book.id in selectedIds,
                                onOpen = { onOpenBook(item.book) },
                                onToggleSelect = { onToggleSelect(item.book.id) },
                                onEnterSelection = { onEnterSelection(item.book.id) },
                            )
                        }
                        // Web由来（未取込）セル。外す操作に確認を挟まないのは D/P と同じ判断（失う進捗が無く詳細から即戻せる）。
                        is ShelfItem.Web -> WebGridDoorCell(
                            novel = item.novel,
                            lastReadEpisode = item.lastReadEpisode,
                            timePhase = timePhase,
                            onOpen = { onOpenWebNovel(item.novel) },
                            onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                            onImport = { onImportWebNovel(item.novel) },
                            onRemove = { onRemoveWebNovel(item.novel) },
                        )
                    }
                }

                if (!isLoading && shelfItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        // 空状態はモック未定義＝発明を最小化した一文（語はフィルタ有無で分岐・SoftPortal）。
                        Text(
                            text = if (selectedStatus == null) "扉はまだ無い" else "この棚に該当する扉は無い",
                            fontFamily = MinchoFamily,
                            fontSize = 13.sp,
                            letterSpacing = 0.06.em,
                            color = SoftPortal,
                            modifier = Modifier.padding(vertical = Spacing.S40),
                        )
                    }
                }
            }

            // 選択モード中は下端の選択アクションバー（D の bottomBar と同型・J 語彙で最小翻訳）。
            if (selectionMode) {
                GridSelectionBar(
                    count = selectedIds.size,
                    onCancel = onExitSelection,
                    onSelectAll = {
                        // 全選択の対象は蔵書（Book）のみ＝Web未取込は選択削除の対象外（D/P と同一）。
                        onSelectAll(shelfItems.filterIsInstance<ShelfItem.Book>().map { it.book.id })
                    },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.S16),
        )
    }

    // 複数選択削除の確認（D/P と同語＝不可逆を本文で明示）。構造は Material AlertDialog。
    // 面の色は D 系モックの `.dlg{background:var(--base)}`（素地・分離はスクリムと影）を surfaceContainerHigh へ
    // 移植したものが効く（SkinContainerTiers.kt）＝OS 既定の紫面ではない。
    if (showDeleteConfirm) {
        val targets = books.filter { it.id in selectedIds }
        val deletableCount = targets.count { it.sourceUri != null }
        // 欠落本を含む削除は「復元の最後の機会」を消す（機序＝domain/ReimportPlan.kt の該当節）。J は欠落バッジ自体が
        // 未翻訳（モック未裁定＝スキン後回し枠）だが、削除の破壊性はスキンに依存しないため警告は先に入れる。
        val lossWarning = missingContentDeleteWarning(
            missingCount = countMissingContentTargets(targets.map { it.id }, data.reimportPlans),
            bookCount = targets.size,
        )
        var alsoDeleteSource by remember { mutableStateOf(false) }
        NovelReaderAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("選択した${targets.size}冊を本棚から削除しますか？") },
            text = {
                Column {
                    // 欠落本の警告は本文の先頭（後段の一般文より固有かつ重い）。欠落0冊なら描画そのものが無い。
                    MissingContentDeleteWarningText(lossWarning)
                    Text("変換済みの本文データも削除されます。この操作は取り消せません。")
                    DeleteSourcePdfOption(deletableCount, alsoDeleteSource) { alsoDeleteSource = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteBooks(targets, alsoDeleteSource)
                    onExitSelection()
                }) { Text(deleteConfirmLabel(lossWarning != null)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("やめる") }
            },
        )
    }
}

// ============================================================
// g-top（.g-top＝本棚題字＋デッキへ戻る・メニュー。
// 見つける🔍・装いの間は撤去＝2026-07-29 K形正本追従で発見は「さがす」タブ・装いは設定タブへ移管）
// ============================================================
@Composable
private fun GridTopBar(
    count: Int,
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    onToggleDeck: () -> Unit,
    onFabClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.S16, end = Spacing.S16, top = Spacing.S4, bottom = Spacing.S12), // .g-top padding 6px 16px 12px
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4), // .g-top gap 6px
    ) {
        // 本棚題字＋薄く冊数（モック .head .ttl＝h1＋.count・K形の明示冊数）。Row に weight(1f) を持たせ右のアイコン群を押し出す。
        Row(modifier = Modifier.weight(1f)) {
            Text(
                "本棚",
                fontFamily = MinchoFamily,
                fontSize = 24.sp,             // .g-top h1 24px
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.12.em,
                color = InkPortal,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(Spacing.S8))
            // .count 12px soft（題字とベースラインを揃え、控えめに添える）。
            Text(
                "${count}冊",
                fontSize = 12.sp,             // .head .count 12px
                letterSpacing = 0.08.em,
                color = SoftPortal,
                modifier = Modifier.alignByBaseline(),
            )
        }
        // デッキ表示へ戻る（一覧⇄デッキトグル）。開いた本＝没入デッキの語＝MenuBook で「読む面へ戻る」を表す。
        PortalIconButton(onClick = onToggleDeck) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "デッキ表示に切替", tint = InkPortal, modifier = Modifier.size(19.dp))
        }
        // メニュー⋮。テーマ・新着通知は設定タブ（SettingsScreenK）へ移行済みのため撤去（系2）。非設定項目の「PDFを追加」のみ残す。
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            PortalIconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー", tint = InkPortal, modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("PDFを追加") },
                    onClick = { menuOpen = false; onFabClick() },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        }
    }
}

// ============================================================
// 升目セル（.bk＝小さな扉。cover=扉固有 ambient＋象徴文字＋題名／下に進捗・続きあり）
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridDoorCell(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    novelDetail: WorkSummary?,
    // 続きバッジの Web 蔵書側の観測値（Worker が最後に見たサイト総話数。null=なろう本/未チェック）。
    // 判定は D と同じ newEpisodeCountFor（既定値を置かない＝配線忘れをコンパイルエラーへ）。
    webSiteTotal: Int?,
    timePhase: PortalTimePhase,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val pct = ((frac ?: 0f) * 100).roundToInt()
    val status = readingStatusFor(progress, totalChaps)
    val isUnread = status == ReadingStatus.UNREAD
    val newCount = newEpisodeCountFor(novelDetail, totalChaps, webSiteTotal)
    // 扉固有 ambient パレット＝bookId 安定ハッシュ（デッキと同じ関数）で4世界から選ぶ＝扉とセルで世界が一致・並び替え不変。
    val palette = portalDoorPaletteFor(book.id)
    // 〈遊び心〉J1: open＝読了率。0%＝薄暗い升／読むほどグローが強まる（升の大気が明るむ＝グリッドでも直交維持）。
    val open = (frac ?: 0f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            ),
    ) {
        // .cover（3:4・角丸10・扉大気＋象徴文字＋題名を下寄せ）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)          // .bk .cover aspect-ratio 3/4
                .clip(RoundedCornerShape(10.dp)) // border-radius 10px
                .drawCellAmbient(palette, timePhase, open)
                // 選択中は金縁で升を囲う（mock未定義＝J 署名の金で選択を示す最小翻訳）。
                .then(if (selected) Modifier.border(2.dp, GoldPortal, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            // 象徴1文字（.cg＝題名頭文字を右上・極淡・巨大）。
            Text(
                text = book.title.take(1),
                fontFamily = MinchoFamily,
                fontSize = 74.sp,              // .cg 74px
                color = GlyphDarkPortal.copy(alpha = 0.08f), // .cg rgba(233,240,228,.08)＝GlyphDarkPortal と同RGB
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Spacing.S4, end = Spacing.S8),
            )
            // 題名（.cin＝明朝・下寄せ・3行省略）。
            Text(
                text = book.title,
                fontFamily = MinchoFamily,
                fontSize = 12.5.sp,           // .cin 12.5px
                lineHeight = 17.5.sp,         // line-height 1.4
                fontWeight = FontWeight.SemiBold,
                color = InkPortal,            // .cin #F1F4EC
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = Spacing.S12, vertical = Spacing.S8),
            )
            if (selectionMode) SelectionCheckJ(selected = selected, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.S8))
        }

        // 進捗行（.prow＝話数＋バー＋%）。未読は進捗を出さない（嘘の0%を描かない）。
        if (!isUnread && frac != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.S8), // .prow margin-top 9px→S8
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S8),      // .prow gap 8px
            ) {
                Text("${chapNum ?: 1}話", fontSize = 10.5.sp, color = SoftPortal) // .prow span 10.5px --soft
                // .pbar（height2・track --line・fill --gold width%）。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LinePortal),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac.coerceIn(0f, 1f))
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GoldPortal),
                    )
                }
                Text("$pct%", fontSize = 10.5.sp, color = SoftPortal)
            }
        }
        // .upd（森緑ドット＋続きN話・金）。続きありのみ。
        if (newCount != null) {
            Row(
                modifier = Modifier.padding(top = Spacing.S4), // .upd margin-top 6px→S4
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S4), // .upd gap 5px→S4
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(GreenPortal)) // .upd .d 6px 森緑
                Text("続き${newCount}話", fontSize = 10.5.sp, color = GoldPortal)   // .upd 10.5px --gold
            }
        }
    }
}

// ============================================================
// Web由来（未取込）セル（.bk＝cover＋「なろう・未取込」。ncode ハッシュで色相を蔵書行と繋ぐ＝1作1色相の整合）
// 主タップ＝進捗あれば続きから／無ければ目次。長押し＝⋮メニュー（目次/取込/外す）＝mock未定義を J 語彙で最小翻訳。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebGridDoorCell(
    novel: WebNovelEntity,
    lastReadEpisode: Int,
    timePhase: PortalTimePhase,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasProgress = lastReadEpisode > 0
    // 未取込セルは読了率が無い＝扉は既定の見え（open=.62＝発見扉と同じデッキ既定値）で描く。
    val palette = portalDoorPaletteFor(novel.ncode)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = if (hasProgress) onResume else onOpen,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(10.dp))
                    .drawCellAmbient(palette, timePhase, open = 0.62f),
            ) {
                Text(
                    text = novel.title.take(1),
                    fontFamily = MinchoFamily,
                    fontSize = 74.sp,
                    color = GlyphDarkPortal.copy(alpha = 0.08f),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = Spacing.S4, end = Spacing.S8),
                )
                Text(
                    text = novel.title,
                    fontFamily = MinchoFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 17.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkPortal,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = Spacing.S12, vertical = Spacing.S8),
                )
            }
            // .unimp（なろう・未取込＝森緑）。進捗があれば続き話数も併記（D の WebGridBookCard 相当）。
            if (hasProgress) {
                Text(
                    "続き 第${lastReadEpisode}話",
                    fontSize = 10.5.sp, color = GreenPortal,
                    modifier = Modifier.padding(top = Spacing.S8),
                )
            } else {
                Text(
                    "なろう・未取込",
                    fontSize = 10.5.sp, color = GreenPortal, // .unimp 10.5px --green
                    modifier = Modifier.padding(top = Spacing.S8),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // 進捗ありのとき目次導線を⋮へ降格して残す（主タップが続きからに移るため・D/P と同判断）。
            if (hasProgress) {
                DropdownMenuItem(text = { Text("目次を開く") }, onClick = { menuOpen = false; onOpen() })
            }
            DropdownMenuItem(text = { Text("縦書きPDFを取り込む") }, onClick = { menuOpen = false; onImport() })
            DropdownMenuItem(text = { Text("本棚から外す") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}

/** 選択チェック（mock未定義＝J 語彙で最小翻訳: 選択=金の実丸＋暗インクの✓／非選択=温白ヘアラインの環）。 */
@Composable
private fun SelectionCheckJ(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) GoldPortal else Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, if (selected) GoldPortal else InkPortal.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = ResumeInkPortal, modifier = Modifier.size(14.dp))
        }
    }
}

// ============================================================
// 選択モードの下端アクションバー（mock未定義＝D の SelectionActionBar を J 語彙へ翻訳）。
// キャンセル（解除・soft）／件数（ink）／全選択（ink）／削除（温白の実塗り＝一画面唯一の強調・破壊は確認ダイアログ前段）。
// ============================================================
@Composable
private fun GridSelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = LinePortal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PagePortal)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "キャンセル",
                fontSize = 13.sp, color = SoftPortal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = Spacing.S8, vertical = Spacing.S8),
            )
            Text(
                "${count}冊選択中",
                fontSize = 12.5.sp, color = InkPortal,
                letterSpacing = 0.04.em,
                modifier = Modifier.weight(1f).padding(start = Spacing.S8),
            )
            Text(
                "全選択",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkPortal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSelectAll)
                    .padding(horizontal = Spacing.S8, vertical = Spacing.S8),
            )
            Spacer(Modifier.width(Spacing.S8))
            // 削除＝温白の実塗り（.resume と同じ一画面唯一の強調語彙）＋暗インク文字。押下は確認ダイアログを開く非破壊ステップ。
            Text(
                "削除",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ResumeInkPortal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ResumeSurfacePortal)
                    .clickable(enabled = count > 0, onClick = onDelete)
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
            )
        }
    }
}

/**
 * 升目セルの扉 ambient（.cover.amb-*2/-maou/-en/-kusa）を描く。扉間の色相差は palette.baseStops が本体
 *   （デッキ面と同じ「森世界の中の変化」）。デッキの全面扉より小さい升なので mock のセル専用 radial 位置/α を写す:
 *   base = palette 起点→2番目のストップ（mock セルは 2 ストップの 160deg リニア／yaku は top→mid・他は top→bot）。
 *   glow = 扉固有署名色（金/宵紫/森緑）＝palette.glow。中心/基底αは cellAmbientFor（mock 各 amb セルの radial 値）。
 * 〈遊び心〉直交2軸: J1 open でグロー α に .20*open を加算（升が読進で明るむ）／J3 timePhase でデッキと整合する
 *   温度差（warm デルタ）＋夜の底沈み（floorDarken）を効かせる。既定=夕は delta 0・darken 0＝mock 値を厳密再現。
 */
private fun Modifier.drawCellAmbient(
    palette: PortalDoorPalette,
    phase: PortalTimePhase,
    open: Float,
): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    val cell = cellAmbientFor(palette)
    val phaseParams = portalAmbientParamsFor(phase)
    // J3 整合: 既定=夕を基準に、相ごとの warm デルタでセルの明暗をデッキと同方向へ寄せる（夕は delta 0＝mock 厳密）。
    val eveningWarm = portalAmbientParamsFor(PortalTimePhase.EVENING).warm
    val warmDelta = phaseParams.warm - eveningWarm

    // base: mock セルの 160deg 2ストップ リニア（起点→2番目のストップ）。夜は末端を外殻へ寄せて沈める（floorDarken）。
    val baseTop = palette.baseStops.first().second
    val baseEnd = lerp(palette.baseStops[1].second, PagePortal, phaseParams.floorDarken)
    drawRect(
        Brush.linearGradient(
            colors = listOf(baseTop, baseEnd),
            start = Offset(w * 0.15f, 0f), // 160deg 相当（左上→右下やや縦）
            end = Offset(w * 0.5f, h),
        )
    )
    // 上部グロー: 扉固有署名色。α=セル基底＋J1 読進＋J3 相の温度デルタ（負にもなり得るため coerceIn で床/天井）。
    val glowAlpha = (cell.glowBaseAlpha + 0.20f * open + warmDelta).coerceIn(0f, 1f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(palette.glow.copy(alpha = glowAlpha), Color.Transparent),
            center = Offset(w * cell.glowCenterX, h * cell.glowCenterY),
            radius = w * 1.2f, // mock セル radial は 120% 90%＝幅比 1.2
        )
    )
    // 底の苔（yaku セルのみ mock が floor radial を持つ。他世界は floorAlpha=0 で描かない）。夜の floorAlpha 増で沈む。
    if (cell.floorBaseAlpha > 0f) {
        val floorA = (cell.floorBaseAlpha * (phaseParams.floorAlpha / eveningFloorAlpha)).coerceIn(0f, 1f)
        drawRect(
            Brush.radialGradient(
                colors = listOf(palette.floor.copy(alpha = floorA), Color.Transparent),
                center = Offset(w * 0.6f, h * 1.2f), // amb-yaku2 floor radial at 60% 120%
                radius = w * 1.2f,
            )
        )
    }
}

// 夕（既定）の底αの基準値（mock amb-yaku --floor .9）。セル floor の相スケール分母＝夕基準で厳密再現するため。
private const val eveningFloorAlpha = 0.9f

/**
 * 升目セル専用の ambient パラメータ（mock の .cover.amb-yaku2/.amb-maou/.amb-en/.amb-kusa の radial 実値）。
 *   glowCenterX/Y = 各セル radial の `at X% Y%`／glowBaseAlpha = 既定=夕でのグロー基底α（J1/J3 前）／
 *   floorBaseAlpha = セル底の苔α（yaku のみ>0・mock rgba(20,46,30,.9)）。
 * デッキの全面扉（PortalDoorPalette.glowCenter*）とは位置/αが別＝升は狭く mock が別値を与えるため（近似せず両方持つ）。
 */
private data class CellAmbient(
    val glowCenterX: Float,
    val glowCenterY: Float,
    val glowBaseAlpha: Float,
    val floorBaseAlpha: Float,
)

private fun cellAmbientFor(palette: PortalDoorPalette): CellAmbient = when (palette.name) {
    // .cover.amb-yaku2: 金 radial at 32% 12% .28 ＋ floor rgba(20,46,30,.9) at 60% 120%。
    "yaku" -> CellAmbient(glowCenterX = 0.32f, glowCenterY = 0.12f, glowBaseAlpha = 0.28f, floorBaseAlpha = 0.9f)
    // .amb-maou: 宵紫 radial at 74% 12% .40。
    "maou" -> CellAmbient(glowCenterX = 0.74f, glowCenterY = 0.12f, glowBaseAlpha = 0.40f, floorBaseAlpha = 0f)
    // .amb-en: 金 radial at 28% 12% .30。
    "en" -> CellAmbient(glowCenterX = 0.28f, glowCenterY = 0.12f, glowBaseAlpha = 0.30f, floorBaseAlpha = 0f)
    // .amb-kusa: 森緑 radial at 30% 14% .26。
    "kusa" -> CellAmbient(glowCenterX = 0.30f, glowCenterY = 0.14f, glowBaseAlpha = 0.26f, floorBaseAlpha = 0f)
    // 発見扉パレットは升に出ない（portalDoorPaletteFor は4世界のみ返す）。安全側のデッキ既定を流用。
    else -> CellAmbient(glowCenterX = palette.glowCenterX, glowCenterY = palette.glowCenterY, glowBaseAlpha = 0.28f, floorBaseAlpha = 0f)
}
