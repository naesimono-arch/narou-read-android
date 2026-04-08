package com.novelreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.novelreader.model.ChapterContent
import com.novelreader.model.ParseResult
import com.novelreader.model.TextSegment
import com.novelreader.model.TocEntry
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.ui.compose.RubyText
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
fun NativeReadingScreen(
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
        // htmlDirPath 自体が存在しない致命的エラー
        ReadingErrorScreen(
            message = "書籍データが見つかりません",
            onNavigateToBookshelf = onNavigateToBookshelf,
        )
        return
    }

    if (resolvedFile == "index.html") {
        NativeTableOfContentsScreen(
            tocEntries = tocEntries,
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
        bookId = bookId,
        currentFile = resolvedFile,
        htmlDirPath = htmlDirPath,
        tocEntries = tocEntries,
        viewModel = viewModel,
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
    bookId: String,
    currentFile: String,
    htmlDirPath: String,
    tocEntries: List<TocEntry>,
    viewModel: BookshelfViewModel,
    onNavigateToBookshelf: () -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    // 章HTMLを非同期パース（メインスレッドブロック防止）
    // なぜ produceState か: キーが変わったときの再起動が自動化され、
    // Loading → Success の状態遷移をシンプルに記述できるため
    val parseResult by produceState<ParseResult>(
        initialValue = ParseResult.Loading,
        key1 = currentFile,
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        containerColor = Color(0xFFFCFAF2),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFCFAF2),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            // なぜ rgba(252,250,242,0.95) か: スクロール中も文字が透けて読めるよう
            // 背景色を半透明にするため（html_exporter.py の .nav-footer に対応）
            BottomAppBar(
                containerColor = Color(0xFFFCFAF2).copy(alpha = 0.95f),
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
                )

                is ParseResult.Error -> ReadingErrorScreen(
                    message = result.message,
                    onNavigateToBookshelf = onNavigateToBookshelf,
                )
            }
        }
    }
}

/** 章本文を LazyColumn でレンダリングする */
@Composable
private fun ChapterContent(content: ChapterContent) {
    val paragraphs = remember(content) { content.segments.splitIntoParagraphs() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // 章タイトル
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(start = 15.dp, end = 15.dp, top = 20.dp, bottom = 12.dp),
            ) {
                Text(
                    text = content.title,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 32.sp,
                        letterSpacing = 0.sp,
                    ),
                )
                // タイトル下の区切り線（html_exporter.py の h1 border-bottom に対応）
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(1.dp),
                ) {
                    drawLine(
                        color = Color(0xFFE0DCD0),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 段落ごとにレンダリング
        items(paragraphs) { paragraph ->
            ParagraphItem(
                paragraph = paragraph,
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
    modifier: Modifier = Modifier,
) {
    val bodyStyle = TextStyle(
        fontSize = 18.sp,
        lineHeight = 2.5.em,
        fontFamily = FontFamily.Serif,
        letterSpacing = 0.sp,
    )

    when {
        paragraph.isEmpty() -> {
            // 空段落: なろう系小説のシーン転換・演出として意図的な空行を保持する
            // なぜフィルタリングしないか: 削除すると原作者の意図が失われるため
            Spacer(modifier = Modifier.height(bodyStyle.lineHeight.value.dp))
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
                    color = Color(0xFFCCCCCC),
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
                color = Color(0xFFF9F9F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
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
                            Spacer(modifier = Modifier.height(bodyStyle.lineHeight.value.dp))
                        } else {
                            RubyText(
                                segments = innerPara,
                                style = bodyStyle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        else -> {
            // 通常の段落
            RubyText(
                segments = paragraph,
                style = bodyStyle,
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

/** エラー表示UI（ファイル欠損・パース失敗時） */
@Composable
private fun ReadingErrorScreen(
    message: String,
    onNavigateToBookshelf: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "読み込みに失敗しました",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = Color(0xFF666666),
            )
            Text(
                text = message,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
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
