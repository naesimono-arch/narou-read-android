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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.novelreader.data.BookEntity
import com.novelreader.ui.components.BookCover
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.ProcessingState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onOpenBook: (bookId: String, startFile: String) -> Unit,
) {
    val books by viewModel.books.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val useNativeReader by viewModel.useNativeReader.collectAsState()
    // 3点メニューの開閉状態（Phase 4 で useNativeReader フラグ削除時にこのメニューも削除すること）
    var showMenu by remember { mutableStateOf(false) }
    val isProcessing = processingState.isProcessing
    val errorMessage by viewModel.errorMessage.collectAsState()
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

    // FAB タップ時: 未確認かつバッテリー最適化が有効なら除外を促してからPDF選択へ
    val onFabClick: () -> Unit = {
        if (!isProcessing) {
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
                        Text(
                            "本棚",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        // グリッド/リスト切り替えボタン
                        IconButton(onClick = {
                            isGridView = !isGridView
                            prefs.edit().putBoolean("is_grid_view", isGridView).apply()
                        }) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                contentDescription = if (isGridView) "リスト表示" else "グリッド表示",
                            )
                        }
                        // 設定メニュー（Phase 4 で WebView 削除時にこの Box ごと削除すること）
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "設定")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text("ネイティブ読書（試験中）")
                                            Switch(
                                                checked = useNativeReader,
                                                // なぜ null か: DropdownMenuItem の onClick でまとめて処理するため
                                                onCheckedChange = null,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setUseNativeReader(!useNativeReader)
                                        showMenu = false
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
                    ProcessingBanner(processingState = processingState)
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            modifier = Modifier.animateItemPlacement(),
                        )
                    }
                }
            } else {
                // ────── リストレイアウト ──────
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            modifier = Modifier.animateItemPlacement(),
                        )
                    }
                }
            }
        }
    }

    // エラー Snackbar（snackbarHostState 経由で表示、dismissで clearError を呼ぶ）
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, actionLabel = "閉じる")
            viewModel.clearError()
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

// ============================================================
// グリッド用書籍カード
// ============================================================
@Composable
private fun GridBookCard(
    book: BookEntity,
    lastRead: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalChaps by produceState(initialValue = 0, key1 = book.id) {
        value = withContext(Dispatchers.IO) {
            File(book.htmlDirPath)
                .listFiles { f -> f.name.matches(Regex("chap_\\d+\\.html")) }
                ?.size ?: 0
        }
    }

    val chapNum = lastRead
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

    val progressFraction = if (chapNum != null && totalChaps > 0) {
        chapNum.toFloat() / totalChaps.toFloat()
    } else null

    // タップ時にスケールダウンするアニメーション（Apple Books 的な触感）
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "gridCardScale",
    )

    Surface(
        onClick = onOpen,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        interactionSource = interactionSource,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // 書影（縦横比 2:3）
            Box(modifier = Modifier.fillMaxWidth()) {
                BookCover(
                    bookId = book.id,
                    title = book.title,
                    author = book.author,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
                // 削除ボタン（右上に重ねて配置）
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (progressFraction != null) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                } else {
                    Text(
                        text = "未読",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

// ============================================================
// リスト用書籍カード
// ============================================================
@Composable
private fun ListBookCard(
    book: BookEntity,
    lastRead: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalChaps by produceState(initialValue = 0, key1 = book.id) {
        value = withContext(Dispatchers.IO) {
            File(book.htmlDirPath)
                .listFiles { f -> f.name.matches(Regex("chap_\\d+\\.html")) }
                ?.size ?: 0
        }
    }

    val chapNum = lastRead
        ?.takeIf { it.startsWith("chap_") }
        ?.removePrefix("chap_")?.removeSuffix(".html")?.toIntOrNull()

    val progressFraction = if (chapNum != null && totalChaps > 0) {
        chapNum.toFloat() / totalChaps.toFloat()
    } else null

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listCardScale",
    )

    Surface(
        onClick = onOpen,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        interactionSource = interactionSource,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 小さい書影
            BookCover(
                bookId = book.id,
                title = book.title,
                author = book.author,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (progressFraction != null) {
                    val percent = (progressFraction * 100).toInt()
                    Text(
                        text = "第${chapNum}話 · $percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                } else {
                    Text(
                        text = "未読",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

// ============================================================
// 空状態（本が1冊もないとき）
// ============================================================
@Composable
private fun EmptyBookshelf(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Canvas で描く空の本棚イラスト
        Canvas(
            modifier = Modifier.size(140.dp),
        ) {
            val w = size.width
            val h = size.height
            val color = androidx.compose.ui.graphics.Color(0xFFD7C6BF)

            // 棚板（上下2本）
            drawLine(color, start = Offset(0f, h * 0.30f), end = Offset(w, h * 0.30f), strokeWidth = 3.dp.toPx())
            drawLine(color, start = Offset(0f, h * 0.72f), end = Offset(w, h * 0.72f), strokeWidth = 3.dp.toPx())

            // 縦柱（左右）
            drawLine(color, start = Offset(w * 0.05f, h * 0.20f), end = Offset(w * 0.05f, h * 0.80f), strokeWidth = 3.dp.toPx())
            drawLine(color, start = Offset(w * 0.95f, h * 0.20f), end = Offset(w * 0.95f, h * 0.80f), strokeWidth = 3.dp.toPx())

            // 中央に小さな本シルエット3冊（薄い）
            val bookColor = color.copy(alpha = 0.4f)
            val bw = w * 0.12f
            val bh = h * 0.30f
            val by = h * 0.35f
            listOf(0.30f, 0.46f, 0.62f).forEach { cx ->
                drawRect(bookColor, topLeft = Offset(w * cx - bw / 2, by), size = Size(bw, bh))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "本棚はまだ空です",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "右下の＋からPDFを追加してください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        FilledTonalButton(onClick = onAddClick) {
            Text("PDFを追加する")
        }
    }
}

// ============================================================
// 処理中バナー（TopAppBar直下からスライドイン）
// ============================================================
@Composable
private fun ProcessingBanner(processingState: ProcessingState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = processingState.phase.ifEmpty { "PDF処理中…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            // ステッパーインジケーター
            val stepLabels = listOf("タイトル", "本文", "分割", "HTML")
            StepperIndicator(
                stepIndex = processingState.stepIndex,
                stepTotal = processingState.stepTotal,
                labels = stepLabels,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // プログレスバー（ステップ切替時は瞬時リセット、通常時はtweenでアニメーション）
            val progress = remember { Animatable(0f) }
            var lastStep by remember { mutableIntStateOf(-1) }
            LaunchedEffect(processingState.stepIndex, processingState.stepLocalPercent) {
                if (processingState.stepIndex != lastStep) {
                    progress.snapTo(0f)
                    lastStep = processingState.stepIndex
                }
                progress.animateTo(
                    targetValue = processingState.stepLocalPercent,
                    animationSpec = tween(durationMillis = 400),
                )
            }
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ステップ ${processingState.stepIndex + 1}/${processingState.stepTotal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

// ============================================================
// ステッパーインジケーター
// ============================================================
@Composable
private fun StepperIndicator(
    stepIndex: Int,
    stepTotal: Int,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(stepTotal) { i ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (i <= stepIndex) primary else outline),
                )
                if (i < stepTotal - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (i < stepIndex) primary else outline),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == stepIndex) primary else outline,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
