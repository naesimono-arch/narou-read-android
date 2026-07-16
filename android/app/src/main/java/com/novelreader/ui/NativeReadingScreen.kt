package com.novelreader.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.abs
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novelreader.NovelReaderApplication
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.model.ChapterContent as ChapterContentModel
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
import com.novelreader.ui.theme.FontNavLabel
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.MotionDurationCrossfade
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationNavTransition
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * [ReadingBackStack] の rememberSaveable 用 Saver。画面はファイル名 String の列のため、
 * listSaver で screens をそのまま保存/復元する（各要素は生 String＝素の Saver で保存可）。
 * Saver をここに置くのは ReadingBackStack を Compose 非依存（JVM 単体テスト可）に保つため。
 */
private val readingBackStackSaver = listSaver<ReadingBackStack, String>(
    save = { it.screens },
    restore = { ReadingBackStack(it) },
)

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
    // 読書ナビの Back スタック（実データ構造＝ReadingBackStack）。末尾が現在地。
    // なぜ「現在地1個」でなく実スタックへ再設計したか（2026-07-15 決定・07/12 固定2階層の棄却）:
    // 07/12 は「本棚>目次>本文」の固定2階層で Back を常に collapse したが、本棚→本文直行（続きから）でも
    // Back が目次を強制通過する悪 UX だった。実スタックにすると Back が実際に辿った経路だけを逆再生する
    // （直行なら Back 1発で本棚・目次経由なら目次→本棚）。push/replace/pop 規則と不変条件は ReadingBackStack 参照。
    // なぜ旧 navHistory 全逆再生バグを再発させないか: 覗き（目次⇄章）は ReadingBackStack が
    // 「既出は巻き戻し・話送りは置き換え」で段を増やさないため、何度覗いてもスタック深さは不変（不変条件②）。
    // なぜ rememberSaveable に bookId.value（生 String）をキーとして含めるか:
    // ルートが reading/{bookId}/{startFile} なので NavBackStackEntry 単位でスコープされるが、
    // Navigation の実装詳細に依存しないよう bookId を明示キーに含めて書籍切替時の状態混線を防ぐ。
    // BookId は value class のため素の $bookId 補間は "BookId(value=…)" になる＝保存キーの文字列同一性を
    // 型付け前と厳密に保つため生の値で補間する。プロセス再生成後も経路全体を復元できるよう永続化する
    // （画面はファイル名 String の列＝listSaver で素直に保存できる。Saver 不要の生 String 要素）。
    var backStack by rememberSaveable(key = "backStack_${bookId.value}", stateSaver = readingBackStackSaver) {
        mutableStateOf(ReadingBackStack.initial(startFile))
    }
    // 現在表示中のファイル（章 or "index.html"＝目次）は常にスタックトップから導く（単一正本＝backStack）。
    val currentFile = backStack.current

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

    // 章/目次へ「進む」共通処理（前後章ボタン・目次ボタン・章の Up 用）。スタックへ反映し、章なら現在章を更新する。
    // 【生命線】index.html（目次）への遷移は進捗を保存しない（ブロックリスト方式の既存保証を踏襲）。
    // なぜ eager saveProgress を廃したか（C1／公理14D・公理6）: 目次以外への遷移で無条件に
    // saveProgress（scrollIndex=0）を書くと、目次から章を確認しに開いた瞬間に読みかけ先端が
    // 章先頭へ恒久上書きされ喪失していた。新章の位置は ChapterScreen の debounce/ON_STOP
    // フラッシュが現在地で保存するため、遷移時点の即時保存は不要（＝二重に壊す原因を除去）。
    val navigateForward: (String) -> Unit = { target ->
        // 前後章の話送りは横移動（置き換え＝深さ不変）・目次ボタン/章の Up（"index.html"）は目次を開く。
        // どちらも ReadingBackStack.sibling が振り分ける（"index.html"→既存目次へ巻き戻し・無ければ積む）。
        // なぜ replace か: 何話読んでも Back 一段で目次/本棚へ抜けるため（旧固定2階層の「本文→目次」を維持）。
        backStack = backStack.sibling(target)
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
        // 目次からの章選択＝下層へ潜る drill（覗き含む）。既出章なら巻き戻し、無ければ積む。
        // 覗きの反復（目次→章→目次→別章…）は push→Back の pop で相殺され段が増えない（不変条件②）。
        backStack = backStack.openChapter(target)
        lastChapterFile = target
        // 目次から参照元の続き章そのものを選び直したら参照モードを解除（既に続きへ戻ったため）。
        if (target == jumpOrigin) {
            jumpOrigin = null
        }
    }

    // Back キー: 実際に辿った経路（backStack）を末尾から1枚ずつ逆再生する（2026-07-15 再設計・07/12 固定2階層の棄却）。
    // back() が null（スタックが入場画面1枚だけ＝もう戻る先が無い）を返したら本棚へ抜ける。
    // これで「本棚→本文直行なら Back 1発で本棚／本棚→目次→本文なら Back で目次→本棚」が実経路どおりになる。
    // App bar の ←（Up）は今回据え置き＝別経路（各画面が直接呼ぶ）: 章の ← は onNavigateTo("index.html")＝目次を
    // 開く（backStack にも反映）／目次の ← は onNavigateToBookshelf で本棚へ直行（スタックを介さない親ジャンプ）。
    // Up は「目次を開く/本棚へ跳ぶ」の可視挙動を変えないため Back とは別物のまま（Back のみ経路反映へ再設計）。
    // 【旧 navHistory 全逆再生バグの再発防止】Back は必ず「1枚 pop」だけ＝訪れた画面を再 push しない。
    // 覗きで段が増えないのは ReadingBackStack 側の push/replace/巻き戻し規則が担保する（不変条件②）。
    // 【生命線】戻り時に saveProgress を呼ばない理由: 章へ戻ると saveProgress は scrollIndex=0 を書き、
    // その章の保存済みスクロールを先頭へ潰してしまう。戻り先（目次経由で再選択した章）は ChapterScreen
    // 再表示時の debounce/onStop フラッシュで正しい現在位置が保存されるため、ここでは backStack の
    // 更新に留め、進捗の破壊的上書きを避ける（前進時のみ「新しく開いた章＝先頭から」を保存する非対称設計）。
    // lastChapterFile は現在章ハイライト用に維持する（Back では更新しない）。
    // 参照モード（jumpOrigin）の解除は「続きに戻る」チップ・滞留昇格・目次からの続き章再選択が担う。
    // Back で覗き章を pop して目次へ戻っても jumpOrigin は残すが、目次上では referenceMode は無害
    // （抑止・チップは章表示中のみ効く）＝旧固定2階層と同じ挙動を保つ（invariant④: jumpOrigin 挙動を壊さない）。
    BackHandler(enabled = true) {
        val popped = backStack.back()
        if (popped != null) {
            backStack = popped
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
                // 退避元章へ復帰＝横移動（下段に在れば巻き戻し・無ければ覗き章を置き換え）＝段を増やさない。
                backStack = backStack.returnTo(origin)
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

    // メニューの章跨ぎ維持（2026-07-16 実機フィードバック）: 表示状態を章サブコンポジションの外＝ここで
    // 保持し、前章/次章の連続操作でメニューが閉じないようにする（「一度出したら再度タップするまで残る」）。
    // 既定 false＝本の入場時は従来どおり没入。rememberSaveable でプロセス再生成にも耐える。
    var chromeVisibleAcrossChapters by rememberSaveable { mutableStateOf(false) }

    // 章パース結果のキャッシュ（この本の直近数章・アクセス順 LRU）。スワイプ覗きの先読みと章遷移後の
    // 初期表示を共有し、遷移の瞬間に Loading（無地の紙面が一瞬挟まる「暗転」）を挟まないためのもの
    //（2026-07-16 実機所見「移った瞬間に一瞬暗くなる」の真因＝章切替ごとの再パース待ち）。
    // なぜここで所有するか: ChapterScreen は章ごとに作り直されるため、章を跨いで生きる置き場が要る。
    // 上限6: 現在章と前後の往復に十分な最小限（章 HTML は取込後不変なので陳腐化しない）。
    val chapterCache = remember {
        object : LinkedHashMap<String, ChapterContentModel>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChapterContentModel>) = size > 6
        }
    }

    // 章ごとの読書位置のセッション内記憶（file→index/offset）。DB の progress は「本の最後に読んだ場所」
    // 1点のみで、章を跨いで戻ったときの各章の位置は持たない。スワイプ覗きと章送りの着地を「その章を
    // 読んでいた場所」へ揃えるための記憶（2026-07-16 実機フィードバック「覗きが読んだ場所を反映しない」）。
    // プロセス死で消える割り切り＝DB 側の正本（最後に読んだ1点）は従来どおり不変。
    val sessionScrollByFile = remember { mutableMapOf<String, Pair<Int, Int>>() }
    // 章の初期位置の解決順: ①セッション内でその章を読んでいた場所 → ②本の入場復元（最後に読んだ章のみ）
    // → ③先頭。覗きパネルと実着地の両方がこの1本を使う＝覗いた内容と遷移後の表示が必ず一致する。
    val resolveInitialScroll: (String) -> Pair<Int, Int> = { file ->
        sessionScrollByFile[file]
            ?: chapterRestore?.takeIf { it.targetFile == file }?.let { it.scrollIndex to it.scrollOffset }
            ?: (0 to 0)
    }

    // 目次⇄本文の切替を NavHost のスライドと同じ向きルールで演出する（進む=目次→章は右から左へ潜る／
    // 戻る=章→目次は左から右へ戻す）。同一 nav ルート内の state 切替（resolvedFile の出し分け）を
    // AnimatedContent で包む。章→章（話送り）は現状どおり瞬間＝向きを付けない（P1 は別枠のため据え置き）。
    // 尺は MotionDurationNavTransition（250ms）で NavHost の遷移と共有する。
    AnimatedContent(
        targetState = resolvedFile,
        transitionSpec = {
            val d = MotionDurationNavTransition
            val toToc = targetState == "index.html"
            val fromToc = initialState == "index.html"
            when {
                // 章→目次（戻る）: 前画面（目次）が左から入り、本文は右へ抜ける
                toToc -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(d)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(d))
                // 目次→章（進む）: 本文が右から入り、目次は左へ抜ける
                fromToc -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(d)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(d))
                // 章→章は瞬間（現状維持）
                else -> EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "toc-body-slide",
    ) { file ->
        if (file == "index.html") {
            NativeTableOfContentsScreen(
                tocState = tocState,
                colors = readingColors,
                currentChapterFile = lastChapterFile,
                // 章選択は参照ジャンプ扱い（C1）。続き位置と別章なら jumpOrigin へ退避し自動保存を抑止する。
                onSelectChapter = onSelectChapterFromToc,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onRetry = { tocRetryKey++ },
            )
        } else {
            // 章表示。読書再開位置の取得を待ってから描画する。
            // なぜ待つか: LazyListState に初期スクロール位置を注入するため。
            // 取得後に scrollToItem する方式だと「先頭→保存位置」へのジャンプが見えてしまう。
            val restore = chapterRestore
            if (restore == null) {
                // 取得待ちの一瞬。テーマ背景で塗りつぶし白フラッシュを防ぐ
                Box(modifier = Modifier.fillMaxSize().background(readingColors.background))
            } else {
                // なろう紐付けシートの候補検索の状態。検索実行は VM が持つ単一正本を collect して ChapterScreen へ渡す
                // （旧: シートが NovelApiRepository を直接受け produceState で回していた依存注入漏れを解消）。
                val ncodeSearchState by viewModel.ncodeSearchState.collectAsStateWithLifecycle()

                ChapterScreen(
                    // AnimatedContent の各サブコンポジションは自身の state（file）で描画する＝遷移中の退場側が
                    // 外側の新しい resolvedFile を読んで中身が入れ替わるのを防ぐため、以降は resolvedFile でなく file を使う。
                    currentFile = file,
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
                    // 初期位置は resolveInitialScroll の1本で解決（セッション内記憶→入場復元→先頭）。
                    // 旧「最後に読んだ章のみ復元」は②として吸収済み＝章を跨いで戻っても読んだ場所へ着地する。
                    initialScrollIndex = resolveInitialScroll(file).first,
                    initialScrollOffset = resolveInitialScroll(file).second,
                    onSaveScroll = { index, offset ->
                        // セッション内記憶へも記録（スワイプ覗き・章送りの着地が「読んだ場所」を再現する材料）。
                        sessionScrollByFile[file] = index to offset
                        // file は画面内部の String。型付き API 境界でのみ ChapterFilename に包む。
                        viewModel.saveScrollPosition(bookId, ChapterFilename(file), index, offset)
                    },
                    onNavigateToBookshelf = onNavigateToBookshelf,
                    // 前後章・目次ボタン・章の Up からの遷移はスタックへ反映（話送りは置換・目次開きは巻戻し/積み）
                    onNavigateTo = navigateForward,
                    // 参照ジャンプ（C1）: 抑止フラグ・「続きに戻る」復帰・滞留昇格を ChapterScreen へ渡す。
                    referenceMode = referenceMode,
                    onReturnToContinuation = onReturnToContinuation,
                    onPromoteToReading = onPromoteToReading,
                    // 読了検出（ssot Major）: 最終章の末尾到達を ChapterScreen が検知して呼ぶ。
                    onReachedEnd = onReachedEnd,
                    // メニュー章跨ぎ維持: 章を跨いだ入場時の初期表示と、トグル結果の還流。
                    chromeVisibleInitial = chromeVisibleAcrossChapters,
                    onChromeVisibleChange = { chromeVisibleAcrossChapters = it },
                    chapterCache = chapterCache,
                    // 覗きパネルの初期位置も着地と同じ規則で解決させる（覗き＝遷移後表示の完全一致）。
                    resolveInitialScroll = resolveInitialScroll,
                )
            }
        }
    }
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
    // メニューの章跨ぎ維持（2026-07-16 実機フィードバック）: 章→章は AnimatedContent の別サブコンポジション
    // ＝topAppBarState が作り直されるため、表示状態は親 ReadingScreen が章を跨いで保持し、入場時の初期値
    // として受け取る。トグル結果は onChromeVisibleChange で親へ還流する。既定は従来挙動（入場時没入）。
    chromeVisibleInitial: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    // 章パースのキャッシュ（親 ReadingScreen 所有・章を跨いで共有）。遷移後の初期表示と覗き先読みが使う。
    chapterCache: MutableMap<String, ChapterContentModel> = mutableMapOf(),
    // 章の初期スクロール位置の解決（親 ReadingScreen の1本＝セッション内記憶→入場復元→先頭）。
    // 覗きパネルへこの結果を焼き込み、着地（initialScrollIndex/Offset）と必ず一致させる。
    resolveInitialScroll: (String) -> Pair<Int, Int> = { 0 to 0 },
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
        // キャッシュ命中（覗き先読み済み/一度開いた章）は初期値から Success＝遷移フレームに Loading
        //（無地の紙面の「暗転」）を挟まない。スワイプで覗いた内容がそのまま連続して本表示になる。
        initialValue = chapterCache[currentFile]?.let { ParseResult.Success(it) } ?: ParseResult.Loading,
        key1 = currentFile,
        key2 = retryKey,
    ) {
        // 章 HTML は取込後不変のためキャッシュ再利用は安全。再試行（retryKey>0）は明示操作なので必ず読み直す。
        val cached = chapterCache[currentFile]
        if (cached != null && retryKey == 0) {
            value = ParseResult.Success(cached)
            return@produceState
        }
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
        // 成功をキャッシュへ（withContext の外＝main で書く。LinkedHashMap は非スレッドセーフのため）。
        (value as? ParseResult.Success)?.let { chapterCache[currentFile] = it.content }
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

    // スワイプ覗き用に隣章を先読みパースする（Content の引っ張りプレビューが使う）。
    // なぜ現在章の Success 後か: 現在章のパースと IO を取り合わない＋ドラッグ開始時のその場パースでは
    // 覗いた瞬間に間に合わず空白が見えるため。失敗（章ファイル欠損等）は null＝覗き無しへの縮退で、
    // エラー表示は本遷移側（parseResult の Error 経路）が正本のためここでは出さない（握り潰しではなく縮退）。
    val prevPreview by produceState<ChapterContentModel?>(null, prevFile, htmlDirPath, parseResult is ParseResult.Success) {
        value = null
        if (parseResult !is ParseResult.Success || prevFile == "index.html") return@produceState
        chapterCache[prevFile]?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching { ChapterHtmlParser.parse(File(htmlDirPath, prevFile)) }.getOrNull()
        }
        // 先読み結果もキャッシュへ＝スワイプ確定・前章ボタンどちらの遷移も Loading 無しで着地する。
        value?.let { chapterCache[prevFile] = it }
    }
    val nextPreview by produceState<ChapterContentModel?>(null, nextFile, htmlDirPath, parseResult is ParseResult.Success) {
        value = null
        if (parseResult !is ParseResult.Success || nextFile == "index.html") return@produceState
        chapterCache[nextFile]?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching { ChapterHtmlParser.parse(File(htmlDirPath, nextFile)) }.getOrNull()
        }
        value?.let { chapterCache[nextFile] = it }
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

    // Back キー（実経路を逆再生＝backStack を1枚 pop、空なら本棚へ）は親の ReadingScreen が
    // backStack＋BackHandler で一元管理する。経路スタックを所有するのが ReadingScreen のため、
    // rememberSaveable 永続化もそちらに集約した（ここでは扱わない）。

    // バーの表示/非表示は中央タップのトグルだけで駆動する（2026-07-16 実機フィードバックで
    // スクロール量・速度連動の出没を廃止＝「出たり引っ込んだり」する複雑な挙動をやめる）。
    // scrollBehavior を残すのは TopAppBar に渡して heightOffsetLimit（バー実高の負値）を
    // 実測させるためだけ——nestedScroll 接続はどこにも張らないためスクロールでは一切動かない。
    // snapAnimationSpec = null: 内蔵スナップも無効化（動きは settleTopBar の spring が一元所有）。
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        topAppBarState,
        snapAnimationSpec = null,
    )

    // 入場時既定=「無」（d-chrome Design/09-A）。章題は本文先頭の ChapterHeader が担うため、
    // 入場時に上部バーを見せる必要はない。heightOffsetLimit は TopAppBar が実測後に負値へ更新するため、
    // 確定を待って一度だけ全退避する（初期 0 のまま畳んでも効かないため待つ）。
    // ただしメニュー表示中に前章/次章で章を跨いだ場合（chromeVisibleInitial=true）は退避しない＝
    // 「一度出したら再度タップするまで残る」の章跨ぎ維持（2026-07-16 実機フィードバック）。
    // なぜ rememberSaveable の guard か: ユーザーが一度バーを出した後（プロセス再生成の復元含む）に
    // 再び勝手に畳んで操作を奪わないため。topAppBarState 自体も heightOffset を復元するので二重に安全。
    var didInitialCollapse by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(topAppBarState) {
        if (didInitialCollapse) return@LaunchedEffect
        snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
        if (!chromeVisibleInitial) {
            topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        }
        didInitialCollapse = true
    }

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
                // 章跨ぎ維持: トグル結果を親（ReadingScreen）の保持状態へ還流する。初期退避前の一瞬
                //（heightOffset=0 のまま実測待ち）に true が流れても、直後の退避で false が上書きするため無害。
                onChromeVisibleChange(chromeVisible)
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
        // 覗きの初期位置は着地と同じ resolveInitialScroll で焼き込む（覗き＝遷移後表示の完全一致）。
        prevPeek = prevPreview?.let { c ->
            val (index, offset) = resolveInitialScroll(prevFile)
            ChapterPeek(c, index, offset)
        },
        nextPeek = nextPreview?.let { c ->
            val (index, offset) = resolveInitialScroll(nextFile)
            ChapterPeek(c, index, offset)
        },
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
// internal（旧 private）: 没入モードの customActions（a11y 到達回復）を Robolectric semantics テストで
// 直接検証するため、描画層 Content を同一モジュール内テストへ開く（private だと file スコープで到達不可）。
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChapterScreenContent(
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
    // スワイプ覗きプレビュー（隣章のパース済み本文＋着地と同一規則の初期位置・route が先読み構築）。
    // null=先読み中/端章/章欠損＝覗きは無地の紙面に縮退する（ドラッグと章送り自体は可）。
    prevPeek: ChapterPeek? = null,
    nextPeek: ChapterPeek? = null,
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

    // 左右スワイプで章送り（handover D 回収→2026-07-16 ユーザー指示で「引っ張りプレビュー」へ増強）:
    // ドラッグ量に本文が追従し、隣章の実物の冒頭が端から覗く。覗きの内容＝遷移後に実際に表示される章頭と
    // 同一なので、確定スライドがそのまま新章の初期表示へ連続して見える。旧 experiment/lab-old(23b5f33) は
    // フリック検出のみで覗き無し＝流用不可のため draggable で新規実装。縦スクロールとの軸判別は
    // draggable(Horizontal) の touch slop に委ねる（旧知見 de60869「軸ロック」相当）。確定は
    // 「距離 OR 速度（向き一致時のみ）」の複合（旧知見 4a0719b 踏襲）・未達は戻す。左へ引く=次章／右=前章
    //（slide push「進む=右→左」と同じ身体感覚・ADR 0019）。端章はその向きへ引けない（clamp 0）: ボタンの
    // index.html 縮退と違い、スワイプで目次へ跳ぶのは予期しない移動になるため。閾値は暫定較正値（実機後詰め層）。
    val density = LocalDensity.current
    // ドラッグ追従オフセット（px）。なぜ Animatable+launch{snapTo} でなく素の Float state か:
    // draggable の onDelta は同一フレームに複数回届き、launch 経由の snapTo は全 delta が同じ古い
    // value から次値を計算して最後の1個しか効かない累積レースになる（実機で追従ゼロを実測・2026-07-16）。
    // 同期加算し、指を離した後の確定/戻しだけ animate で滑らかに動かす（settleTopBar と同型）。
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var bodyWidthPx by remember { mutableIntStateOf(0) }
    // 確定/戻しアニメの Job。アニメ中に新しいドラッグが始まったら中断して指への追従を返す。
    var swipeSettleJob by remember { mutableStateOf<Job?>(null) }
    val canGoNext = navEnabled && nextFile != "index.html"
    val canGoPrev = navEnabled && prevFile != "index.html"
    val settleSwipe: (Float) -> Unit = { velocityPx ->
        swipeSettleJob = scope.launch {
            val offset = dragOffsetPx
            val distanceThreshold = with(density) { 96.dp.toPx() }
            val velocityThreshold = with(density) { 700.dp.toPx() } // px/s（dp/s 換算の速度閾値）
            // 方向は実際に引いた距離の符号で決める。速度は「同方向のとき」だけ確定条件に加える＝
            // 引いて戻す最中に指を離すと逆向き速度で誤確定するのを防ぐ。
            val fire = offset != 0f && (
                abs(offset) >= distanceThreshold ||
                    (abs(velocityPx) >= velocityThreshold && (velocityPx < 0f) == (offset < 0f))
                )
            val target = if (offset < 0f) nextFile else prevFile
            if (fire && navEnabled && target != "index.html") {
                // 覗かせた隣章をそのまま全面へスライドさせ切ってから遷移（尺は画面遷移と共有＝ADR 0019）。
                val end = if (offset < 0f) -bodyWidthPx.toFloat() else bodyWidthPx.toFloat()
                animate(offset, end, animationSpec = tween(MotionDurationNavTransition)) { v, _ ->
                    dragOffsetPx = v
                }
                onNavigateTo(target)
                // 新章は原点から（章切替でこの Content ごと破棄されるが、破棄されない経路に備え防御的に戻す）。
                dragOffsetPx = 0f
            } else {
                // 未達＝短尺で戻す（08-C: 退場は enter より短い。spring は禁止則③の跳ね回避で使わない）。
                animate(offset, 0f, animationSpec = tween(MotionDurationDismiss)) { v, _ ->
                    dragOffsetPx = v
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ────── 没入モードの a11y 到達回復（device-verify 2026-07-16 #4）──────
            // 真因: 没入モード（クローム非表示）では上下バーを graphicsLayer{translationY} で画面外へ
            // 退避させており、Compose は画面外ノードを a11y ツリーから除外する。結果、戻る/目次/前章/次章/
            // 表示設定の clickable ノードが 0 になり、TalkBack のスワイプ走査で到達不能になる（本文段落
            // だけが残り、上スクロールでのクローム復帰しか手段が無い）。
            // 対処: 視覚・タップ挙動は一切変えず、没入中だけ読書画面ルートへ「実ボタンと同一コールバック」の
            // customActions を貼り、TalkBack のローカルコンテキストメニューから各操作への到達を回復する（標準パターン）。
            // なぜ collapsedFraction を semantics ラムダ内で読むか: このラムダは semantics フェーズで評価される
            // ＝deferred read になり、バー追従アニメでフレーム毎に collapsedFraction が動いても composition を
            // 再実行させない（フレームレート state を composition で読まない方針）。
            // なぜクローム表示中（collapsedFraction<0.5）は付けないか: そのときは実ボタンが a11y ツリーに居るため、
            // customActions を重ねると同一操作が「実ボタン＋アクション一覧」で二重に読み上げられるのを避ける。
            .semantics {
                if (topAppBarState.collapsedFraction > 0.5f) {
                    customActions = buildList {
                        // 「戻る」=上端 ← ボタン（目次へ）／「目次を開く」=下端目次ボタン。どちらも実ボタンと同じ
                        // onNavigateTo("index.html")＝別経路を作らず挙動乖離を防ぐ（実 UI も両ボタンが目次へ向かう）。
                        add(CustomAccessibilityAction("戻る") { onNavigateTo("index.html"); true })
                        add(CustomAccessibilityAction("目次を開く") { onNavigateTo("index.html"); true })
                        // 前後章は実ボタンの活性条件（navEnabled＝目次ロード済）に一致させる。端章では隣接章が無く
                        // prev/next が index.html へ縮退するため、「前の章/次の章」ラベルが目次を開く誤誘導になる。
                        // その遷移は「目次を開く」が既に担う＝到達性を落とさずに、ラベルと挙動の齟齬だけを避ける。
                        if (navEnabled && prevFile != "index.html") {
                            add(CustomAccessibilityAction("前の章") { onNavigateTo(prevFile); true })
                        }
                        if (navEnabled && nextFile != "index.html") {
                            add(CustomAccessibilityAction("次の章") { onNavigateTo(nextFile); true })
                        }
                        // 「表示設定」=下端歯車ボタンと同じく設定シートを開く（Content ローカルの開閉 state を立てる）。
                        add(CustomAccessibilityAction("表示設定") { showSettings = true; true })
                        // 「最上部へ」＝ピルと同一コールバック・同一の出現条件（章の3割以上）。没入中も
                        // TalkBack から先頭復帰へ到達できるよう実 UI と対で揃える（この semantics ラムダは
                        // deferred read＝スクロールで composition を再実行させない）。
                        val total = lazyListState.layoutInfo.totalItemsCount
                        if (total > 0 && lazyListState.firstVisibleItemIndex * 10 >= total * 3) {
                            add(
                                CustomAccessibilityAction("最上部へ") {
                                    scope.launch { lazyListState.animateScrollToItem(0) }
                                    true
                                },
                            )
                        }
                    }
                }
            },
    ) {
        Scaffold(
            containerColor = colors.background,
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
                    // 本文タップで上下バーをトグル表示する（表示/非表示の唯一の駆動元）。
                    // なぜ barsVisible の真偽値を持たないか: settle アニメ中の再タップや
                    // プロセス再生成の復元で真偽値と実オフセットが乖離すると「隠れているものを
                    // 隠す」空打ちが起き2回タップが必要になる。実オフセット(collapsedFraction)から
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
                    }
                    // 本文の実測幅（px）＝覗きパネルの初期位置と確定スライドの終端に使う。
                    .onSizeChanged { bodyWidthPx = it.width }
                    // 左右スワイプで章送り（追従・確定/戻し＝settleSwipe）。端章側へは clamp で引けない。
                    .draggable(
                        state = rememberDraggableState { delta ->
                            val min = if (canGoNext) -bodyWidthPx.toFloat() else 0f
                            val max = if (canGoPrev) bodyWidthPx.toFloat() else 0f
                            // 同期加算（launch 経由の加算は同一フレーム内の複数 delta が潰し合う＝上のなぜ参照）
                            dragOffsetPx = (dragOffsetPx + delta).coerceIn(min, max)
                        },
                        orientation = Orientation.Horizontal,
                        onDragStarted = { swipeSettleJob?.cancel() },
                        onDragStopped = { velocity -> settleSwipe(velocity) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 本体はドラッグに追従して横へずれる（translationX は draw 段の deferred read＝
                // ドラッグの毎フレームで composition を再実行しない。露出した地は Scaffold の紙色）。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = dragOffsetPx },
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
                        // 本文リストのみ stretch オーバースクロール（Compose 既定 ON）を無効化する。
                        // なぜ本文だけか: 各社は読書面で端のゴム伸びバウンドを消し、一覧（目次・本棚）は
                        // 既定のまま残すのが業界一致（docs/reference/06 §2/§3）。ChapterContent の唯一の
                        // スクロール要素がこの本文 LazyColumn なので、呼び出しを null 供給で包めば本文へ限定される。
                        // foundation 1.7 系の API。1.8+ へ上げる際は LocalOverscrollFactory provides null へ読み替え。
                        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
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

                // ────── スワイプ覗きパネル ──────
                // 引いている間だけ隣章の冒頭を実寸で端に出す（内容＝遷移後の章頭と同一）。プレビュー未取得
                //（先読み中/章欠損）の間は無地の紙面が覗く縮退。出し分けの boolean は derivedStateOf＝
                // ドラッグ開始/終了時だけ recompose し、連続オフセットは各パネルの draw 段で読む。
                val peekNext by remember { derivedStateOf { dragOffsetPx < 0f } }
                val peekPrev by remember { derivedStateOf { dragOffsetPx > 0f } }
                if (peekNext && nextPeek != null) {
                    ChapterPeekPanel(
                        translationX = { bodyWidthPx + dragOffsetPx },
                        peek = nextPeek,
                        colors = colors,
                        fontSize = fontSize,
                        lineHeightEm = lineHeightEm,
                        bodyMarginDp = bodyMarginDp,
                    )
                }
                if (peekPrev && prevPeek != null) {
                    ChapterPeekPanel(
                        translationX = { -bodyWidthPx + dragOffsetPx },
                        peek = prevPeek,
                        colors = colors,
                        fontSize = fontSize,
                        lineHeightEm = lineHeightEm,
                        bodyMarginDp = bodyMarginDp,
                    )
                }
            }
        }

        // ────── ボトムバー（オーバーレイ）──────
        // collapsedFraction（トップバーの退避割合）に連動して下方向へスライド退避させる。
        // これにより中央タップトグルでトップバーと同フレームで同期して動く。
        BottomAppBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .graphicsLayer {
                    // 退避割合 × 実測高さ分だけ下へずらす（collapsedFraction=1 で完全に画面外）
                    translationY = bottomBarHeightPx * topAppBarState.collapsedFraction
                },
            // なぜ IgnoringVisibility か: トグルと同フレームで systemBars を hide/show するため、
            // 可視追従の既定 insets だとバー内パディングが 0⇄実測値で振れ、バー高の再測定で
            // 開閉のたびに下端がガタつく（本文側 ChapterContent と同じ対策をバー自身にも適用）。
            windowInsets = WindowInsets.systemBarsIgnoringVisibility
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            // なぜ alpha 0.95f か: スクロール中も文字が透けて読めるよう
            // 背景色を半透明にするため（html_exporter.py の .nav-footer に対応）
            containerColor = colors.navBackground.copy(alpha = 0.95f),
            contentColor = colors.topBarIcon,
        ) {
            // C①案A: 下端を4分割 [前章｜目次｜表示設定｜次章]。表示設定を右上隅の歯車から下端へ集約し、
            // 藍＋太字で画面唯一の強調にする（原則4「一画面一強調」）。前後章は目次未ロード中の disabled を維持。
            BottomBarButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "前章",
                colors = colors,
                // 目次未ロード中は無効化（disabled トークンで淡色化）。押下時の目次フォールバックを防ぐ
                enabled = navEnabled,
                onClick = { onNavigateTo(prevFile) },
            )
            // 目次ボタンは常に有効（ロード状況に関わらず目次を開ける＝開けばスケルトン表示）
            BottomBarButton(
                icon = Icons.AutoMirrored.Filled.List,
                label = "目次",
                colors = colors,
                onClick = { onNavigateTo("index.html") },
            )
            // 表示設定＝下端集約の主役。旧・右上歯車の起動導線をここへ移設（シート中身は不変）。
            BottomBarButton(
                icon = Icons.Filled.Tune,
                label = "表示設定",
                colors = colors,
                accent = true,
                onClick = { showSettings = true },
            )
            BottomBarButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "次章",
                colors = colors,
                enabled = navEnabled,
                onClick = { onNavigateTo(nextFile) },
            )
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
            // 表示設定は右上隅の歯車を撤去し下端バーへ集約した（C①案A・handover ★残1）。
            // 隅の歯車は「毎セッション触る唯一の入口が隅に複利蓄積」＝標準の悪例で、親指の届く下端へ移す。
            // 上端は ←（目次へ）＋章題のみに絞り、原則1「UIは黒衣」を強める（起動導線だけの変更＝シート中身は不変）。
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
            // なぜ IgnoringVisibility か: トグルと同フレームで systemBars を hide/show するため、
            // 可視追従の既定 insets だとバー内パディングが 0⇄実測値で振れ、heightOffsetLimit の
            // 再測定で開閉のたびに上端がガタつく（本文側 ChapterContent と同じ対策をバー自身にも適用）。
            windowInsets = WindowInsets.systemBarsIgnoringVisibility
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            // scrollBehavior は heightOffsetLimit の測定のため維持する（nestedScroll 接続は無し）。
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
                // IgnoringVisibility: メニュー開閉の systemBars hide/show でチップ位置が跳ねないよう、
                // 可視追従の statusBarsPadding ではなく常時実測値でパディングする。
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
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

        // 「最上部へ」ピル（2026-07-16 実機フィードバック・案C裁定＝reading-backtotop-D.html）:
        // メニュー表示中かつ「章の3割以上読み進めた」ときだけ、下端バー直上に出す。意匠は復帰ヒント・
        // 続きに戻ると同型のピル（新意匠を発明しない）。なぜ序盤は出さないか: 章頭付近では戻る意味が
        // 無くただの浮遊物になる。押して先頭へ戻ると条件が外れて自然に消える＝完了フィードバックを兼ねる。
        // 進捗は〈可視先頭アイテム÷全アイテム〉の段落数ベース近似（画素精度は不要・1画面で収まる短章では
        // 出ない）。閾値 3割＝実機較正値（初期の「半分」はユーザー所見で多すぎ→3割へ・2026-07-16。
        // 実機後詰め層＝ADR0005 §B）。
        // derivedStateOf: スクロール毎フレームの再評価を boolean 反転時だけの recompose に落とす
        //（本棚 showBand と同じ定石）。
        val chromeVisibleForPill by remember {
            derivedStateOf { topAppBarState.collapsedFraction < 0.5f }
        }
        val pastThreshold by remember(lazyListState) {
            derivedStateOf {
                val total = lazyListState.layoutInfo.totalItemsCount
                // index/total ≥ 0.3 の整数演算形（×10 ≥ ×3）
                total > 0 && lazyListState.firstVisibleItemIndex * 10 >= total * 3
            }
        }
        AnimatedVisibility(
            visible = chromeVisibleForPill && pastThreshold,
            enter = fadeIn(tween(MotionDurationCrossfade)),
            exit = fadeOut(tween(MotionDurationCrossfade)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 下端バーの実測高さ＋S12 で「バー直上」に浮かべる（バー高はナビバー実高で変わるため実測値）。
                .padding(bottom = with(LocalDensity.current) { bottomBarHeightPx.toDp() } + Spacing.S12),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.navBackground.copy(alpha = 0.92f))
                    // animateScrollToItem: 瞬間ジャンプは味気ないというユーザー所見（2026-07-16）で滑走化。
                    // 遠距離は Lazy が目標近くまで内部で座標を寄せてから滑らかに着地する＝長章でも安全。
                    .clickable(onClick = { scope.launch { lazyListState.animateScrollToItem(0) } })
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S8),
            ) {
                Icon(
                    imageVector = Icons.Filled.VerticalAlignTop,
                    contentDescription = null, // 隣のテキストが意味を担う（重複読み上げ回避）
                    tint = colors.topBarIcon,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "最上部へ",
                    color = colors.topBarIcon,
                    fontFamily = MinchoFamily,
                    fontSize = FontSubTitle,
                )
            }
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
 * スワイプ覗きの表示素材＝隣章のパース済み本文と、着地と同一規則（resolveInitialScroll）で解決した
 * 初期スクロール位置。位置を焼き込むのは「覗いた表示＝遷移後の表示」を構造的に保証するため。
 */
internal data class ChapterPeek(
    val content: ChapterContentModel,
    val initialScrollIndex: Int,
    val initialScrollOffset: Int,
)

/**
 * スワイプ引っ張りで端から覗く隣章パネル。
 * なぜ実物の [ChapterContent] を使うか: 覗きの内容を遷移後の初期表示と完全一致させ、
 * 確定スライド→章切替が連続して見えるようにするため（専用の軽量プレビューだと書体・版面の再現が
 * 二重管理になる）。LazyListState は peek の初期位置＝その章を読んでいた場所から表示する
 *（章ごとの位置記憶＝親 ReadingScreen の sessionScrollByFile。2026-07-16 実機フィードバック）。
 * @param translationX 覗き位置（px）。draw 段で読む deferred read（ドラッグ毎フレームの recompose 回避）。
 */
@Composable
private fun ChapterPeekPanel(
    translationX: () -> Float,
    peek: ChapterPeek,
    colors: ReadingColors,
    fontSize: Int,
    lineHeightEm: Float,
    bodyMarginDp: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.translationX = translationX() }
            // 不透明の紙面で現章を覆う（ChapterContent は背景を塗らず Scaffold 任せのため、ここで明示する）。
            .background(colors.background),
    ) {
        ChapterContent(
            content = peek.content,
            colors = colors,
            fontSize = fontSize,
            lineHeightEm = lineHeightEm,
            bodyMarginDp = bodyMarginDp,
            lazyListState = remember(peek) {
                LazyListState(peek.initialScrollIndex, peek.initialScrollOffset)
            },
        )
    }
}

/**
 * バーを全表示または全非表示へスナップさせる（中央タップのトグル専用）。
 * なぜ自前実装か: enterAlways の内蔵 snap はスクロール消費戦略と一体化しているが、
 * 本実装はスクロール接続そのものを持たない（タップ駆動のみ）ため、animate で直接動かす。
 *
 * @param target 退避先の heightOffset（0f=全表示／heightOffsetLimit=全退避）。
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleTopBar(
    state: TopAppBarState,
    target: Float,
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

/**
 * 下端バーの1ボタン（アイコン＋ラベル縦積み）。C①案A の4分割バー（reading-gear-alt-D 案A②の翻訳）。
 * ラベルはゴシック（道具の声＝ADR 0014「明朝は題字と本文」＝既定 sans をそのまま使う）・[FontNavLabel]。
 * @param accent 藍で強調するか（表示設定＝画面唯一の強調・原則4「一画面一強調」）。
 * @param enabled false で disabled トークンへ淡色化しタップ無効化（前後章の目次未ロード中）。
 */
@Composable
private fun RowScope.BottomBarButton(
    icon: ImageVector,
    label: String,
    colors: ReadingColors,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 淡色化の優先順位: 無効（機能不能）＞ accent（強調）＞ 通常。無効時は強調より不能表示を優先する。
    // 無効色は placeholder（「無効ボタン文字」用の専用シェード＝alpha 沈めでなく役割別トークン・Design/10§9）。
    val tint = when {
        !enabled -> colors.placeholder
        accent -> colors.accent
        else -> colors.topBarIcon
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            // ラベル Text が可視の読み上げ名を担うため、アイコンは装飾扱い（null）にして二重読み上げを防ぐ。
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        // モックのアイコン↔ラベル間 3px を離散スケールへ最近傍丸め（round-half-up＝4dp）。
        Spacer(Modifier.height(Spacing.S4))
        Text(
            text = label,
            color = tint,
            fontSize = FontNavLabel,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
