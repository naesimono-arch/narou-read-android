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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.DeleteSourcePdfOption
import com.novelreader.ui.MissingContentBadge
import com.novelreader.ui.DeleteTargetTitlesText
import com.novelreader.ui.MissingContentDeleteWarningText
import com.novelreader.ui.NewChaptersBadge
import com.novelreader.ui.ProcessingBanner
import com.novelreader.ui.ReimportScanBanner
import com.novelreader.ui.ReimportSweepBanner
import com.novelreader.ui.emptyStatusSemantics
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.components.ShioriCover
import com.novelreader.ui.components.horizontalScrollEdgeFade
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
import com.novelreader.ui.theme.NovelReaderAlertDialog
import com.novelreader.ui.theme.Spacing
import com.novelreader.domain.ReadingStatus
import com.novelreader.domain.ScanProgress
import com.novelreader.domain.ShelfItem
import com.novelreader.domain.chapterNumberOf
import com.novelreader.domain.countMissingContentTargets
import com.novelreader.domain.deleteConfirmBody
import com.novelreader.domain.deleteConfirmLabel
import com.novelreader.domain.filterShelfByStatus
import com.novelreader.domain.mergeShelfItems
import com.novelreader.domain.missingContentDeleteWarning
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
//     （正本モック bookshelf-list-K.html。Web未取込行は field 沈め＋青磁の四隅マーカー＝.web。
//     四辺の破線から角だけの徴へ差し替えたのは 2026-08-26 裁定＝narouCornerMarks の KDoc に理由を置く）。
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
    // 栞アニメ高負荷（ADR 0023 の明快K展開・2026-08-06 裁定）。蔵書グリッドの栞書影（tip 0〜8）だけが読む。
    // 既定 false＝既存呼び出し（K の golden 含む）は完全静止の従来描画のまま。
    highLoadShioriK: Boolean = false,
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

    // 空棚（蔵書0）か。**FAB の出没（2026-08-20 裁定②）と空状態の分岐が同じ1つの式を読む**ように畳む。
    // ⚠️ selectedStatus == null を式に含めるのが肝: 「この分類の本はありません」（状態フィルタで0件・蔵書はある）
    //    は空棚ではなく CTA も持たないため、あちらで FAB を隠すと PDF 追加の導線が全部消える。
    //    条件を2箇所に書き分けるとこの取り違えが静かに入り込むので、式は1つしか置かない。
    val isEmptyShelf = selectedStatus == null && shelfItems.isEmpty() && !isLoading && !isProcessing

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // 向き判定は既存流儀の LocalConfiguration.orientation（回転で Configuration が変われば自動で再コンポーズ）。
    // 横向きだけ構造が変わる（ADR 0034）＝縦向きの版面はこの val の false 枝で従来のまま通る。
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Rail 化の起動条件（＝タブ選択の結線が来ているか）。詳細は [LocalKTabSelect] の KDoc。
    val railSelect = LocalKTabSelect.current
    val railActive = isLandscape && railSelect != null
    // 一覧下端の余白（2026-08-25 見直し）。縦向き＝[Insets.ScrollBottomForFab] 96dp のまま
    //（拡張FAB が本文の上に浮くので回避帯が要る＝内訳は同トークンの KDoc）。
    // 横向き（Rail 化）＝**回避すべき相手が本文の上に居なくなる**（FAB は Rail 上端＝本文の外）ので
    // リズムの下余白 [Spacing.S24] へ戻す。96dp のまま残すと「1枚も入らない画面の末尾に 96dp の空白」
    // という体感悪化（ADR 0034 背景の指摘そのもの）を、Rail で稼いだ縦から差し引くことになる。
    val shelfScrollBottom = if (railActive) Spacing.S24 else Insets.ScrollBottomForFab

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Rail は本文の**左隣**に立つので Row で受ける（ADR 0034）。縦向き・未結線では子が本文1つだけの Row
        // ＝レイアウト結果は従来の Column 単独と同値（本文を二重に書き分けないためにこの形にしている）。
        Row(modifier = Modifier.fillMaxSize()) {
            if (railActive) {
                KNavigationRail(
                    current = KTab.BOOKSHELF,
                    onSelect = railSelect!!,
                    // T1 横一列化: 題字「本棚」＋冊数の移設先＝Rail のヘッダ（画面名を消さずに縦の固定分から外す）。
                    header = { KRailHeader(title = "本棚", meta = "${libraryCount}冊") },
                    // FAB は Rail 上端（裁定③）。出没条件は本文側の拡張FABと同一＝押す対象が二重に出ない。
                    // 淡入淡出も縦向きの拡張FABと同一にする（2026-09-03 小口裁定①＝棚 FAB は全数対象）。
                    // スロットへ null を渡す形をやめて常に非 null にするのは、退場アニメを再生する主体が
                    // 消えてしまうと淡出が一瞬も描かれないため。隠れている間の AnimatedVisibility は
                    // 高さ0＝Rail の版面は従来（null を渡していたとき）と同一。
                    fab = {
                        AnimatedVisibility(
                            visible = !selectionMode && !isEmptyShelf,
                            enter = fadeIn(tween(MotionDurationReveal)),
                            exit = fadeOut(tween(MotionDurationDismiss)),
                        ) {
                            KRailFab(onClick = onFabClick)
                        }
                    },
                )
            }
        Column(
            // statusBars のみ避ける（ボトムナビは NavHost の外＝下端 nav インセットは KBottomNav が持つ・二重加算しない）。
            // 横向きは帯が消える＝下端／本文側の nav インセットを引き受ける相手が居なくなるので、本文が自分で持つ
            // （左端ぶんは Rail が持っているので End+Bottom だけを取る＝二重加算しない）。
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(
                    if (railActive) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.End + WindowInsetsSides.Bottom),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            // ヘッダ（.head）: 「本棚」＋薄く冊数＋右端は表示切替のみ。
            // 横向き（T1）では出さない＝題字と冊数は Rail ヘッダへ移り、表示切替は状態チップ行の右端へ寄る
            // （縦に積んだ2行〈ヘッダ68dp＋チップ46dp〉を1行 52dp へ畳むのが T1 の要件）。
            if (!railActive) {
                KHeader(
                    count = libraryCount,
                    isGridView = isGridView,
                    onToggleView = gridToggle::toggle,
                )
            }

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
            // 横向き（T1）ではこの行が唯一の操作行になる＝右端に表示切替を同居させる（モック .headt）。
            KStatusChipRow(
                selectedStatus = selectedStatus,
                onSelect = onSelectStatus,
                statusCounts = statusCounts,
                trailing = if (railActive) {
                    { KViewToggleButton(isGridView = isGridView, onToggleView = gridToggle::toggle) }
                } else {
                    null
                },
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
                // 条件は FAB の出没と共有する [isEmptyShelf] ただ1つ（上の分岐で selectedStatus != null は既に消えている）。
                isEmptyShelf -> {
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
                    // 構成は縦横同値＝裁定の変数は列数のみ。判定（isLandscape）は画面冒頭で1度だけ取る。
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isLandscape) 5 else 2),
                        state = gridState,
                        // Column 内で残り空間を占める（weight＝ヘッダ/チップの下の全域。fillMaxSize は縦過剰確保になる）。
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        // 上端はヘッダ（チップ行）が持つ。下端は FAB と最終行の重なり回避ぶん（D と同じ Insets 値）。
                        contentPadding = PaddingValues(
                            start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = shelfScrollBottom,
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
                                    highLoadAnim = highLoadShioriK,
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
                            start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = shelfScrollBottom,
                        ),
                    ) {
                        // contentType=型: 蔵書/Web はカード構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                        items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
                            when (item) {
                                is ShelfItem.Book -> KListBookCard(
                                    book = item.book,
                                    progress = progressMap[item.book.id],
                                    novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                    webSiteTotal = webNewEpisodeTotals[item.book.id],
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
        } // Column（本文）
        } // Row（Rail ＋ 本文）

        // 拡張FAB「＋ PDFを追加」（.fab 藍・ラベル付き）。選択モード中は下端の選択バーへ場を譲り隠す（D の Scaffold と同挙動）。
        // 横向き（Rail 化）では出さない＝FAB は Rail 上端の円形へ移る（裁定③。拡張ラベルを失う代償は受け入れ済み）。
        // 右下据え置きを採らない理由＝横向きでは最終列の書影に恒久的に重なるため。
        // 空棚（蔵書0）でも隠す（2026-08-20 ユーザー裁定②）＝押す対象を空棚CTA〈PDFを追加〉一本へ寄せる。
        // 同じ操作が拡張FABと CTA で二重に出ており、fontScale 2.0 では FAB が CTA へ被っていた（実機 PGEM10）。
        // ⚠️ [isEmptyShelf] は「この分類の本はありません」を含まない＝あちらは CTA が無いので FAB を残す。
        // 淡入淡出（2026-09-03 小口裁定①）: 出没条件（選択モード・空棚）は長押しや 0冊目の取込完了で
        // 瞬間的に跳ねるため、尺ゼロだと FAB がパチンと現れ／消えて何が起きたか読めない。位置ずれを
        // 伴わない純フェードにするのは、右下固定の FAB に「動いてくる先」が無く、滑り込ませると嘘の
        // 空間語彙を足すため。尺は同ファイルのバナー入退場と同じスロット（enter>exit＝Design/08-C）。
        // 横向き（railActive）だけは AnimatedVisibility の外＝素の if に残す。Rail への移設は構成変更（回転）で
        // 起き、面ごと作り直されるので出没のフェードとは別事象（包むと回転のたびに薄く光る）。
        // 横向きの円形 FAB は Rail 側で同じ尺の淡入淡出を持つ（[KNavigationRail] へ渡す fab スロット）。
        if (!railActive) {
            AnimatedVisibility(
                visible = !selectionMode && !isEmptyShelf,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.S16, bottom = Spacing.S16),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("PDFを追加") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onFabClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // 読み上げ名（2026-08-07 実機 TalkBack で無名と判明）: M3 の
                    // ExtendedFloatingActionButton は text スロットを clearAndSetSemantics{} で包む
                    // （展開/縮退アニメで読み上げが揺れないようにするため）。結果、ラベルが見えていても
                    // ボタンの意味ノードは Role=Button だけで text も contentDescription も空になる
                    // ＝名前は呼び出し側が与えるしかない（unmerged ツリーで確認＝BookshelfKFabTest）。
                    // 名前は見える文字と同一にする（label-in-name＝音声操作で「PDFを追加」と言える）。
                    // 用語: これは端末内PDFの取り込みで、発見（A「見つける」）でも検索（B「探す」）でも
                    // ない＝docs/patterns/discovery-terminology.md の2語を借りない。
                    // ⚠️ 配置（align/padding）は AnimatedVisibility 側へ移した＝FAB 自身は器の中身になった。
                    modifier = Modifier.semantics { contentDescription = "PDFを追加" },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.S16),
        )
    }

    // 複数選択削除の確認（D と同語＝内訳ごとに正しい不可逆性を本文で明示・取込元PDF削除オプションは共通 DeleteSourcePdfOption）。
    // 構造は Material AlertDialog をそのまま使う（各スキンのダイアログ流儀と同じ）。
    // ⚠️ 面の色は「モックに無いから OS 既定」ではない——D 系モック（bookshelf-multiselect-D／reimport-sweep-D）が
    // `.dlg{background:var(--base)}`＝素地・分離はスクリムと影、と規定しており、それを surfaceContainerHigh へ
    // 移植してある（SkinContainerTiers.kt）。旧コメントの「モックに意匠が無い」という前提が誤りで、
    // M3 baseline の紫面が仕様として通っていた（2026-07-30 実機で発覚・是正）。
    if (showDeleteConfirm) {
        val bookTargets = books.filter { it.id in selectedIds }
        // Web由来（未取込）カードも選択削除の対象（系3）。選択キー "web:<ncode>" を ncode へ分解し webNovels と突合する。
        val webNcodes = webNcodesInSelection(selectedIds).toSet()
        val webTargets = webNovels.filter { it.ncode in webNcodes }
        val deletableCount = bookTargets.count { it.sourceUri != null }
        val total = bookTargets.size + webTargets.size
        // 欠落本を含む削除は「復元の最後の機会」を消す（機序＝domain/ReimportPlan.kt の該当節）。冊数は蔵書分だけを
        // 数える（title の「件」は Web カード込みの中立表記だが、復元手段を失うのは books 行を持つ蔵書のみ）。
        val lossWarning = missingContentDeleteWarning(
            missingCount = countMissingContentTargets(bookTargets.map { it.id }, data.reimportPlans),
            bookCount = bookTargets.size,
        )
        var alsoDeleteSource by remember { mutableStateOf(false) }
        NovelReaderAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            // 蔵書とWebが混じり得るため中立の「件」で数える。
            title = { Text("選択した${total}件を本棚から削除しますか？") },
            text = {
                Column {
                    // 欠落本の警告は本文の先頭（後段の一般文より固有かつ重い）。欠落0冊なら描画そのものが無い。
                    MissingContentDeleteWarningText(lossWarning)
                    // 削除対象の題名列挙（監査 A11・D と同じ共有部品＝先頭5件＋ほかN件。理由は部品側コメント参照）。
                    DeleteTargetTitlesText(bookTargets.map { it.title } + webTargets.map { it.title })
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
                }) { Text(deleteConfirmLabel(lossWarning != null)) }
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
        KViewToggleButton(isGridView = isGridView, onToggleView = onToggleView)
    }
}

/**
 * グリッド⇄リスト表示切替（.view＝唯一のヘッダアクション）。図柄は D の本棚と同じ規則で入替。
 * 縦向きはヘッダ右端・横向き（T1）は状態チップ行の右端＝**置き場所だけが変わる**ので実装を1つに保つ。
 */
@Composable
private fun KViewToggleButton(isGridView: Boolean, onToggleView: () -> Unit) {
    IconButton(onClick = onToggleView) {
        Icon(
            imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
            contentDescription = if (isGridView) "リスト表示" else "グリッド表示",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Rail 上端の FAB（裁定③）。拡張FAB は Rail 幅 80dp に入らないので**円形**になる
 * ＝ラベル「PDFを追加」を字として持てないぶん、読み上げ名を明示で与える（拡張FAB 側と同じ
 * label-in-name の理由＝[BookshelfK] の ExtendedFloatingActionButton のコメント）。
 * 寸法はモック `.rfab` 52x52 / r16（＝角丸は M3 の large と同義でなく正本値なので shape で明示する）。
 */
@Composable
private fun KRailFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .size(52.dp)
            .semantics { contentDescription = "PDFを追加" },
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
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
    // 行の右端に同居させる操作（横向き T1 の表示切替）。null＝縦向き＝従来どおりチップだけの行。
    trailing: @Composable (() -> Unit)? = null,
) {
    if (trailing == null) {
        KStatusChips(selectedStatus, onSelect, statusCounts, Modifier.fillMaxWidth())
        return
    }
    // T1 の操作行（モック .headt）: 左にチップ（溢れは既存の横スクロールが吸う）・右端に表示切替。
    // チップ側を weight(1f) で残り幅にするので、切替ボタンは幅を取られず必ず右端に居る。
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        KStatusChips(selectedStatus, onSelect, statusCounts, Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(Spacing.S8))
    }
}

/** チップの並び本体（横スクロール器）。[KStatusChipRow] が縦横で置き方だけを変えて使う。 */
@Composable
private fun KStatusChips(
    selectedStatus: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    statusCounts: Map<ReadingStatus, Int>,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            // 端フェード（正本 .chipsrow .fade・2026-08-20 裁定①）。horizontalScroll の**直前**に置く＝
            // この修飾子のノード寸法が可視域そのものになり、レイアウトノードは1つも増えない（版面不変）。
            // bottomInset に S12 を渡すのは、下パディングがこの行の**内側**（スクロール器の中）にあり
            // ノード高へ含まれるため＝チップの帯だけを溶かし下の余白には掛けない（正本 bottom:12px）。
            .horizontalScrollEdgeFade(
                scrollState = scrollState,
                baseColor = MaterialTheme.colorScheme.background,
                bottomInset = Spacing.S12,
            )
            .horizontalScroll(scrollState)
            .padding(start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        // 「すべて」＝選択なし（null）。棚が非空のときだけ出る行なので常に押せる。
        KStatusChip(label = "すべて", selected = selectedStatus == null) { onSelect(null) }
        // よみかけ／未読／読了。0件でも淡色化しない（2026-08-07 ユーザー裁定・D と同規則＝理由は
        // BookshelfScreen の StatusChipRow のコメント。K も「選択中の淡色化で選択が消える」同じ真因を持つ）。
        KStatusChip(
            label = "よみかけ",
            selected = selectedStatus == ReadingStatus.READING,
            isEmpty = (statusCounts[ReadingStatus.READING] ?: 0) == 0,
        ) { onSelect(ReadingStatus.READING) }
        KStatusChip(
            label = "未読",
            selected = selectedStatus == ReadingStatus.UNREAD,
            isEmpty = (statusCounts[ReadingStatus.UNREAD] ?: 0) == 0,
        ) { onSelect(ReadingStatus.UNREAD) }
        KStatusChip(
            label = "読了",
            selected = selectedStatus == ReadingStatus.FINISHED,
            isEmpty = (statusCounts[ReadingStatus.FINISHED] ?: 0) == 0,
        ) { onSelect(ReadingStatus.FINISHED) }
    }
}

/** フィルタチップ1個（.chip＝角丸ピル）。選択＝藍塗り＋白字／非選択＝ヘアライン枠＋補助色。 */
@Composable
private fun KStatusChip(
    label: String,
    selected: Boolean,
    // 該当0件か。**見た目には効かせない**（0件の淡色化・押下不能は 2026-08-07 裁定で廃止）。
    // TalkBack へ「該当なし」を残すためだけに受ける＝文言は D と共有（emptyStatusSemantics）。
    isEmpty: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = CircleShape // border-radius:999px＝完全な丸ピル
    val base = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(shape)
            .then(base)
            .clickable(onClick = onClick)
            .then(emptyStatusSemantics(selected = selected, isEmpty = isEmpty))
            // ⚠️ **見た目 34dp・当たり判定 48dp は意図的な食い違い**＝この padding を 48dp 目当てに
            // 膨らませないこと（膨らませると上の lineHeight 修正が無意味になり、ピルが正本より太る）。
            // 当たり判定は clickable が 48dp を下限として自動確保する＝2026-08-26 に emulator-5558 で実測:
            // ①この画面の clickable 14件すべてが 48dp 以上 ②ピル上端(242px)の外・当たり判定上端(237px)の
            // 内側 y=239 を叩くと選択が切り替わる。ピルが 34dp へ縮んでも下限側は 48dp のまま効く。
            .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
    ) {
        Text(
            label,
            fontSize = FontChipLarge,
            // なぜ lineHeight を明示するか: 既定の LocalTextStyle（＝Typography.bodyLarge）が
            // lineHeight=28.sp を持つため、fontSize だけ 11.5sp へ落としても**行箱は 28sp のまま残る**。
            // ピルは行箱の外周をなぞる＝正本 .chip（font 12.5px・line-height:normal で行箱 20px・
            // 器の総高 34px）に対し、実測で 44.19dp まで肥大していた（+30%／2026-08-26 計測）。
            // em で持つ理由＝正本の比（20/12.5＝1.6）を、フォント token や fontScale が動いても保つため。
            lineHeight = 1.6.em,
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
    // 栞アニメ高負荷（2026-08-06 裁定）: ShioriCover の高負荷経路へ素通し（対象判定は ShioriCover 側が持つ）。
    highLoadAnim: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = readingStatusFor(progress, totalChaps)
    val chapNum = chapterNumberOf(progress?.lastReadFilename)

    Column(
        modifier = modifier
            // 1冊=1トラバーサル単位に束ねる（D カードと同流儀）。
            .semantics(mergeDescendants = true) {
                // 選択モード中の選択状態宣言（D の GridBookCard と同文・監査 A11。理由はそちらのコメント参照）。
                if (selectionMode) {
                    this.selected = selected
                    this.stateDescription = if (selected) "選択中" else "未選択"
                }
            }
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
                highLoadAnim = highLoadAnim,
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
        // キャプション行（正本 bookshelf-K.html `.cap`）＝題名（左）＋可視⋮（右端）。
        // 状態行（`.st`）はこの行に入れず、下に**カード全幅**で置く（モックでも .cap と .st は兄弟要素）。
        //
        // 真因（2026-07-30 実機観察の是正）: 旧実装は .st を .cap の左カラム（weight(1f)）へ入れ子にしていた。
        // そのぶん状態行の使える幅がカード幅 −⋮のタップ面 32dp になり、2列グリッドでは
        // 「本文なし・タップで再取込」がちょうど溢れて「…再／取込」と割れていた。⋮ が消える選択モードでだけ
        // 1行に収まっていたことが、足りない幅の出所が⋮であることの証拠。幅の決め打ちで広げるのではなく、
        // モックどおりの入れ子（.st は .cap の外）へ戻して状態行にカード全幅を与える。
        Row(
            // ⋮ を隠す選択モードでも行高が変わらないようにする＝モード切替でカード高（グリッドの行高）が跳ねない。
            // 値は⋮のタップ面と同一定数を使う＝ここで新しい寸法を決めていない。
            modifier = Modifier.heightIn(min = KCardMenuTapSize),
        ) {
            // 題名（.t＝明朝・1行clamp）。表紙内(ShioriCover)の縦組み題字とは別に、下段へ横組みで添える（モック .cvt＋.t の二重表示）。
            // 2列改A で書影を大きく取るぶんキャプションは1行へ圧縮（2026-07-24 裁定）。
            Text(
                text = book.title,
                fontFamily = MinchoFamily,
                fontSize = FontSubTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
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
        Spacer(Modifier.height(Spacing.S4))
        if (missingLabel != null) {
            // 欠落本の状態行（案B・.st）: 進捗の徴を欠落文言に置き換える（本文が無い本に話数を出すと嘘になる）。
            Text(
                missingLabel,
                fontSize = FontMicroLabel,
                lineHeight = KGridStatusLineHeight,
                color = LocalShelfColors.current.infoText,
            )
        } else {
            KBookStatusLine(status = status, chapNum = chapNum, totalChaps = totalChaps, lineHeight = KGridStatusLineHeight)
        }
    }
}

// ============================================================
// 行送り（lineHeight）の較正値。**すべて正本モックの該当セレクタ1つと1対1で対応させる**。
// なぜ明示が要るか: 既定の LocalTextStyle（＝Typography.bodyLarge）は lineHeight=28.sp を持ち、
// fontSize だけ小さくしても**行箱は 28sp のまま残る**（Compose の lineHeight は下限＝
// docs/knowledge/compose-lineheight-is-a-floor-not-css-line-height.md）。器（ピル・バッジ・行）が
// 行箱の外周をなぞる要素では、その 28sp がそのまま器の肥大になる。
// em で持つ理由＝正本の比を、フォント token や fontScale が動いても保つため。
// ============================================================

/**
 * グリッド書籍カードの状態行。正本 skins/bookshelf-K.html `.st`（font-size:10.5px・line-height 未指定
 * ＝normal）。ゴシックの normal は実測 1.6。10.5sp では実測 17.0dp（従来の継承 28.0dp から −11.0dp）。
 */
private val KGridStatusLineHeight = 1.6.em

/**
 * 目録（リスト）行の題字。正本 skins/bookshelf-list-K.html `.lc .t`（font-size:16px・line-height:1.5）。
 * FontCardTitle 16.5sp では実測 25.0dp（自然行高 24.5dp より上＝下限として効く／従来 28.0dp）。
 */
private val KListTitleLineHeight = 1.5.em

/**
 * 目録（リスト）行のメタ1行。正本 skins/bookshelf-list-K.html `.lc .m`（font-size:13px・line-height:1.4）。
 * ⚠️ FontMicroLabel 10.5sp では 1.4×10.5＝14.7sp が**自然行高 15.5dp を下回るためクランプされる**
 * ＝指定した比そのものは効かず 15.5dp に着地する（Compose の lineHeight は下限）。それでもここに置くのは、
 * この一行の目的が「bodyLarge の 28sp 継承を切って自然行高まで落とす」ことであり、比は正本の由来を
 * 残すための記録だから。正本どおりの 18.2px 相当を厳密に出すには行送りと箱高(dp)を対で置く必要がある
 * （＝上記 knowledge の対処。行構造ごと作り直すことになるので本便では踏み込まない）。
 */
private val KListMetaLineHeight = 1.4.em

/** 蔵書カードの状態行（.st）。読了＝「読了」／未読＝藍ドット＋「未読」／よみかけ＝「第N/M話」。 */
@Composable
private fun KBookStatusLine(
    status: ReadingStatus,
    chapNum: Int?,
    totalChaps: Int,
    // グリッド（.st）と目録（.lc .m）で**正本セレクタが別＝比も別**なので、行送りは呼び出し側が渡す。
    lineHeight: TextUnit,
) {
    when (status) {
        ReadingStatus.FINISHED -> Text(
            "読了",
            fontSize = FontMicroLabel,
            lineHeight = lineHeight,
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
            Text("未読", fontSize = FontMicroLabel, lineHeight = lineHeight, color = LocalShelfColors.current.infoText)
        }
        // よみかけ＝読んだ章/全章（進捗バーでなく到達話数を数字で示すモック流儀）。chapNum は READING では非 null。
        ReadingStatus.READING -> Text(
            "第${chapNum ?: 1}/${totalChaps}話",
            fontSize = FontMicroLabel,
            lineHeight = lineHeight,
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
    // 未取込の署名色＝濃青磁（正本 --seiji-ink #50685C／トークン UnreadSeiji）。2026-08-26 ユーザー裁定で
    // 「実装（淡 secondary #9CB3A8）を正本へ寄せる」と確定。LocalShelfColors 経由で引くのは、ライト/セピアは
    // UnreadSeiji・ダークは暗面で合格済みの SecondaryDark を返す既存の役割配線に乗せるため（K は SkinD へ全委譲）。
    // 新トークンは作らない＝2026-08-21 のキャプション色是正と同じく「既存トークンの適用漏れを埋める」だけ。
    // DrawScope 内では @Composable の MaterialTheme を読めないため事前に捕捉する。
    val seijiInk = LocalShelfColors.current.semanticMicroText
    // 沈めた紙（正本 --field）。onSurface を NarouSinkAlpha だけ紙へ焼き込む＝ライトは #FBFAF8 → #F4F3F2。
    val shiori = LocalShioriColors.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val sunkenShiori = remember(shiori, onSurface) {
        shiori.copy(paper = onSurface.copy(alpha = NarouSinkAlpha).compositeOver(shiori.paper))
    }

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                // 選択モード中の選択状態宣言（D の GridBookCard と同文・監査 A11。理由はそちらのコメント参照）。
                if (selectionMode) {
                    this.selected = selected
                    this.stateDescription = if (selected) "選択中" else "未選択"
                }
            }
            .combinedClickable(
                // 選択モード中はタップ/長押しで選択トグル。通常時は進捗あれば主タップ=続きから／未読は目次、長押しで選択モードへ（系3）。
                // 旧・長押し＝⋮は、キャプション行右端の可視⋮（KCardMenuButton）が代替導線になったため選択入口へ譲る。
                onClick = { if (selectionMode) onToggleSelect() else if (hasProgress) onResume() else onOpen() },
                onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 未取込＝D改（2026-07-24 ユーザー裁定）: 影は付けない＝「まだ実体がない一冊」を浮かせない
            // （実体のある蔵書カードだけ手順2の影を持つ）。徴は下の四隅マーカーが担う。
            //
            // 紙地一段沈め＝取込前の「仮置き」感。正本 `.cv.narou` は background を --field へ差し替える
            // ＝沈めは**地**であって被膜ではない（栞棒と縦題字はくすまない）。旧実装は ShioriCover の**上**へ
            // veil を重ねており、一覧（行の background＝内容の下）と重ね順が食い違っていた——2面とも
            // 「地」へ揃えた（揃える先を地にしたのは、正本2枚がどちらも background 差し替えで、かつ
            // 被膜側へ揃えると一覧のメタ文字まで曇って下の AA 是正と正面衝突するため）。
            // ShioriCover は紙を LocalShioriColors.paper から読むので、このカードの範囲だけ沈めた紙を
            // 供給すれば ink（縦題字）と accent（栞棒・先端）は素のまま残る＝正本と同じ効き方になる。
            CompositionLocalProvider(LocalShioriColors provides sunkenShiori) {
                ShioriCover(
                    title = novel.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
            if (selected) {
                // 選択中は書影へ藍の細縁取り＋淡い藍かぶせ（KGridBookCard と同じ .bk.sel）。
                // ⚠️ ここでは四隅マーカーを描かない: 2dp の藍縁取りは器の外周 0〜2dp を塗り、マーカーの線
                // （外周 0〜1dp）と同じ画素を占める＝描いても埋もれて二重輪郭にしかならない。未取込の徴は
                // 沈めた紙地とキャプション「なろう・未取込」が引き続き担うので、選択中に失われる情報は無い。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            } else {
                // 四隅マーカー（正本 `.cv.narou .mk`）。角丸3dp＝書影 clip と整合。
                // 描画本体は共有 narouCornerMarks（一覧行と1定義を共用＝署名の脱落を構造的に防ぐ）。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .narouCornerMarks(
                            color = seijiInk,
                            cornerRadius = 3.dp,
                            armLength = NarouMarkArmGrid,
                        ),
                )
            }
            // 選択モード中は書影右上に選択マーク（蔵書カードと共有の KSelectionCheck）。
            // ⚠️ 右上マーカーと同じ隅を使う。未選択のあいだは画素が重ならない（マーク＝22dp を S8 で寄せる
            // ＝上端/右端から 8dp・マーカーの腕は端から 12dp／線 1dp ＝実測クリアランス 7dp）が、選択された
            // 瞬間は上の藍縁取りがマーカーを完全に覆う——そこを上の if/else で描き分けて重なりを解消している。
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
                        lineHeight = KGridStatusLineHeight,
                        color = LocalShelfColors.current.infoText,
                    )
                } else {
                    Text(
                        "なろう・未取込",
                        fontSize = FontMicroLabel,
                        lineHeight = KGridStatusLineHeight,
                        fontWeight = FontWeight.Medium,
                        // 状態を名指す＝意味を運ぶ文字なので AA(4.5:1) が要る。青磁 secondary #9CB3A8 は
                        // 素地 2.14:1 で未達＝ADR 0014-D の濃青磁へ寄せる（正本 skins/bookshelf-K.html の
                        // `.st.narou` も --seiji-ink。ここが「既知の乖離（意匠裁定待ち）」として残っていた）。
                        color = LocalShelfColors.current.semanticMicroText,
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
    // 続きバッジの Web 蔵書側の観測値（Worker が最後に見たサイト総話数。null=なろう本/未チェック）。
    // 判定は D と同じ newEpisodeCountFor（既定値を置かない＝配線忘れをコンパイルエラーへ）。
    webSiteTotal: Int?,
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
    val newCount = newEpisodeCountFor(novelDetail, totalChaps, webSiteTotal)
    // 作品識別色（左端の色帯）。書架の栞と同じ title 由来 accent で「1冊=1色相」を保つ（ListBookCard と同一導出＝再実装なし）。
    val accentLightness = LocalShioriColors.current.accentLightness
    val barColor = remember(book.title, accentLightness) { shioriAccentFor(shioriHue(book.title), accentLightness) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                // 1冊=1トラバーサル単位に束ねる（行末⋮は別フォーカスとして残る＝D の目録行と同流儀）。
                .semantics(mergeDescendants = true) {
                    // 選択モード中の選択状態宣言（D の GridBookCard と同文・監査 A11。理由はそちらのコメント参照）。
                    if (selectionMode) {
                        this.selected = selected
                        this.stateDescription = if (selected) "選択中" else "未選択"
                    }
                }
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
                    lineHeight = KListTitleLineHeight,
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
                            lineHeight = KListMetaLineHeight,
                            color = LocalShelfColors.current.infoText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 著者が長くても状態・バッジを押し出さない（D の目録行と同じ収縮）。
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("・", fontSize = FontMicroLabel, lineHeight = KListMetaLineHeight, color = LocalShelfColors.current.infoText)
                    }
                    // 状態部＝グリッドの KBookStatusLine を再利用（読了/未読(藍ドット)/第N/M話）＝徴を1箇所に集約。
                    // 本文欠落（案B）はグリッドと同じ置き換え（進捗の徴を出さず欠落文言のみ）。
                    if (missingLabel != null) {
                        Text(
                            missingLabel,
                            fontSize = FontMicroLabel,
                            lineHeight = KListMetaLineHeight,
                            color = LocalShelfColors.current.infoText,
                        )
                    } else {
                        KBookStatusLine(status = status, chapNum = chapNum, totalChaps = totalChaps, lineHeight = KListMetaLineHeight)
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
// 未取込の徴＝行地を field へ沈め、行の四隅へ青磁のコーナーマーカーを置く（角丸6dp）。グリッド .cv.narou が
//   書影（実体）の隅に同じ徴を置くのと同じ言葉を、書影のない目録では行そのものに掛ける（2026-07-26 に帯だけの
//   破線化→行フレームへ、2026-08-26 に四辺の破線→四隅マーカーへ。後者の理由は narouCornerMarks の KDoc）。色帯は蔵書行と同じ title 由来色に戻す
//   （正本 .web は --band を保持＝四隅マーカーが「未取込」を語り、帯は「1冊=1色相」の識別に専念する役割分担）。
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
    // 未取込の署名色＝濃青磁（正本 --seiji-ink／トークン UnreadSeiji。引き方の理由は KWebGridBookCard と同文）。
    // 四隅マーカー（装飾）とメタ文字（AA 対象）の両方がこの1色を使う。
    // ⚠️ メタ文字は装飾ではなく AA 対象（ADR 0014-D「意味を運ぶ文字は WCAG 4.5:1」）＝枠と別に決めてよい。
    // 旧実装の淡 secondary #9CB3A8 は素地 2.14:1 で未達だった——2026-08-21 の是正がグリッドのキャプション
    // （unreadLabel→semanticMicroText）にだけ入り、この一覧行が取り残されていた。濃青磁は沈めた行地
    // #F4F3F2 上 5.45:1・素地 #FBFAF8 上 5.79:1 で AA を満たす。今回は枠の裁定も濃青磁なので1つの値を共有する。
    // DrawScope 内では @Composable の MaterialTheme を読めないため事前に捕捉する。
    val seijiInk = LocalShelfColors.current.semanticMicroText
    // 帯の作品識別色（蔵書行 KListBookCard と同一導出＝「1冊=1色相」を Web由来でも保つ）。
    val accentLightness = LocalShioriColors.current.accentLightness
    val bandColor = remember(novel.title, accentLightness) { shioriAccentFor(shioriHue(novel.title), accentLightness) }

    // 蔵書行と違い下ヘアラインを持たない＝正本 .web が border-bottom-color を transparent にしているため
    // （沈めた行地の縁に線を重ねると、四隅マーカーと合わせて区切りが二重に見える）。行の切れ目は前後の
    // 蔵書行が持つヘアラインと下側マーカーが担う。Column 包みも不要になり Row 単体で組む。
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                // 選択モード中の選択状態宣言（D の GridBookCard と同文・監査 A11。理由はそちらのコメント参照）。
                if (selectionMode) {
                    this.selected = selected
                    this.stateDescription = if (selected) "選択中" else "未選択"
                }
            }
            // 角丸6dp＝正本 .web の border-radius。clip が field 地・選択かぶせ・リップルを枠形に収める。
            .clip(RoundedCornerShape(6.dp))
            // 行地一段沈め（--field）＝KWebGridBookCard と同じ onSurface かぶせのテーマ非依存翻訳。
            // ここは従来から「内容の下」＝地。グリッド側をこの重ね順へ揃えた（理由はあちらのコメント）。
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = NarouSinkAlpha))
            // 選択中は field の上へ淡い藍かぶせ（蔵書行と同値）。
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent,
            )
            // 青磁の四隅マーカー（線幅1.0dp・実線・腕14dp＝グリッド書影と1定義を共用する narouCornerMarks）。
            // グリッドと違い選択中も描いたままにする: 一覧の選択マークは Row の子として行内に並ぶ＝四隅とは
            // 場所が競合せず、選択かぶせも背景（内容の下）なのでマーカーを覆わない＝選択作業中も
            // 「どれが未取込か」を保てる（グリッドは書影右上で重なるため描き分けが要った）。
            .narouCornerMarks(color = seijiInk, cornerRadius = 6.dp, armLength = NarouMarkArmList)
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
        // 色帯（蔵書行と同寸: 幅4dp・角丸2dp）。沈めた行地の左角丸6dpを跨がないよう枠内へ 6dp インセットし
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
                lineHeight = KListTitleLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 題字→メタの詰め S4＝案A（正本 .m の margin-top:4px）。
            Spacer(Modifier.height(Spacing.S4))
            // メタ1行: 著者＋状態を中黒で連結（蔵書行と同構造＝正本 .web の .m）。未取込の行なので
            // 文字は著者ごと濃青磁で統一する（正本 .web .m,.web .m span＝--seiji-ink）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (novel.writer.isNotBlank()) {
                    Text(
                        text = novel.writer,
                        fontSize = FontMicroLabel,
                        lineHeight = KListMetaLineHeight,
                        color = seijiInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 著者が長くても状態を押し出さない（蔵書行と同じ収縮）。
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("・", fontSize = FontMicroLabel, lineHeight = KListMetaLineHeight, color = seijiInk)
                }
                // 進捗あれば「第N話まで既読」（K グリッド Web と同文言）／無ければ「なろう・未取込」（Medium＝未取込の徴）。
                if (hasProgress) {
                    Text(
                        "第${lastReadEpisode}話まで既読",
                        fontSize = FontMicroLabel,
                        lineHeight = KListMetaLineHeight,
                        color = seijiInk,
                    )
                } else {
                    Text(
                        "なろう・未取込",
                        fontSize = FontMicroLabel,
                        lineHeight = KListMetaLineHeight,
                        fontWeight = FontWeight.Medium,
                        color = seijiInk,
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
 * 未取込の紙地沈め（正本 --field）＝紙／行地へ onSurface をこの割合だけ焼き込む。
 * 2026-08-26 ユーザー裁定で 5%→3% へ浅くした（四隅マーカー化と同じ便＝徴を全体に静める枠での再調整）。
 * ライトでは #FBFAF8 → #F4F3F2。テーマ非依存に一段沈めるため固定色でなく onSurface のかぶせで持つ。
 */
private const val NarouSinkAlpha = 0.03f

/** 未取込マーカーの線幅（2026-08-26 裁定＝1.0dp）。面別に振らないので共有定数で持つ。 */
private val NarouMarkStroke = 1.dp

/** グリッド書影の腕の長さ（正本 `.cv.narou .mk`＝12px）。 */
private val NarouMarkArmGrid = 12.dp

/** 一覧行の腕の長さ（正本 `.lc.web .mk`＝14px。行は横長なので角をやや長く取る）。 */
private val NarouMarkArmList = 14.dp

/**
 * 未取込Webカードの徴＝四隅のコーナーマーカー（2026-08-26 ユーザー裁定・候補紙
 * docs/design-candidates/skins/candidates/bookshelf-K-narou-frame-candidates.html の案D＋詰め D2）:
 * 実線・線幅 [NarouMarkStroke]・色は濃青磁・角丸は器と同値・腕は [NarouMarkArmGrid]/[NarouMarkArmList]。
 *
 * なぜ四辺の破線をやめたのか（＝線を弱めたのではなく記号を替えた）: 2026-08-21 の実機ツアーで
 * 「目立ちすぎ。書庫に入っているのは同じなのに存在しないように見える」と FAIL した。四辺を等しく囲う枠は
 * 〈中身のない枠＝不在〉と読めてしまう——これは線の *強さ* ではなく *記号* の問題なので、淡く/細く/粗く
 * する方向（候補 A/B/C/H）では「枠であること」が残り評は解けない。角だけを示せば「中身のない枠」という
 * 読みが成立しない一方、「まだ確定していない一冊」の比喩と一目の識別は保てる、というのが裁定の理路。
 *
 * なぜ共有 Modifier に集約するか（従来どおり）: グリッド書影とリスト行は別 Composable で、値を各所へ
 * 写経するとリスト新設時のような署名脱落（2026-07-26 是正の真因）が再発する＝1定義に束ねて構造的に防ぐ。
 *
 * なぜ [drawWithContent] か: 器の版面（栞書影の栞棒・縦題字／行の題字・メタ・⋮）を 1dp も狭めずに
 * 上へ重ねるため。border や padding で描くと中身が痩せる＝正本モックが絶対配置のオーバーレイ（.mk）で
 * 描いているのと同じ思想に合わせる。
 */
private fun Modifier.narouCornerMarks(color: Color, cornerRadius: Dp, armLength: Dp): Modifier =
    drawWithContent {
        drawContent()
        val stroke = NarouMarkStroke.toPx()
        // 線の中心を半ストローク内側へ寄せ、線全体を器の中へ収める（clip の角丸と整合）。
        val inset = stroke / 2f
        val r = cornerRadius.toPx()
        // 防御: 器が極端に狭いと左右（上下）の腕が届き合って「角の徴」が「枠」に化ける＝裁定の意図が
        // 反転する。器の半分を超えないよう丸める（fontScale 2.0 の縦伸びや横画面の細い列を想定）。
        val arm = armLength.toPx().coerceAtMost(minOf(size.width, size.height) / 2f - inset)
        val end = inset + arm
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // 左上
            moveTo(inset, end)
            lineTo(inset, inset + r)
            arcTo(Rect(inset, inset, inset + 2 * r, inset + 2 * r), 180f, 90f, false)
            lineTo(end, inset)
            // 右上
            moveTo(w - end, inset)
            lineTo(w - inset - r, inset)
            arcTo(Rect(w - inset - 2 * r, inset, w - inset, inset + 2 * r), 270f, 90f, false)
            lineTo(w - inset, end)
            // 右下
            moveTo(w - inset, h - end)
            lineTo(w - inset, h - inset - r)
            arcTo(Rect(w - inset - 2 * r, h - inset - 2 * r, w - inset, h - inset), 0f, 90f, false)
            lineTo(w - end, h - inset)
            // 左下
            moveTo(end, h - inset)
            lineTo(inset + r, h - inset)
            arcTo(Rect(inset, h - inset - 2 * r, inset + 2 * r, h - inset), 90f, 90f, false)
            lineTo(inset, h - end)
        }
        drawPath(
            path = path,
            color = color,
            // round cap＝候補紙が裁定を受けたときの描き方（1dp 線では butt と識別できないが値を写す）。
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

/**
 * 可視⋮のタップ面（モック `.cap .dots` は 28px だが、最小タップ面 32dp まで広げてある）。
 * 定数として括り出すのは、キャプション行の最小行高がこの寸法に一致していないと
 * 「選択モードで⋮が消える＝行が縮んでカード高が跳ねる」ため（同じ値であることが不変条件）。
 */
private val KCardMenuTapSize = 32.dp

/**
 * キャプション行右端の可視⋮（32dpタップ面）。書影上でなく通常面に載るためスクリム不要＝トークン色で描く
 * （書影右上案は栞書影の縦題字と衝突するため移設＝実機検分 2026-07-23）。
 */
@Composable
private fun KCardMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KCardMenuTapSize)
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
// 空状態（.empty＝「まだ本がありません」＋説明＋CTA2つ〈PDFを追加〉〈作品をさがす〉）
// 〈作品をさがす〉はさがすタブ（発見ホーム）へ＝K は発見帯を本棚に置かず、さがすタブへ一本化した（plan 確定5）。
// 本文と CTA の主従は 2026-09-07 裁定（ADR 0037 追記）で D の案B へ揃えた＝正本モックは
// docs/design-candidates/skins/bookshelf-K.html の .empty。見出しだけは K の語彙のまま（同上・個性として残す側）。
// ============================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KEmptyState(
    onFindWorks: () -> Unit,
    onAddPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // なぜ Box(中央寄せ)＋内側 Column(verticalScroll) の二段構えか（監査 2026-08-06 G-6）:
    // fontScale 2.0 では文言＋CTA の全高が親の weight(1f) 領域を超え、下端（CTA）が画面外へ切れていた。
    // Column へ直接 verticalScroll を足すと、内容が可視域より低いとき Column が内容高で wrap して
    // 上詰めになり 1.0 の中央寄せが崩れるため、中央寄せは外の Box・あふれ時のスクロールは内側 Column
    // へ役割を分ける（1.0 の見た目は不変・2.0 だけスクロール可能になる）。
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // ⚠️ 下端に FAB 回避帯（Insets.ScrollBottomForFab）は敷かない（2026-08-20 裁定②で撤去）。
        // 空棚では拡張FAB 自体を出さなくなった＝避ける相手が居ない。予約だけ残すと帯の半分（48dp）ぶん
        // 中央寄せが上へずれる（旧・敷いていた理由は「2.0 で FAB が CTA を覆う」ことへの器側の対処で、
        // 真因＝同じ操作の二重表示そのものを裁定②が消した）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.S40),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "まだ本がありません",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.S12))
            // 案B〈操作と結果を明示する語り〉＝D と一字同じ本文（2026-09-07 裁定）。旧文言は操作しか言わず、
            // このアプリの核心価値（ふりがな付きで読める）を一度も名乗らなかった。K は既定スキン＝
            // 新規インストール直後の初見が最初に見る空棚なので、価値を告げる効きが D より大きい。
            Text(
                "お手元のPDFを取り込むと、ふりがな付きで読めるようになります。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.S32))
            // なぜ FlowRow か（監査 2026-08-06 G-6）: 素の Row は幅を分け合わず、2.0 では先行ボタンが実寸を
            // 取り切って輪郭ボタンが残り幅へ1文字ずつ縦積みになる破綻が golden（BookshelfK_empty_light_2.0）に
            // 焼かれていた。入り切らないボタンは次行へ折り返して両導線の判読を保つ（テーマ3択チップと同じ流儀）。
            // spacedBy の第2引数 CenterHorizontally は折返し後の各行を中央へ揃える（1.0 の1行時は見た目不変）。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S12, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.S12),
            ) {
                // 一画面一強調＝実塗りは主導線だけ・順序も主を先に置く。主従の根拠は 2026-08-20 裁定② そのもの
                // ＝空棚では拡張FAB を出さないので、**PDF 追加の入口はこの CTA だけ**になる。一方〈作品をさがす〉は
                // ボトムナビ（KBottomNav の さがす）に常設＝空棚 CTA が唯一の入口ではない。導線の希少性が主従を決める。
                // 旧「新規ユーザーの主導線＝作品をさがす」は plan 確定5 由来の想定で、裁定②の後に見直されておらず、
                // 「押す対象を空棚CTA〈PDFを追加〉一本へ寄せる」という決定に対し寄せた先が画面で最も弱いボタンだった
                // （2026-09-07 裁定で解消・ADR 0037 追記）。ラベルは「PDFを追加」のまま＝蔵書ありの拡張FAB と同じ語。
                Button(onClick = onAddPdf) { Text("PDFを追加") }
                OutlinedButton(onClick = onFindWorks) { Text("作品をさがす") }
            }
        }
    }
}
