package com.novelreader.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.model.TocEntry
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.ui.compose.RubyText
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.BookshelfViewModel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ネイティブ読書画面のエントリポイント。
 * startFile に応じて目次または章を表示する。
 *
 * @param bookId 書籍ID
 * @param startFile ナビゲーション引数で渡された初期ファイル名
 * @param htmlDirPath 章HTMLが格納されたディレクトリの絶対パス
 * @param viewModel BookshelfViewModel（進捗保存に使用）
 * @param onNavigateToBookshelf 本棚に戻るコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    bookId: String,
    startFile: String,
    htmlDirPath: String,
    viewModel: BookshelfViewModel,
    onNavigateToBookshelf: () -> Unit,
) {
    // なぜ rememberSaveable に bookId をキーとして含めるか:
    // ルートが reading/{bookId}/{startFile} なので NavBackStackEntry 単位でスコープされるが、
    // Navigation の実装詳細に依存しないよう bookId を明示的にキーに含めて
    // 書籍切替時の状態混線を防ぐ。
    var currentFile by rememberSaveable(key = "currentFile_$bookId") {
        mutableStateOf(startFile)
    }

    // 読書テーマ（ライト/セピア/ダーク）。SharedPreferences で永続化する。
    // なぜ runCatching で包むか: 不正値が保存されていた場合や将来 enum 名を変更した場合に
    // クラッシュせず LIGHT へフォールバックするため（防御的だが起動不能よりよい）。
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var readingTheme by remember {
        mutableStateOf(
            runCatching { ReadingTheme.valueOf(prefs.getString("reading_theme", "") ?: "") }
                .getOrDefault(ReadingTheme.LIGHT)
        )
    }
    val onThemeChange: (ReadingTheme) -> Unit = { theme ->
        readingTheme = theme
        prefs.edit().putString("reading_theme", theme.name).apply()
    }
    val readingColors = readingTheme.colors

    // ステータスバーアイコン色を読書テーマに合わせる。
    // なぜ DisposableEffect か: NovelReaderTheme 側の SideEffect は読書画面から
    // 本棚へ戻ったときに再実行される保証がないため、onDispose で必ず
    // システムテーマ準拠（ライト=暗アイコン）へ復元する必要がある。
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    if (!view.isInEditMode) {
        DisposableEffect(readingTheme, systemDark) {
            val controller = WindowCompat.getInsetsController(
                (view.context as Activity).window, view
            )
            controller.isAppearanceLightStatusBars = readingColors.isLight
            onDispose { controller.isAppearanceLightStatusBars = !systemDark }
        }
    }

    // パストラバーサル防御: currentFile が htmlDirPath 配下に収まることを保証。
    // なぜ canonicalPath で検証するか: "../../etc/passwd" のような相対パスが
    // htmlDirPath 外のファイルを指す可能性を排除するため。
    val resolvedFile = remember(currentFile, htmlDirPath) {
        val candidate = File(htmlDirPath, currentFile)
        val isUnderHtmlDir = candidate.canonicalPath.startsWith(
            File(htmlDirPath).canonicalPath + File.separator
        )
        when {
            isUnderHtmlDir && candidate.exists() -> currentFile
            File(htmlDirPath, "index.html").exists() -> "index.html"
            else -> null // エラー状態: htmlDirPath 自体が壊れている
        }
    }

    // 目次を非同期でロード（画面ライフサイクル中1回のみ）
    val tocEntries by produceState<List<TocEntry>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            ChapterHtmlParser.parseToc(File(htmlDirPath, "index.html"))
        }
    }

    if (resolvedFile == null) {
        // htmlDirPath 自体が存在しない致命的エラー（再試行不可）
        ReadingErrorScreen(
            message = "書籍データが見つかりません",
            colors = readingColors,
            onNavigateToBookshelf = onNavigateToBookshelf,
        )
        return
    }

    if (resolvedFile == "index.html") {
        NativeTableOfContentsScreen(
            tocEntries = tocEntries,
            colors = readingColors,
            onSelectChapter = { fileName ->
                currentFile = fileName
                viewModel.saveProgress(bookId, fileName)
            },
            onNavigateToBookshelf = onNavigateToBookshelf,
        )
        return
    }

    // 章表示
    ChapterScreen(
        currentFile = resolvedFile,
        htmlDirPath = htmlDirPath,
        tocEntries = tocEntries,
        readingTheme = readingTheme,
        onThemeChange = onThemeChange,
        onNavigateToBookshelf = onNavigateToBookshelf,
        onNavigateTo = { fileName ->
            currentFile = fileName
            if (fileName != "index.html") {
                viewModel.saveProgress(bookId, fileName)
            }
        },
    )
}

/** 章本文を表示する内部 Composable */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterScreen(
    currentFile: String,
    htmlDirPath: String,
    tocEntries: List<TocEntry>,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    val colors = readingTheme.colors

    // 表示設定ボトムシートの開閉状態
    var showSettings by remember { mutableStateOf(false) }

    // 再試行カウンタ。インクリメントで produceState を再起動させる。
    // なぜ currentFile だけでなく retryKey も key に持つか:
    // currentFile が同じままパースを再実行するには別のキーが必要なため。
    var retryKey by remember { mutableStateOf(0) }

    // 章HTMLを非同期パース（メインスレッドブロック防止）
    // なぜ produceState か: キーが変わったときの再起動が自動化され、
    // Loading → Success の状態遷移をシンプルに記述できるため
    val parseResult by produceState<ParseResult>(
        initialValue = ParseResult.Loading,
        key1 = currentFile,
        key2 = retryKey,
    ) {
        value = ParseResult.Loading
        value = withContext(Dispatchers.IO) {
            try {
                val content = ChapterHtmlParser.parse(File(htmlDirPath, currentFile))
                if (content != null) ParseResult.Success(content)
                else ParseResult.Error("ファイルの読み込みに失敗しました", currentFile)
            } catch (e: Exception) {
                ParseResult.Error(e.message ?: "不明なエラー", currentFile)
            }
        }
    }

    // TOC から現在の章インデックスを特定して前後ナビゲーション先を決定
    val currentIndex = tocEntries.indexOfFirst { it.fileName == currentFile }
    val prevFile = when {
        currentIndex > 0 -> tocEntries[currentIndex - 1].fileName
        else -> "index.html" // 最初の章 → 目次に戻る
    }
    val nextFile = when {
        currentIndex in 0 until tocEntries.size - 1 -> tocEntries[currentIndex + 1].fileName
        else -> "index.html" // 最後の章 → 目次に戻る
    }

    // バックキーはデフォルトで本棚に戻る（Navigation の popBackStack）。
    // なぜ Phase 3 では章履歴スタックを導入しないか:
    // 章履歴の上限管理・永続化・プロセス再生成時の復元はネイティブ化の本質ではなく
    // 複雑度が高いため Phase 3 では省略する。将来の拡張ポイント:
    // BackHandler(enabled = chapterHistory.size > 1) {
    //     chapterHistory.removeLast()
    //     currentFile = chapterHistory.last()
    // }

    // snapAnimationSpec = null: デフォルトのスナップを無効化する。
    // スナップが有効だとわずかなスクロールでバーが「自走」し、
    // ページの動きと乖離した独立した動きに見えてしまうため。
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        topAppBarState,
        snapAnimationSpec = null,
    )

    // enterAlwaysScrollBehavior のデフォルト接続はスクロールを横取りしやすい。
    // 読書体験を優先するため、本文には常にスクロールを渡しつつバー状態だけ追従させる。
    val lazyListState = rememberLazyListState()
    val nonStealingConnection = remember(topAppBarState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 下スクロール（読み進め）ではバーを非表示方向へ追従させるが、消費はしない。
                if (available.y < 0) {
                    topAppBarState.heightOffset =
                        (topAppBarState.heightOffset + available.y)
                            .coerceAtLeast(topAppBarState.heightOffsetLimit)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 上スクロール（戻り）は本文が実際に動いた分だけバーを表示方向へ追従させる。
                if (consumed.y > 0) {
                    topAppBarState.heightOffset =
                        (topAppBarState.heightOffset + consumed.y).coerceAtMost(0f)
                }
                return Offset.Zero
            }

            // なぜ onPostFling でスナップするか:
            // フリック後に半端な位置で止まるとバーが宙ぶらりんになるため、
            // 勢いのある操作が終わった直後に全表示/全非表示へ吸いつかせる。
            // ゆっくりドラッグして止めた場合は onPostFling が低速度で発火するが
            // settleTopBar の 0.5f 閾値判定で適切な方向へスナップする。
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settleTopBar(topAppBarState)
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            modifier = Modifier.nestedScroll(nonStealingConnection),
            bottomBar = {
                // なぜ alpha 0.95f か: スクロール中も文字が透けて読めるよう
                // 背景色を半透明にするため（html_exporter.py の .nav-footer に対応）
                BottomAppBar(
                    containerColor = colors.navBackground.copy(alpha = 0.95f),
                    contentColor = colors.topBarIcon,
                ) {
                    IconButton(
                        onClick = { onNavigateTo(prevFile) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "前の章",
                        )
                    }
                    IconButton(
                        onClick = { onNavigateTo("index.html") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "目次",
                        )
                    }
                    IconButton(
                        onClick = { onNavigateTo(nextFile) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "次の章",
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                when (val result = parseResult) {
                    is ParseResult.Loading -> CircularProgressIndicator()

                    is ParseResult.Success -> ChapterContent(
                        content = result.content,
                        colors = colors,
                        lazyListState = lazyListState,
                    )

                    is ParseResult.Error -> ReadingErrorScreen(
                        message = result.message,
                        colors = colors,
                        onNavigateToBookshelf = onNavigateToBookshelf,
                        onRetry = { retryKey++ },
                    )
                }
            }
        }

        TopAppBar(
            modifier = Modifier.graphicsLayer {
                // なぜ graphicsLayer か: レイアウトを再計算せず描画位置のみを変えるため。
                // これによりバーの追従中でも本文の位置が一切動かない。
                translationY = topAppBarState.heightOffset
            },
            title = {
                when (val r = parseResult) {
                    is ParseResult.Success -> Text(
                        text = r.content.title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                    else -> Unit
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateToBookshelf) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "本棚に戻る",
                    )
                }
            },
            actions = {
                // 表示設定（テーマ切替）ボトムシートを開く
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "表示設定",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.topBarBackground,
                scrolledContainerColor = colors.topBarBackground,
                // Material3 内部の色計算に依存せず読書テーマの色を直接指定。
                // containerColor が非デフォルト値のとき titleContentColor が
                // 意図しない薄さになる場合があるため明示する。
                titleContentColor = colors.topBarTitle,
                navigationIconContentColor = colors.topBarIcon,
                actionIconContentColor = colors.topBarIcon,
            ),
            // scrollBehavior は heightOffsetLimit の測定のため維持する。
            scrollBehavior = scrollBehavior,
        )

        if (showSettings) {
            ReadingSettingsSheet(
                readingTheme = readingTheme,
                onThemeChange = onThemeChange,
                onDismiss = { showSettings = false },
            )
        }
    }
}

/** 表示設定ボトムシート（テーマ切替）。色はアプリ全体の MaterialTheme に従う */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsSheet(
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "表示設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "テーマ",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // values() を使うのは Kotlin バージョン非依存のため（entries は 1.9+）
                ReadingTheme.values().forEach { theme ->
                    FilterChip(
                        selected = readingTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = {
                            Text(
                                when (theme) {
                                    ReadingTheme.LIGHT -> "ライト"
                                    ReadingTheme.SEPIA -> "セピア"
                                    ReadingTheme.DARK -> "ダーク"
                                }
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 章本文を LazyColumn でレンダリングする */
@Composable
private fun ChapterContent(
    content: ChapterContent,
    colors: ReadingColors,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val paragraphs = remember(content) { content.segments.splitIntoParagraphs() }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize(),
        // なぜ contentPadding で 64.dp を確保するか:
        // TopAppBar がオーバーレイ配置のため Scaffold の innerPadding にバー分が含まれない。
        // Box の padding にすると全画面（ローディング等）に影響しバー非表示時も常に隙間が残る。
        // contentPadding はスクロール領域内の余白なので、中盤では画面外に収まり本文位置に影響しない。
        // 章の最上部でのみバー高さ分のスペースが確保され、先頭行がバーに隠れなくなる。
        contentPadding = PaddingValues(top = 64.dp),
    ) {

        // 段落ごとにレンダリング
        items(paragraphs) { paragraph ->
            ParagraphItem(
                paragraph = paragraph,
                colors = colors,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 15.dp),
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/** 1段落分を描画する。空段落は Spacer、StyledBlock は背景付き Surface で描画 */
@Composable
private fun ParagraphItem(
    paragraph: List<TextSegment>,
    colors: ReadingColors,
    modifier: Modifier = Modifier,
) {
    val bodyStyle = TextStyle(
        color = colors.text,
        fontSize = 18.sp,
        lineHeight = 2.5.em,
        fontFamily = FontFamily.Serif,
        letterSpacing = 0.sp,
        // なぜ Trim.LastLineBottom か:
        // lineHeight = 2.5.em を RubyText 内折り返しとParagraphItem 間で統一するため。
        // LastLineBottom のみ除去することで上 leading(13.5sp=ルビ描画領域)を保ちつつ、
        // composable 高さを 31.5sp に確定させる。
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.LastLineBottom,
        ),
    )

    when {
        paragraph.isEmpty() -> {
            // 空段落: なろう系小説のシーン転換・演出として意図的な空行を保持する
            // なぜフィルタリングしないか: 削除すると原作者の意図が失われるため
            // 空行 = 20dp Spacer + 次アイテムの上 leading 13.5dp = 計 47.5dp ≈ WebView の空行
            Spacer(modifier = Modifier.height(20.dp))
        }
        paragraph.size == 1 && paragraph[0] is TextSegment.HorizontalRule -> {
            // 水平線（html_exporter.py の <hr> に対応）
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(1.dp),
            ) {
                drawLine(
                    color = colors.hr,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                    ),
                )
            }
        }
        paragraph.size == 1 && paragraph[0] is TextSegment.StyledBlock -> {
            // 前書き・後書きブロック（背景色付き領域）
            val block = paragraph[0] as TextSegment.StyledBlock
            val innerParagraphs = block.segments.splitIntoParagraphs()
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                color = colors.blockBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.blockBorder),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            ) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text(
                        text = block.label,
                        style = bodyStyle.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    innerParagraphs.forEach { innerPara ->
                        if (innerPara.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                        } else {
                            // padding(bottom=14.dp): 下 padding + 次アイテムの上 leading = 27.5dp ≈ 折り返し行間
                            RubyText(
                                segments = innerPara,
                                style = bodyStyle,
                                rubyColor = colors.ruby,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            )
                        }
                    }
                }
            }
        }
        else -> {
            // 通常の段落
            // padding(bottom=14.dp): 下 padding + 次アイテムの上 leading = 27.5dp ≈ 折り返し行間
            RubyText(
                segments = paragraph,
                style = bodyStyle,
                rubyColor = colors.ruby,
                modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
        }
    }
}

/** エラー表示UI（ファイル欠損・パース失敗時）*/
@Composable
private fun ReadingErrorScreen(
    message: String,
    colors: ReadingColors,
    onNavigateToBookshelf: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        // トップレベル（Scaffold 外）からも呼ばれるため自前で背景色を塗る
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "読み込みに失敗しました",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = colors.textSecondary,
            )
            Text(
                text = message,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                color = colors.textSecondary.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Text("再試行")
                }
            }
            Button(onClick = onNavigateToBookshelf) {
                Text("本棚に戻る")
            }
        }
    }
}

/**
 * TextSegment リストを LineBreak で段落分割する。
 * 空段落（LineBreak 連続）はフィルタリングせず保持する。
 * なぜか: なろう系小説では連続空行によるシーン転換演出が頻出するため。
 */
private fun List<TextSegment>.splitIntoParagraphs(): List<List<TextSegment>> {
    val result = mutableListOf<List<TextSegment>>()
    val current = mutableListOf<TextSegment>()

    for (segment in this) {
        when {
            segment is TextSegment.LineBreak -> {
                result.add(current.toList())
                current.clear()
            }
            segment is TextSegment.HorizontalRule -> {
                // 水平線は独立した段落として扱う
                if (current.isNotEmpty()) {
                    result.add(current.toList())
                    current.clear()
                }
                result.add(listOf(segment))
            }
            segment is TextSegment.StyledBlock -> {
                // 前書き・後書きも独立した段落として扱う
                if (current.isNotEmpty()) {
                    result.add(current.toList())
                    current.clear()
                }
                result.add(listOf(segment))
            }
            else -> current.add(segment)
        }
    }
    if (current.isNotEmpty()) result.add(current.toList())

    return result
}


/**
 * collapsedFraction に応じてバーを全表示または全非表示へスナップさせる。
 * なぜ自前実装か: enterAlways の標準 snap はスクロール消費戦略と一体化しており、
 * 本実装の「本文優先・非消費」方針と両立しないため。
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleTopBar(state: TopAppBarState) {
    val target = if (state.collapsedFraction > 0.5f) state.heightOffsetLimit else 0f
    animate(
        initialValue = state.heightOffset,
        targetValue = target,
        // なぜ StiffnessMediumLow か: オーバーレイ化によりバーの動きが本文レイアウトに
        // 伝わらなくなったため、デフォルトのバウンシー挙動を復元して軽快な触感にする。
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    ) { value, _ ->
        state.heightOffset = value
    }
}
