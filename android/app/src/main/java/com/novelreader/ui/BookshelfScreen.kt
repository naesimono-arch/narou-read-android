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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.novelreader.data.BookEntity
import com.novelreader.ui.components.BookCover
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.ProcessingState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
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
                        // 削除UI方式の切り替えボタン（一時機構：採用方式確定後にトグルごと削除予定）
                        IconButton(onClick = {
                            deleteUiMode = if (deleteUiMode == 1) 0 else 1
                            prefs.edit().putInt("delete_ui_mode", deleteUiMode).apply()
                        }) {
                            Icon(
                                imageVector = if (deleteUiMode == 1) Icons.Filled.MoreVert else Icons.Outlined.DeleteOutline,
                                contentDescription = if (deleteUiMode == 1) "削除UI:⋮メニュー（タップで長押し方式へ）" else "削除UI:長押し（タップで⋮方式へ）",
                            )
                        }
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
                            // Foundation1.6系(BOM 2024.04.01)のanimateItemPlacementは高速フリング中に
                            // カバーが画面外の古い位置から補間され重なる既知不具合があるため使用しない。
                            // 詰め直しアニメは案B(BOM 2024.09+へ更新しanimateItem()へ置換)で別タスク復活予定。
                            modifier = Modifier,
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
                            // グリッドと同理由でanimateItemPlacementは使用しない（案B参照）。
                            modifier = Modifier,
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

// ============================================================
// 進捗行（モック .pr）: 「N話 + 細い藍バー + N%」を藍で、未読は青磁で表示。
// グリッド=バー伸縮(flexBar=true) / リスト=バー80dp固定(false)。
// 色は token 経由（primary=藍 #1C3D5A / secondary=青磁 #9CB3A8 / track=outlineVariant）＝直書き回避。
// ============================================================
@Composable
private fun BookProgressRow(
    totalChaps: Int,
    progressFraction: Float?,
    flexBar: Boolean,
) {
    if (progressFraction != null) {
        val percent = (progressFraction * 100).toInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${totalChaps}話",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = (if (flexBar) Modifier.weight(1f) else Modifier.width(80.dp))
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$percent%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        // 未読は青磁。素地上で低コントラスト（モック意図の「静かに沈める」）＝完全準拠のトレードオフ。
        Text(
            text = "未読",
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ============================================================
// グリッド用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBookCard(
    book: BookEntity,
    lastRead: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteUiMode: Int,
    modifier: Modifier = Modifier,
) {
    // 削除メニューの開閉状態（⋮タップ または 長押しで開く）
    var menuExpanded by remember { mutableStateOf(false) }

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

    // モック .bk: カード地・影・角丸チップを廃したフラット構図。書影＋メタを地に直接置く。
    // クリック=開く / 長押し=削除メニュー（モックは削除アフォーダンスを持たないため既定は長押しに集約）。
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onOpen,
                // 既定(0)は長押しで削除メニュー。⋮方式(1)を選んだ場合は書影上の⋮で開くため長押しは無効。
                onLongClick = if (deleteUiMode == 0) ({ menuExpanded = true }) else null,
            ),
    ) {
        // 書影（縦横比 2:3・角丸2px・下部に明朝タイトル焼き込み）
        Box(modifier = Modifier.fillMaxWidth()) {
            BookCover(
                bookId = book.id,
                title = book.title,
                showTitle = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(2.dp)),
            )
            // ⋮方式(1)のみ書影右上に削除ボタン（既定0では非表示＝モック準拠のフラット）。
            // Box は方式に関わらず DropdownMenu のアンカーとして常設する。
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                if (deleteUiMode == 1) {
                    // なぜスクリム背景を敷くか: 書影はタイトルハッシュ由来の任意の色相のため、
                    // アイコン単体では明色カバー上で視認できない。半透明黒＋白でコントラストを確保。
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .padding(6.dp)
                            .size(30.dp)
                            .background(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "メニュー",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                DeleteDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }

        Spacer(Modifier.height(11.dp))
        // メタ題字（明朝）。著者はモックのグリッドでは表示しない（リストのみ）。
        // 書影内タイトルと本欄タイトルが重複する点は完全準拠ゆえのトレードオフ（後日検証・調整）。
        Text(
            text = book.title,
            fontFamily = MinchoFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(9.dp))
        BookProgressRow(
            totalChaps = totalChaps,
            progressFraction = progressFraction,
            flexBar = true,
        )
    }
}

// ============================================================
// リスト用書籍カード
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListBookCard(
    book: BookEntity,
    lastRead: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteUiMode: Int,
    modifier: Modifier = Modifier,
) {
    // 削除メニューの開閉状態（⋮タップ または 長押しで開く）
    var menuExpanded by remember { mutableStateOf(false) }

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

    // モック .li: カード地・影を廃し、上下余白＋下ヘアラインで区切る静かな行。
    // 外側 Column が行本体(Row)と区切り線(HorizontalDivider)を束ねる。
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onOpen,
                    onLongClick = if (deleteUiMode == 0) ({ menuExpanded = true }) else null,
                )
                .padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 小さい書影（46×69・角丸2px・文字なしの色面のみ）
            BookCover(
                bookId = book.id,
                title = book.title,
                showTitle = false,
                modifier = Modifier
                    .width(46.dp)
                    .height(69.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontFamily = MinchoFamily,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = book.author,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                BookProgressRow(
                    totalChaps = totalChaps,
                    progressFraction = progressFraction,
                    flexBar = false,
                )
            }
            // 削除アフォーダンス。⋮方式(1)のみ行末にボタン。既定0は非表示（長押しで開く）。
            // Box は方式に関わらず DropdownMenu のアンカーとして常設する。
            Box {
                if (deleteUiMode == 1) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "メニュー",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DeleteDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }
        // 行下のヘアライン区切り（モック .li の border-bottom 1px）
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// ============================================================
// 削除メニュー（⋮タップ・長押し共通のドロップダウン）
// 一時機構：削除UIの採用方式が確定したら呼び出し側の分岐ごと整理する。
// ============================================================
@Composable
private fun DeleteDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("削除") },
            onClick = onDelete,
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
                // 複数件キューイング時のみ件数を付記（通知の表記と揃える）
                val queueSuffix = if (processingState.queueTotal > 1) {
                    "（${processingState.queueCurrent}/${processingState.queueTotal}件）"
                } else ""
                Text(
                    text = processingState.phase.ifEmpty { "PDF処理中…" } + queueSuffix,
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
