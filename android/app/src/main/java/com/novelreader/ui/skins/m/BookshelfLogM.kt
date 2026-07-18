package com.novelreader.ui.skins.m

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.narou.model.NarouNovel
import com.novelreader.ui.DeleteSourcePdfOption
import com.novelreader.ui.NewEpisodeNotificationMenuSection
import com.novelreader.ui.newEpisodeCountFor
import com.novelreader.ui.theme.DimSeizu
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.MoonSlateSeizu
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.RubySeizu
import com.novelreader.ui.theme.SkyGradEndSeizu
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.StarGlowInnerSeizu
import com.novelreader.ui.theme.StarSeizu
import com.novelreader.ui.theme.TextSeizu
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.ShelfItem
import com.novelreader.viewmodel.chapterNumberOf
import com.novelreader.viewmodel.filterShelfByStatus
import com.novelreader.viewmodel.mergeShelfItems
import com.novelreader.viewmodel.progressFractionFor
import com.novelreader.viewmodel.readingStatusFor

// ============================================================
// スキンM「星図」の本棚＝一覧ビュー『観測野帳』（正本 bookshelf-M.html 下部 l* 名前空間・L3 2026-07-17 承認）。
//
// 是正の背景（ADR 0022 追記その2）: ユーザー最上位原則「各スキンは全く別のアプリ＝Dの見た目の型を引き継がない
//   （Dは機能参照のみ）」により、星図⇄一覧トグルの一覧側を旧・D構造フォールバック（栞書影＝別UI）から M 自身の
//   意匠へ差し替える。星図面（BookshelfSkyM）とは〈群青の地／月光スレートの軸罫／温白の弧／学名色の星／地平の
//   導線〉を共有し、装いの間を経ずとも世界が切れない（部品は BookshelfSkyM の internal 部品を再利用＝二重実装なし）。
//
// 意匠の骨子（モック l* セクション）: 作品＝一本の時刻軸レール上の「観測ノード」（作品固有色の星ディスク＋
//   ノードを巡る「観測弧」＝進捗メーター。読了=満環／未読=点線環）。記録本体は箱のない「観測票」＝明朝の銘＋
//   readout 一行「観測者=著者 · 到達話数 · 光度=%/読了」。最近読んだ星から〈今夜／昨夜／先週／まだ観測なし／
//   未収蔵の星〉と時系列に綴じる（二段 recency=ADR 0016 の lastReadAt を epoch へ写像）。
//
// 先例踏襲（BookshelfListCartridgeP と同3設計）: ①選択モードは骨格 BookshelfContent の単一状態機械を引数共有
//   （selectionMode/selectedIds を所有せず受け取る＝二重管理を避け骨格の BackHandler 1本が効く）。
//   ②shelfItems 合成は D と同じ純関数（filterShelfByStatus＋mergeShelfItems）。③機体/共通部品は星図ファイルから
//   internal 昇格で共有（チップ／取込中バナー／地平／4条星／id 色／.const 較正色）。
//
// モーション（ADR 0022 §3）: ①今夜ノード（＝最も新しい観測＝live）のディスクのみ脈動 ②選択時に観測ノードが
//   選択リングへ変わり、選択された瞬間だけ温白リングが一度点灯（justpicked）。reduce-motion では両者とも静止。
//
// モック未定義の翻訳（発明を最小化し明示）: ①削除確認ダイアログは OS 面（Material AlertDialog）＝モックに
//   ダイアログ意匠は無い（P/D と同語） ②未収蔵行の「外す/目次」導線は行内の⋯メニューへ（モックは coll-act
//   「この星を迎える」＝取込のみ可視。D の Web 操作全数＝目次/続きから/取込/外すを M 語彙で写像）。
// ============================================================

// ---- 観測野帳専用の較正色（bookshelf-M .l* の直書き値。この画面専用＝ADR 0022 §5 の in-file 集約）----
// by/link/unread-ttl/unread-prog/badge-border は星図 .const と同値ゆえ BookshelfSkyM の internal 版を共有（再定義しない）。
private val MetaCountInkSeizu = Color(0xFFB9C2DA)     // .lplate .lmeta b（観測数の強調）
private val EpochLabSeizu = Color(0xFF7C86A2)         // .epoch .lab（時系列節の見出し）
private val SepSeizu = Color(0xFF5C6688)              // .readout .sep（諸元の中黒）／.node .ring border
private val UncollDiscBorderSeizu = Color(0xFF4E587A) // .rec.uncoll .node .disc（未収蔵の点線ディスク縁）
private val DelInkSeizu = Color(0xFFE4A9A0)           // .selhead .del（「星を消す」＝沈めた朱）
private val NightScrimSeizu = Color(0xFF0A1128)       // .selhead 地／.node .ring 地（α.82）＝夜天終端の不透明

// レール/トラックの月光スレート α（--rail .26／.gauge .track .22）。既存 MoonSlateSeizu へ α 付与（直書きでない）。
private val RailSeizu = MoonSlateSeizu.copy(alpha = 0.26f)
private val TrackSeizu = MoonSlateSeizu.copy(alpha = 0.22f)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** 時系列節（epoch）。label＝見出し・isNow＝「今夜」（見出しを星金で強調）・entries＝その節の観測票。 */
private class LogEpoch(val label: String, val isNow: Boolean, val entries: List<ShelfItem>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfLogM(
    // 表示対象の蔵書（骨格 visibleBooks）と Web 由来（未取込）。
    books: List<BookEntity>,
    webNovels: List<WebNovelEntity>,
    webReadingProgress: Map<String, Int>,
    webLastReadAt: Map<String, Long>,
    progressMap: Map<String, ProgressEntity>,
    chapterCountMap: Map<String, Int>,
    newEpisodeNovelMap: Map<String, NarouNovel>,
    processingState: ProcessingState,
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    onSelectStatus: (ReadingStatus?) -> Unit,
    // 選択モードは骨格（BookshelfContent）と共有する単一の状態機械（D/P と同じ）。ここでは所有せず引数で受ける。
    selectionMode: Boolean,
    selectedIds: List<String>,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onExitSelection: () -> Unit,
    onDeleteBooks: (List<BookEntity>, deleteSource: Boolean) -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onOpenWebNovel: (WebNovelEntity) -> Unit,
    onResumeWebNovel: (WebNovelEntity, Int) -> Unit,
    onImportWebNovel: (WebNovelEntity) -> Unit,
    onRemoveWebNovel: (WebNovelEntity) -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onFabClick: () -> Unit,
    onToggleSky: () -> Unit,
    onCancelProcessing: () -> Unit,
    snackbarHostState: SnackbarHostState,
    isLoading: Boolean,
) {
    // reduce-motion（アニメーター無効）を尊重＝脈動・選択点灯を静止（ADR 0022 §3・星図面と同判定）。
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // 今夜ノードの脈動位相（モック mlpulse 2.8s・scale 1→1.28 の往復）。draw 段でだけ読むためラムダで供給。
    // reduce 時は無限アニメ自体を作らない（静止値のみ＝電池も浪費しない・モックの rAF 停止と同値）。
    val phase = if (reduceMotion) null else rememberInfiniteTransition(label = "logPulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "logPulsePhase",
    )
    val pulse: () -> Float = { phase?.value ?: 0f }

    // 蔵書＋Web由来を「最近の活動順」で1本にマージ＝D/P と同一の純関数（並び規則 ADR 0016 を共有＝再実装なし）。
    val shelfItems = remember(books, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }

    // shelfItems を時系列節（epoch）へ振り分ける＝二段 recency（ADR 0016）を可視化する観測野帳の綴じ方。
    //   ・触った蔵書（lastReadAt>0）は経過時間で 今夜(<1d)/昨夜(<2d)/先週(それ以前) に落とす。
    //   ・未接触の蔵書（lastReadAt=0）は「まだ観測なし」＝未読の帯（ADR 0016 の tier1）。
    //   ・Web 由来（未取込）は「未収蔵の星」。
    // mergeShelfItems が層内を時刻降順で返すため、iteration 順のまま各節へ積めば節内も新しい順で保たれる。
    // なぜ古い観測も「先週」へ畳むか: モック l* は 今夜/昨夜/先週 の3時間帯で固定＝実データの任意経過を3段へ丸める
    //   （分秒精度は野帳に不要・relativeReadLabel と同じ日粒度思想）。2日より前の観測はすべて先週節へ集約する。
    val epochs = remember(shelfItems, progressMap) {
        val now = System.currentTimeMillis()
        val tonight = ArrayList<ShelfItem>()
        val lastNight = ArrayList<ShelfItem>()
        val earlier = ArrayList<ShelfItem>()
        val unobserved = ArrayList<ShelfItem>()
        val uncollected = ArrayList<ShelfItem>()
        shelfItems.forEach { item ->
            when (item) {
                is ShelfItem.Book -> {
                    val lra = progressMap[item.book.id]?.lastReadAt ?: 0L
                    val bucket = when {
                        lra <= 0L -> unobserved
                        now - lra < DAY_MS -> tonight
                        now - lra < 2 * DAY_MS -> lastNight
                        else -> earlier
                    }
                    bucket += item
                }
                is ShelfItem.Web -> uncollected += item
            }
        }
        listOf(
            LogEpoch("今夜", isNow = true, entries = tonight),
            LogEpoch("昨夜", isNow = false, entries = lastNight),
            LogEpoch("先週", isNow = false, entries = earlier),
            LogEpoch("まだ観測なし", isNow = false, entries = unobserved),
            LogEpoch("未収蔵の星", isNow = false, entries = uncollected),
        ).filter { it.entries.isNotEmpty() }
    }
    // 遊び心: 今夜（最も新しい観測）の先頭ノードだけ脈動させる＝モック .rec.live（1件のみ）。
    val liveBookId = remember(epochs) {
        (epochs.firstOrNull { it.isNow }?.entries?.firstOrNull() as? ShelfItem.Book)?.book?.id
    }
    // 銘の meta「観測 N 天体 · 最新 <節>」＝観測票の総数と、最も新しい観測の属する節。
    val recordCount = shelfItems.size
    val latestLabel = epochs.firstOrNull()?.label

    // 削除確認ダイアログの表示フラグ（選択状態そのものは骨格が所有・この面ローカルの UI 状態のみ）。
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 固定地平（発見導線＋迎える）の実測高でスクロール下端クリアランスを確保する（星図面と同機構）。
    val density = LocalDensity.current
    var horizonHeightPx by remember { mutableStateOf(0) }
    val horizonClearance = if (horizonHeightPx > 0) with(density) { horizonHeightPx.toDp() }
    else Insets.SkyHorizonClearance
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // .phone 背景＝群青の夜天グラデ（星図スキン共通 drawNightSky）。観測野帳も同じ地の上に綴じる。
            .drawBehind { drawNightSky() },
    ) {
        // 深空の簡易下地（ldeepsky）＝星雲＋アクセント星の淡い地。DeepSkyM の drawDeepSky を読了星なしで再利用
        //（一覧の主役は前景の観測票＝星図面の天の川粒帯 drawFarStars は敷かない軽い版。pointer 非介入で下へ素通し）。
        val deepSkyField = remember { buildDeepSkyField() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawDeepSky(deepSkyField, emptyList()) },
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 選択モード中は銘/チップの座を選択ヘッダ（selhead）へ譲る（モック body.selecting）。
            if (selectionMode) {
                SelHead(
                    count = selectedIds.size,
                    onCancel = onExitSelection,
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                LogPlate(
                    recordCount = recordCount,
                    latestLabel = latestLabel,
                    onOpenDiscovery = onOpenDiscovery,
                    onToggleSky = onToggleSky,
                    onOpenWardrobe = onOpenWardrobe,
                )
            }
            // 取込中バナー（PDFを星に変換中）＝星図面と同一部品（機能フィードバックの出没のみ Motion スロット）。
            AnimatedVisibility(
                visible = processingState.isProcessing,
                enter = fadeIn(tween(MotionDurationReveal)),
                exit = fadeOut(tween(MotionDurationDismiss)),
            ) {
                SkyProcessingBanner(processingState, onCancelProcessing)
            }
            // 読書状態フィルタ（.lchips）＝星図 .chips と同一意匠・同一機能（選択モード中は隠す＝selhead が場を占める）。
            if (!selectionMode) {
                SkyChips(selectedStatus, statusCounts, onSelectStatus)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 下端は地平（発見導線＋迎える）ぶんを空ける＝末尾の観測票が地平に沈まない（実測高）。
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = horizonClearance),
            ) {
                epochs.forEach { epoch ->
                    item(key = "epoch:${epoch.label}") { EpochHeader(epoch.label, epoch.isNow) }
                    items(epoch.entries, key = { it.key }) { si ->
                        when (si) {
                            is ShelfItem.Book -> ObservationRecord(
                                book = si.book,
                                progress = progressMap[si.book.id],
                                totalChaps = chapterCountMap[si.book.id] ?: 0,
                                novelDetail = si.book.ncode?.let { newEpisodeNovelMap[it] },
                                isLive = si.book.id == liveBookId,
                                selectionMode = selectionMode,
                                selected = si.book.id in selectedIds,
                                reduceMotion = reduceMotion,
                                pulse = pulse,
                                onOpen = { onOpenBook(si.book) },
                                onToggleSelect = { onToggleSelect(si.book.id) },
                                onEnterSelection = { onEnterSelection(si.book.id) },
                            )
                            is ShelfItem.Web -> UncollectedRecord(
                                novel = si.novel,
                                lastReadEpisode = si.lastReadEpisode,
                                onOpen = { onOpenWebNovel(si.novel) },
                                onResume = { onResumeWebNovel(si.novel, si.lastReadEpisode) },
                                onImport = { onImportWebNovel(si.novel) },
                                onRemove = { onRemoveWebNovel(si.novel) },
                            )
                        }
                    }
                }
                if (!isLoading && shelfItems.isEmpty()) {
                    item {
                        // 空状態はモック未定義＝最小の一文（星図面と同じ発明最小化・語はフィルタ有無で分岐）。
                        Text(
                            text = if (selectedStatus == null) "まだ観測の記録がない" else "この空には該当する観測がない",
                            fontFamily = MinchoFamily,
                            fontSize = 14.sp,
                            color = DimSeizu,
                            modifier = Modifier.padding(horizontal = Spacing.S24, vertical = Spacing.S40),
                        )
                    }
                }
            }
        }

        // 固定地平背後の夜天スクリム（星図面と同機構＝観測票が地平バー裏に透けて重ならないよう終端色へ沈める）。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(horizonClearance)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.14f to SkyGradEndSeizu.copy(alpha = 0.85f),
                            0.3f to SkyGradEndSeizu,
                            1f to SkyGradEndSeizu,
                        )
                    )
                },
        )

        // 下辺の地平（発見導線＋未取込カウント＋新しい星を迎える）＝星図面と同一部品（世界＝地平が切れない）。
        SkyHorizon(
            webNovelCount = webNovels.size,
            onOpenDiscovery = onOpenDiscovery,
            onFabClick = onFabClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { horizonHeightPx = it.height },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = horizonClearance),
        )
    }

    // 複数選択削除の確認（D/P と同語＝不可逆を本文で明示）。モックにダイアログ意匠は無いため OS 面の Material を使う。
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
// 銘＋操作クラスタ（モック .lplate: 見つける/星図へ戻す/装いの間/メニュー）
// ============================================================
@Composable
private fun LogPlate(
    recordCount: Int,
    latestLabel: String?,
    onOpenDiscovery: () -> Unit,
    onToggleSky: () -> Unit,
    onOpenWardrobe: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S16, vertical = Spacing.S4),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.S4, top = Spacing.S4)) {
            Text(
                "本棚",
                fontFamily = MinchoFamily,
                fontSize = 20.sp,              // .lplate .lname 20px
                letterSpacing = 0.22.em,
                fontWeight = FontWeight.Medium,
                color = TextSeizu,
            )
            // .lmeta「観測 <b>N</b> 天体 · 最新 <節>」。数字だけ強調色（.lmeta b #B9C2DA）。
            Row(modifier = Modifier.padding(top = Spacing.S4)) {
                Text("観測 ", fontSize = 10.sp, letterSpacing = 0.12.em, color = DimSeizu)
                Text(
                    "$recordCount",
                    fontSize = 10.sp, letterSpacing = 0.12.em,
                    fontWeight = FontWeight.SemiBold, color = MetaCountInkSeizu,
                )
                Text(
                    " 天体" + (latestLabel?.let { " · 最新 $it" } ?: ""),
                    fontSize = 10.sp, letterSpacing = 0.12.em, color = DimSeizu,
                )
            }
        }
        PlateIcon(onClick = onOpenDiscovery) {
            Icon(Icons.Filled.Search, contentDescription = "見つける", tint = DimSeizu, modifier = Modifier.size(19.dp))
        }
        // 星図へ戻す（モック .lib「星図表示に戻す」＝星座線図）。ラベルは既存トグル語 "星図表示に切替" と統一。
        PlateIcon(onClick = onToggleSky) {
            ConstellationGlyph(tint = DimSeizu, modifier = Modifier.size(19.dp).semantics { contentDescription = "星図表示に切替" })
        }
        PlateIcon(onClick = onOpenWardrobe) {
            // 装いの間だけ星光でほのめかす（星図面と同じ4条星＝別の空への扉）。
            FourPointStar(color = StarSeizu, modifier = Modifier.size(19.dp).semantics { contentDescription = "着せ替え" })
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            PlateIcon(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー", tint = DimSeizu, modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // M は固定1変種＝テーマ節を出さない（星図面と同思想・ADR 0022 §2）。新着通知節のみ。
                NewEpisodeNotificationMenuSection()
            }
        }
    }
}

/** .lib のタップ領域（34×34・角丸9）。中身は呼び出し側のアイコン。 */
@Composable
private fun PlateIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 星図へ戻すアイコン（モック .lib の星座線図＝5星を結ぶ小さな星座）。 */
@Composable
private fun ConstellationGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        // モック points: (6,7)(13,5)(18,10)(9,14)(16,18) を線で結び、各点に小さな星。
        val pts = listOf(
            Offset(6f * s, 7f * s), Offset(13f * s, 5f * s), Offset(18f * s, 10f * s),
            Offset(9f * s, 14f * s), Offset(16f * s, 18f * s),
        )
        for (i in 1 until pts.size) drawLine(tint, pts[i - 1], pts[i], strokeWidth = 1.4f * s)
        for (p in pts) drawCircle(tint, radius = 1.2f * s, center = p)
    }
}

// ============================================================
// 選択ヘッダ（モック .selhead: 長押しで出現・×解除／N 天体を選択／星を消す）
// mock: 全選択の座は無い＝観測野帳の選択は「解除／件数／削除」の3語のみ（faithful・全選択は非提供）。
// ============================================================
@Composable
private fun SelHead(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightScrimSeizu) // .selhead background #0A1128（夜天終端の不透明）
            .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S12),
    ) {
        Text(
            "×",
            fontSize = 19.sp, color = DimSeizu, // .selhead .cancel 19px --dim
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = Spacing.S8, vertical = Spacing.S4)
                .semantics { contentDescription = "選択を解除" },
        )
        Text(
            "$count 天体を選択",
            fontSize = 14.sp, letterSpacing = 0.08.em, color = TextSeizu, // .selhead .count 14px
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = count > 0, onClick = onDelete)
                .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
        ) {
            TrashGlyph(tint = DelInkSeizu, modifier = Modifier.size(15.dp))
            Text("星を消す", fontSize = 12.5.sp, color = DelInkSeizu) // .selhead .del 12.5px
        }
    }
}

/** ゴミ箱の線画（モック .selhead .del の SVG＝蓋線＋箱）。 */
@Composable
private fun TrashGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        val w = 1.6f * s
        drawLine(tint, Offset(4f * s, 7f * s), Offset(20f * s, 7f * s), strokeWidth = w) // 蓋線
        // 箱（8,7→8,19→16,19→16,7）＋蓋つまみ（8,7 V5 H16 v2）を簡略。
        drawLine(tint, Offset(8f * s, 7f * s), Offset(8f * s, 19f * s), strokeWidth = w)
        drawLine(tint, Offset(16f * s, 7f * s), Offset(16f * s, 19f * s), strokeWidth = w)
        drawLine(tint, Offset(8f * s, 19f * s), Offset(16f * s, 19f * s), strokeWidth = w)
        drawLine(tint, Offset(9f * s, 7f * s), Offset(9f * s, 5f * s), strokeWidth = w)
        drawLine(tint, Offset(9f * s, 5f * s), Offset(15f * s, 5f * s), strokeWidth = w)
        drawLine(tint, Offset(15f * s, 5f * s), Offset(15f * s, 7f * s), strokeWidth = w)
    }
}

// ============================================================
// 時系列節の見出し（モック .epoch: 左のレール点＋見出し。今夜は星金で強調）
// ============================================================
@Composable
private fun EpochHeader(label: String, isNow: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // レール（56dp）＝縦罫＋中点（.epoch .rail::before/::after）。
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = 27.dp.toPx()
                    drawLine(RailSeizu, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    drawCircle(RailSeizu, radius = 1.5.dp.toPx(), center = Offset(x, size.height / 2f))
                },
        )
        Text(
            label,
            fontSize = 10.sp, letterSpacing = 0.24.em, // .epoch .lab 10px
            color = if (isNow) StarSeizu else EpochLabSeizu,
        )
    }
}

// ============================================================
// 観測票（蔵書1件）＝レール上の観測ノード（星ディスク＋観測弧）＋箱なしの記録票。
// 選択モード: 行タップ/長押しが選択トグルへ切り替わり、ノードが選択リングへ変わる（D/P と同挙動）。
// ============================================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ObservationRecord(
    book: BookEntity,
    progress: ProgressEntity?,
    totalChaps: Int,
    novelDetail: NarouNovel?,
    isLive: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    reduceMotion: Boolean,
    pulse: () -> Float,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    val chapNum = chapterNumberOf(progress?.lastReadFilename)
    val frac = progressFractionFor(chapNum, totalChaps, progress?.scrollIndex ?: 0, progress?.scrollOffset ?: 0)
    val status = readingStatusFor(progress, totalChaps)
    val isUnread = status == ReadingStatus.UNREAD
    val isDone = status == ReadingStatus.FINISHED
    val pct = ((frac ?: 0f) * 100).toInt()
    val newCount = newEpisodeCountFor(novelDetail, totalChaps)
    val idColor = idColorFor(book.id)

    // 選択の瞬間だけ温白リングが一度点灯する（justpicked＝mlringlit 0.5s）。reduce-motion では点灯させない。
    val pickFlash = remember { Animatable(0f) }
    LaunchedEffect(selected, reduceMotion) {
        if (selected && !reduceMotion) {
            pickFlash.snapTo(1f)
            pickFlash.animateTo(0f, tween(500, easing = LinearEasing))
        } else {
            pickFlash.snapTo(0f)
        }
    }

    RecordShell(
        selected = selected,
        onClick = { if (selectionMode) onToggleSelect() else onOpen() },
        onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() },
        node = {
            ObservationNode(
                idColor = idColor,
                frac = (frac ?: 0f).coerceIn(0f, 1f),
                isUnread = isUnread,
                isUncoll = false,
                selectionMode = selectionMode,
                selected = selected,
                pickFlash = { pickFlash.value },
                isLive = isLive,
                pulse = pulse,
            )
        },
    ) {
        Text(
            book.title,
            fontFamily = MinchoFamily,
            fontSize = 14.sp, lineHeight = 20.sp, // .entry .ttl 14px 1.42
            fontWeight = FontWeight.Medium,
            color = if (isUnread) UnreadTitleSeizu else TextSeizu,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        // readout（観測諸元）＝観測者(著者) · 到達話数 · 光度。読了は光度が「読了」で星金。
        val readoutBase = if (isUnread) UnreadProgSeizu else RubySeizu
        FlowRow(
            modifier = Modifier.padding(top = Spacing.S8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            verticalArrangement = Arrangement.spacedBy(Spacing.S4),
        ) {
            if (book.author.isNotBlank()) {
                ReadoutText(book.author, BySeizu) // .obsv 観測者
                SepDot()
            }
            when {
                isDone -> {
                    ReadoutText("全${totalChaps}話", readoutBase)
                    SepDot()
                    ReadoutText("読了", StarSeizu) // .rec.done .lum＝星金
                }
                isUnread -> ReadoutText("全${totalChaps}話", readoutBase)
                else -> {
                    ReadoutText("第${chapNum ?: 1}話", readoutBase)
                    SepDot()
                    ReadoutText("$pct%", readoutBase) // 光度＝%
                }
            }
        }
        // 新着（続きあり）＝観測票の下に灯る星（.new）。
        if (newCount != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.S8),
            ) {
                Box(
                    Modifier.size(5.dp).drawBehind {
                        drawCircle(Brush.radialGradient(listOf(StarSeizu, StarSeizu.copy(alpha = 0.3f), Color.Transparent)))
                        drawCircle(StarSeizu, radius = size.minDimension / 2f)
                    }
                )
                Text(
                    "＋${newCount}話 新着",
                    fontSize = 9.5.sp, letterSpacing = 0.04.em, color = StarSeizu, // .new 9.5px
                    modifier = Modifier.padding(start = Spacing.S4),
                )
            }
        }
        // 未読は「未観測 · 最初の星を灯す」バッジ（.entry .badge）。
        if (isUnread) {
            Text(
                "未観測 · 最初の星を灯す",
                fontSize = 8.5.sp, letterSpacing = 0.16.em, color = DimSeizu, // .badge 8.5px #8791AD
                modifier = Modifier
                    .padding(top = Spacing.S8)
                    .border(1.dp, BadgeBorderSeizu, RoundedCornerShape(3.dp))
                    .padding(horizontal = Spacing.S8, vertical = Spacing.S4),
            )
        }
    }
}

// ============================================================
// 観測票（Web 由来・未収蔵）＝点線の観測ノード＋「なろう · 著者」＋「この星を迎える」（取込）。
// モック未定義の翻訳: 目次/続きから/外す は行内⋯メニューへ（D の Web 操作全数を M 語彙へ写像）。
// ============================================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun UncollectedRecord(
    novel: WebNovelEntity,
    lastReadEpisode: Int,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasProgress = lastReadEpisode > 0

    RecordShell(
        selected = false,
        // 進捗あれば主タップ=続きから／未読は目次（onOpen）＝D の WebListBookCard と同判断。
        onClick = if (hasProgress) onResume else onOpen,
        onLongClick = { menuOpen = true },
        node = {
            ObservationNode(
                idColor = Color.Transparent,
                frac = 0f,
                isUnread = false,
                isUncoll = true,
                selectionMode = false,
                selected = false,
                pickFlash = { 0f },
                isLive = false,
                pulse = { 0f },
            )
        },
    ) {
        Text(
            novel.title,
            fontFamily = MinchoFamily,
            fontSize = 14.sp, lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = TextSeizu,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        // readout: 「なろう · 著者」（.uncoll の出所ラベル）。進捗があれば「続き 第N話」を添える。
        FlowRow(
            modifier = Modifier.padding(top = Spacing.S8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
            verticalArrangement = Arrangement.spacedBy(Spacing.S4),
        ) {
            ReadoutText(if (novel.writer.isNotBlank()) "なろう · ${novel.writer}" else "なろう", BySeizu)
            if (hasProgress) {
                SepDot()
                ReadoutText("続き 第${lastReadEpisode}話", RubySeizu)
            }
        }
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // この星を迎える（.coll-act＝取込）。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
                    modifier = Modifier
                        .padding(top = Spacing.S8)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onImport)
                        .padding(vertical = Spacing.S4),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = LinkSeizu, modifier = Modifier.size(11.dp))
                    Text("この星を迎える", fontSize = 10.5.sp, color = LinkSeizu) // .coll-act 10.5px #AEB7D2
                }
                // ⋯＝未収蔵作品の行内メニュー（外す・目次＝長押しに気づかない導線の担保）。
                Text(
                    "⋯",
                    fontSize = 16.sp, color = DimSeizu,
                    modifier = Modifier
                        .padding(top = Spacing.S8, start = Spacing.S12)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = Spacing.S8, vertical = Spacing.S4)
                        .semantics { contentDescription = "未収蔵作品のメニュー" },
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // 進捗ありのとき目次導線を降格して残す（主タップが続きからに移るため・D と同判断）。
                if (hasProgress) {
                    DropdownMenuItem(text = { Text("目次を開く") }, onClick = { menuOpen = false; onOpen() })
                }
                DropdownMenuItem(text = { Text("縦書きPDFを取り込む") }, onClick = { menuOpen = false; onImport() })
                DropdownMenuItem(text = { Text("本棚から外す") }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
}

// ============================================================
// 観測票の骨組み（レール罫＋観測ノード＋箱なしの記入欄）。Book/Web で共有する版下。
// レール罫は行の全高を貫く縦線（drawBehind on Row）＝節から節へ連続する一本の時刻軸に見せる。
// ノードは top:26/left:27（モック）＝レール罫の上に中心が重なる位置へ絶対配置する。
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordShell(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    node: @Composable () -> Unit,
    entry: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 選択中の観測票は極淡の温白リフト（.rec.picked .entry rgba(233,221,180,.05)）。
            .background(if (selected) StarSeizu.copy(alpha = 0.05f) else Color.Transparent)
            .drawBehind {
                // 時刻軸レール（.rec .rail::before＝x=27・全高の縦罫）。
                val x = 27.dp.toPx()
                drawLine(RailSeizu, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.Top,
    ) {
        // ノード領域（56dp 幅）。node center を (27,26) へ＝offset(10,9) に 34dp ノードを置く（rhythm でなく
        // レール罫上への絶対配置＝spacing でなく位置。checker の spacing 文脈対象外の offset を使う）。
        Box(modifier = Modifier.width(56.dp)) {
            Box(modifier = Modifier.offset(x = 10.dp, y = 9.dp).size(34.dp)) { node() }
        }
        // 観測票の内側余白（.entry padding:14 0 18／.log padding-right:22）を rhythm スケールへ正規化（ADR 0014 §C）:
        // 14→S16・18→S16・22→S24（±2px 内の丸め＝呼吸の単位へ寄せる）。
        Column(
            modifier = Modifier.weight(1f).padding(top = Spacing.S16, bottom = Spacing.S16, end = Spacing.S24),
            content = entry,
        )
    }
}

/** readout の1諸元テキスト（.readout 10.5px）。 */
@Composable
private fun ReadoutText(text: String, color: Color) {
    Text(text, fontSize = 10.5.sp, letterSpacing = 0.02.em, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** readout の中黒（.readout .sep＝2px の点）。行の中央寄りへ微調整（FlowRow は cross 軸を上寄せするため）。
 *  下げ量は位置合わせのオフセット（rhythm でなく行中央への視覚補正）＝spacing 文脈対象外の offset を使う。 */
@Composable
private fun SepDot() {
    Box(modifier = Modifier.offset(y = 5.dp).size(2.dp).clip(CircleShape).background(SepSeizu))
}

// ============================================================
// 観測ノード（34dp）: 通常＝星ディスク＋観測弧（進捗）／選択中＝選択リング。
// ・disc: 作品固有色（idColor）＋グロー（box-shadow 9px）。未読は淡く（opacity .7・グロー無し）。
// ・gauge track: 月光スレート α.22 の円。未読は破線 2,4／未収蔵は破線 1,4 でさらに淡く。
// ・arc: frac ぶんを頂点(-90°)から時計回りに星金で点灯（drop-shadow は太→細の2層で近似）。
// ・今夜(live)ノードは disc を脈動（scale 1→1.28）。reduce-motion は pulse=0 で静止。
// ・選択リング: 26dp・境界 Sep／選択時は星金＋内側星ドット＋一度点灯（pickFlash）。
// ============================================================
@Composable
private fun ObservationNode(
    idColor: Color,
    frac: Float,
    isUnread: Boolean,
    isUncoll: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    pickFlash: () -> Float,
    isLive: Boolean,
    pulse: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val d = 1.dp.toPx()
        val gaugeR = 11f * d

        if (selectionMode) {
            // 選択リング（.node .ring＝26dp・地 rgba(10,17,40,.82)）。
            val ringR = 13f * d
            drawCircle(NightScrimSeizu.copy(alpha = 0.82f), radius = ringR, center = c)
            val flash = pickFlash()
            if (selected) {
                if (flash > 0f) {
                    // 選択の一度点灯＝リング外周のグロー（flash 1→0）。
                    drawCircle(
                        Brush.radialGradient(
                            listOf(StarSeizu.copy(alpha = 0.6f * flash), Color.Transparent),
                            center = c, radius = ringR + 5f * d,
                        ),
                        radius = ringR + 5f * d, center = c,
                    )
                }
                drawCircle(StarSeizu, radius = ringR, center = c, style = Stroke(width = 1.4f * d))
                drawCircle(StarSeizu, radius = 5.5f * d, center = c) // 内側の星ドット（11px）
            } else {
                drawCircle(SepSeizu, radius = ringR, center = c, style = Stroke(width = 1.4f * d))
            }
            return@Canvas
        }

        // gauge track（未読=破線2,4／未収蔵=破線1,4・さらに淡く）。
        val trackColor = if (isUncoll) MoonSlateSeizu.copy(alpha = 0.16f) else TrackSeizu
        val trackDash = when {
            isUncoll -> PathEffect.dashPathEffect(floatArrayOf(1f * d, 4f * d))
            isUnread -> PathEffect.dashPathEffect(floatArrayOf(2f * d, 4f * d))
            else -> null
        }
        drawCircle(trackColor, radius = gaugeR, center = c, style = Stroke(width = 2f * d, pathEffect = trackDash))

        // 観測弧（frac ぶん・頂点から時計回り）。drop-shadow は太→細の2層で近似。
        if (frac > 0f) {
            val topLeft = Offset(c.x - gaugeR, c.y - gaugeR)
            val arcSize = Size(gaugeR * 2f, gaugeR * 2f)
            val sweep = frac * 360f
            drawArc(
                color = StarSeizu.copy(alpha = 0.3f),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = 3.4f * d, cap = StrokeCap.Round),
            )
            drawArc(
                color = StarSeizu,
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = 2f * d, cap = StrokeCap.Round),
            )
        }

        // 星ディスク（作品固有色）。未収蔵は点線縁の透明ディスク。
        val discR = 7.5f * d // .disc 15px
        if (isUncoll) {
            drawCircle(
                UncollDiscBorderSeizu, radius = discR, center = c,
                style = Stroke(width = 1f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * d, 2f * d))),
            )
        } else {
            // グロー（box-shadow 0 0 9px id）。未読はグロー無し・opacity .7。
            if (!isUnread) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(idColor.copy(alpha = 0.6f), Color.Transparent),
                        center = c, radius = 9f * d,
                    ),
                    radius = 9f * d, center = c,
                )
            }
            val liveScale = if (isLive) 1f + pulse() * 0.28f else 1f
            drawCircle(
                idColor.copy(alpha = if (isUnread) 0.7f else 1f),
                radius = discR * liveScale, center = c,
            )
            // 温白の芯（星らしさ＝微光）。
            drawCircle(StarGlowInnerSeizu.copy(alpha = if (isUnread) 0.3f else 0.5f), radius = 2f * d, center = c)
        }
    }
}
