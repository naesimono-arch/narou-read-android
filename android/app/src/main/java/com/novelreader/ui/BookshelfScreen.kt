package com.novelreader.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.ui.discovery.FilterChipItem
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.narouWorkUrl
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.ShelfItem
import com.novelreader.viewmodel.filterShelfByStatus
import com.novelreader.viewmodel.mergeShelfItems
import kotlinx.coroutines.launch

/**
 * 本棚画面のルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取り・状態の collect・エラーイベントの購読・PDF 選択/通知権限/バッテリー最適化ダイアログ
 * といったプラットフォーム副作用と永続化（SharedPreferences）を担い、純粋な描画は [BookshelfContent] に委ねる。
 * なぜ分割するか: 描画層を state+callback の葉にして VM 非依存にすることで、Robolectric の JVM UI テスト
 * （ADR 0009）で空/一覧の分岐やコールバック結線を検証できるようにするため（chrisbanes state-holder-ui-split）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    // 見た目テーマの単一正本（読書と共有）。本棚の⋮メニューからも切り替えられるようにする。
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onOpenBook: (bookId: String, startFile: String) -> Unit,
    onOpenDiscovery: () -> Unit,
    // (b) Web由来カードの「縦書きPDFを取り込む」→ 取り込み画面（discovery/detail/{ncode}/import）への
    // ナビゲーション。navController は MainActivity が握るためコールバックで委譲する。
    onImportWebNovel: (ncode: String) -> Unit = {},
) {
    // Loading と Empty を型で区別する（F-O）。Loading 中はスケルトンを出し、
    // DB から Content(空) が確定して初めて空状態を表示することで cold start の空フラッシュを防ぐ。
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    // 各本の章数（bookId→章数）。カードの進捗行表示と状態フィルタ判定の単一真実源（VM が一括で数える）。
    val chapterCountMap by viewModel.chapterCountMap.collectAsStateWithLifecycle()
    // 続きありバッジ用のなろう詳細（key=ncode）。VM が本棚一覧の紐付け作品をまとめて照会し配布する。
    val newEpisodeNovelMap by viewModel.newEpisodeNovelMap.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // PDF ファイル選択ランチャー
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addBook(it) }
    }

    // 通知権限ランチャー（Android 13+）。権限結果に関わらずPDF選択を開始
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pdfPicker.launch(arrayOf("application/pdf"))
    }

    // 「二度と表示しない」フラグをSharedPreferencesで永続化、mutableStateOfでリアルタイム反映
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var batteryDialogDismissed by remember { mutableStateOf(prefs.getBoolean("battery_dialog_dismissed", false)) }

    // バッテリー最適化除外ダイアログの表示フラグ（onFabClickより先に宣言必須）。
    // rememberSaveable: 回転・ダーク切替（Activity 再生成）でダイアログと「二度と表示しない」
    // チェック入力が消えないよう保持する（M4）。
    var showBatteryOptDialog by rememberSaveable { mutableStateOf(false) }
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }

    // グリッド/リスト表示の切り替え状態（SharedPreferencesで永続化）。
    // 永続化を伴うためルート層で所有し、値とトグルを描画層へ渡す（描画層は VM/prefs 非依存に保つ）。
    // 既定=false（リスト＝文字目録）: 骨格3「文字目録」を本棚の既定骨格に採用（表紙を持たない
    // このアプリでは題字主役の目録が素直＝生成書影を捨てて装画を捏造しない）。グリッドは切替で残す
    // （実機で要否を詰める。将来グリッドを廃するならこのトグルと GridBookCard 経路ごと整理する）。
    var isGridView by remember { mutableStateOf(prefs.getBoolean("is_grid_view", false)) }

    // (b) Web由来カードの Custom Tabs 二重起動ガード（NovelDetailScreen と同じ機序）。
    var lastWebLaunchAt by remember { mutableStateOf(0L) }

    // 削除UIの方式（SharedPreferencesで永続化）。0=長押しメニュー / 1=⋮メニュー。
    // なぜトグルで両方式を残すか: 2方式を実機で触り比べて採用方式を決めるための一時機構。
    // 採用方式が確定したら、他方の分岐とこのトグル自体を削除する。
    // 既定を 1(⋮メニュー) にした理由（UX監査 M5）: 既定 0(長押しのみ) は削除手段に可視の
    // 手がかりが無く、長押しを知らないユーザーは本を削除できない（発見不能）。視覚言語D の
    // フラット構図は崩したくないが、削除の発見性を優先し、既存トークンの控えめな⋮を既定で出す。
    // 長押し経路も残す（⋮に気づかない層のフォールバック）。トグルで長押しのみへ戻せる。
    var deleteUiMode by remember { mutableStateOf(prefs.getInt("delete_ui_mode", 1)) }

    // PDF選択を実際に開始するヘルパー（通知権限チェック後に呼ぶ）
    val launchPdfPicker: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                pdfPicker.launch(arrayOf("application/pdf"))
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            pdfPicker.launch(arrayOf("application/pdf"))
        }
    }

    // FAB タップ時: 未確認かつバッテリー最適化が有効なら除外を促してからPDF選択へ。
    // なぜ処理中もブロックしないか: Service 側がキュー（ArrayDeque）で複数PDFを
    // 逐次処理できるため。処理中に追加された分はキュー末尾に積まれ、
    // バナーと通知に「N件目/全M件」として反映される。
    val onFabClick: () -> Unit = {
        val pm = context.getSystemService(PowerManager::class.java)
        val needsWarning = !batteryDialogDismissed &&
            !pm.isIgnoringBatteryOptimizations(context.packageName)
        if (needsWarning) {
            doNotShowAgain = false
            showBatteryOptDialog = true
        } else {
            launchPdfPicker()
        }
    }

    BookshelfContent(
        uiState = uiState,
        progressMap = progressMap,
        chapterCountMap = chapterCountMap,
        newEpisodeNovelMap = newEpisodeNovelMap,
        processingState = processingState,
        appTheme = appTheme,
        onThemeChange = onThemeChange,
        isGridView = isGridView,
        onToggleView = {
            isGridView = !isGridView
            prefs.edit().putBoolean("is_grid_view", isGridView).apply()
        },
        deleteUiMode = deleteUiMode,
        onToggleDeleteMode = {
            deleteUiMode = if (deleteUiMode == 1) 0 else 1
            prefs.edit().putInt("delete_ui_mode", deleteUiMode).apply()
        },
        onFabClick = onFabClick,
        // 開く際の再開ファイル解決（suspend の DB 参照）はルート層の責務。描画層は BookEntity を渡すだけ。
        onOpenBook = { book ->
            scope.launch {
                val lastReadFile = viewModel.getLastRead(book.id) ?: "index.html"
                onOpenBook(book.id, lastReadFile)
            }
        },
        onDeleteBook = { viewModel.deleteBook(it) },
        onOpenDiscovery = onOpenDiscovery,
        onCancelProcessing = { viewModel.cancelProcessing() },
        snackbarHostState = snackbarHostState,
        // (b) Web由来カード: なろうを Custom Tabs で開く（加工なし送客＝ADR 0010）。
        // 再入ガードは NovelDetailScreen の「なろうで読む」と同じ機序（連打で2枚開くのを防ぐ）。
        onOpenWebNovel = { novel ->
            val now = System.currentTimeMillis()
            if (now - lastWebLaunchAt >= 1000L) {
                lastWebLaunchAt = now
                openInAppBrowser(context, narouWorkUrl(Ncode(novel.ncode)))
            }
        },
        onImportWebNovel = { novel -> onImportWebNovel(novel.ncode) },
        onRemoveWebNovel = { viewModel.removeWebNovel(it.ncode) },
    )

    // エラーは一度きりのイベントとして Channel から受信し Snackbar 表示する（VM イベント購読＝ルート層の責務）。
    // collect は画面の生存期間中ずっと購読し続ければよいので key は Unit。
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { event ->
            // 取込失敗（retryUri あり）は「再試行」を出し、押されたら同一 URI を再投入する（M7）。
            // それ以外（強制終了リカバリ等の情報通知）は従来どおり「閉じる」のみ。
            if (event.retryUri != null) {
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = "再試行",
                    // 失敗の再試行は見落とすと復旧手段を失うため長め表示にする。
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.retryImport(event.retryUri)
                }
            } else {
                snackbarHostState.showSnackbar(message = event.message, actionLabel = "閉じる")
            }
        }
    }

    // バッテリー最適化除外ダイアログ（プラットフォーム設定への遷移を伴うためルート層に置く）
    if (showBatteryOptDialog) {
        val dismiss: (openSettings: Boolean) -> Unit = { openSettings ->
            if (doNotShowAgain) {
                prefs.edit().putBoolean("battery_dialog_dismissed", true).apply()
                batteryDialogDismissed = true  // リアルタイムで状態に反映
            }
            showBatteryOptDialog = false
            if (openSettings) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } else {
                launchPdfPicker()
            }
        }
        AlertDialog(
            onDismissRequest = { dismiss(false) },
            title = { Text("バックグラウンド処理について") },
            text = {
                Column {
                    Text("ホーム画面に移動するとPDF変換が途中で止まる場合があります。\n\n【推奨設定】\n設定 → バッテリー → アプリごとの消費管理 → NovelReader → バックグラウンドアクティビティを許可\n\n「設定を開く」でバッテリー設定画面に移動します。")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = doNotShowAgain,
                            onCheckedChange = { doNotShowAgain = it },
                        )
                        Text("二度と表示しない", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dismiss(true) }) { Text("設定を開く") }
            },
            dismissButton = {
                TextButton(onClick = { dismiss(false) }) { Text("このまま続ける") }
            },
        )
    }
}

/**
 * 本棚の描画層（stateless / UI 分割の content）。BookshelfScreen からの純移動。
 * VM や SharedPreferences・ランチャー等のプラットフォーム依存を持たず、[uiState]＋コールバックだけで
 * 一覧/空/スケルトンの分岐と各操作の結線を描画する葉。これにより Robolectric UI テスト（ADR 0009）が
 * VM 抜きで組める。メニュー開閉・FAB 展開・削除確認の対象といった画面ローカルな UI 状態のみ内部に残す
 * （過剰な hoisting は避ける）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfContent(
    uiState: BookshelfUiState,
    progressMap: Map<String, ProgressEntity>,
    // 各本の章数（bookId→章数）。カードの進捗行と状態フィルタ判定が共有する。既定 emptyMap は
    // 既存テスト・呼び出しの互換のため（章数 0＝進捗行「未読」表示で従来どおり成立する）。
    chapterCountMap: Map<String, Int> = emptyMap(),
    // 続きありバッジ用のなろう詳細（key=ncode）。カードは自分の book.ncode 分を引いて突き合わせる。
    newEpisodeNovelMap: Map<String, NarouNovel>,
    processingState: ProcessingState,
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    deleteUiMode: Int,
    onToggleDeleteMode: () -> Unit,
    onFabClick: () -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onOpenDiscovery: () -> Unit,
    onCancelProcessing: () -> Unit,
    snackbarHostState: SnackbarHostState,
    // (b) Web由来・未取込カードの操作。既定 no-op は既存テスト・呼び出しの互換のため
    // （Web カードが無い状態では従来の描画・結線が完全に不変であることを保つ）。
    onOpenWebNovel: (WebNovelEntity) -> Unit = {},
    onImportWebNovel: (WebNovelEntity) -> Unit = {},
    onRemoveWebNovel: (WebNovelEntity) -> Unit = {},
) {
    val isLoading = uiState is BookshelfUiState.Loading
    val books = (uiState as? BookshelfUiState.Content)?.books ?: emptyList()
    val webNovels = (uiState as? BookshelfUiState.Content)?.webNovels ?: emptyList()

    // 読書状態フィルタの選択（「すべて/よみかけ/未読/読了」＝モック .filters）。回転・再生成で選択が
    // 飛ばないよう rememberSaveable で保持する。ReadingStatus enum は直接 Saveable でないため
    // name(String) で保存し、復元時に entries から引き直す（null＝「すべて」）。
    var selectedStatusName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedStatus = selectedStatusName?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } }

    // 蔵書と Web由来を「最近の活動順」で1本に混在させる（bookshelf-fusion-D の並置。純関数で合成）。
    // 前段で読書状態フィルタを噛ませる（選択中は該当蔵書のみ・Web カードは落とす＝filterShelfByStatus の why）。
    val shelfItems = remember(books, webNovels, progressMap, selectedStatus, chapterCountMap) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(books, webNovels, selectedStatus, progressMap, chapterCountMap)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb)
    }
    val isProcessing = processingState.isProcessing

    // 本棚トップバーの⋮オーバーフロー（テーマ切替＋開発トグル）の開閉状態
    var showOverflowMenu by remember { mutableStateOf(false) }

    // 削除確認ダイアログの対象。回転・ダーク切替（Activity 再生成）で確認ダイアログが消えないよう
    // rememberSaveable で保持する（M4）。BookEntity は Saveable でないため bookId(String) だけ保存し、
    // 実体は books から都度解決する（対象が一覧から消えたら null＝ダイアログは自然に閉じる）。
    var bookToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val bookToDelete = bookToDeleteId?.let { id -> books.firstOrNull { it.id == id } }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // FAB 展開/縮小: 下方向スクロール中は縮小、上スクロールで展開
    // 先頭位置の有無ではなく「直近の移動方向」で判定するため snapshotFlow で追う
    var fabExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(isGridView) {
        // isGridView が変わったら展開状態をリセットし、新しいリストを監視し直す
        fabExpanded = true
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow {
            if (isGridView) {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            } else {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
        }.collect { (index, offset) ->
            // 前回より下に移動していれば縮小、それ以外（上移動/停止）は展開
            val scrollingDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
            fabExpanded = !scrollingDown
            prevIndex = index
            prevOffset = offset
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            Column {
                // TopAppBar を固定表示。スクロール連動を廃止。
                // scrollBehavior による吸着アニメーションがもたつき感の原因だったため完全に除去。
                TopAppBar(
                    title = {
                        // モック .top h1: 明朝・字間広め・中肉。余白主導のエディトリアル題字。
                        Text(
                            "本棚",
                            fontFamily = MinchoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 26.sp,
                            letterSpacing = 2.sp,
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenDiscovery) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "小説を探す",
                            )
                        }
                        // グリッド/リスト切り替え（モック .top の第1アクション）。永続化はルート層の onToggleView に委譲。
                        IconButton(onClick = onToggleView) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                contentDescription = if (isGridView) "リスト表示" else "グリッド表示",
                            )
                        }
                        // ⋮ オーバーフロー（モック .top の第2アクション）。
                        // テーマ切替を本棚からも行えるようにする（読書と同じ単一正本 appTheme を変更）。
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                Text(
                                    "テーマ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                )
                                // ライト/セピア/ダーク。選択中に藍のチェックを付ける。
                                ReadingTheme.values().forEach { theme ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (theme) {
                                                    ReadingTheme.LIGHT -> "ライト"
                                                    ReadingTheme.SEPIA -> "セピア"
                                                    ReadingTheme.DARK -> "ダーク"
                                                }
                                            )
                                        },
                                        onClick = {
                                            onThemeChange(theme)
                                            showOverflowMenu = false
                                        },
                                        leadingIcon = {
                                            // 選択中のみチェック表示（未選択はアイコン領域を空けて字頭を揃える）
                                            if (appTheme == theme) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "選択中",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                Spacer(Modifier.width(24.dp))
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider()
                                // 開発用: 削除UI方式トグル（一時機構。採用方式が確定したらこの項目ごと削除予定）。
                                DropdownMenuItem(
                                    text = { Text("削除方式: " + if (deleteUiMode == 1) "⋮メニュー" else "長押し") },
                                    onClick = {
                                        onToggleDeleteMode()
                                        showOverflowMenu = false
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                // PDF処理中バナー（TopAppBar直下からスライドイン）
                AnimatedVisibility(
                    visible = isProcessing,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                ) {
                    ProcessingBanner(
                        processingState = processingState,
                        onStop = onCancelProcessing,
                        // 幅指定は呼び出し側の責務。従来の内部 fillMaxWidth と同じ描画を維持。
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("PDFを追加") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onFabClick,
                expanded = fabExpanded,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLoading) {
                // 初回DB発行前は表紙スケルトンを出す（F-O）。Content(空) が確定するまで空状態を出さない
                // ことで cold start の空フラッシュ（Loading と Empty の混同）を防ぐ。
                BookshelfSkeleton(isGridView = isGridView, modifier = Modifier.fillMaxSize())
            } else {
              // 状態フィルタ絞り込み中の0件は「蔵書ゼロ」ではないため EmptyBookshelf を出さない
              // （チップ行を出し続けて「すべて」へ戻れる導線を保つ。下の Lazy 側で該当なし文言を出す）。
              if (shelfItems.isEmpty() && !isProcessing && selectedStatus == null) {
                // 空状態。サイズ指定は呼び出し側の責務（fillMaxSize は従来と同じ描画）。
                // なぜ else-if で空のグリッド/リストを合成しないか: 以前は空状態の上にも
                // fillMaxSize の Lazy コンテナが重なっており、scrollable が hit test 上で
                // 下層の「PDFを追加する」ボタンを遮蔽してタップ不能だった（Robolectric の
                // 結線テストで検出した実バグ）。空棚では Lazy 側に描くものが無いため、
                // 排他分岐にして遮蔽を根元から無くす。
                EmptyBookshelf(onAddClick = onFabClick, modifier = Modifier.fillMaxSize())
              } else if (isGridView) {
                // ────── グリッドレイアウト ──────
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    // bottom にFAB分の余白を足す。FABは浮動でレイアウト領域を予約しないため、
                    // これがないと最終行のカード（削除ボタン等）がFABに隠れてタップできない。
                    // ナビバーインセットはScaffoldのinnerPadding(Box.padding)で吸収済みなので二重加算しない。
                    // モック D は余白主導。列間20/行間26相当へ広げ、左右も24px相当の余白を取る。
                    contentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 見つける導線帯（モック fusion .find-guide）。空棚では EmptyBookshelf と重なるため出さない
                    // （状態フィルタ絞り込み中は0件でもチップ行ごと出し続ける）。
                    if (shelfItems.isNotEmpty() || selectedStatus != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FindGuideBand(onClick = onOpenDiscovery)
                        }
                        // 読書状態フィルタのチップ行（モック .filters は常設のため FindGuideBand と同じ条件で常時出す）。
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StatusChipRow(
                                selectedStatus = selectedStatus,
                                onSelect = { selectedStatusName = it?.name },
                            )
                        }
                        if (selectedStatus != null && shelfItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { StatusFilterEmptyText() }
                        }
                    }
                    items(shelfItems, key = { it.key }) { item ->
                        when (item) {
                            is ShelfItem.Book -> GridBookCard(
                                book = item.book,
                                progress = progressMap[item.book.id],
                                novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                totalChaps = chapterCountMap[item.book.id] ?: 0,
                                onOpen = { onOpenBook(item.book) },
                                onDelete = { bookToDeleteId = item.book.id },
                                deleteUiMode = deleteUiMode,
                                // 削除時の詰め直しアニメ。旧animateItemPlacementはFoundation1.6系で高速フリング中に
                                // カバーが画面外の古い位置から補間され重なる既知不具合があり一時撤去していたが、
                                // BOM 2025.02.00(Foundation 1.7系)でstable化したanimateItem()に置き換えて復活（案B）。
                                modifier = Modifier.animateItem(),
                            )
                            // (b) Web由来カード。外す操作は確認ダイアログを挟まない: 蔵書削除と違い
                            // 読書進捗等の失うものが無く、詳細画面の「本棚に置く」で即座に戻せるため。
                            is ShelfItem.Web -> WebGridBookCard(
                                novel = item.novel,
                                onOpen = { onOpenWebNovel(item.novel) },
                                onImport = { onImportWebNovel(item.novel) },
                                onRemove = { onRemoveWebNovel(item.novel) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            } else {
                // ────── リストレイアウト ──────
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // bottom にFAB分の余白を足す（グリッドと同理由＝最終行がFABに隠れるのを防ぐ）。
                    // 行間スペーシングは置かない: 各行が自前の縦余白＋下ヘアラインで区切るモック .li 準拠のため。
                    contentPadding = PaddingValues(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 96.dp),
                ) {
                    // 見つける導線帯（グリッドと同一・リストは行間スペーシングが無いため下余白を帯側に持たせる）
                    // （状態フィルタ絞り込み中は0件でもチップ行ごと出し続ける＝グリッドと同じ判断）
                    if (shelfItems.isNotEmpty() || selectedStatus != null) {
                        item {
                            FindGuideBand(
                                onClick = onOpenDiscovery,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        // 読書状態フィルタのチップ行（モック .filters は常設。グリッドと同じ判断）。
                        item {
                            StatusChipRow(
                                selectedStatus = selectedStatus,
                                onSelect = { selectedStatusName = it?.name },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (selectedStatus != null && shelfItems.isEmpty()) {
                            item { StatusFilterEmptyText() }
                        }
                    }
                    items(shelfItems, key = { it.key }) { item ->
                        when (item) {
                            is ShelfItem.Book -> ListBookCard(
                                book = item.book,
                                progress = progressMap[item.book.id],
                                novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                totalChaps = chapterCountMap[item.book.id] ?: 0,
                                onOpen = { onOpenBook(item.book) },
                                onDelete = { bookToDeleteId = item.book.id },
                                deleteUiMode = deleteUiMode,
                                // グリッドと同理由: 1.7系でstable化したanimateItem()で詰め直しアニメを復活（案B）。
                                modifier = Modifier.animateItem(),
                            )
                            // グリッドと同じ判断（確認ダイアログ無し＝失うものが無く即座に戻せる）。
                            is ShelfItem.Web -> WebListBookCard(
                                novel = item.novel,
                                onOpen = { onOpenWebNovel(item.novel) },
                                onImport = { onImportWebNovel(item.novel) },
                                onRemove = { onRemoveWebNovel(item.novel) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
              }
            }
        }
    }

    // 削除確認ダイアログ（books＋onDeleteBook のみに依存する純 UI のため描画層に残す）
    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDeleteId = null },
            title = { Text("削除の確認") },
            text = { Text("「${book.title}」を削除しますか？\n読書進捗も削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBook(book)
                    bookToDeleteId = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookToDeleteId = null }) { Text("キャンセル") }
            },
        )
    }
}

// ============================================================
// 読書状態フィルタのチップ行（モック bookshelf-mokuroku-D .filters/.fc）
// 「すべて／よみかけ／未読／読了」の固定4チップをモック順に横スクロール1行で並べる。
// チップ意匠は検索画面の FilterChipItem（.rchip 翻訳の正本＝11.5sp・角丸2dp・ヘアライン→選択で藍）を
// そのまま流用する（モック .fc は同じ意匠系のため、本棚用に部品を増やさない）。
// ============================================================
@Composable
private fun StatusChipRow(
    selectedStatus: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 「すべて」＝選択なし（null）。モックどおり既定選択。
        FilterChipItem(
            selected = selectedStatus == null,
            label = "すべて",
            onClick = { onSelect(null) },
        )
        // よみかけ／未読／読了。ReadingStatus と表示名・並びの対応はここが唯一の正本（モック .filters 順）。
        FilterChipItem(
            selected = selectedStatus == ReadingStatus.READING,
            label = "よみかけ",
            onClick = { onSelect(ReadingStatus.READING) },
        )
        FilterChipItem(
            selected = selectedStatus == ReadingStatus.UNREAD,
            label = "未読",
            onClick = { onSelect(ReadingStatus.UNREAD) },
        )
        FilterChipItem(
            selected = selectedStatus == ReadingStatus.FINISHED,
            label = "読了",
            onClick = { onSelect(ReadingStatus.FINISHED) },
        )
    }
}

// 状態フィルタで該当0件のときの静かな案内（EmptyBookshelf は蔵書ゼロ専用のため使わない）。
@Composable
private fun StatusFilterEmptyText() {
    Text(
        text = "この分類の本はありません",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

// ============================================================
// 見つける導線帯（モック bookshelf-fusion-D .find-guide）
// 本棚先頭に置く静かな発見入口。TopAppBar の🔍と役割が重なるが、モックは両方持つ
// （帯＝発見機能を知らない人への明示導線／🔍＝知っている人の常設ショートカット）。
// ============================================================
@Composable
private fun FindGuideBand(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "新しい物語を見つける",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ============================================================
// 本棚スケルトン（F-O）: DB 初回発行前に表紙の場所取りを出し、cold start の空フラッシュを防ぐ。
// 既存カード寸法（グリッド=2列・書影2:3／リスト=46×69）に合わせた静的プレースホルダ。
// 意匠を発明しないため新規色は使わず surfaceVariant/outlineVariant トークンのみで構成する。
// シマー等のアニメは付けない（既存画面に同型の演出が無く、最小の同型要素に留めるため）。
// ============================================================
@Composable
private fun BookshelfSkeleton(
    isGridView: Boolean,
    modifier: Modifier = Modifier,
) {
    // プレースホルダの塗り（トークン経由・直書き回避）。書影は少し濃く、文字行は薄く。
    val blockColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    if (isGridView) {
        // グリッド: 実カードと同じ左右24dp・列間20dp・行間26dp。3行ぶん(6枚)出す。
        Column(
            modifier = modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    repeat(2) {
                        Column(modifier = Modifier.weight(1f)) {
                            // 書影（2:3・角丸2px）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(blockColor),
                            )
                            Spacer(Modifier.height(11.dp))
                            // タイトル2行ぶん
                            SkeletonLine(color = lineColor, widthFraction = 0.9f)
                            Spacer(Modifier.height(6.dp))
                            SkeletonLine(color = lineColor, widthFraction = 0.6f)
                            Spacer(Modifier.height(9.dp))
                            // 進捗行
                            SkeletonLine(color = lineColor, widthFraction = 0.7f)
                        }
                    }
                }
            }
        }
    } else {
        // リスト（文字目録）: 実行と同じ左右24dp・6行ぶん。表紙は無く、左端の色帯＋題字/進捗の場所取り。
        Column(modifier = modifier.padding(start = 24.dp, top = 4.dp, end = 24.dp)) {
            repeat(6) {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .padding(top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左端の色帯プレースホルダ（実カードの作品識別色の場所取り）。
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(blockColor),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // 明朝題字2行ぶん
                        SkeletonLine(color = lineColor, widthFraction = 0.85f)
                        Spacer(Modifier.height(7.dp))
                        SkeletonLine(color = lineColor, widthFraction = 0.55f)
                        Spacer(Modifier.height(11.dp))
                        // 進捗行
                        SkeletonLine(color = lineColor, widthFraction = 0.5f)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = lineColor)
            }
        }
    }
}

/** スケルトンの1行分プレースホルダ（角丸の細い横棒）。 */
@Composable
private fun SkeletonLine(
    color: androidx.compose.ui.graphics.Color,
    widthFraction: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(11.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}
