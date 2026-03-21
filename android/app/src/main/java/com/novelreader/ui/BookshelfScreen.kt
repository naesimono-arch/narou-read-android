package com.novelreader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.novelreader.data.BookEntity
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onOpenBook: (bookId: String, startFile: String) -> Unit,
) {
    val books by viewModel.books.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val isProcessing = processingState.isProcessing
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    // FAB タップ時の処理: Android 13+ は通知権限を確認してからPDF選択へ
    val onFabClick: () -> Unit = {
        if (!isProcessing) {
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
    }

    // 削除確認ダイアログ用の状態
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("本棚") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFabClick,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "PDFを追加")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (books.isEmpty() && !isProcessing) {
                // 空の本棚メッセージ
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Book,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("右下の＋ボタンでPDFを追加してください", color = MaterialTheme.colorScheme.outline)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(books, key = { it.id }) { book ->
                    ListItem(
                        headlineContent = { Text(book.title) },
                        trailingContent = {
                            IconButton(onClick = { bookToDelete = book }) {
                                Icon(Icons.Filled.Delete, contentDescription = "削除")
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                scope.launch {
                                    val lastRead = viewModel.getLastRead(book.id) ?: "index.html"
                                    onOpenBook(book.id, lastRead)
                                }
                            },
                        ),
                    )
                    HorizontalDivider()
                }
            }

            // PDF処理中インジケーター
            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .width(280.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val stepLabels = listOf("タイトル", "本文", "分割", "HTML")
                            StepperIndicator(
                                stepIndex = processingState.stepIndex,
                                stepTotal = processingState.stepTotal,
                                labels = stepLabels,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = processingState.phase.ifEmpty { "PDF処理中…" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            // ステップ切替時は瞬時リセット、通常時はtweenでアニメーション
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ステップ ${processingState.stepIndex + 1}/${processingState.stepTotal}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
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
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("キャンセル") }
            },
        )
    }

    // エラー Snackbar
    errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            viewModel.clearError()
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { viewModel.clearError() }) { Text("閉じる") } },
        ) { Text(msg) }
    }
}

@Composable
private fun StepperIndicator(
    stepIndex: Int,
    stepTotal: Int,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = modifier) {
        // ドットとライン
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(stepTotal) { i ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
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
        // ステップラベル
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
