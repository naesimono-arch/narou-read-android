package com.novelreader.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.novelreader.model.ParseResult
import com.novelreader.model.TocEntry
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.BookshelfViewModel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
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
    // テーマは MainActivity が持つ単一正本を受け取る（本棚と共有して全体を同期させるため）。
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onNavigateToBookshelf: () -> Unit,
) {
    // なぜ rememberSaveable に bookId をキーとして含めるか:
    // ルートが reading/{bookId}/{startFile} なので NavBackStackEntry 単位でスコープされるが、
    // Navigation の実装詳細に依存しないよう bookId を明示的にキーに含めて
    // 書籍切替時の状態混線を防ぐ。
    var currentFile by rememberSaveable(key = "currentFile_$bookId") {
        mutableStateOf(startFile)
    }

    // 最後に表示していた章。目次表示中の「現在章ハイライト」に使う。
    // なぜ currentFile と別に持つか: 目次を開くと currentFile は "index.html" に
    // 上書きされ、どの章から来たかが失われるため。
    var lastChapterFile by rememberSaveable(key = "lastChapter_$bookId") {
        mutableStateOf(startFile.takeIf { it != "index.html" })
    }

    // 読書再開位置。画面初回に一度だけ DB から取得する（章の途中から復元するため）。
    // null=取得待ち。getProgress は DB 1行クエリのため一瞬で解決する。
    var chapterRestore by remember { mutableStateOf<ChapterRestore?>(null) }
    LaunchedEffect(bookId) {
        val p = viewModel.getProgress(bookId)
        chapterRestore = ChapterRestore(
            targetFile = p?.lastReadFilename,
            scrollIndex = p?.scrollIndex ?: 0,
            scrollOffset = p?.scrollOffset ?: 0,
        )
    }

    // テーマ（readingTheme/onThemeChange）は MainActivity から受け取る単一正本を使う。
    // 本棚(NovelReaderTheme)と同じ状態を共有し設定変更を全体へ同期させるため、ここでは pref を
    // 直接読まない（初期決定・永続化は MainActivity 側 = loadInitialTheme/onThemeChange が担う）。
    // context/prefs は文字サイズ・行間（読書固有設定）の読み書きに引き続き使う。
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val readingColors = readingTheme.colors

    // 本文フォントサイズ（sp）。lineHeight は em 指定のため自動追従する。
    // なぜ coerceIn か: 将来レンジを狭めた場合に保存済みの範囲外値で
    // レイアウトが崩れないよう、読み出し時点で必ず現行レンジに丸める。
    var fontSize by remember {
        mutableIntStateOf(prefs.getInt("reading_font_size", 18).coerceIn(14, 24))
    }
    val onFontSizeChange: (Int) -> Unit = { size ->
        fontSize = size
        // apply はメモリ即時反映＋非同期ディスク書込のため、
        // スライダードラッグ中に連続発火しても UI をブロックしない
        prefs.edit().putInt("reading_font_size", size).apply()
    }

    // 本文の行間（em）。
    // なぜ 2.3〜2.8em の狭めレンジに絞るか: ルビは行の上端基準で描画されるため、
    // 行間を広げるほどルビが親文字から離れ、狭めるほど被るという物理制約がある。
    // 段落間スペースも lineHeight=2.5em 前提で微調整済みのため、可変幅を狭く保つことで
    // ルビ被り/離れと段落リズムの破綻を許容範囲に抑える。
    var lineHeightEm by remember {
        mutableFloatStateOf(prefs.getFloat("reading_line_height", 2.5f).coerceIn(2.3f, 2.8f))
    }
    val onLineHeightChange: (Float) -> Unit = { v ->
        lineHeightEm = v
        prefs.edit().putFloat("reading_line_height", v).apply()
    }

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
            currentChapterFile = lastChapterFile,
            onSelectChapter = { fileName ->
                currentFile = fileName
                lastChapterFile = fileName
                viewModel.saveProgress(bookId, fileName)
            },
            onNavigateToBookshelf = onNavigateToBookshelf,
        )
        return
    }

    // 章表示。読書再開位置の取得を待ってから描画する。
    // なぜ待つか: LazyListState に初期スクロール位置を注入するため。
    // 取得後に scrollToItem する方式だと「先頭→保存位置」へのジャンプが見えてしまう。
    val restore = chapterRestore
    if (restore == null) {
        // 取得待ちの一瞬。テーマ背景で塗りつぶし白フラッシュを防ぐ
        Box(modifier = Modifier.fillMaxSize().background(readingColors.background))
        return
    }

    ChapterScreen(
        currentFile = resolvedFile,
        htmlDirPath = htmlDirPath,
        tocEntries = tocEntries,
        readingTheme = readingTheme,
        onThemeChange = onThemeChange,
        fontSize = fontSize,
        onFontSizeChange = onFontSizeChange,
        lineHeightEm = lineHeightEm,
        onLineHeightChange = onLineHeightChange,
        // resolvedFile が「最後に読んだ章」と一致する場合のみスクロール位置を復元する
        initialScrollIndex = if (resolvedFile == restore.targetFile) restore.scrollIndex else 0,
        initialScrollOffset = if (resolvedFile == restore.targetFile) restore.scrollOffset else 0,
        onSaveScroll = { index, offset ->
            viewModel.saveScrollPosition(bookId, resolvedFile, index, offset)
        },
        onNavigateToBookshelf = onNavigateToBookshelf,
        onNavigateTo = { fileName ->
            currentFile = fileName
            if (fileName != "index.html") {
                lastChapterFile = fileName
                viewModel.saveProgress(bookId, fileName)
            }
        },
    )
}

/** 読書再開位置。targetFile（最後に読んだ章）と一致する章のみスクロール位置を復元する。 */
private data class ChapterRestore(
    val targetFile: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
)

/** 章本文を表示する内部 Composable */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ChapterScreen(
    currentFile: String,
    htmlDirPath: String,
    tocEntries: List<TocEntry>,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    lineHeightEm: Float,
    onLineHeightChange: (Float) -> Unit,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onSaveScroll: (index: Int, offset: Int) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    val colors = readingTheme.colors
    val scope = rememberCoroutineScope()

    // 表示設定ボトムシートの開閉状態
    var showSettings by remember { mutableStateOf(false) }

    // ボトムバーの実測高さ（px）。退避スライド量に使う。
    // なぜ固定値にしないか: ナビゲーションバー実高（ボタン式/ジェスチャー式）でバー総高が
    // 変わるため、onSizeChanged で実測した高さ分だけスライドさせて完全に画面外へ退避させる。
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // 再試行カウンタ。インクリメントで produceState を再起動させる。
    // なぜ currentFile だけでなく retryKey も key に持つか:
    // currentFile が同じままパースを再実行するには別のキーが必要なため。
    var retryKey by remember { mutableIntStateOf(0) }

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
    // 章ごとに初期スクロール位置付きで生成し、remember(currentFile) で章移動時に
    // 必ず作り直すことで前章のスクロール位置の引き継ぎを防ぐ。
    val lazyListState = remember(currentFile) {
        LazyListState(initialScrollIndex, initialScrollOffset)
    }

    // スクロール位置を継続保存する（読書中にプロセスが kill されても続きから読めるように）。
    // 読書中の連続発火を debounce で間引き、DB 書き込みを最小化する。
    LaunchedEffect(lazyListState, currentFile) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
            .debounce(400)
            .collect { (index, offset) -> onSaveScroll(index, offset) }
    }

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
            // なぜ contentWindowInsets を 0 にするか: 上下バーを Scaffold スロットではなく
            // オーバーレイで描くため、インセットは本文側(ChapterContent の contentPadding)で
            // 完全に管理する。Scaffold が二重にインセットを足さないよう無効化する。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // ボトムバーは Scaffold スロットに置かずオーバーレイ化する。
            // なぜか: スロットに置くと退避させても Scaffold が下部余白を確保し続け、
            // 本文が画面最下部まで届かない。オーバーレイなら退避時に本文が全画面を使える。
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // 本文中央タップで上下バーをトグル表示する。
                    // なぜ barsVisible の真偽値を持たないか: スクロール退避で既にバーが
                    // 隠れている状態でも真偽値は true のままになり「隠れているものを隠す」
                    // 空打ちが起き2回タップが必要になる。実オフセット(collapsedFraction)から
                    // 現在の表示状態を判定して反転させることで1タップで必ず切り替わる。
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            val target = if (topAppBarState.collapsedFraction < 0.5f) {
                                topAppBarState.heightOffsetLimit // 表示中→全退避
                            } else {
                                0f // 退避中→全表示
                            }
                            scope.launch { settleTopBar(topAppBarState, target) }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (val result = parseResult) {
                    is ParseResult.Loading -> CircularProgressIndicator()

                    is ParseResult.Success -> ChapterContent(
                        content = result.content,
                        colors = colors,
                        fontSize = fontSize,
                        lineHeightEm = lineHeightEm,
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

        // ────── ボトムバー（オーバーレイ）──────
        // collapsedFraction（トップバーの退避割合）に連動して下方向へスライド退避させる。
        // これによりスクロール退避・中央タップトグルの両方でトップバーと同期して動く。
        BottomAppBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .graphicsLayer {
                    // 退避割合 × 実測高さ分だけ下へずらす（collapsedFraction=1 で完全に画面外）
                    translationY = bottomBarHeightPx * topAppBarState.collapsedFraction
                },
            // なぜ alpha 0.95f か: スクロール中も文字が透けて読めるよう
            // 背景色を半透明にするため（html_exporter.py の .nav-footer に対応）
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
                    imageVector = Icons.AutoMirrored.Filled.List,
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
                        fontFamily = MinchoFamily,
                        fontSize = 16.sp,
                        maxLines = 1,
                        // 長い章タイトルは文字途中で切らず末尾を「…」で省略する
                        overflow = TextOverflow.Ellipsis,
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
                colors = colors,
                readingTheme = readingTheme,
                onThemeChange = onThemeChange,
                fontSize = fontSize,
                onFontSizeChange = onFontSizeChange,
                lineHeightEm = lineHeightEm,
                onLineHeightChange = onLineHeightChange,
                onDismiss = { showSettings = false },
            )
        }
    }
}

/**
 * バーを全表示または全非表示へスナップさせる。
 * なぜ自前実装か: enterAlways の標準 snap はスクロール消費戦略と一体化しており、
 * 本実装の「本文優先・非消費」方針と両立しないため。
 *
 * @param target 退避先の heightOffset。省略時は現在の collapsedFraction から近い方へ吸着
 *               （フリック後の半端位置の整列に使用）。中央タップでは反転先を明示的に渡す。
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleTopBar(
    state: TopAppBarState,
    target: Float = if (state.collapsedFraction > 0.5f) state.heightOffsetLimit else 0f,
) {
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
