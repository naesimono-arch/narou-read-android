package com.novelreader.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novelreader.NovelReaderApplication
import com.novelreader.model.ParseResult
import com.novelreader.model.TocEntry
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.computeContinuation
import com.novelreader.narou.narouEpisodeUrl
import com.novelreader.narou.narouWorkUrl
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.rememberReadingColors
import com.novelreader.viewmodel.BookshelfViewModel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * @param bookTitle 蔵書タイトル（なろう紐付けシートの初期検索語に使う）
 * @param ncode 紐付け済みなろう作品の Nコード（null = 未紐付け。継続導線の分岐に使う）
 * @param viewModel BookshelfViewModel（進捗保存・ncode 紐付けに使用）
 * @param onNavigateToBookshelf 本棚に戻るコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    bookId: String,
    startFile: String,
    htmlDirPath: String,
    bookTitle: String,
    ncode: String?,
    viewModel: BookshelfViewModel,
    // テーマは MainActivity が持つ単一正本を受け取る（本棚と共有して全体を同期させるため）。
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onNavigateToBookshelf: () -> Unit,
) {
    // 章⇄目次の内部遷移履歴（Back で1段ずつ遡るためのスタック）。末尾が現在表示中のファイル。
    // なぜ currentFile を単独 state にせず履歴の末尾から導出するか: Back キーで「章・目次を
    // 1段遡ってから本棚へ」を実現するには過去の遷移列が必要で、現在位置と履歴を二重管理すると
    // 齟齬の温床になるため単一正本（navHistory）に統一する（公理1＝Back の予測可能性）。
    // なぜ rememberSaveable に bookId をキーとして含めるか:
    // ルートが reading/{bookId}/{startFile} なので NavBackStackEntry 単位でスコープされるが、
    // Navigation の実装詳細に依存しないよう bookId を明示的にキーに含めて書籍切替時の状態混線を防ぐ。
    // 履歴はプロセス再生成後も遡行できるよう listSaver で永続化する。
    var navHistory by rememberSaveable(
        key = "navHistory_$bookId",
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it }),
    ) { mutableStateOf(listOf(startFile)) }
    val currentFile = navHistory.last()

    // 最後に表示していた章。目次表示中の「現在章ハイライト」に使う。
    // なぜ currentFile と別に持つか: 目次を開くと currentFile は "index.html" に
    // なるため、どの章から来たかを別途保持する必要があるため。
    var lastChapterFile by rememberSaveable(key = "lastChapter_$bookId") {
        mutableStateOf(startFile.takeIf { it != "index.html" })
    }

    // 章/目次へ「進む」共通処理。履歴を1段積み、章なら現在章更新＋進捗保存する。
    // 【生命線】index.html（目次）への遷移は進捗を保存しない（ブロックリスト方式の既存保証を踏襲）。
    // なぜ: 目次を開いただけで lastReadFilename が index.html に上書きされると読書再開位置が壊れるため。
    val navigateForward: (String) -> Unit = { target ->
        navHistory = pushNavHistory(navHistory, target)
        if (target != "index.html") {
            lastChapterFile = target
            viewModel.saveProgress(bookId, target)
        }
    }

    // Back キー: 内部遷移履歴があれば1段遡り、無ければ無効化してシステム既定（本棚へ pop）に委ねる。
    // App bar の ←（Up）は従来どおり常に本棚へ戻す（Back と Up の分離）。
    // 【生命線】戻り時に saveProgress を呼ばない理由: 章へ戻ると saveProgress は scrollIndex=0 を書き、
    // その章の保存済みスクロールを先頭へ潰してしまう。戻り先の章は ChapterScreen 再表示時の debounce/
    // onStop フラッシュで正しい現在位置が保存されるため、ここでは currentFile と現在章ハイライトの
    // 更新に留め、進捗の破壊的上書きを避ける（前進時のみ「新しく開いた章＝先頭から」を保存する非対称設計）。
    BackHandler(enabled = navHistory.size > 1) {
        val popped = navHistory.dropLast(1)
        navHistory = popped
        val target = popped.last()
        if (target != "index.html") {
            lastChapterFile = target
        }
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
    val readingColors = rememberReadingColors(readingTheme)

    // なぜ「UI状態の更新」と「永続化」を2つのコールバックに分けるか:
    // スライダーのドラッグ中は毎値 onValueChange が発火する。状態更新は本文プレビューを
    // リアルタイム追従させるため毎値で必要だが、prefs 書き込みまで毎値行うと無駄な
    // ディスク I/O が連続する。永続化は確定時（onValueChangeFinished）に一度だけにする。
    // 確定時点で下記 state は最終値を保持しているため、persist 側は現在の state 値を読んで書く。

    // 本文フォントサイズ（sp）。lineHeight は em 指定のため自動追従する。
    // なぜ coerceIn か: 将来レンジを狭めた場合に保存済みの範囲外値で
    // レイアウトが崩れないよう、読み出し時点で必ず現行レンジに丸める。
    var fontSize by remember {
        mutableIntStateOf(prefs.getInt("reading_font_size", 18).coerceIn(14, 24))
    }
    // ドラッグ中の毎値：本文プレビュー追従のため状態のみ更新（永続化しない）
    val onFontSizeChange: (Int) -> Unit = { size -> fontSize = size }
    // 確定時のみ：現在の fontSize を永続化する。apply は非同期ディスク書込のため UI をブロックしない
    val onFontSizePersist: () -> Unit = {
        prefs.edit().putInt("reading_font_size", fontSize).apply()
    }

    // 本文の行間（em）。
    // なぜ 2.3〜2.8em の狭めレンジに絞るか: ルビは字面上端アンカーで描画されるため
    // 行間を広げても親文字から離れなくなったが（バグ#1修正）、狭めるとルビの描画領域
    // （字面より上の leading）が前行と被るリスクは残る。段落間スペースも lineHeight=2.5em
    // 前提で微調整済みのため、可変幅を狭く保つことでルビ被りと段落リズムの破綻を抑える。
    var lineHeightEm by remember {
        mutableFloatStateOf(prefs.getFloat("reading_line_height", 2.5f).coerceIn(2.3f, 2.8f))
    }
    // フォントサイズと同型：ドラッグ中は状態のみ・永続化は確定時に一度だけ
    val onLineHeightChange: (Float) -> Unit = { v -> lineHeightEm = v }
    val onLineHeightPersist: () -> Unit = {
        prefs.edit().putFloat("reading_line_height", lineHeightEm).apply()
    }

    // 本文の左右余白（dp）。既定 15 は設定化前の固定値と同じ＝既存ユーザーの見た目を変えない。
    // スマホ幅では widthIn(max=600dp) が効かず実質この余白だけが行長を決めるため、
    // 行長を詰めたい要望（旧 backlog C-05/06）はこの1値の設定化で吸収する。
    var bodyMarginDp by remember {
        mutableIntStateOf(prefs.getInt("reading_body_margin", 15).coerceIn(10, 40))
    }
    // フォントサイズと同型：ドラッグ中は状態のみ・永続化は確定時に一度だけ
    val onBodyMarginChange: (Int) -> Unit = { v -> bodyMarginDp = v }
    val onBodyMarginPersist: () -> Unit = {
        prefs.edit().putInt("reading_body_margin", bodyMarginDp).apply()
    }

    // ステータスバーアイコン明暗はここでは設定しない（所有権は NovelReaderTheme の SideEffect に一本化）。
    // なぜ: テーマは MainActivity の appTheme 単一正本で本棚と読書が常に同値のため、画面側での
    // 設定は冗長で、かつて在った onDispose の「システム準拠へ復元」は手動テーマ選択時に本棚へ
    // 戻るとステータスバーが誤った明暗になる実バグだった（旧・本棚=システム追従／読書=独立pref
    // の2系統時代の残骸。単一正本化＝handover B「11 本棚テーマ追従」解消後は復元自体が不要）。

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

    // 目次パースの再試行カウンタ。インクリメントで下記 produceState を再起動させる。
    var tocRetryKey by remember { mutableIntStateOf(0) }

    // 目次を非同期でロード（Loading→Content/Empty/Error の状態遷移を保持）。
    // なぜ initialValue を Loading にし例外を捕捉するか: 旧実装は initialValue=emptyList で
    // 「パース未完了の一瞬」と「真の空」を区別できず、目次画面が誤って「章が見つかりません」を
    // 出していた（公理8）。状態を明示化してロード中はスケルトン・例外は再試行に振り分ける。
    val tocState by produceState<TocState>(initialValue = TocState.Loading, key1 = tocRetryKey) {
        value = TocState.Loading
        value = try {
            val entries = withContext(Dispatchers.IO) {
                ChapterHtmlParser.parseToc(File(htmlDirPath, "index.html"))
            }
            if (entries.isEmpty()) TocState.Empty else TocState.Content(entries)
        } catch (e: Exception) {
            // 例外時は静かに空表示せず原因を出して再試行導線を与える（読者が復帰できるように）
            TocState.Error(e.message ?: "目次の読み込みに失敗しました")
        }
    }

    // 章の前後ナビゲーション・継続導線ロジックは章リストの実体（List<TocEntry>）を必要とする。
    // Content のときだけ中身を渡し、それ以外（Loading/Empty/Error）は空リスト＝「未ロード」として扱う。
    val tocEntries = (tocState as? TocState.Content)?.entries ?: emptyList()

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
            tocState = tocState,
            colors = readingColors,
            currentChapterFile = lastChapterFile,
            // 章選択は「進む」遷移＝履歴を積み進捗も保存する（navigateForward が index 以外を保存）
            onSelectChapter = navigateForward,
            onNavigateToBookshelf = onNavigateToBookshelf,
            onRetry = { tocRetryKey++ },
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
        bookTitle = bookTitle,
        ncode = ncode,
        // 紐付けの永続化。books は hot StateFlow のため、書き込みは MainActivity → ncode 引数へ
        // 自動で還流し、確定直後から継続導線が紐付け済み表示に切り替わる。
        onLinkNcode = { newNcode -> viewModel.linkNcode(bookId, newNcode) },
        readingTheme = readingTheme,
        onThemeChange = onThemeChange,
        fontSize = fontSize,
        onFontSizeChange = onFontSizeChange,
        onFontSizePersist = onFontSizePersist,
        lineHeightEm = lineHeightEm,
        onLineHeightChange = onLineHeightChange,
        onLineHeightPersist = onLineHeightPersist,
        bodyMarginDp = bodyMarginDp,
        onBodyMarginChange = onBodyMarginChange,
        onBodyMarginPersist = onBodyMarginPersist,
        // resolvedFile が「最後に読んだ章」と一致する場合のみスクロール位置を復元する
        initialScrollIndex = if (resolvedFile == restore.targetFile) restore.scrollIndex else 0,
        initialScrollOffset = if (resolvedFile == restore.targetFile) restore.scrollOffset else 0,
        onSaveScroll = { index, offset ->
            viewModel.saveScrollPosition(bookId, resolvedFile, index, offset)
        },
        onNavigateToBookshelf = onNavigateToBookshelf,
        // 前後章・目次ボタンからの遷移も「進む」＝履歴を積む（Back で1段ずつ遡れる）
        onNavigateTo = navigateForward,
    )
}

/** 読書再開位置。targetFile（最後に読んだ章）と一致する章のみスクロール位置を復元する。 */
private data class ChapterRestore(
    val targetFile: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
)

/** 内部遷移履歴の上限。長い読書セッションで際限なく伸びるのを防ぐ（超過分は最古から捨てる）。 */
private const val MAX_NAV_HISTORY = 32

/**
 * 章⇄目次の内部遷移履歴に1段追加する純関数（重複排除＋上限管理）。テスト対象。
 * なぜ純関数に切り出すか: Back の遡行順に直結するロジックを UI 非依存で単体テストするため。
 *
 * @return 追加後の履歴。直前と同じ遷移先なら積まず（連打・現在章の再選択で無駄に伸ばさない）、
 *         上限超過時は先頭（最古）を落とす。履歴は進捗保存とは独立なので古い要素の破棄は安全。
 */
internal fun pushNavHistory(
    history: List<String>,
    target: String,
    max: Int = MAX_NAV_HISTORY,
): List<String> {
    if (history.lastOrNull() == target) return history
    val appended = history + target
    return if (appended.size > max) appended.subList(appended.size - max, appended.size).toList()
    else appended
}

/** 章本文を表示する内部 Composable */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ChapterScreen(
    currentFile: String,
    htmlDirPath: String,
    tocEntries: List<TocEntry>,
    bookTitle: String,
    ncode: String?,
    onLinkNcode: (String?) -> Unit,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    // 永続化はスライダー確定時のみ呼ぶ（ドラッグ中の毎値書き込みを避ける）
    onFontSizePersist: () -> Unit,
    lineHeightEm: Float,
    onLineHeightChange: (Float) -> Unit,
    onLineHeightPersist: () -> Unit,
    bodyMarginDp: Int,
    onBodyMarginChange: (Int) -> Unit,
    onBodyMarginPersist: () -> Unit,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onSaveScroll: (index: Int, offset: Int) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    val colors = rememberReadingColors(readingTheme)
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

    // 目次がまだロードされていない（tocEntries が空）間は前後の隣章を決定できない。
    // なぜ enabled で制御するか: 未ロード時は currentIndex=-1 となり prev/next が index.html へ
    // フォールバックしていたため、隣章のつもりで押すと目次へ飛ぶ非決定的挙動だった（公理2）。
    // ロード完了までボタンを disabled にして「押せば必ず隣章」を保証する。
    val navEnabled = tocEntries.isNotEmpty()
    val prevFile = when {
        currentIndex > 0 -> tocEntries[currentIndex - 1].fileName
        else -> "index.html" // 最初の章 → 目次に戻る
    }
    val nextFile = when {
        currentIndex in 0 until tocEntries.size - 1 -> tocEntries[currentIndex + 1].fileName
        else -> "index.html" // 最後の章 → 目次に戻る
    }

    // ────── PDF↔Web継続読書（目玉①）──────
    // 最終章か。tocEntries ロード完了前（empty）は false になり継続導線は出ない。
    val isLastChapter = tocEntries.isNotEmpty() && currentIndex == tocEntries.size - 1

    val context = LocalContext.current
    val narouRepository = remember(context) {
        (context.applicationContext as NovelReaderApplication).novelApiRepository
    }

    // 紐付け済みかつ最終章のときだけ、なろうAPIへ話数を照会する。
    // なぜ最終章に限定するか: 読書中の無駄な通信を避けるため（照会自体も Repository の
    // 6h TTL キャッシュに乗るため、章を行き来しても実通信は最大6時間に1回）。
    val continuationInfo by produceState<ContinuationInfo?>(
        initialValue = null,
        key1 = ncode,
        key2 = isLastChapter,
        key3 = tocEntries.size,
    ) {
        value = null
        if (ncode != null && isLastChapter) {
            // オフライン等の失敗時は静かに何も出さない（読書の没入を通信エラーで壊さない）。
            // 次に最終章を開き直せば produceState が再起動し自然に再試行される。
            value = try {
                narouRepository.novelDetail(ncode)?.let { computeContinuation(tocEntries.size, it) }
            } catch (e: NarouApiException) {
                null
            }
        }
    }

    // なろう紐付けシートの開閉状態
    var showLinkSheet by remember { mutableStateOf(false) }

    // 継続カードからの外部遷移（Custom Tabs 起動）の再入ガード用タイムスタンプ（M1/公理3）。
    // なぜ必要か: Custom Tabs はブラウザ別プロセスの起動待ちがあり、反応が無いと利用者が連打しやすい。
    // その間 launchUrl が複数回走ると続き/作品ページが2枚重なって開くため、直近起動から一定時間内の
    // タップを無視して二重起動を防ぐ。「続きを読む」「作品ページ」の両ボタンで共有し、跨ぎ連打も抑える。
    var lastLaunchAt by remember { mutableStateOf(0L) }

    // Back キーの段階化（章⇄目次を1段ずつ遡り、履歴が尽きたら本棚へ）は親の ReadingScreen が
    // navHistory＋BackHandler で一元管理する。currentFile を所有するのが ReadingScreen のため、
    // 履歴の上限管理・rememberSaveable 永続化もそちらに集約した（ここでは扱わない）。

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
    // なぜ rememberUpdatedState か: onSaveScroll は呼び出し側（ReadingScreen）で
    // resolvedFile 等を capture して毎コンポジション新しく生成されるラムダ。これを
    // LaunchedEffect のキーに含めると章移動でもないのに保存コルーチンが再起動してしまい、
    // 含めなければ最初に捕捉した古い参照（陳腐化した resolvedFile capture）を呼び続ける。
    // 最新参照へ更新する State 越しに呼ぶことで、コルーチンは再起動せず常に最新の onSaveScroll を呼ぶ。
    val latestOnSaveScroll by rememberUpdatedState(onSaveScroll)
    LaunchedEffect(lazyListState, currentFile) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
            .debounce(400)
            .collect { (index, offset) -> latestOnSaveScroll(index, offset) }
    }

    // 離脱時（アプリ background 化＝ON_STOP）に最終スクロール位置を即時フラッシュする。
    // なぜ必要か（公理6）: 上の debounce(400) は「最後の 400ms 分」を溜めてから書くため、
    // スクロール直後にホーム/アプリ切替で離脱するとその 400ms 分が未保存のまま失われ、
    // 再開時に位置がわずかに巻き戻る。ON_STOP で現在の LazyListState を直接書いて取りこぼしを消す。
    // 【生命線の安全性】
    //  ・この DisposableEffect は ChapterScreen 内＝実章表示中のみ構成される（目次 index.html 表示中は
    //    ReadingScreen が NativeTableOfContentsScreen を描き ChapterScreen 自体が構成されない）。
    //    ゆえにフラッシュ対象は必ず実章で、目次を「読書位置」として書く事故は構造的に起きない
    //    （ブロックリスト方式と非干渉）。latestOnSaveScroll→onSaveScroll は resolvedFile（実章）を保存する。
    //  ・書く値は実際の firstVisibleItem 位置なので、章先頭に居るとき以外に 0 を書く「ゼロ上書き」は起きない。
    //  ・debounce と ON_STOP が同値を二重送出しても保存先は CONFLATED チャネル＋単一行 REPLACE のため
    //    冪等で、順序破綻や巻き戻りは生じない。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, lazyListState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                latestOnSaveScroll(
                    lazyListState.firstVisibleItemIndex,
                    lazyListState.firstVisibleItemScrollOffset,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // ────── 没入クローム復帰ヒント（層②）──────
    // クローム（上下バー）が初めて画面外へ退避したとき、復帰操作（中央タップ）を数秒だけ
    // 一過性ラベルで示す。なぜ一度きり・自動消灯か: 常時の帯は没入を削ぐため、初回消灯時の
    // 学習機会だけを与え以後は出さない（M12＝復帰手段が不可視だった問題への最小介入）。
    // セッション内 remember で一度だけ＝設定/prefs を増やさない（過剰にならない範囲に留める）。
    var chromeHintConsumed by remember { mutableStateOf(false) }
    var showChromeHint by remember { mutableStateOf(false) }
    LaunchedEffect(topAppBarState) {
        snapshotFlow { topAppBarState.collapsedFraction > 0.9f }
            .distinctUntilChanged()
            .collect { hidden ->
                if (hidden && !chromeHintConsumed) {
                    chromeHintConsumed = true
                    showChromeHint = true
                    delay(2600)
                    showChromeHint = false
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

                    is ParseResult.Success -> {
                        // 継続導線スロット。最終章のみ: 未紐付け=静かな探索導線／紐付け済み=継続カード。
                        // ローカル val に固めるのは produceState 委譲プロパティのスマートキャストを効かせるため。
                        val info = continuationInfo
                        val continuationSlot: (@Composable () -> Unit)? = when {
                            !isLastChapter -> null
                            ncode == null -> ({
                                ContinuationLinkPrompt(
                                    colors = colors,
                                    bodyMarginDp = bodyMarginDp,
                                    onClick = { showLinkSheet = true },
                                )
                            })
                            info != null -> ({
                                ContinuationCard(
                                    info = info,
                                    colors = colors,
                                    bodyMarginDp = bodyMarginDp,
                                    onReadContinuation = {
                                        // 再入ガード（M1/公理3）: 連打で Custom Tabs が2枚開くのを防ぐ。
                                        val now = System.currentTimeMillis()
                                        if (now - lastLaunchAt >= 1000L) {
                                            lastLaunchAt = now
                                            // 主ボタンは NewEpisodes のときしか描画されないが、防御的に型で絞る。
                                            // ツールバー色は明示指定せず既定（ブラウザのサイト識別色）に委ねる
                                            // （M9・公理8）＝なろうへ外部遷移した事実を隠さず、今どこに居るかを
                                            // 判別できるようにするため（背景同化＝没入優先は M2 指摘で撤回）。
                                            (info as? ContinuationInfo.NewEpisodes)?.let {
                                                openInAppBrowser(
                                                    context,
                                                    narouEpisodeUrl(it.ncode, it.nextEpisode),
                                                )
                                            }
                                        }
                                    },
                                    onOpenWorkPage = {
                                        // 同上: 再入ガード＋ツールバー色は既定（サイト識別可能）に委ねる。
                                        val now = System.currentTimeMillis()
                                        if (now - lastLaunchAt >= 1000L) {
                                            lastLaunchAt = now
                                            openInAppBrowser(context, narouWorkUrl(info.ncode))
                                        }
                                    },
                                    onUnlink = { onLinkNcode(null) },
                                )
                            })
                            else -> null // 照会中 or 照会失敗（オフライン）→ 静かに出さない
                        }
                        ChapterContent(
                            content = result.content,
                            colors = colors,
                            fontSize = fontSize,
                            lineHeightEm = lineHeightEm,
                            bodyMarginDp = bodyMarginDp,
                            lazyListState = lazyListState,
                            continuation = continuationSlot,
                        )
                    }

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
                // 目次未ロード中は無効化（disabled トークンで淡色化）。押下時の目次フォールバックを防ぐ
                enabled = navEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "前の章",
                )
            }
            // 目次ボタンは常に有効（ロード状況に関わらず目次を開ける＝開けばスケルトン表示）
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
                // 目次未ロード中は無効化（disabled トークンで淡色化）。押下時の目次フォールバックを防ぐ
                enabled = navEnabled,
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

        // 没入クローム復帰ヒント（初回消灯時に数秒フェード）。タップは奪わない純表示。
        AnimatedVisibility(
            visible = showChromeHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) {
            Box(
                modifier = Modifier
                    // 半透明のナビ背景色で本文に沈める丸ピル（色は必ずテーマトークン経由）
                    .clip(RoundedCornerShape(50))
                    .background(colors.navBackground.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "画面中央をタップでメニュー表示",
                    color = colors.topBarIcon,
                    fontFamily = MinchoFamily,
                    fontSize = 13.sp,
                )
            }
        }

        if (showLinkSheet) {
            NcodeLinkSheet(
                bookTitle = bookTitle,
                repository = narouRepository,
                colors = colors,
                onConfirm = { picked ->
                    onLinkNcode(picked)
                    showLinkSheet = false
                },
                onDismiss = { showLinkSheet = false },
            )
        }

        if (showSettings) {
            ReadingSettingsSheet(
                colors = colors,
                readingTheme = readingTheme,
                onThemeChange = onThemeChange,
                fontSize = fontSize,
                onFontSizeChange = onFontSizeChange,
                onFontSizePersist = onFontSizePersist,
                lineHeightEm = lineHeightEm,
                onLineHeightChange = onLineHeightChange,
                onLineHeightPersist = onLineHeightPersist,
                bodyMarginDp = bodyMarginDp,
                onBodyMarginChange = onBodyMarginChange,
                onBodyMarginPersist = onBodyMarginPersist,
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
