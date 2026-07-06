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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.novelreader.data.BookEntity
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    // 見た目テーマの単一正本（読書と共有）。本棚の⋮メニューからも切り替えられるようにする。
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onOpenBook: (bookId: String, startFile: String) -> Unit,
) {
    val books by viewModel.books.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val isProcessing = processingState.isProcessing
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

    // バッテリー最適化除外ダイアログの表示フラグ（onFabClickより先に宣言必須）
    var showBatteryOptDialog by remember { mutableStateOf(false) }
    var doNotShowAgain by remember { mutableStateOf(false) }

    // グリッド/リスト表示の切り替え状態（SharedPreferencesで永続化）
    var isGridView by remember { mutableStateOf(prefs.getBoolean("is_grid_view", true)) }

    // 削除UIの方式（SharedPreferencesで永続化）。0=長押しメニュー / 1=⋮メニュー。
    // なぜトグルで両方式を残すか: 2方式を実機で触り比べて採用方式を決めるための一時機構。
    // 採用方式が確定したら、他方の分岐とこのトグル自体を削除する。
    // 既定を 0(長押し) にした理由: 視覚言語D（モック bookshelf-D）は削除アフォーダンスを持たない
    // フラットな構図のため、既定で⋮を出さない長押し方式がモック準拠。⋮方式はトグルで opt-in。
    var deleteUiMode by remember { mutableStateOf(prefs.getInt("delete_ui_mode", 0)) }

    // 本棚トップバーの⋮オーバーフロー（テーマ切替＋開発トグル）の開閉状態
    var showOverflowMenu by remember { mutableStateOf(false) }

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

    // 削除確認ダイアログ用の状態
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }

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
                        // グリッド/リスト切り替え（モック .top の第1アクション）
                        IconButton(onClick = {
                            isGridView = !isGridView
                            prefs.edit().putBoolean("is_grid_view", isGridView).apply()
                        }) {
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
                                        deleteUiMode = if (deleteUiMode == 1) 0 else 1
                                        prefs.edit().putInt("delete_ui_mode", deleteUiMode).apply()
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
                        onStop = { viewModel.cancelProcessing() },
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
            if (books.isEmpty() && !isProcessing) {
                // 空状態
                EmptyBookshelf(onAddClick = onFabClick)
            }

            if (isGridView) {
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
                    items(books, key = { it.id }) { book ->
                        GridBookCard(
                            book = book,
                            lastRead = progressMap[book.id],
                            onOpen = {
                                scope.launch {
                                    val lastReadFile = viewModel.getLastRead(book.id) ?: "index.html"
                                    onOpenBook(book.id, lastReadFile)
                                }
                            },
                            onDelete = { bookToDelete = book },
                            deleteUiMode = deleteUiMode,
                            // 削除時の詰め直しアニメ。旧animateItemPlacementはFoundation1.6系で高速フリング中に
                            // カバーが画面外の古い位置から補間され重なる既知不具合があり一時撤去していたが、
                            // BOM 2025.02.00(Foundation 1.7系)でstable化したanimateItem()に置き換えて復活（案B）。
                            modifier = Modifier.animateItem(),
                        )
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
                    items(books, key = { it.id }) { book ->
                        ListBookCard(
                            book = book,
                            lastRead = progressMap[book.id],
                            onOpen = {
                                scope.launch {
                                    val lastReadFile = viewModel.getLastRead(book.id) ?: "index.html"
                                    onOpenBook(book.id, lastReadFile)
                                }
                            },
                            onDelete = { bookToDelete = book },
                            deleteUiMode = deleteUiMode,
                            // グリッドと同理由: 1.7系でstable化したanimateItem()で詰め直しアニメを復活（案B）。
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    // エラーは一度きりのイベントとして Channel から受信し Snackbar 表示する。
    // collect は画面の生存期間中ずっと購読し続ければよいので key は Unit。
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { msg ->
            snackbarHostState.showSnackbar(message = msg, actionLabel = "閉じる")
        }
    }

    // バッテリー最適化除外ダイアログ
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

    // 削除確認ダイアログ
    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("削除の確認") },
            text = { Text("「${book.title}」を削除しますか？\n読書進捗も削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(book)
                    bookToDelete = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("キャンセル") }
            },
        )
    }
}
