package com.novelreader.ui.skins.p

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.DeleteSourcePdfOption
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.theme.BlueInkCartridge
import com.novelreader.ui.theme.InkCartridge
import com.novelreader.ui.theme.InkMidCartridge
import com.novelreader.ui.theme.InkSoftCartridge
import com.novelreader.ui.theme.LcdCartridge
import com.novelreader.ui.theme.LcdInkCartridge
import com.novelreader.ui.theme.LineCartridge
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.PanelCartridge
import com.novelreader.ui.theme.PlasticHiCartridge
import com.novelreader.ui.theme.PlasticLoCartridge
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.RedCartridge
import com.novelreader.ui.theme.RedLoCartridge
import com.novelreader.ui.theme.Spacing
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.ShelfItem
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.filterShelfByStatus
import com.novelreader.viewmodel.mergeShelfItems
import com.novelreader.viewmodel.progressFractionFor
import com.novelreader.viewmodel.readingStatusFor
import kotlin.math.roundToInt

// ============================================================
// スキンP「カートリッジ」の本棚＝一覧面（正本 bookshelf-P.html の LIST / 一覧表示＝`.list`/`.li` 系）。
//
// 是正の背景（ADR 0022 追記その2）: ユーザー原則「各UIは装いの間でのみ接続する」により、ラック⇄一覧トグルの
//   一覧側を D構造フォールバック（栞書影＝別UI）から P自身の意匠へ差し替える。世界（POCKET NOVEL 筐体）が
//   切れないよう、上半分の機体（銘板/NOW PLAYING/つづきから/取込中バナー/CARTRIDGE LIBRARY/機体下端デッキ）は
//   ラック面と同一部品を共有し（BookshelfCartridgeP の internal 部品を再利用＝二重実装なし）、中身のスクロール域だけ
//   カセットカード→単列の詰めたリスト（`.li`）へ替える。
//
// D 機能の全数引き継ぎ（欠落ゼロが合格条件・ADR 0022 追記その2）: 選択削除・Webカード（未取込）操作・読書状態
//   フィルタ・PDF追加・取込中バナー・スナックバー・空状態。ロジック（選択状態・shelfItems マージ・コールバック）は
//   D 骨格（BookshelfContent）と同一の状態機械・純関数を共有し、本ファイルは「見た目の層」だけを P にする。
//   選択モード状態（selectionMode/selectedIds と各操作）は BookshelfContent が所有＝引数で受けて共有する
//   （P で状態を再定義しない＝二重管理を避ける。BackHandler も骨格側の1本が効く）。
//
// タイポ: P 流儀＝ゴシック（題名/本文＝既定サンセリフ）＋ monospace（英数HUD＝PixelFamily）。明朝は使わない。
// モーション: P はモックにモーション無し（ADR 0022 §3）＝静止。取込中バナーの出没だけ既存 Motion スロット流用。
// モック未定義の翻訳（発明を最小化し明示）: ①選択モードの下端バー・選択チェック（`.li` に選択意匠が無い）は
//   P 筐体語（プラ面・pixel ラベル・退色レッドの削除）で最小翻訳 ②削除確認ダイアログは OS 面＝Material AlertDialog
//   （D と同語・P モックにダイアログ意匠は無い） ③Webカード行（`.li` に Web 行が無い）は D の WebListBookCard の
//   機能（目次/続きから/取込/外す）を `.li` 版へ写像。いずれも近似でなく「P に無い意匠を P 語彙で最小化」した翻訳。
// ============================================================

// PixelFamily は package 共有部品へ集約（CartridgePartsP.kt の internal val）＝当ファイルからは参照のみ。

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfListCartridgeP(
    // 表示対象の蔵書（状態フィルタ適用済み＝骨格 visibleBooks）と Web由来（未取込）。
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    // 機能②の Web 読書位置（ncode→最後に開いた話）と二層ソート用の最終接触時刻。マージ純関数へそのまま渡す。
    webReadingProgress: Map<String, Int>,
    webLastReadAt: Map<String, Long>,
    progressMap: Map<String, ProgressEntity>,
    chapterCountMap: Map<String, Int>,
    newEpisodeNovelMap: Map<String, WorkSummary>,
    processingState: ProcessingState,
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    onSelectStatus: (ReadingStatus?) -> Unit,
    // 選択モードは骨格（BookshelfContent）と共有する単一の状態機械（D と同じ）。ここでは所有せず引数で受ける。
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
    onOpenWardrobe: () -> Unit,
    onFabClick: () -> Unit,
    onToggleRack: () -> Unit,
    onCancelProcessing: () -> Unit,
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
) {
    // 蔵書＋Web由来を「最近の活動順」で1本にマージ＝D else 経路と同一の純関数（並び規則 ADR 0016 を共有＝再実装なし）。
    val shelfItems = remember(books, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap, webReadingProgress)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }
    // hero＝NOW PLAYING（いま挿さっている1本＝先頭の「よみかけ」蔵書）。ラック面と同一規則で一貫させる。
    val hero = remember(books, progressMap, chapterCountMap) {
        books.firstOrNull { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) == ReadingStatus.READING }
    }
    // CARTRIDGE LIBRARY の件数＝「本」＝蔵書（Book）の数のみ（Web未取込は数に含めない＝ラック「NN 本」と語義一致）。
    val bookCount = shelfItems.count { it is ShelfItem.Book }

    // 削除確認ダイアログの表示フラグは純粋にこの面ローカルの UI 状態（選択状態そのものは骨格が所有）。
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // .phone 退色プラスチック筐体（linear-gradient(150deg, plastic-hi, plastic 22%, plastic-lo)）。
                Brush.linearGradient(
                    0f to PlasticHiCartridge,
                    0.22f to PanelCartridge,
                    1f to PlasticLoCartridge,
                )
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            BrandRow(
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                followingSystem = followingSystem,
                onFollowSystem = onFollowSystem,
                onOpenWardrobe = onOpenWardrobe,
                onToggleList = onToggleRack,
                // 一覧面では表示切替ボタンは「ラックへ戻る」＝2x2グリッド図柄（.btn.sq aria-label「ラックへ切替」）。
                inListMode = true,
            )

            // NOW PLAYING（続きから）＝ラック面と同一の液晶ヒーロー（一覧面モックも NOW PLAYING を持つ）。
            if (hero != null) {
                LcdNowPlaying(
                    book = hero,
                    progress = progressMap[hero.id],
                    totalChaps = chapterCountMap[hero.id] ?: 0,
                )
                StartButton(onClick = { onOpenBook(hero) })
            }

            // 取込中バナー（ProcessingBanner の P 意匠）。出没のみ既存 Motion スロット（機能フィードバック）。
            AnimatedVisibility(
                visible = processingState.isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                WritingBanner(processingState, onCancelProcessing)
            }

            LibraryHeader(count = bookCount, countPrefix = "一覧 · ")

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.S24, end = Spacing.S24, bottom = Spacing.S24,
                ),
            ) {
                // 見つける導線（.shop）と読書状態フィルタ（.chips）は一覧スクロールの先頭に置く（モック .list 構造）。
                item { ShopBand(onClick = onOpenDiscovery); Spacer(Modifier.height(Spacing.S12)) }
                item { CartridgeChips(selectedStatus, statusCounts, onSelectStatus); Spacer(Modifier.height(Spacing.S8)) }

                // contentType=型: 蔵書/Web は行構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is ShelfItem.Book -> CartridgeListRow(
                            book = item.book,
                            progress = progressMap[item.book.id],
                            novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                            totalChaps = chapterCountMap[item.book.id] ?: 0,
                            selectionMode = selectionMode,
                            selected = item.book.id in selectedIds,
                            onOpen = { onOpenBook(item.book) },
                            onToggleSelect = { onToggleSelect(item.book.id) },
                            onEnterSelection = { onEnterSelection(item.book.id) },
                        )
                        // Web由来（未取込）行。外す操作に確認を挟まないのは D と同じ判断（失う進捗が無く詳細から即戻せる）。
                        is ShelfItem.Web -> WebCartridgeListRow(
                            novel = item.novel,
                            lastReadEpisode = item.lastReadEpisode,
                            onOpen = { onOpenWebNovel(item.novel) },
                            onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                            onImport = { onImportWebNovel(item.novel) },
                            onRemove = { onRemoveWebNovel(item.novel) },
                        )
                    }
                }

                if (!isLoading && shelfItems.isEmpty()) {
                    item {
                        // 空状態はモック未定義＝ラック面と同一の最小一文（発明を最小化・語はフィルタ有無で分岐）。
                        Text(
                            text = if (selectedStatus == null) "カセットはまだ挿さっていない" else "この棚に該当するカセットは無い",
                            fontFamily = PixelFamily,
                            fontSize = 11.sp,
                            letterSpacing = 0.1.em,
                            color = InkSoftCartridge,
                            modifier = Modifier.padding(vertical = Spacing.S40),
                        )
                    }
                }
                // 空きスロット＝PDF追加（.slotadd）。一覧末尾（モック .list の末尾 slotadd）。
                item { Spacer(Modifier.height(Spacing.S12)); SlotAdd(onClick = onFabClick) }
            }

            // 機体下端: 通常はデッキ意匠（.deck）、選択モード中は下端の選択アクションバーへ場を譲る（D の bottomBar と同型）。
            if (selectionMode) {
                CartridgeSelectionBar(
                    count = selectedIds.size,
                    onCancel = onExitSelection,
                    onSelectAll = {
                        // 全選択の対象は蔵書（Book）のみ＝Web未取込は選択削除の対象外（D の SelectionActionBar と同一）。
                        onSelectAll(shelfItems.filterIsInstance<ShelfItem.Book>().map { it.book.id })
                    },
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                Deck()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.S40), // 機体下端の意匠の上へ逃がす（ラック面と同一）
        )
    }

    // 複数選択削除の確認（D と同語＝不可逆を本文で明示）。P モックにダイアログ意匠は無いため OS 面の Material を使う。
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
// カセット行（.li＝単列の詰めた1行）: chip-lb（識別色の小カセット）＋題名/著者＋セーブメタ。
// 選択モード時は行タップ/長押しが選択トグルへ切り替わり、行末に選択チェックを出す（D の ListBookCard と同挙動）。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartridgeListRow(
    book: BookEntity,
    progress: ProgressEntity?,
    novelDetail: WorkSummary?,
    totalChaps: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    // 読了は reachedEnd 実績で判定（progressFractionFor が高%でも実績なしは FINISHED にしない＝嘘の100%を出さない）。
    val status = readingStatusFor(progress, totalChaps)
    val pct = ((frac ?: 0f) * 100).roundToInt()
    val newCount = newEpisodeCountFor(novelDetail, totalChaps)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 選択中は行に淡い緑かぶせ（署名の LCD 緑で選択を示す＝mock未定義のため P 語彙で最小翻訳）。
                .background(if (selected) LcdCartridge.copy(alpha = 0.35f) else Color.Transparent)
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                    // 通常は長押しで選択モードへ・選択モード中はタップ/長押しとも選択トグル（D と同一・案B裁定）。
                    onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
                )
                .padding(horizontal = Spacing.S4, vertical = Spacing.S12), // .li padding 13px 4px
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipLabel(labelColorFor(book.id))
            Spacer(Modifier.width(Spacing.S12)) // .li gap 13px
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    fontSize = 13.5.sp,            // .li .lt 13.5px（ゴシック）
                    fontWeight = FontWeight.Bold,
                    color = InkCartridge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, // .li .lt nowrap ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        fontSize = 10.5.sp,        // .li .la 10.5px
                        color = InkMidCartridge,   // モック --ink-soft は AA 不足＝意味メタは --ink-mid（ラック面と同規則）
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.S4),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.S12)) // .li gap 13px
            // セーブメタ（.ls＝右寄せ・pixel）。状態でスタット表記＋しるしが替わる（ラックの .csave と同規則）。
            SaveMeta(status = status, chapNum = chapNum, totalChaps = totalChaps, pct = pct, newCount = newCount)
            if (selectionMode) {
                Spacer(Modifier.width(Spacing.S8))
                SelectionCheck(selected = selected)
            }
        }
        // 行下のヘアライン区切り（.li border-bottom:1px solid --line）。
        HorizontalDivider(thickness = 1.dp, color = LineCartridge)
    }
}

/**
 * セーブメタ（.li .ls）: 状態別のスタット表記＋しるし。ラックの .csave と同一規則（reachedEnd 実績で CLEAR‼）。
 * ・よみかけ: 第N話 ＋ %（青） ＋ 続きあれば「続きN」（.u 緑バッジ）
 * ・未読: 全M話 ＋「未読」（.n 赤バッジ）
 * ・読了: 全M話 ＋「CLEAR‼」（.clr 緑バッジ＝水平・進捗100%表記の代わり＝遊び心P1の一覧版）
 */
@Composable
private fun SaveMeta(status: ReadingStatus, chapNum: Int?, totalChaps: Int, pct: Int, newCount: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            ReadingStatus.UNREAD -> {
                Text(
                    "全${totalChaps}話",
                    fontFamily = PixelFamily, fontSize = 10.5.sp, // .li .ls 10.5px
                    color = InkMidCartridge,
                )
                Spacer(Modifier.width(Spacing.S8))
                MetaBadge("未読", bg = RedCartridge, fg = Color.White) // .li .ls .n
            }
            ReadingStatus.FINISHED -> {
                Text(
                    "全${totalChaps}話",
                    fontFamily = PixelFamily, fontSize = 10.5.sp,
                    color = InkMidCartridge,
                )
                Spacer(Modifier.width(Spacing.S8))
                MetaBadge("CLEAR‼", bg = LcdCartridge, fg = LcdInkCartridge) // .li .ls .clr（水平の刻印）
            }
            ReadingStatus.READING -> {
                Text(
                    "第${chapNum ?: 1}話",
                    fontFamily = PixelFamily, fontSize = 10.5.sp,
                    color = InkMidCartridge,
                )
                Spacer(Modifier.width(Spacing.S8))
                Text(
                    "$pct%",
                    fontFamily = PixelFamily, fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueInkCartridge, // .li .ls .p 青（weight700）
                )
                if (newCount != null) {
                    Spacer(Modifier.width(Spacing.S8))
                    MetaBadge("続き$newCount", bg = LcdCartridge, fg = LcdInkCartridge) // .li .ls .u
                }
            }
        }
    }
}

/** .li .ls の小バッジ（.n/.u/.clr＝角丸2px・padding 1px5px・8.5px 700）。地色/字色で意味を分ける。 */
@Composable
private fun MetaBadge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontFamily = PixelFamily,
        fontSize = 8.5.sp,                 // .li .ls .n/.u/.clr 8.5px
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.em,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp)) // border-radius 2px
            .background(bg)
            // .n/.u/.clr padding 1px 5px＝ラックの .csave バッジと同じ P バッジ規格へ寄せる（トークン S8/S4）。
            .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
    )
}

/**
 * 小カセットのラベルチップ（.li .chip-lb＝34×40・上寄せ角丸・作品識別色）。上端に溝ストライプ（.chip-lb::before＝
 * カセットの署名リブ）。識別色は labelColorFor（book.id ハッシュ）＝ラックの clabel と同じ本が同色になる整合。
 */
@Composable
private fun ChipLabel(color: Color) {
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 3.dp, bottomStart = 3.dp) // 4 4 3 3
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 40.dp) // .chip-lb 34×40
            .clip(shape)
            .background(color)
            .border(1.dp, InkCartridge.copy(alpha = 0.14f), shape), // inset 0 0 0 1px rgba(0,0,0,.14)
    ) {
        // 溝ストライプ（.chip-lb::before＝left5 right5 top5 高さ5・2px点灯/3px空の反復）。
        // 位置(5,5)と溝寸法はモック実 px を drawScope 内の絶対オフセットで写す（padding を使わずカセット物性を保つ）。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 5.dp.toPx()      // left/top 5px
            val ridgeH = 5.dp.toPx()     // 溝の高さ 5px
            val on = 2.dp.toPx()         // 点灯 2px
            val gap = 3.dp.toPx()        // 空 3px（2→5 の反復）
            var x = inset
            val right = size.width - inset
            while (x < right) {
                drawRect(
                    Color.Black.copy(alpha = 0.16f),
                    topLeft = Offset(x, inset),
                    size = Size(minOf(on, right - x), ridgeH),
                )
                x += on + gap
            }
        }
    }
}

/** 選択チェック（mock未定義＝P 語彙で最小翻訳: 選択=LCD緑塗り／非選択=プラ地の枠のみ）。 */
@Composable
private fun SelectionCheck(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp)) // カセット面取りに寄せた角丸（円でなく方形＝機械の押しボタン語彙）
            .background(if (selected) LcdCartridge else PlasticHiCartridge)
            .border(1.dp, if (selected) LcdInkCartridge else LineCartridge, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                "✓",
                fontFamily = PixelFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LcdInkCartridge,
            )
        }
    }
}

// ============================================================
// Web由来（未取込）の一覧行（.li 版＝モック未定義のため D の WebListBookCard 機能を P 語彙へ写像）。
// 主タップ＝進捗あれば続きから／無ければ目次。⋮メニュー＝目次(進捗時)/取り込む/本棚から外す（D と全数一致）。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebCartridgeListRow(
    novel: WebNovelEntity,
    lastReadEpisode: Int,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasProgress = lastReadEpisode > 0

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    // 進捗あれば主タップ=続きから（PDF蔵書と統一）／未読は目次(onOpen)＝D の WebListBookCard と同判断。
                    onClick = if (hasProgress) onResume else onOpen,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = Spacing.S4, vertical = Spacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 未取込カセットの識別色は ncode ハッシュ＝取込後の蔵書行と同じ色相へ繋がる（1作=1色相の整合）。
            ChipLabel(labelColorFor(novel.ncode))
            Spacer(Modifier.width(Spacing.S12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    novel.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkCartridge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (novel.writer.isNotBlank()) {
                    Text(
                        novel.writer,
                        fontSize = 10.5.sp,
                        color = InkMidCartridge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.S4),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.S12))
            // メタ: 進捗あれば「続き 第N話」（青）／無ければ「未取込」（沈めた pixel）＝D の「なろう・未取込」の P 版。
            if (hasProgress) {
                Text(
                    "続き 第${lastReadEpisode}話",
                    fontFamily = PixelFamily, fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueInkCartridge,
                )
            } else {
                Text(
                    "未取込",
                    fontFamily = PixelFamily, fontSize = 9.5.sp,
                    letterSpacing = 0.1.em,
                    color = InkSoftCartridge,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    // 機体トップの⋮（"メニュー"）と区別する固有ラベル＝未取込作品の行内メニュー。
                    Icon(Icons.Filled.MoreVert, contentDescription = "未取込作品のメニュー", tint = InkMidCartridge)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // 進捗ありのとき目次導線を⋮へ降格して残す（主タップが続きからに移るため・D と同判断）。
                    if (hasProgress) {
                        DropdownMenuItem(text = { Text("目次を開く") }, onClick = { menuOpen = false; onOpen() })
                    }
                    DropdownMenuItem(text = { Text("縦書きPDFを取り込む") }, onClick = { menuOpen = false; onImport() })
                    DropdownMenuItem(text = { Text("本棚から外す") }, onClick = { menuOpen = false; onRemove() })
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LineCartridge)
    }
}

// ============================================================
// 選択モードの下端アクションバー（mock未定義＝D の SelectionActionBar を P 筐体語へ翻訳）。
// キャンセル（解除）／件数／全選択／削除（退色レッドの厚みボタン＝破壊を主張しつつ確認ダイアログ前段）。
// ============================================================
@Composable
private fun CartridgeSelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = LineCartridge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(PlasticHiCartridge, PanelCartridge)))
                .navigationBarsPadding()
                .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "キャンセル",
                fontFamily = PixelFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = InkMidCartridge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = Spacing.S8, vertical = Spacing.S8),
            )
            Text(
                "${count}本選択中",
                fontFamily = PixelFamily, fontSize = 11.sp,
                letterSpacing = 0.06.em,
                color = InkMidCartridge,
                modifier = Modifier.weight(1f).padding(start = Spacing.S12),
            )
            Text(
                "全選択",
                fontFamily = PixelFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = InkCartridge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSelectAll)
                    .padding(horizontal = Spacing.S8, vertical = Spacing.S8),
            )
            Spacer(Modifier.width(Spacing.S8))
            // 削除＝退色レッドの厚みボタン（.start と同じ 3D 縁の語彙で「機械の物理ボタン」を保つ）。
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = 3.dp) // 3D 縁の一段（.start と同じ物理ボタン語彙・offset は spacing でなく位置）
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedLoCartridge),
                )
                Text(
                    "削除",
                    fontFamily = PixelFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(RedCartridge, RedLoCartridge)))
                        .clickable(enabled = count > 0, onClick = onDelete)
                        .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
                )
            }
        }
    }
}

