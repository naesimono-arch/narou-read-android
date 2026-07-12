package com.novelreader.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novelreader.NovelReaderApplication
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.model.ParseResult
import com.novelreader.model.TocEntry
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.computeContinuation
import com.novelreader.narou.narouEpisodeUrl
import com.novelreader.narou.narouWorkUrl
import com.novelreader.narou.model.Ncode
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.MotionDurationCrossfade
import com.novelreader.ui.theme.MotionSpringBarSettle
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.rememberReadingColors
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.NcodeSearchUiState
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.Spacing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    bookId: BookId,
    startFile: String,
    htmlDirPath: String,
    bookTitle: String,
    ncode: Ncode?,
    viewModel: BookshelfViewModel,
    // テーマは MainActivity が持つ単一正本を受け取る（本棚と共有して全体を同期させるため）。
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    // 「システムに従う」＝reading_theme 未宣言状態（正本・切替とも MainActivity 側）。
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    onNavigateToBookshelf: () -> Unit,
) {
    // 現在表示中のファイル（章 or "index.html"＝目次）。読書画面のナビは「本棚 ＞ 目次 ＞ 本文」の
    // 固定2階層で、Back は必ず 本文→その本の目次／目次→本棚 に collapse する（下の BackHandler 参照）。
    // なぜ履歴リストを持たないか（2026-07-12 戻るスタック再設計）: 旧実装は訪れたファイルを全て積む
    // navHistory を持ち、本文→目次→本文…と潜ると Back で全経路を逆再生させられていた（重大な UX 問題）。
    // 階層が「本棚>目次>本文」と一定である以上、現在地1個だけ持てば Back 先は決定論的に導ける（履歴は不要）。
    // なぜ rememberSaveable に bookId.value（生 String）をキーとして含めるか:
    // ルートが reading/{bookId}/{startFile} なので NavBackStackEntry 単位でスコープされるが、
    // Navigation の実装詳細に依存しないよう bookId を明示キーに含めて書籍切替時の状態混線を防ぐ。
    // BookId は value class のため素の $bookId 補間は "BookId(value=…)" になる＝保存キーの文字列同一性を
    // 型付け前と厳密に保つため生の値で補間する。プロセス再生成後も現在地を復元できるよう永続化する。
    var currentFile by rememberSaveable(key = "currentFile_${bookId.value}") { mutableStateOf(startFile) }

    // 最後に表示していた章。目次表示中の「現在章ハイライト」に使う。
    // なぜ currentFile と別に持つか: 目次を開くと currentFile は "index.html" に
    // なるため、どの章から来たかを別途保持する必要があるため。
    var lastChapterFile by rememberSaveable(key = "lastChapter_${bookId.value}") {
        mutableStateOf(startFile.takeIf { it != "index.html" })
    }

    // 参照ジャンプの退避元（C1／公理14D・公理6）。目次から別章を「確認しに」開いたときの
    // 元の続き位置（章ファイル名）を保持する。null = 参照モードでない＝通常読書。
    // なぜ rememberSaveable か: 参照中にプロセス再生成されても「続きに戻る」導線を失わないため。
    // なぜ生 String? を素の既定 Saver で保存できるか: 章ファイル名は String のため Saver 不要。
    var jumpOrigin by rememberSaveable(key = "jumpOrigin_${bookId.value}") {
        mutableStateOf<String?>(null)
    }
    // 参照モード中は現在地の自動保存を抑止する（続き先端の DB 値を守るため）。
    val referenceMode = jumpOrigin != null

    // 章/目次へ「進む」共通処理（前後章ボタン・目次ボタン用）。履歴を1段積み、章なら現在章を更新する。
    // 【生命線】index.html（目次）への遷移は進捗を保存しない（ブロックリスト方式の既存保証を踏襲）。
    // なぜ eager saveProgress を廃したか（C1／公理14D・公理6）: 目次以外への遷移で無条件に
    // saveProgress（scrollIndex=0）を書くと、目次から章を確認しに開いた瞬間に読みかけ先端が
    // 章先頭へ恒久上書きされ喪失していた。新章の位置は ChapterScreen の debounce/ON_STOP
    // フラッシュが現在地で保存するため、遷移時点の即時保存は不要（＝二重に壊す原因を除去）。
    val navigateForward: (String) -> Unit = { target ->
        currentFile = target
        if (target != "index.html") {
            lastChapterFile = target
        }
        // 前後章で参照元の続き章に戻ったら参照モードを解除（続きに戻るチップの役目終了）。
        if (target == jumpOrigin) {
            jumpOrigin = null
        }
    }

    // 目次からの章選択（C1）。前後章の「読み進め」と区別し、続き位置と別の章を選んだら
    // 参照ジャンプとみなして続き位置を jumpOrigin へ退避する。滞留昇格まで自動保存は抑止する。
    // なぜ「初回のみ退避」か: 参照中にさらに目次で別章へ飛んでも、真の続き位置（最初に離れた章）を
    // 保持し続けるため（2段目以降の退避で jumpOrigin を上書きしない）。
    val onSelectChapterFromToc: (String) -> Unit = { target ->
        val origin = lastChapterFile
        if (jumpOrigin == null && origin != null && origin != target) {
            jumpOrigin = origin
        }
        currentFile = target
        lastChapterFile = target
        // 目次から参照元の続き章そのものを選び直したら参照モードを解除（既に続きへ戻ったため）。
        if (target == jumpOrigin) {
            jumpOrigin = null
        }
    }

    // Back キー: 「本棚 ＞ 目次 ＞ 本文」の固定2階層で、本文なら必ずその本の目次へ、目次なら本棚へ
    // collapse する（どう潜っても戻るは最大2手＝本文→目次→本棚。旧 navHistory の全経路逆再生を撤廃）。
    // App bar の ←（Up）もこの2階層に統一する（章の ←→目次／目次の ←→本棚＝2026-07-12 追補・ユーザー要望）。
    // 章の ← は onNavigateTo("index.html")、目次の ← は onNavigateToBookshelf を各画面側で呼ぶため、
    // 挙動は Back と一致する（かつての「Back と Up の分離」は廃止）。
    // 【生命線】戻り時に saveProgress を呼ばない理由: 章へ戻ると saveProgress は scrollIndex=0 を書き、
    // その章の保存済みスクロールを先頭へ潰してしまう。戻り先（目次経由で再選択した章）は ChapterScreen
    // 再表示時の debounce/onStop フラッシュで正しい現在位置が保存されるため、ここでは currentFile の
    // 更新に留め、進捗の破壊的上書きを避ける（前進時のみ「新しく開いた章＝先頭から」を保存する非対称設計）。
    // 目次へ戻るとき lastChapterFile は現在章ハイライト用に維持する（更新しない）。
    // 参照モード（jumpOrigin）の解除は「続きに戻る」チップ・滞留昇格・目次からの続き章再選択が担う。
    // Back は本文→目次にしか進まず jumpOrigin 章へ直接戻ることはないため、ここでの解除処理は不要。
    BackHandler(enabled = true) {
        if (currentFile != "index.html") {
            currentFile = "index.html"
        } else {
            onNavigateToBookshelf()
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

    val scope = rememberCoroutineScope()

    // 「続きに戻る」チップ（C1）。参照ジャンプの退避元へ復帰する。
    // なぜ DB を取り直して chapterRestore を最新化するか: 参照中は現在地の自動保存を抑止しており
    // DB は続き先端を保持している。入場時に一度取得した chapterRestore はその後の読み進めを
    // 反映していないため、復帰直前に取り直して「章一致時のみスクロール注入」で先端へ正確に戻す。
    val onReturnToContinuation: () -> Unit = {
        val origin = jumpOrigin
        if (origin != null) {
            scope.launch {
                val p = viewModel.getProgress(bookId)
                chapterRestore = ChapterRestore(
                    targetFile = p?.lastReadFilename,
                    scrollIndex = p?.scrollIndex ?: 0,
                    scrollOffset = p?.scrollOffset ?: 0,
                )
                currentFile = origin
                lastChapterFile = origin
                jumpOrigin = null
            }
        }
    }

    // 滞留昇格（C1）。参照ジャンプ先に十分留まった＝読み進めと判断したら参照モードを解除する。
    // 以後は現在地が正規の読書位置として保存される（現在地の即時保存は ChapterScreen 側が行う）。
    val onPromoteToReading: () -> Unit = {
        jumpOrigin = null
    }

    // 読了記録（ssot Major）。最終章の末尾までスクロールし切ったとき ChapterScreen から一度だけ呼ばれる。
    // 呼び出しは冪等（reachedEnd を UPDATE で立てるだけ・sticky）。参照モード中は ChapterScreen 側で抑止する。
    val onReachedEnd: () -> Unit = { viewModel.markReachedEnd(bookId) }

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
            // 例外時は静かに空表示せず、再試行導線つきのエラー状態にする（読者が復帰できるように）。
            // なぜ固定文言か（errtext 08§B①）: 生の例外メッセージは絶対パスや ENOENT 等の内部理由を
            // 含み UI に露出すると読者を混乱させるため。原因の詳細は Log.e で開発ログにのみ残す。
            Log.e("ReadingScreen", "目次パース失敗", e)
            TocState.Error("目次を読み込めませんでした")
        }
    }

    // 章の前後ナビゲーション・継続導線ロジックは章リストの実体（List<TocEntry>）を必要とする。
    // Content のときだけ中身を渡し、それ以外（Loading/Empty/Error）は空リスト＝「未ロード」として扱う。
    val tocEntries = (tocState as? TocState.Content)?.entries ?: emptyList()

    if (resolvedFile == null) {
        // HTML 実体（index.html／章ファイル）がこの端末に無い＝本文データ不在。
        // 主因はバックアップ復元後（ADR 0015 の層別 Auto Backup＝メタデータのみ復元・HTML 実体は非バックアップ）で、
        // 蔵書メタと読書位置は在るが本文が端末に存在しないケース（=C2 graceful degrade）。破損で HTML が消えた
        // 場合も救済策は同じ「同じ PDF を取り込み直す」なので、固定文言で行き止まりにせず再取込へ導く。
        // 【重要】進捗 DB は一切触らない（位置・しおりを保持したまま再取込で続きから読めるようにするのが C2 の要件）。
        // 再取込はファイルピッカー起点＝本棚からしか始められないため、導線は「本棚に戻る」のみ（onRetry は付けない）。
        ReadingErrorScreen(
            message = "本文データがこの端末にありません。同じ PDF を取り込み直すと続きから読めます",
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
            // 章選択は参照ジャンプ扱い（C1）。続き位置と別章なら jumpOrigin へ退避し自動保存を抑止する。
            onSelectChapter = onSelectChapterFromToc,
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

    // なろう紐付けシートの候補検索の状態。検索実行は VM が持つ単一正本を collect して ChapterScreen へ渡す
    // （旧: シートが NovelApiRepository を直接受け produceState で回していた依存注入漏れを解消）。
    val ncodeSearchState by viewModel.ncodeSearchState.collectAsStateWithLifecycle()

    ChapterScreen(
        currentFile = resolvedFile,
        htmlDirPath = htmlDirPath,
        tocEntries = tocEntries,
        bookTitle = bookTitle,
        ncode = ncode,
        // 紐付けの永続化。books は hot StateFlow のため、書き込みは MainActivity → ncode 引数へ
        // 自動で還流し、確定直後から継続導線が紐付け済み表示に切り替わる。
        onLinkNcode = { newNcode -> viewModel.linkNcode(bookId, newNcode) },
        ncodeSearchState = ncodeSearchState,
        onSearchNcode = { query -> viewModel.searchNcodeCandidates(query) },
        onRetryNcodeSearch = { viewModel.retryNcodeSearch() },
        readingTheme = readingTheme,
        onThemeChange = onThemeChange,
        followingSystem = followingSystem,
        onFollowSystem = onFollowSystem,
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
            // resolvedFile は画面内部の String。型付き API 境界でのみ ChapterFilename に包む。
            viewModel.saveScrollPosition(bookId, ChapterFilename(resolvedFile), index, offset)
        },
        onNavigateToBookshelf = onNavigateToBookshelf,
        // 前後章・目次ボタンからの遷移も「進む」＝履歴を積む（Back で1段ずつ遡れる）
        onNavigateTo = navigateForward,
        // 参照ジャンプ（C1）: 抑止フラグ・「続きに戻る」復帰・滞留昇格を ChapterScreen へ渡す。
        referenceMode = referenceMode,
        onReturnToContinuation = onReturnToContinuation,
        onPromoteToReading = onPromoteToReading,
        // 読了検出（ssot Major）: 最終章の末尾到達を ChapterScreen が検知して呼ぶ。
        onReachedEnd = onReachedEnd,
    )
}

/** 読書再開位置。targetFile（最後に読んだ章）と一致する章のみスクロール位置を復元する。 */
private data class ChapterRestore(
    val targetFile: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
)

/** 参照ジャンプ滞留昇格（C1）: この時間だけ参照先に滞在したら読み進めとみなし正規位置へ昇格。 */
private const val REFERENCE_DWELL_TIMEOUT_MS = 20_000L

/** 参照ジャンプ滞留昇格（C1）: この段落数だけスクロールしたら読み進めとみなし正規位置へ昇格。 */
private const val REFERENCE_DWELL_SCROLL_ITEMS = 4

/** 章本文を表示する内部 Composable */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ChapterScreen(
    currentFile: String,
    htmlDirPath: String,
    tocEntries: List<TocEntry>,
    bookTitle: String,
    ncode: Ncode?,
    onLinkNcode: (Ncode?) -> Unit,
    // なろう紐付けシートの候補検索（state は VM の単一正本／検索・再試行は VM へ依頼）。
    ncodeSearchState: NcodeSearchUiState,
    onSearchNcode: (query: String) -> Unit,
    onRetryNcodeSearch: () -> Unit,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
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
    // 参照ジャンプ（C1）。referenceMode 中は現在地の自動保存を抑止し「続きに戻る」チップを出す。
    referenceMode: Boolean,
    onReturnToContinuation: () -> Unit,
    onPromoteToReading: () -> Unit,
    // 読了検出（ssot Major）。最終章の末尾を可視化したとき一度だけ呼ぶ（参照モード中は抑止）。
    onReachedEnd: () -> Unit,
) {
    val colors = rememberReadingColors(readingTheme)
    // 画面ローカルの UI 状態（表示設定シート開閉・紐付けシート開閉・ボトムバー実測高・コルーチンスコープ・
    // 非横取り NestedScroll 接続）は描画層 ChapterScreenContent 内に保持する（route/Content 分割＝BookshelfContent
    // と同方針で画面ローカル UI は Content の内部状態に留める）。route が保持するのは副作用（parse/継続照会/
    // スクロール保存/ライフサイクルフラッシュ/没入ヒント）と VM・プラットフォーム依存・派生ナビ状態のみ。

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
                else ParseResult.Error("章を開けませんでした", currentFile)
            } catch (e: Exception) {
                // 固定文言化（errtext 08§B①）: 生の例外メッセージ（絶対パス/ENOENT 等）を UI に出さず、
                // 原因の詳細は Log.e で開発ログにのみ残す。UI は再試行導線つきの固定文言に留める。
                Log.e("ReadingScreen", "章パース失敗: $currentFile", e)
                ParseResult.Error("章を開けませんでした", currentFile)
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

    // 継続カードからの外部遷移（Custom Tabs 起動）の再入ガード用タイムスタンプ（M1/公理3）。
    // なぜ必要か: Custom Tabs はブラウザ別プロセスの起動待ちがあり、反応が無いと利用者が連打しやすい。
    // その間 launchUrl が複数回走ると続き/作品ページが2枚重なって開くため、直近起動から一定時間内の
    // タップを無視して二重起動を防ぐ。「続きを読む」「作品ページ」の両ボタンで共有し、跨ぎ連打も抑える。
    var lastLaunchAt by remember { mutableStateOf(0L) }

    // Back キー（本文→その本の目次／目次→本棚 の固定2階層 collapse）は親の ReadingScreen が
    // currentFile＋BackHandler で一元管理する。currentFile を所有するのが ReadingScreen のため、
    // rememberSaveable 永続化もそちらに集約した（ここでは扱わない）。

    // snapAnimationSpec = null: デフォルトのスナップを無効化する。
    // スナップが有効だとわずかなスクロールでバーが「自走」し、
    // ページの動きと乖離した独立した動きに見えてしまうため。
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        topAppBarState,
        snapAnimationSpec = null,
    )

    // 入場時既定=「無」（d-chrome Design/09-A）。章題は本文先頭の ChapterHeader が担うため、
    // 入場時に上部バーを見せる必要はない。heightOffsetLimit は TopAppBar が実測後に負値へ更新するため、
    // 確定を待って一度だけ全退避する（初期 0 のまま畳んでも効かないため待つ）。
    // なぜ rememberSaveable の guard か: ユーザーが一度バーを出した後（プロセス再生成の復元含む）に
    // 再び勝手に畳んで操作を奪わないため。topAppBarState 自体も heightOffset を復元するので二重に安全。
    var didInitialCollapse by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(topAppBarState) {
        if (didInitialCollapse) return@LaunchedEffect
        snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
        topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        didInitialCollapse = true
    }

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
    // 参照モードの最新値を State 越しに読む（コルーチンを再起動させずに抑止/再開を切り替えるため）。
    val referenceModeState = rememberUpdatedState(referenceMode)
    LaunchedEffect(lazyListState, currentFile) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
            .debounce(400)
            // 参照ジャンプ中（C1）は現在地を書かない＝続き先端の DB 値を守る。昇格後に再開する。
            .collect { (index, offset) -> if (!referenceModeState.value) latestOnSaveScroll(index, offset) }
    }

    // 滞留昇格（C1）: 参照ジャンプ先に一定スクロール到達 or 一定時間の滞在で「読み進め」と判断し、
    // 参照モードを解除して現在地を正規の読書位置として保存する（参照ジャンプと読み進めの区別）。
    // なぜ if で括った LaunchedEffect か: referenceMode が true の間だけ滞留タイマーを走らせ、
    // 昇格（onPromoteToReading で referenceMode=false）や画面離脱で自動的にキャンセルさせるため。
    if (referenceMode) {
        LaunchedEffect(currentFile) {
            // どちらか早い方で昇格。タイムアウト（＝十分に滞在）でもスクロール到達でも読み進めとみなす。
            withTimeoutOrNull(REFERENCE_DWELL_TIMEOUT_MS) {
                snapshotFlow { lazyListState.firstVisibleItemIndex }
                    .first { it >= REFERENCE_DWELL_SCROLL_ITEMS }
            }
            onPromoteToReading()
            // 昇格直後に現在地を保存して「今ここ」を確定させる（onSaveScroll は抑止されない生の保存）。
            latestOnSaveScroll(
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }

    // 読了検出（ssot Major 2026-07-12）: 最終章で末尾アイテムまで可視化されたら一度だけ読了を記録する。
    // なぜ最終章限定か: 読了＝「本の末尾に到達」なので、中間章の末尾は継続であり読了ではない（isLastChapter で絞る）。
    // なぜ参照モード中は昇格しないか（C1）: 目次から最終章を「確認しに」開いただけで読了になる事故を防ぐため、
    //   自動保存抑止と同じ referenceMode ゲートに載せる（覗き見は読了にしない）。
    // なぜ snapshotFlow か: layoutInfo（末尾アイテムの可視判定）はフレーム毎に変わるフレームレート state で、
    //   composition 内で読むと毎フレーム再コンポーズを誘発する。snapshotFlow で composition 外へ逃がして軽く観測する。
    // 末尾判定: 可視アイテムの最終 index が総アイテム数-1 以上＝リスト末尾（最終段落 or 継続カード）が画面に入った。
    // marked ローカルで一度だけに絞る（markReachedEnd 自体は冪等だが不要な DB 叩きを避ける）。章再入場で作り直され
    //   marked=false に戻るため、参照モードで見た末尾は次回の通常読書で拾い直せる（自己回復）。
    val latestOnReachedEnd by rememberUpdatedState(onReachedEnd)
    if (isLastChapter) {
        LaunchedEffect(lazyListState, currentFile) {
            var marked = false
            snapshotFlow {
                val layoutInfo = lazyListState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                val total = layoutInfo.totalItemsCount
                // 末尾アイテムが可視かつ実アイテムがある（Loading の 0 件を末尾誤検知しない）。
                lastVisibleIndex != null && total > 0 && lastVisibleIndex >= total - 1
            }
                .distinctUntilChanged()
                .collect { atEnd ->
                    // 参照モード中は記録しない（覗き見で読了にしない）。抑止フラグは最新値を State 越しに読む。
                    if (atEnd && !marked && !referenceModeState.value) {
                        marked = true
                        latestOnReachedEnd()
                    }
                }
        }
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
            // 参照ジャンプ中（C1）は ON_STOP でも書かない＝続き先端の DB 値を守る。
            if (event == Lifecycle.Event.ON_STOP && !referenceModeState.value) {
                latestOnSaveScroll(
                    lazyListState.firstVisibleItemIndex,
                    lazyListState.firstVisibleItemScrollOffset,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ────── 没入バー契約＋消灯抑止（d-chrome Design/09 D・F）──────
    // なぜ view から window を辿るか: Edge-to-Edge（MainActivity で setDecorFitsSystemWindows(false)）済みの
    // ウィンドウに対し、Compose から WindowInsetsController でシステムバーの可視性を直接駆動するため。
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        // 09-F 消灯抑止: 読書中（章表示中のみ）は画面を消灯させない。長章の無操作読書で暗転しないように。
        view.keepScreenOn = true
        // 09-D バー契約: システムバーを隠しても縁スワイプで一時的に呼び戻せる挙動にする。
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            // 読書画面を離れたら消灯抑止を解除し、システムバーを必ず戻す（本棚・発見系を没入にしない）。
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 09-D バー契約: 自作の上下バー（collapsedFraction）とシステムバーを同フレームで出入りさせる。
    // これが無いと自作バーは退避してもOSのステータス/ナビバーが黒衣で残り、読書画面が一度も「無」に
    // 到達しなかった。collapsedFraction<0.5＝自作バー表示側 → システムバーも表示、以外は隠す。
    LaunchedEffect(topAppBarState, view) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        snapshotFlow { topAppBarState.collapsedFraction < 0.5f }
            .distinctUntilChanged()
            .collect { chromeVisible ->
                if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
                else controller.hide(WindowInsetsCompat.Type.systemBars())
            }
    }

    // ────── 没入クローム復帰ヒント（層②）──────
    // クローム（上下バー）が初めて画面外へ退避したとき、復帰操作（中央タップ）を数秒だけ
    // 一過性ラベルで示す。なぜ一度きり・自動消灯か: 常時の帯は没入を削ぐため、初回消灯時の
    // 学習機会だけを与え以後は出さない（M12＝復帰手段が不可視だった問題への最小介入）。
    // なぜ prefs で永続化しアプリ通算初回のみにするか: セッション毎の表示は、復帰操作を既に
    // 学習済みのユーザーには冗長。ヒントの目的（復帰手段の可視化）は一度の学習で達成されるため、
    // 表示済みフラグを prefs に持たせて通算初回だけに絞る。他の読書設定と同じ app_prefs に置く。
    val chromeHintPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var chromeHintConsumed by remember {
        mutableStateOf(chromeHintPrefs.getBoolean("immersive_hint_shown", false))
    }
    var showChromeHint by remember { mutableStateOf(false) }
    LaunchedEffect(topAppBarState) {
        snapshotFlow { topAppBarState.collapsedFraction > 0.9f }
            .distinctUntilChanged()
            .collect { hidden ->
                if (hidden && !chromeHintConsumed) {
                    chromeHintConsumed = true
                    // 表示に踏み切った時点で永続フラグを立てる＝以後のセッションでは二度と出さない。
                    // apply は非同期ディスク書込のため UI をブロックしない。
                    chromeHintPrefs.edit().putBoolean("immersive_hint_shown", true).apply()
                    showChromeHint = true
                    delay(2600)
                    showChromeHint = false
                }
            }
    }

    // 継続カード → Custom Tabs の外部遷移コールバック。再入ガード（M1/公理3）・context・openInAppBrowser は
    // すべて副作用のため route（状態保持層）に留め、描画層 ChapterScreenContent には「押された」ことだけを渡す。
    val onReadContinuation: () -> Unit = {
        // 再入ガード（M1/公理3）: 連打で Custom Tabs が2枚開くのを防ぐ。
        val now = System.currentTimeMillis()
        if (now - lastLaunchAt >= 1000L) {
            lastLaunchAt = now
            // 主ボタンは NewEpisodes のときしか描画されないが、防御的に型で絞る。
            // ツールバー色は明示指定せず既定（ブラウザのサイト識別色）に委ねる（M9・公理8）＝なろうへ外部
            // 遷移した事実を隠さず、今どこに居るかを判別できるようにするため（背景同化＝没入優先は M2 で撤回）。
            (continuationInfo as? ContinuationInfo.NewEpisodes)?.let {
                openInAppBrowser(context, narouEpisodeUrl(it.ncode, it.nextEpisode))
            }
        }
    }
    val onOpenWorkPage: () -> Unit = {
        // 同上: 再入ガード＋ツールバー色は既定（サイト識別可能）に委ねる。
        val now = System.currentTimeMillis()
        if (now - lastLaunchAt >= 1000L) {
            lastLaunchAt = now
            // このコールバックは Content 側で continuationInfo!=null の作品ページボタンからのみ配線されるため
            // null 安全に読んでも挙動は元の info.ncode と同一（カード非表示時は発火経路が無い）。
            continuationInfo?.let { openInAppBrowser(context, narouWorkUrl(it.ncode)) }
        }
    }

    ChapterScreenContent(
        parseResult = parseResult,
        colors = colors,
        fontSize = fontSize,
        onFontSizeChange = onFontSizeChange,
        onFontSizePersist = onFontSizePersist,
        lineHeightEm = lineHeightEm,
        onLineHeightChange = onLineHeightChange,
        onLineHeightPersist = onLineHeightPersist,
        bodyMarginDp = bodyMarginDp,
        onBodyMarginChange = onBodyMarginChange,
        onBodyMarginPersist = onBodyMarginPersist,
        readingTheme = readingTheme,
        onThemeChange = onThemeChange,
        followingSystem = followingSystem,
        onFollowSystem = onFollowSystem,
        lazyListState = lazyListState,
        topAppBarState = topAppBarState,
        scrollBehavior = scrollBehavior,
        prevFile = prevFile,
        nextFile = nextFile,
        navEnabled = navEnabled,
        isLastChapter = isLastChapter,
        ncode = ncode,
        continuationInfo = continuationInfo,
        showChromeHint = showChromeHint,
        // 参照ジャンプ中（C1）は「続きに戻る」チップを表示する。
        showReturnChip = referenceMode,
        onReturnToContinuation = onReturnToContinuation,
        bookTitle = bookTitle,
        ncodeSearchState = ncodeSearchState,
        onSearchNcode = onSearchNcode,
        onRetryNcodeSearch = onRetryNcodeSearch,
        onLinkNcode = onLinkNcode,
        onReadContinuation = onReadContinuation,
        onOpenWorkPage = onOpenWorkPage,
        onNavigateTo = onNavigateTo,
        onNavigateToBookshelf = onNavigateToBookshelf,
        onRetryParse = { retryKey++ },
    )
}

/**
 * 章読書画面の描画層（stateless / route/Content 分割の content）。ChapterScreen からの純移動。
 * VM・SharedPreferences・narouRepository・非同期パース/継続照会・スクロール保存/ライフサイクルフラッシュ・
 * 没入ヒントといった副作用はすべて route（ChapterScreen）に残し、ここは受け取った state＋コールバックだけで
 * Scaffold＋上下バー（オーバーレイ）＋継続導線＋各シートを描画する葉（BookshelfContent と同方針）。
 * 画面ローカルの UI 状態（設定シート/紐付けシート開閉・ボトムバー実測高・タップ用コルーチンスコープ・
 * 非横取り NestedScroll 接続）のみ内部に保持する（過剰な hoisting は避ける）。
 * Custom Tabs 起動（再入ガード付き）は副作用のため route の [onReadContinuation]/[onOpenWorkPage] へ委譲する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterScreenContent(
    parseResult: ParseResult,
    colors: ReadingColors,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onFontSizePersist: () -> Unit,
    lineHeightEm: Float,
    onLineHeightChange: (Float) -> Unit,
    onLineHeightPersist: () -> Unit,
    bodyMarginDp: Int,
    onBodyMarginChange: (Int) -> Unit,
    onBodyMarginPersist: () -> Unit,
    readingTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    // トップバー退避・スクロール位置の state holder は route が保持する副作用（没入ヒント・スクロール保存）と
    // 共有するため route で生成し、ここへ渡す（描画はこの holder を読むだけ＝純移動）。
    lazyListState: LazyListState,
    topAppBarState: TopAppBarState,
    scrollBehavior: TopAppBarScrollBehavior,
    prevFile: String,
    nextFile: String,
    navEnabled: Boolean,
    isLastChapter: Boolean,
    ncode: Ncode?,
    continuationInfo: ContinuationInfo?,
    showChromeHint: Boolean,
    // 参照ジャンプ（C1）の「続きに戻る」チップ表示と復帰コールバック。
    showReturnChip: Boolean,
    onReturnToContinuation: () -> Unit,
    bookTitle: String,
    ncodeSearchState: NcodeSearchUiState,
    onSearchNcode: (query: String) -> Unit,
    onRetryNcodeSearch: () -> Unit,
    onLinkNcode: (Ncode?) -> Unit,
    // 継続カードの外部遷移（Custom Tabs）は route が再入ガード付きで実行する。
    onReadContinuation: () -> Unit,
    onOpenWorkPage: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onRetryParse: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // 表示設定ボトムシートの開閉状態。
    // なぜ rememberSaveable か: 素の remember だとプロセス再生成（回転・background kill）で
    // シートだけ閉じてしまい、開いていた文脈が飛ぶ。検索シート（DiscoverySearchScreen）と
    // 同様に開閉を Saveable 化して復元する（Boolean は既定 Saver で保存可＝Saver 不要）。
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // なろう紐付けシートの開閉状態。
    // なぜ rememberSaveable か: 上の表示設定シートと同じく、プロセス再生成でシートだけ
    // 閉じる不整合を避けるため開閉を Saveable 化する（Boolean は既定 Saver で保存可）。
    var showLinkSheet by rememberSaveable { mutableStateOf(false) }

    // ボトムバーの実測高さ（px）。退避スライド量に使う。
    // なぜ固定値にしないか: ナビゲーションバー実高（ボタン式/ジェスチャー式）でバー総高が
    // 変わるため、onSizeChanged で実測した高さ分だけスライドさせて完全に画面外へ退避させる。
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // enterAlwaysScrollBehavior のデフォルト接続はスクロールを横取りしやすい。
    // 読書体験を優先するため、本文には常にスクロールを渡しつつバー状態だけ追従させる。
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

                    is ParseResult.Success -> {
                        // 継続導線スロット。最終章のみ: 未紐付け=静かな探索導線／紐付け済み=継続カード。
                        // ローカル val に固めるのはスマートキャストを効かせるため。
                        val info = continuationInfo
                        // liveRegion=Polite（a11y 公理11F/WCAG4.1.3）: 継続導線は最終章で非同期に
                        // 出現するため、フォーカス外でも TalkBack が出現を穏やかに告知するよう包む。
                        val continuationSlot: (@Composable () -> Unit)? = when {
                            !isLastChapter -> null
                            ncode == null -> ({
                                Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                                    ContinuationLinkPrompt(
                                        colors = colors,
                                        bodyMarginDp = bodyMarginDp,
                                        onClick = { showLinkSheet = true },
                                    )
                                }
                            })
                            info != null -> ({
                                Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                                    ContinuationCard(
                                        info = info,
                                        colors = colors,
                                        bodyMarginDp = bodyMarginDp,
                                        // Custom Tabs 起動（再入ガード）は副作用のため route へ委譲する。
                                        onReadContinuation = onReadContinuation,
                                        onOpenWorkPage = onOpenWorkPage,
                                        onUnlink = { onLinkNcode(null) },
                                    )
                                }
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

                    // liveRegion=Polite（a11y 公理11F/WCAG4.1.3）: 章パースが Loading→Error へ
                    // 非同期に切り替わったことをフォーカス外でも TalkBack が告知する。ReadingErrorScreen は
                    // Modifier を受け取らないため、告知用に semantics つきの Box で包む（併合はしない＝
                    // 「本棚に戻る」「再試行」ボタンを個別ノードのまま残す）。
                    is ParseResult.Error -> Box(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        ReadingErrorScreen(
                            message = result.message,
                            colors = colors,
                            onNavigateToBookshelf = onNavigateToBookshelf,
                            onRetry = onRetryParse,
                        )
                    }
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
                        fontSize = FontSectionTitle,
                        maxLines = 1,
                        // 長い章タイトルは文字途中で切らず末尾を「…」で省略する
                        overflow = TextOverflow.Ellipsis,
                    )
                    else -> Unit
                }
            },
            navigationIcon = {
                // 章の ← は Back と同じく「その本の目次へ」戻る（2階層統一・2026-07-12）。
                // 本棚へは目次画面の ← が担う（本文→目次→本棚。旧: ここから本棚へ直行していた）。
                IconButton(onClick = { onNavigateTo("index.html") }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "目次に戻る",
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
        // fade は motion トークン MotionDurationCrossfade 経由（d-motion 08 禁止則②＝野良既定に委ねない）。
        AnimatedVisibility(
            visible = showChromeHint,
            enter = fadeIn(tween(MotionDurationCrossfade)),
            exit = fadeOut(tween(MotionDurationCrossfade)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Insets.ChromeHintBottom),
        ) {
            Box(
                modifier = Modifier
                    // 半透明のナビ背景色で本文に沈める丸ピル（色は必ずテーマトークン経由）
                    .clip(RoundedCornerShape(50))
                    .background(colors.navBackground.copy(alpha = 0.92f))
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
            ) {
                Text(
                    // 実タップ領域は本文全面（中央に限らない）ため文言も全面に一致させる（gesture 指摘）。
                    text = "画面をタップでメニュー",
                    color = colors.topBarIcon,
                    fontFamily = MinchoFamily,
                    fontSize = FontSubTitle,
                )
            }
        }

        // 「続きに戻る」チップ（C1）。参照ジャンプ中だけ上端中央に常時表示し、退避元の続き位置へ復帰する。
        // 意匠は復帰ヒントの丸ピルと同型（新意匠を発明しない）。ヒントと違い自動消灯せず、タップ可能。
        AnimatedVisibility(
            visible = showReturnChip,
            enter = fadeIn(tween(MotionDurationCrossfade)),
            exit = fadeOut(tween(MotionDurationCrossfade)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Spacing.S8),
        ) {
            Text(
                text = "続きに戻る",
                color = colors.topBarIcon,
                fontFamily = MinchoFamily,
                fontSize = FontSubTitle,
                modifier = Modifier
                    // 復帰ヒントと同じ半透明ピル。こちらはタップで退避元へ戻る。
                    .clip(RoundedCornerShape(50))
                    .background(colors.navBackground.copy(alpha = 0.92f))
                    .clickable(onClick = onReturnToContinuation)
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
            )
        }

        if (showLinkSheet) {
            // シートを開いた瞬間に書名を初期クエリとして検索する（旧: シート内 produceState が
            // activeQuery=bookTitle で初期照会していたのと等価。検索実行は VM へ移設済み）。
            // このシート起動時検索の LaunchedEffect は if(showLinkSheet) ブロックと不可分のため、
            // 純移動として描画層へ一緒に持ち込む（開閉 state がこの Content 内に閉じるため所属も自然）。
            LaunchedEffect(Unit) { onSearchNcode(bookTitle) }
            NcodeLinkSheet(
                bookTitle = bookTitle,
                searchState = ncodeSearchState,
                colors = colors,
                onSearch = onSearchNcode,
                onRetry = onRetryNcodeSearch,
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
                followingSystem = followingSystem,
                onFollowSystem = onFollowSystem,
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
        animationSpec = MotionSpringBarSettle,
    ) { value, _ ->
        state.heightOffset = value
    }
}
