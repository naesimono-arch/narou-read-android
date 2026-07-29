package com.novelreader.ui

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.novelreader.PrefKeys
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.model.ChapterContent as ChapterContentModel
import com.novelreader.model.ParseResult
import com.novelreader.narou.model.Ncode
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.typeset.ParagraphPosition
import com.novelreader.typeset.ReadingPositionMapper
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.FontSectionTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.MotionDurationCrossfade
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationNavTransition
import com.novelreader.ui.theme.MotionDurationSeizuFadeIn
import com.novelreader.ui.theme.MotionDurationSeizuFadeInDelay
import com.novelreader.ui.theme.MotionDurationSeizuFadeOut
import com.novelreader.ui.theme.ReadingColors
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.skins.j.NextDoorEdgeGlowJ
import com.novelreader.ui.skins.m.ReadingProgressStarM
import com.novelreader.ui.skins.m.drawSeizuReadingScrim
import com.novelreader.ui.skins.m.skyBackdropReadingState
import com.novelreader.ui.skins.p.ReadingSaveBarP
import com.novelreader.ui.skins.p.SaveChipP
import com.novelreader.ui.skins.m.LocalSkyParallax
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.rememberReadingColors
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.Spacing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
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
    // 遷移ジャンク対策（案A・2026-07-29 裁定）: NavHost の本棚→読書 push の enter アニメ窓だけ true
    //（MainActivity が遷移の離散状態 currentState/targetState から導出＝P2 本棚と同じ信号）。窓の間、
    // 初期表示面（目次 or 章）の重い実内容を構造骨（TransitionSkeletons.kt）へ差し替える。
    // 既定 false＝既存テスト・呼び出しは無変更。
    deferHeavyContent: Boolean = false,
) {
    // 読書ナビの Back スタック（実データ構造＝ReadingBackStack）。末尾が現在地。
    // なぜ「現在地1個」でなく経路を保持するスタックか（前進操作の巻き戻し・参照退避元の探索に使うため）:
    // 目次ボタンで既存目次へ戻る／「続きに戻る」で退避元章へ復帰、が経路上の位置を必要とする。
    // ただし Back 自体は経路逆再生ではなく「必ず一つ上の階層へ」（2026-07-19 裁定＝章は目次・目次は本棚）。
    // 直行入場でも Back は目次を経て本棚へ抜け、左上 ← ボタンと一致する（push/巻き戻し規則は ReadingBackStack 参照）。
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
        // なぜ replace か: 話送りで段を増やさず、Back（＝一階層 up）が常に目次へまっすぐ上がるようにするため。
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

    // Back キー＝「必ず一つ上の階層へ」（2026-07-19 裁定）。back() は章なら目次を開き・目次なら null（本棚）を返す。
    // null のとき onNavigateToBookshelf で本棚へ抜ける。横スワイプ Back と左上 ← ボタンを同一モデル（一階層 up）へ一本化した。
    // これで「本棚→本文直行でも Back は目次を経て本棚（2段）／目次経由なら目次→本棚」が入場形に依らず ← と一致する
    // ＝07/15 の「実経路を逆再生し直行なら Back 1発で本棚」の意図的撤回（可視の 1→2 タップ化は裁定が織り込んだ回帰）。
    // App bar の ←（Up）は無改修＝別経路（各画面が直接呼ぶ）: 章の ← は onNavigateTo("index.html")＝目次を
    // 開く（backStack にも反映）／目次の ← は onNavigateToBookshelf で本棚へ直行（スタックを介さない親ジャンプ）。
    // Back を ← 側へ寄せたため両者の可視挙動は今や一致する（back() は openToc へ委譲＝定義上つねに同一遷移）。
    // 【旧 navHistory 全逆再生バグの再発防止】前進操作が経路を無制限に伸ばさない（既出巻き戻し・話送り置き換え）ため、
    // Back が上がる目次の位置も一意に定まる。覗きで段が増えないのは ReadingBackStack 側規則が担保する（不変条件②）。
    // 【生命線】Back で saveProgress を呼ばず backStack 更新だけに留める理由: 新仕様の Back は章を再表示せず必ず
    // 目次へ上がる（目次は進捗保存のブロック対象）ため、章先頭への scrollIndex=0 破壊的上書きは Back 経路では起きない。
    // 章の現在位置保存は ChapterScreen 再表示時の debounce/onStop フラッシュに一元化する（前進で新章を開いたときのみ
    // 「先頭から」を保存する非対称設計を Back でも崩さない）。lastChapterFile は現在章ハイライト用に維持（Back では更新しない）。
    // 参照モード（jumpOrigin）の解除は「続きに戻る」チップ・滞留昇格・目次からの続き章再選択が担う。
    // Back で覗き章から目次へ上がっても jumpOrigin は残すが、目次上では referenceMode は無害
    // （抑止・チップは章表示中のみ効く）＝参照の挙動を壊さない（invariant④: jumpOrigin 挙動を壊さない）。
    // PredictiveBackHandler にしない理由: Back は内部スタック（章⇄目次）の階層 up＝離散的な状態切替で、
    // 進捗連動で見せられるプレビュー面が無い（NavHost pop の predictive 対応も navigation-compose 2.7.5 には無い）。
    // ジェスチャ確定時のみ発火する現行セマンティクスを保つ（進捗途中で back() が走ると覗き状態が壊れる）。
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
    val prefs = remember { context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE) }
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
        mutableIntStateOf(prefs.getInt(PrefKeys.READING_FONT_SIZE, 18).coerceIn(14, 24))
    }
    // ドラッグ中の毎値：本文プレビュー追従のため状態のみ更新（永続化しない）
    val onFontSizeChange: (Int) -> Unit = { size -> fontSize = size }
    // 確定時のみ：現在の fontSize を永続化する。apply は非同期ディスク書込のため UI をブロックしない
    val onFontSizePersist: () -> Unit = {
        prefs.edit().putInt(PrefKeys.READING_FONT_SIZE, fontSize).apply()
    }

    // 本文の行間（em）。
    // なぜ 2.3〜2.8em の狭めレンジに絞るか: ルビは字面上端アンカーで描画されるため
    // 行間を広げても親文字から離れなくなったが（バグ#1修正）、狭めるとルビの描画領域
    // （字面より上の leading）が前行と被るリスクは残る。段落間スペースも lineHeight=2.5em
    // 前提で微調整済みのため、可変幅を狭く保つことでルビ被りと段落リズムの破綻を抑える。
    var lineHeightEm by remember {
        mutableFloatStateOf(prefs.getFloat(PrefKeys.READING_LINE_HEIGHT, 2.5f).coerceIn(2.3f, 2.8f))
    }
    // フォントサイズと同型：ドラッグ中は状態のみ・永続化は確定時に一度だけ
    val onLineHeightChange: (Float) -> Unit = { v -> lineHeightEm = v }
    val onLineHeightPersist: () -> Unit = {
        prefs.edit().putFloat(PrefKeys.READING_LINE_HEIGHT, lineHeightEm).apply()
    }

    // 本文の左右余白（dp）。既定 15 は設定化前の固定値と同じ＝既存ユーザーの見た目を変えない。
    // スマホ幅では widthIn(max=600dp) が効かず実質この余白だけが行長を決めるため、
    // 行長を詰めたい要望（旧 backlog C-05/06）はこの1値の設定化で吸収する。
    var bodyMarginDp by remember {
        mutableIntStateOf(prefs.getInt(PrefKeys.READING_BODY_MARGIN, 15).coerceIn(10, 40))
    }
    // フォントサイズと同型：ドラッグ中は状態のみ・永続化は確定時に一度だけ
    val onBodyMarginChange: (Int) -> Unit = { v -> bodyMarginDp = v }
    val onBodyMarginPersist: () -> Unit = {
        prefs.edit().putInt(PrefKeys.READING_BODY_MARGIN, bodyMarginDp).apply()
    }

    // 縦書きモード（全書籍共通・app_prefs の単一 Boolean "reading_vertical"＝プラン裁定「設定は全書籍共通」）。
    // 既定 false＝横書きで既存ユーザーの見た目は不変。他の読書設定と同じ app_prefs に置く。
    // なぜ確定コールバック（onXxxPersist）を分けないか: これはトグルでスライダーのようなドラッグ中の
    // 毎値発火が無く、1タップ＝1確定。状態更新と永続化を1つのコールバックで即時に行う（無駄な間引き不要）。
    var verticalMode by remember {
        mutableStateOf(prefs.getBoolean(PrefKeys.READING_VERTICAL, false))
    }
    val onVerticalModeChange: (Boolean) -> Unit = { enabled ->
        verticalMode = enabled
        prefs.edit().putBoolean(PrefKeys.READING_VERTICAL, enabled).apply()
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

    // スキン差分（M星図・2026-07-19 ユーザー裁定「空の一枚化」）: M は固定天球（常駐 backdrop）を全画面で共有するため
    // 目次⇄本文も slide だと「空ごと」動く＝コンテンツのみをフェードで差し替える（ADR 0019 追記「M星図の例外」）。
    // reduce-motion では即時切替（読書Mのモーションゼロ規律と整合）。他スキンは従来の slide push 不変。
    val isSeizu = LocalSkin.current == Skin.SEIZU_M
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // 【透過の天の川・2026-07-19 裁定】読書Mも常駐 backdrop（R1s 実物の天の川）を透かして見せる（reading-M-rich-R4 中）＝
    // 読書中も hidden=false のまま。ただし本文（index.html 以外）では読書Mモーションゼロ（ADR 0022 §3）との整合で
    // z2 流星のみ抑止する（meteorSuppressed）。目次（index.html）は透過の構造画面＝空も流星も見せる。読書面自体は透明にし
    // （下の Scaffold containerColor=Transparent）、地色スクリム（drawSeizuReadingScrim）で空を減光する。
    // 状態機は純関数 skyBackdropReadingState が正本（JVM テストで固定）。離脱時は DisposableEffect で必ず復帰。
    val skyBackdrop = LocalSkyParallax.current
    LaunchedEffect(isSeizu, resolvedFile, skyBackdrop) {
        val bd = skyBackdrop ?: return@LaunchedEffect
        val st = skyBackdropReadingState(isSeizu, isIndex = resolvedFile == "index.html")
        bd.hidden = st.hidden
        bd.meteorSuppressed = st.meteorSuppressed
    }
    DisposableEffect(skyBackdrop) {
        onDispose { skyBackdrop?.hidden = false; skyBackdrop?.meteorSuppressed = false }
    }

    // 目次⇄本文の切替を NavHost のスライドと同じ向きルールで演出する（進む=目次→章は右から左へ潜る／
    // 戻る=章→目次は左から右へ戻す）。同一 nav ルート内の state 切替（resolvedFile の出し分け）を
    // AnimatedContent で包む。章→章（話送り）は現状どおり瞬間＝向きを付けない（P1 は別枠のため据え置き）。
    // 尺は MotionDurationNavTransition（250ms）で NavHost の遷移と共有する。
    // standalone AnimatedContent から Transition レシーバ版へ持ち替え（2026-07-29・案A）: 遷移の離散状態
    //（currentState/targetState＝端点でのみ変化）を目次→章 push の骨差し替え信号に使うため。standalone 版は
    // 内部で同じ updateTransition を作って委譲するだけ＝描画・尺・イージングは従来と同一（label も同名を維持）。
    val tocBodyTransition = updateTransition(targetState = resolvedFile, label = "toc-body-slide")
    // 目次→章 push の遷移窓だけ true（P2 と同じ離散2値＝毎フレーム recompose を生まない）。向きの契約
    //（pop・話送りの除外）は純関数 isTocToChapterPush が担い JVM テストで固定する。
    // 差し替えタイミングは P2 先例と同じ settle+0ms（currentState が targetState に追いつく瞬間）を採用:
    // 既存の離散信号だけで組めてフレームカウント機構が不要な上、P2 framestats 実測で差し戻しフレームに
    // 17ms 超のヒッチが無かったため。モック提案の settle+2f（+33ms）は後日の実測比較枠として残す。
    val deferChapterForTocPush by remember {
        derivedStateOf {
            isTocToChapterPush(tocBodyTransition.currentState, tocBodyTransition.targetState)
        }
    }
    tocBodyTransition.AnimatedContent(
        transitionSpec = {
            val d = MotionDurationNavTransition
            val toToc = targetState == "index.html"
            val fromToc = initialState == "index.html"
            when {
                // M は方向概念を持たないフェードスルー（退出先行→進入。尺は Nav 遷移バジェット内で二分）。
                isSeizu && reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                isSeizu -> fadeIn(tween(MotionDurationSeizuFadeIn, delayMillis = MotionDurationSeizuFadeInDelay)) togetherWith
                    fadeOut(tween(MotionDurationSeizuFadeOut))
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
    ) { file ->
        if (file == "index.html") {
            NativeTableOfContentsScreen(
                tocState = tocState,
                colors = readingColors,
                // 本棚→目次 push の窓のみ骨（NavHost 由来の信号を素通し）。章→目次（pop）は退場側が既測
                // コンテンツで安価なため対象外＝deferChapterForTocPush は目次面には配線しない（2026-07-29 裁定）。
                deferHeavyContent = deferHeavyContent,
                // 明快K の目次ヘッダ副題に作品名を渡す（他スキンは未使用＝既定 null で無視）。
                workTitle = bookTitle,
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
                    // push 遷移窓の本文骨（案A）: 本棚→本文（NavHost enter）と目次→本文（この AnimatedContent の
                    // push 向きのみ）の OR。退場側の章（章→目次 pop）はどちらの信号も立たない＝実内容のまま抜ける。
                    deferHeavyContent = deferHeavyContent || deferChapterForTocPush,
                    htmlDirPath = htmlDirPath,
                    tocEntries = tocEntries,
                    // なろう紐付けの束（全フィールド必須＝配線忘れはコンパイルエラー。理由は ReadingFace.kt 冒頭）。
                    ncodeLink = NcodeLink(
                        bookTitle = bookTitle,
                        ncode = ncode,
                        ncodeSearchState = ncodeSearchState,
                        onSearchNcode = { query -> viewModel.searchNcodeCandidates(query) },
                        onRetryNcodeSearch = { viewModel.retryNcodeSearch() },
                        // 紐付けの永続化。books は hot StateFlow のため、書き込みは MainActivity → ncode 引数へ
                        // 自動で還流し、確定直後から継続導線が紐付け済み表示に切り替わる。
                        onLinkNcode = { newNcode -> viewModel.linkNcode(bookId, newNcode) },
                    ),
                    // テーマは MainActivity が持つ単一正本（本棚と共有）＝本棚側と同じ ThemeControl を使い回す。
                    theme = ThemeControl(
                        appTheme = readingTheme,
                        onThemeChange = onThemeChange,
                        followingSystem = followingSystem,
                        onFollowSystem = onFollowSystem,
                    ),
                    typography = ReadingTypography(
                        fontSize = fontSize,
                        onFontSizeChange = onFontSizeChange,
                        onFontSizePersist = onFontSizePersist,
                        lineHeightEm = lineHeightEm,
                        onLineHeightChange = onLineHeightChange,
                        onLineHeightPersist = onLineHeightPersist,
                        bodyMarginDp = bodyMarginDp,
                        onBodyMarginChange = onBodyMarginChange,
                        onBodyMarginPersist = onBodyMarginPersist,
                        verticalMode = verticalMode,
                        onVerticalModeChange = onVerticalModeChange,
                    ),
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

/**
 * 目次→章の push 遷移か（読書内 AnimatedContent の骨差し替え信号・案A）。
 * push 限定の理由（2026-07-29 裁定）: pop（章→目次）は退場側が既測コンテンツで安価＝差し替え不要、
 * 話送り（章→章）は遷移なしの瞬間切替＝骨を挟むと1フレームのちらつき退行になるため、向きで機械的に排除する。
 * 純関数に切り出すのは、この向きの契約（push のみ true）を JVM テストで固定するため。
 * @param currentFile 遷移の出発面（Transition.currentState＝アニメ完了までは旧 state のまま）
 * @param targetFile 遷移の到着面（Transition.targetState）
 */
internal fun isTocToChapterPush(currentFile: String, targetFile: String): Boolean =
    currentFile == "index.html" && targetFile != "index.html"

/** 読書再開位置。targetFile（最後に読んだ章）と一致する章のみスクロール位置を復元する。 */
private data class ChapterRestore(
    val targetFile: String?,
    val scrollIndex: Int,
    val scrollOffset: Int,
)

/** 縦書き⇔横書き切替時の位置復元（P5）: 新モードで対象段落の実寸が現れるまで待つ上限。
 *  scrollToItem(index,0) 直後に当該アイテムは可視化されるため通常は即取れる。取れないまま
 *  この時間を過ぎたら offset=0 のまま（段落先頭）に倒す＝無限待機を避ける防御。 */
private const val VERTICAL_RESTORE_TIMEOUT_MS = 2_000L

/**
 * 章読書画面の描画層（stateless / route/Content 分割の content）。ChapterScreen からの純移動。
 * VM・SharedPreferences・narouRepository・非同期パース/継続照会・スクロール保存/ライフサイクルフラッシュ・
 * 没入ヒントといった副作用はすべて route（ChapterScreen）に残し、ここは受け取った state＋コールバックだけで
 * Scaffold＋上下バー（オーバーレイ）＋継続導線＋各シートを描画する葉（BookshelfContent と同方針）。
 * 画面ローカルの UI 状態（設定シート/紐付けシート開閉・ボトムバー実測高・タップ用コルーチンスコープ・
 * 非横取り NestedScroll 接続）のみ内部に保持する（過剰な hoisting は避ける）。
 * Custom Tabs 起動（再入ガード付き）は副作用のため route の
 * [ContinuationCta.onReadContinuation]/[ContinuationCta.onOpenWorkPage] へ委譲する。
 *
 * 引数は役割ごとの束で受ける（2026-07-27 純構造リファクタ・定義と「なぜ」は ReadingFace.kt）。
 * 束には既定値を置かない＝新しい呼び出し元の配線忘れをコンパイルエラーへ格上げするため。
 */
// internal（旧 private）: 没入モードの customActions（a11y 到達回復）を Robolectric semantics テストで
// 直接検証するため、描画層 Content を同一モジュール内テストへ開く（private だと file スコープで到達不可）。
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChapterScreenContent(
    parseResult: ParseResult,
    colors: ReadingColors,
    typography: ReadingTypography,
    theme: ThemeControl,
    chrome: ReadingChrome,
    nav: ChapterNav,
    ncodeLink: NcodeLink,
    continuationCta: ContinuationCta,
    // スワイプ覗きプレビュー（隣章のパース済み本文＋着地と同一規則の初期位置・route が先読み構築）。
    // null=先読み中/端章/章欠損＝覗きは無地の紙面に縮退する（ドラッグと章送り自体は可）。
    prevPeek: ChapterPeek?,
    nextPeek: ChapterPeek?,
    // 参照ジャンプ（C1）の「続きに戻る」チップ表示と復帰コールバック。
    showReturnChip: Boolean,
    onReturnToContinuation: () -> Unit,
    onRetryParse: () -> Unit,
    // push 遷移窓（本棚/目次→本文の slide 250ms）の間だけ true（route＝ChapterScreen 経由で受ける）。
    // 既定 false＝既存テスト・golden は無変更（P2 BookshelfContent と同じ受け口設計）。
    deferHeavyContent: Boolean = false,
) {
    // ── 束の展開（本体の参照名を変えない局所別名＝挙動・描画とも既存と同一） ──
    val fontSize = typography.fontSize
    val onFontSizeChange = typography.onFontSizeChange
    val onFontSizePersist = typography.onFontSizePersist
    val lineHeightEm = typography.lineHeightEm
    val onLineHeightChange = typography.onLineHeightChange
    val onLineHeightPersist = typography.onLineHeightPersist
    val bodyMarginDp = typography.bodyMarginDp
    val onBodyMarginChange = typography.onBodyMarginChange
    val onBodyMarginPersist = typography.onBodyMarginPersist
    val verticalMode = typography.verticalMode
    val onVerticalModeChange = typography.onVerticalModeChange
    val readingTheme = theme.appTheme
    val onThemeChange = theme.onThemeChange
    val followingSystem = theme.followingSystem
    val onFollowSystem = theme.onFollowSystem
    val lazyListState = chrome.lazyListState
    val topAppBarState = chrome.topAppBarState
    val scrollBehavior = chrome.scrollBehavior
    val barsVisualReady = chrome.barsVisualReady
    val showChromeHint = chrome.showChromeHint
    val prevFile = nav.prevFile
    val nextFile = nav.nextFile
    val navEnabled = nav.navEnabled
    val isLastChapter = nav.isLastChapter
    val chapterNumber = nav.chapterNumber
    val totalChapters = nav.totalChapters
    val onNavigateTo = nav.onNavigateTo
    val onNavigateToBookshelf = nav.onNavigateToBookshelf
    val bookTitle = ncodeLink.bookTitle
    val ncode = ncodeLink.ncode
    val ncodeSearchState = ncodeLink.ncodeSearchState
    val onSearchNcode = ncodeLink.onSearchNcode
    val onRetryNcodeSearch = ncodeLink.onRetryNcodeSearch
    val onLinkNcode = ncodeLink.onLinkNcode
    val continuationInfo = continuationCta.continuationInfo
    val onReadContinuation = continuationCta.onReadContinuation
    val onOpenWorkPage = continuationCta.onOpenWorkPage

    val scope = rememberCoroutineScope()

    // ── モード切替時の段落位置維持（同一章内・P5）──
    // 切替「前」に旧モードの寸法で捕捉した ParagraphPosition の保留。切替後の再合成完了後に消費して復元する。
    var pendingVerticalRestore by remember { mutableStateOf<ParagraphPosition?>(null) }

    // トグルをラップ: route の永続化（onVerticalModeChange）へ委譲する前に、旧モードの先頭可視アイテム寸法で
    // 現在の読書位置を (段落index, fraction) として捕捉する。
    // なぜ切替「前」に取るか: onVerticalModeChange で verticalMode が反転すると本文が新モード（LazyColumn⇔
    // LazyRow）で再合成され、visibleItemsInfo.size が別軸の寸法へ化ける。fraction の分母＝先頭可視アイテム
    // size は必ず切替前の旧寸法で取らないと段落内位置がずれる（プラン P5 裁定「必ず切替前に取る」）。
    val onVerticalModeToggle: (Boolean) -> Unit = { enabled ->
        val firstVisible = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
        pendingVerticalRestore = ReadingPositionMapper.fromScroll(
            firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
            // firstOrNull() は firstVisibleItemIndex と同じ先頭可視アイテム＝その軸方向 size が fraction の分母。
            firstVisibleItemSizePx = firstVisible?.size ?: 0,
            headerItemCount = 1,
        )
        onVerticalModeChange(enabled)
    }

    // 切替後の復元: verticalMode 反転（＋保留セット）で再起動し、同一段落 index を可視化→新モードの実寸が
    // 取れたら fraction を掛け戻して offset を適用する。実寸が取れない/待機超過時は scrollToItem(index,0) の
    // まま＝offset=0（段落先頭）に倒す（防御）。fraction は近似ゆえ切替で数行の誤差が出るのは仕様（プラン
    // 「fraction 近似ゆえ切替で数行の誤差は仕様」）。章跨ぎ・再起動復元の厳密化は P7（対象外）。
    LaunchedEffect(verticalMode, pendingVerticalRestore) {
        val pos = pendingVerticalRestore ?: return@LaunchedEffect
        val (targetIndex, _) = ReadingPositionMapper.toScroll(pos, itemSizePx = 0, headerItemCount = 1)
        // まず当該 index を先頭へ（新モードでの measure を誘発し可視化する）。
        lazyListState.scrollToItem(targetIndex, 0)
        // 新モードで当該 index の実寸が現れるまで待つ（layoutInfo はフレームレート state＝snapshotFlow で観測）。
        val sizePx = withTimeoutOrNull(VERTICAL_RESTORE_TIMEOUT_MS) {
            snapshotFlow {
                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.size
            }.filterNotNull().first()
        }
        if (sizePx != null && sizePx > 0) {
            val (index, offset) = ReadingPositionMapper.toScroll(pos, itemSizePx = sizePx, headerItemCount = 1)
            lazyListState.scrollToItem(index, offset)
        }
        // 消費済み。null 化で再合成しても早期 return され、二重復元やスクロール暴走は起きない。
        pendingVerticalRestore = null
    }
    // スキンM（星図）のクローム部品分岐フラグ（ADR 0022 §1＝本文エンジンは共有・替わるのは
    // 地の星屑/上端結線進捗/没入ゴースト題字のみ。M 以外では従来描画と完全同一）。
    val isSeizu = LocalSkin.current == Skin.SEIZU_M
    // スキンP（カートリッジ）のクローム部品分岐フラグ。替わるのは没入時の緑LCDセーブバー（常設クローム）と
    // クローム表示時 HUD の緑LCDセーブチップのみ（章扉/シーン区切りは ChapterContent 側の分岐）。P 以外は不変。
    val isCartridge = LocalSkin.current == Skin.CARTRIDGE_P
    // スキンJ（ポータル）のクローム部品分岐フラグ。J はバー自体は D 標準（署名にしない）で、追加するのは遊び心J2
    // 『敷居光』＝章末到達で右端に立つ次章の扉のみ（章扉/区切り/章末印は ChapterContent 側の分岐）。J 以外は不変。
    val isPortal = LocalSkin.current == Skin.PORTAL_J

    // 表示設定ボトムシートの開閉状態。
    // なぜ rememberSaveable か: 素の remember だとプロセス再生成（回転・background kill）で
    // シートだけ閉じてしまい、開いていた文脈が飛ぶ。検索シート（DiscoverySearchScreen）と
    // 同様に開閉を Saveable 化して復元する（Boolean は既定 Saver で保存可＝Saver 不要）。
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // 案3「一行残し」ライブプレビュー（2026-07-29 裁定・reading-settings-livepreview-D.html VARIANT 3）:
    // 表示設定シートのスライダー押下中フラグ。押している間だけ読書クローム（上下バー）も
    // シート・スクリムと一緒に完全透明化し、本文への効き目を全面で見せる。
    // rememberSaveable にしない理由: 押下はプロセス再生成をまたがない一瞬の対話状態のため。
    var settingsAdjusting by remember { mutableStateOf(false) }
    // 退避割合（0=通常/1=退避）。尺・easing はシート側と同一トークン（animateSettingsPeek）＝視覚同期。
    // 値は上下バーの graphicsLayer 内でのみ読む（draw 段の deferred read）。
    val settingsPeek = animateSettingsPeek(settingsAdjusting)

    // なろう紐付けシートの開閉状態。
    // なぜ rememberSaveable か: 上の表示設定シートと同じく、プロセス再生成でシートだけ
    // 閉じる不整合を避けるため開閉を Saveable 化する（Boolean は既定 Saver で保存可）。
    var showLinkSheet by rememberSaveable { mutableStateOf(false) }

    // ボトムバーの実測高さ（px）。退避スライド量に使う。
    // なぜ固定値にしないか: ナビゲーションバー実高（ボタン式/ジェスチャー式）でバー総高が
    // 変わるため、onSizeChanged で実測した高さ分だけスライドさせて完全に画面外へ退避させる。
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    // スキンP セーブバーの実測高さ（px）。上方向の退避スライド量に使う（フォント拡大でバー高が変わるため実測）。
    var saveBarHeightPx by remember { mutableIntStateOf(0) }

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
            // target 選択だけ方向対応表に従い縦横で鏡像にする（横書き＝左引き(offset<0)で次章／縦書き＝
            // 右引き(offset>0)で次章＝reverseLayout の読み進め方向）。確定スライドの向き（end）と速度の
            // 同符号チェックは offset の符号だけで決まる＝モード非依存でそのまま効く。
            val target = if (verticalMode) {
                if (offset > 0f) nextFile else prevFile
            } else {
                if (offset < 0f) nextFile else prevFile
            }
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

    // 引っ張りオフセットの許容範囲＝端章はその向きへ引けない(0)。方向は縦横で鏡像（方向対応表 P4）:
    // 横書き＝次章が負・前章が正／縦書き＝次章が正・前章が負。draggable の clamp と、縦書きの終端デルタを
    // 拾う nestedScroll(ChapterPullConnection) の bounds で同じ規則を共有する。縦書きでも Loading/Error 時は
    // LazyRow が無く親 draggable が生きるため、draggable 側 clamp も縦書きマッピングに従わせる必要がある。
    val pullBounds: () -> ClosedFloatingPointRange<Float> = {
        val w = bodyWidthPx.toFloat()
        if (verticalMode) {
            (if (canGoPrev) -w else 0f)..(if (canGoNext) w else 0f)
        } else {
            (if (canGoNext) -w else 0f)..(if (canGoPrev) w else 0f)
        }
    }
    // ChapterPullConnection は nestedScroll 装着の安定のため remember するが、章切替で nextFile 等が変わる
    // settleSwipe/bounds/verticalMode を第一フレームで焼き込むと遷移先が古いままになる＝rememberUpdatedState
    // で常に最新へ差し替えてから注入する（ラムダは State 参照を辿り毎回最新を読む）。
    val currentSettle by rememberUpdatedState(settleSwipe)
    val currentBounds by rememberUpdatedState(pullBounds)
    val currentVertical by rememberUpdatedState(verticalMode)
    val pullConnection = remember {
        ChapterPullConnection(
            enabled = { currentVertical },
            dragOffset = { dragOffsetPx },
            onDragOffset = { dragOffsetPx = it },
            bounds = { currentBounds() },
            onPullStart = { swipeSettleJob?.cancel() },
            onSettle = { velocityX -> currentSettle(velocityX) },
        )
    }

    // 【透過の天の川・視差接続・2026-07-19 裁定】読書スクロールも他 M 画面（本棚/目次/発見）と同様に常駐 backdrop の
    // 視差へ接続する＝「同じ空」の連続性（画面遷移でオフセットがリセットされない・積算は controller が保持）。
    // これは自律アニメではなくスクロール連動＝読書Mモーションゼロ規律（ADR 0022 §3・自走するモーション）の対象外。
    // reduce-motion では controller.onScrollDelta が内部で積算を止める（従来どおり）。M 以外は skyParallax=null＝no-op。
    val skyParallax = LocalSkyParallax.current
    val skyParallaxConnection = remember(skyParallax) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                skyParallax?.onScrollDelta(consumed.y)
                return Offset.Zero
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
            // 【透過の天の川】M は読書面を透明にし常駐 backdrop（同じ空）を透かす（地色スクリムは下の drawSeizuReadingScrim）。
            // M 以外は従来どおり不透明な読書地色（1ビットも変えない）。
            containerColor = if (isSeizu) Color.Transparent else colors.background,
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
                    // スキンM【透過の天の川・R4 中】: 読書面は透明で常駐 backdrop（同じ空）を透かし、その上へ地色スクリムで
                    // 空を減光する（全面×0.55＋本文帯×0.6）。本文の下層に静的に一度だけ敷く（スワイプ追従は本文側の
                    // translationX のみ＝地は動かない。M 以外は Modifier 無変化）。
                    .then(if (isSeizu) Modifier.drawBehind { drawSeizuReadingScrim() } else Modifier)
                    // スキンM: 読書スクロールを常駐 backdrop の視差へ接続（他 M 画面と同じ「同じ空」の連続性）。
                    // M 以外は skyParallax=null＝no-op だが、非M で 1 ビットも介入しないよう isSeizu で明示ゲート。
                    .then(if (isSeizu) Modifier.nestedScroll(skyParallaxConnection) else Modifier)
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
                    // 横書き（LazyColumn＝縦スクロール）では横ドラッグが素通りしここが発火する。
                    .draggable(
                        state = rememberDraggableState { delta ->
                            // 同期加算（launch 経由の加算は同一フレーム内の複数 delta が潰し合う＝上のなぜ参照）。
                            // clamp は方向対応表どおり縦横で鏡像＝draggable/nestedScroll で pullBounds を共有。
                            dragOffsetPx = (dragOffsetPx + delta).coerceIn(pullBounds())
                        },
                        orientation = Orientation.Horizontal,
                        onDragStarted = { swipeSettleJob?.cancel() },
                        onDragStopped = { velocity -> settleSwipe(velocity) },
                    )
                    // 縦書きは LazyRow(reverseLayout) が横ドラッグを消費し親 draggable が不発になるため、
                    // 章端で LazyRow が消費し切れず余った横デルタを nestedScroll で拾い引っ張りへ接続する
                    //（enabled=verticalMode のときだけ働く＝横書き経路は 1 ビットも変えない）。
                    .nestedScroll(pullConnection),
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
                // 遷移ジャンク対策（案A・2026-07-29 裁定・正本モック transition-skeleton-D.html）:
                // push 遷移窓の間は本文の実測テキスト（初回 measure が支配コスト＝Perfetto 2026-07-16 で
                // 67ms 級と実測・ChapterContent.kt:77）をコンポーズせず、本文の行リズムへ載せた段落骨だけを
                // 描く。クローム（上下バー・シート・覗き機構）は軽量なので実描画のまま＝P2「重い可変部だけ骨」
                // と同じ分担。パース Loading のスピナーも窓内は骨で置き換わる（遷移中の回転体は視線を奪うため
                // 骨に統一）。縦横分岐（verticalMode）の上流で差し替える＝縦書きの遷移も同経路で効き、骨は
                // 横書き汎形1種のみ（2026-07-29 裁定＝縦書き専用骨は作らない。250ms の場所取りに組方向の
                // 忠実さより「1種で全設定に成立する汎形」を優先）。
                if (deferHeavyContent) {
                    ReadingBodySkeleton(
                        colors = colors,
                        fontSize = fontSize,
                        lineHeightEm = lineHeightEm,
                        bodyMarginDp = bodyMarginDp,
                    )
                } else when (val result = parseResult) {
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
                            // 本文スロットのみ縦書き/横書きを分岐する（item 構成・位置保存は同型＝ChapterContent の鏡写し）。
                            if (verticalMode) {
                                VerticalChapterContent(
                                    content = result.content,
                                    colors = colors,
                                    fontSize = fontSize,
                                    lineHeightEm = lineHeightEm,
                                    bodyMarginDp = bodyMarginDp,
                                    lazyListState = lazyListState,
                                    continuation = continuationSlot,
                                )
                            } else {
                                ChapterContent(
                                    content = result.content,
                                    colors = colors,
                                    fontSize = fontSize,
                                    lineHeightEm = lineHeightEm,
                                    bodyMarginDp = bodyMarginDp,
                                    lazyListState = lazyListState,
                                    continuation = continuationSlot,
                                    chapterNumber = chapterNumber,
                                    totalChapters = totalChapters,
                                    // スキンJ の章扉 ambient/glyph の変種選択に使う（J 以外は不使用・縦書きは非対応）。
                                    readingTheme = readingTheme,
                                )
                            }
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
                // 次章/前章の覗きの出し分けと覗きパネルの湧き出し方向は縦横で鏡像（方向対応表 P4）:
                // 横書き＝offset<0 で次章（右から覗く）／縦書き＝offset>0 で次章（左から覗く）。verticalMode は
                // 素の param のため derivedStateOf は verticalMode をキーに remember し直す（dragOffsetPx は State）。
                val peekNext by remember(verticalMode) {
                    derivedStateOf { if (verticalMode) dragOffsetPx > 0f else dragOffsetPx < 0f }
                }
                val peekPrev by remember(verticalMode) {
                    derivedStateOf { if (verticalMode) dragOffsetPx < 0f else dragOffsetPx > 0f }
                }
                if (peekNext && nextPeek != null) {
                    ChapterPeekPanel(
                        // 横書き＝右から（+bodyWidth）／縦書き＝左から（-bodyWidth）湧き出す。
                        translationX = { (if (verticalMode) -bodyWidthPx else bodyWidthPx) + dragOffsetPx },
                        peek = nextPeek,
                        colors = colors,
                        fontSize = fontSize,
                        lineHeightEm = lineHeightEm,
                        bodyMarginDp = bodyMarginDp,
                        verticalMode = verticalMode,
                        readingTheme = readingTheme,
                    )
                }
                if (peekPrev && prevPeek != null) {
                    ChapterPeekPanel(
                        // 横書き＝左から（-bodyWidth）／縦書き＝右から（+bodyWidth）湧き出す。
                        translationX = { (if (verticalMode) bodyWidthPx else -bodyWidthPx) + dragOffsetPx },
                        peek = prevPeek,
                        colors = colors,
                        fontSize = fontSize,
                        lineHeightEm = lineHeightEm,
                        bodyMarginDp = bodyMarginDp,
                        verticalMode = verticalMode,
                        readingTheme = readingTheme,
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
                    // 初期実測待ちの不可視化＋案3ライブプレビュー退避（スライダー押下中は完全透明）の合成。
                    alpha = readingBarAlpha(barsVisualReady, settingsPeek.value)
                },
            // なぜ IgnoringVisibility か: トグルと同フレームで systemBars を hide/show するため、
            // 可視追従の既定 insets だとバー内パディングが 0⇄実測値で振れ、バー高の再測定で
            // 開閉のたびに下端がガタつく（本文側 ChapterContent と同じ対策をバー自身にも適用）。
            windowInsets = WindowInsets.systemBarsIgnoringVisibility
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            // なぜ不透明か: モック reading-D は上下バーとも background:var(--bar)（不透明）＝
            // 不透明な上部バー（topBarBackground）との対称が正。旧 .copy(alpha=0.95f) は WebView 期
            // html_exporter.py .nav-footer の持ち越しで、BottomAppBar の Surface は nav バー inset 帯まで
            // この色で塗るため、5% 透過が inset 帯（ボタン行の下の無地部分）で本文の透けとして見えていた
            //（2026-07-29 実機・上下バー非対称の真因）。M の navBackground も焼き込み済み不透明トークン＝
            // 使用側で alpha を掛けない前提（SkinM.kt）。
            containerColor = colors.navBackground,
            contentColor = colors.topBarIcon,
        ) {
            // C①案A: 下端を4分割。横書き＝[前章｜目次｜表示設定｜次章]。表示設定を右上隅の歯車から下端へ
            // 集約し、藍＋太字で画面唯一の強調にする（原則4「一画面一強調」）。前後章は目次未ロード中の
            // disabled を維持。
            // 縦書き（右→左進行）中は端の2ボタンを鏡像配置＝[次章｜目次｜表示設定｜前章]（2026-07-29 ユーザー
            // 裁定）。なぜ鏡像か: 縦書きは左へ読み進む＝「左端＝進む先」なので、押す方向と進む方向を一致させる。
            // 矢印アイコンはスロット固定（左端＝左向き・右端＝右向き）のまま、担う遷移（ラベル・遷移先）だけを
            // 入れ替える＝反転後の配置に合う向きが自然に保たれる。モック正本 reading-vertical-scroll-D.html の
            // 下端バーは reading-D の横書き並びの流用（冒頭注記どおり）＝縦書き専用の並びは未規定のため、
            // 本裁定を正とする（モック改訂は別便）。
            BottomBarButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = if (verticalMode) "次章" else "前章",
                colors = colors,
                // 目次未ロード中は無効化（disabled トークンで淡色化）。押下時の目次フォールバックを防ぐ
                enabled = navEnabled,
                onClick = { onNavigateTo(if (verticalMode) nextFile else prevFile) },
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
                // 右端スロット: 横書き＝次章／縦書き＝前章（鏡像配置。理由は左端スロットのコメント参照）。
                label = if (verticalMode) "前章" else "次章",
                colors = colors,
                enabled = navEnabled,
                onClick = { onNavigateTo(if (verticalMode) prevFile else nextFile) },
            )
        }

        TopAppBar(
            modifier = Modifier.graphicsLayer {
                // なぜ graphicsLayer か: レイアウトを再計算せず描画位置のみを変えるため。
                // これによりバーの追従中でも本文の位置が一切動かない。
                translationY = topAppBarState.heightOffset
                // 初期実測待ちの不可視化＋案3ライブプレビュー退避（スライダー押下中は完全透明）の合成。
                alpha = readingBarAlpha(barsVisualReady, settingsPeek.value)
            },
            title = {
                when (val r = parseResult) {
                    // 2026-07-29 裁定(a): 縦書きモード中は章題テキストを出さない（バー自体・戻る←・
                    // 操作アイコンは従来どおり）。真因＝縦書きは列高確保のため上端クリアランスを意図的に
                    // 省略しており（VerticalChapterContent の contentPadding 理由コメント参照）、バー可視時に
                    // 列上端が題字の下へ潜って重なる。章題は本文先頭の章見出し（VerticalChapterHeader）が
                    // 担う・モック正本の没入挙動（没入時は章見出しが唯一の章タイトル表示）と整合。
                    is ParseResult.Success -> if (!verticalMode) {
                        Text(
                            text = r.content.title,
                            fontFamily = MinchoFamily,
                            fontSize = FontSectionTitle,
                            maxLines = 1,
                            // 長い章タイトルは文字途中で切らず末尾を「…」で省略する
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> Unit
                }
            },
            navigationIcon = {
                // 章の ← は「その本の目次へ」戻る。横スワイプ Back も同一モデルへ一本化済み（一階層 up・2026-07-19 裁定）。
                // 本棚へは目次画面の ← が担う（本文→目次→本棚）。
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
            // スキンP のみ: クローム表示時 HUD の緑LCDセーブチップ（reading-P .hud .save）を右端に載せる
            //（没入時は下の ReadingSaveBarP が担い、TopAppBar 退避で自然に入れ替わる）。P 以外は actions 空＝不変。
            actions = {
                if (isCartridge && chapterNumber != null && totalChapters != null && totalChapters > 0) {
                    SaveChipP(
                        chapterNumber = chapterNumber,
                        totalChapters = totalChapters,
                        fraction = chapterNumber.toFloat() / totalChapters,
                        modifier = Modifier.padding(end = Spacing.S8),
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
            // なぜ IgnoringVisibility か: トグルと同フレームで systemBars を hide/show するため、
            // 可視追従の既定 insets だとバー内パディングが 0⇄実測値で振れ、heightOffsetLimit の
            // 再測定で開閉のたびに上端がガタつく（本文側 ChapterContent と同じ対策をバー自身にも適用）。
            windowInsets = WindowInsets.systemBarsIgnoringVisibility
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            // scrollBehavior は heightOffsetLimit の測定のため維持する（nestedScroll 接続は無し）。
            scrollBehavior = scrollBehavior,
        )

        // ────── スキンM: 上端の結線進捗＋没入ゴースト題字（reading-M .prog / .ghost）──────
        if (isSeizu) {
            // 結線進捗＝ほぼ唯一の常設クローム（バーの出没に関わらず最前面・画面最上端 2dp）。
            if (chapterNumber != null && totalChapters != null && totalChapters > 0) {
                ReadingProgressStarM(
                    fraction = chapterNumber.toFloat() / totalChapters,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            // 没入時のゴースト題字（題名 · 第N話）。トップバー退避量に連動して入れ替わりに現れる
            //（collapsedFraction は graphicsLayer 内の deferred read＝バー追従で composition を再実行しない）。
            Text(
                text = buildString {
                    append(bookTitle)
                    if (chapterNumber != null) append(" · 第${chapterNumber}話")
                },
                fontSize = 11.sp,                      // reading-M .ghost .ct 11px
                letterSpacing = 0.14.em,
                color = colors.textSecondary,          // --dim
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(top = Spacing.S12)
                    .padding(horizontal = Spacing.S40)
                    .graphicsLayer { alpha = topAppBarState.collapsedFraction },
            )
        }

        // ────── スキンP: 上端の緑LCDセーブバー（reading-P .savebar・没入中の唯一常設クローム）──────
        // 出没はスライド退避（下端バーと同型）。クローム表示時は共有 TopAppBar（＋SaveChipP の HUD）が代わりに
        // 出るため、没入（collapsedFraction=1）でのみ上端に見せ、表示時（=0）は自身の高さ分だけ上へ退避して隠す。
        // なぜ旧 alpha フェードをやめたか（2026-07-17 実機・真因）: フェード中はバー面が半透明化し、背後を流れる
        // 本文が「SAVE…%」と重なって読めた（LCD面は不透明が正・reading-P .savebar は不透明地）。スライドなら
        // 出没のどの瞬間もバーは不透明のまま＝透け重なりが構造的に起きない。「常設の静かな随伴」は保つ。
        // また上端インセットを外し flush-top（モック .savebar{top:0}）に置く: 旧実装は statusBar 実高ぶん下げて
        // 敷いており、没入で status bar を隠すと生じる帯にスクロール本文の切れ端が覗いていた（隙間の解消）。
        // collapsedFraction/saveBarHeightPx は graphicsLayer 内の deferred read＝バー追従で composition を再実行しない。
        if (isCartridge && chapterNumber != null && totalChapters != null && totalChapters > 0) {
            ReadingSaveBarP(
                fraction = chapterNumber.toFloat() / totalChapters,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { saveBarHeightPx = it.height }
                    .graphicsLayer {
                        // 退避割合（1-collapsedFraction）× 実測高さ分だけ上へ（collapsedFraction=0＝表示時に完全に画面外上）
                        translationY = -saveBarHeightPx * (1f - topAppBarState.collapsedFraction)
                        // 初期退避の実測待ち中は不可視（既定 state が表示位置で生まれる一瞬の露出を防ぐ・下端バーと同型）
                        alpha = if (barsVisualReady) 1f else 0f
                    },
            )
        }

        // ────── 遊び心J2『敷居光』（reading-J .nextdoor・章末到達で右端に次章の扉が灯る）──────
        // なぜ canGoNext ゲートか: 「次の章へ」誘う敷居光は次章が在るときだけ意味を持つ（最終章＝誘い先が無い）。
        // トリガ＝reader が末尾に到達（!canScrollForward＝これ以上下へスクロールできない＝章末読了）。モックの
        // 「章末までスクロールした瞬間」に対応する既存信号で、捏造でなく正直に配線できる（TODO 不要）。
        // なぜ derivedStateOf か: canScrollForward はフレームレート state。boolean 反転時だけ recompose させ、
        // 連続スクロールで composition を回さない（本棚 showBand・最上部ピルと同じ定石）。出没アニメ・呼吸・
        // reduce-motion は NextDoorEdgeGlowJ 内（Motion.kt reveal/dismiss ＋ M 脈動先例）。
        if (isPortal && canGoNext) {
            val atChapterEnd by remember(lazyListState) {
                derivedStateOf { !lazyListState.canScrollForward }
            }
            NextDoorEdgeGlowJ(
                atChapterEnd = atChapterEnd,
                colors = colors,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

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
        // barsVisualReady を key に持つ: remember がラムダごと固定するため、初期化完了フラグの反転を
        // 織り込むには作り直しが要る（章インスタンスごとに高々1回の反転）。
        val chromeVisibleForPill by remember(barsVisualReady) {
            derivedStateOf { barsVisualReady && topAppBarState.collapsedFraction < 0.5f }
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
                    // 不透明地（alpha を掛けない）。真因＝半透明(.92)地は暗色スキン(J=#101913)で
                    // 背後の章末mark「— 第N話 了 —」(明色)が8%透けてピル文字とだぶる（2026-07-17 実機）。
                    // モックの .92 は D 明色テーマ（ピル色≒地色で透過が目立たない）較正で、暗地×明背景の
                    // J では成立しない。操作可能ピルは可読性優先＝navBackground を不透明で敷く。
                    .background(colors.navBackground)
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
                verticalMode = verticalMode,
                // 切替前に段落位置を捕捉してから永続化するラッパを渡す（素の onVerticalModeChange ではない）。
                onVerticalModeChange = onVerticalModeToggle,
                // 案3ライブプレビュー: スライダー押下中は上下バーも退避する（シート内の押下検出の還流）。
                onAdjustingChange = { settingsAdjusting = it },
                // dismiss 時は調整中フラグも必ず解除する（押下中に外タップ等で閉じられた場合に
                // バーが透明のまま取り残されないための防御。通常はシート破棄時の null 通知が先に解除する）。
                onDismiss = {
                    showSettings = false
                    settingsAdjusting = false
                },
            )
        }
    }
}

/**
 * 読書クローム（上下バー）の描画 alpha。初期実測待ちの不可視化（barsVisualReady=false は常に 0）と、
 * 案3ライブプレビュー退避（settingsPeek: 0=通常/1=退避）を合成する純関数。
 * なぜ関数へ切り出すか: 上下バーの graphicsLayer 2箇所で同一規則を共有し、合成規則を JVM テストで固定するため。
 */
internal fun readingBarAlpha(barsVisualReady: Boolean, settingsPeek: Float): Float =
    if (barsVisualReady) 1f - settingsPeek else 0f
