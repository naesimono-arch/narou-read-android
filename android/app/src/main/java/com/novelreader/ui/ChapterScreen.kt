// 章読書の route 層（state holder）。NativeReadingScreen.kt の純移動分割（2026-07-27）で切り出した。
// なぜ分離したか: NativeReadingScreen.kt には画面骨格（ReadingScreen／描画層 ChapterScreenContent）だけを残し、
// 副作用（章パース・継続照会・スクロール保存・ライフサイクルフラッシュ・没入バー制御）を持つ route 層を
// 役割単位の独立ファイルにするため。中身は無改変の純移動（可視性昇格のみ）＝名前・値・ロジック・順序は不変。
package com.novelreader.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novelreader.NovelReaderApplication
import com.novelreader.PrefKeys
import com.novelreader.model.ChapterContent as ChapterContentModel
import com.novelreader.model.ParseResult
import com.novelreader.model.TocEntry
import com.novelreader.narou.ContinuationInfo
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.computeContinuation
import com.novelreader.narou.narouEpisodeUrl
import com.novelreader.narou.narouWorkUrl
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.rememberReadingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** 参照ジャンプ滞留昇格（C1）: この時間だけ参照先に滞在したら読み進めとみなし正規位置へ昇格。 */
private const val REFERENCE_DWELL_TIMEOUT_MS = 20_000L

/** 参照ジャンプ滞留昇格（C1）: この段落数だけスクロールしたら読み進めとみなし正規位置へ昇格。 */
private const val REFERENCE_DWELL_SCROLL_ITEMS = 4

/** 章本文を表示する内部 Composable */
// internal（旧 private）: 純移動のファイル分割で呼び出し元 ReadingScreen（NativeReadingScreen.kt）と別ファイルに
// なったため、跨ファイル参照できる最小可視性へ昇格する（トップレベル private はファイルスコープで参照不可）。
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun ChapterScreen(
    currentFile: String,
    htmlDirPath: String,
    tocEntries: List<TocEntry>,
    // なろう紐付けの束（書名・Nコード・候補検索）。state は VM の単一正本／検索・再試行は VM へ依頼する。
    // 束の定義と「既定値を置かない理由」は ReadingFace.kt 冒頭。
    ncodeLink: NcodeLink,
    // テーマ4択の束。MainActivity が本棚と共有する単一正本（2026-07-17 裁定②）をそのまま受ける。
    theme: ThemeControl,
    // 文字組設定の束（文字サイズ・行間・左右余白・縦書き）。ReadingScreen が app_prefs で読み書きする。
    typography: ReadingTypography,
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
    // として受け取る。トグル結果は onChromeVisibleChange で親へ還流する。false＝入場時没入（従来挙動）。
    chromeVisibleInitial: Boolean,
    onChromeVisibleChange: (Boolean) -> Unit,
    // 章パースのキャッシュ（親 ReadingScreen 所有・章を跨いで共有）。遷移後の初期表示と覗き先読みが使う。
    chapterCache: MutableMap<String, ChapterContentModel>,
    // 章の初期スクロール位置の解決（親 ReadingScreen の1本＝セッション内記憶→入場復元→先頭）。
    // 覗きパネルへこの結果を焼き込み、着地（initialScrollIndex/Offset）と必ず一致させる。
    resolveInitialScroll: (String) -> Pair<Int, Int>,
) {
    // ── 束の展開（本体の参照名を変えない局所別名＝挙動・値とも既存と同一） ──
    val ncode = ncodeLink.ncode
    val readingTheme = theme.appTheme

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
                // 詳細は WorkDetail で返る＝継続判定に要る要約（summary）を渡す（P5 第2段の脱なろう境界）。
                narouRepository.novelDetail(ncode)?.let { computeContinuation(tocEntries.size, it.summary) }
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

    // Back キー（＝一階層 up＝章なら目次・目次なら本棚。2026-07-19 裁定）は親の ReadingScreen が
    // backStack＋BackHandler で一元管理する。経路スタックを所有するのが ReadingScreen のため、
    // rememberSaveable 永続化もそちらに集約した（ここでは扱わない）。

    // バーの表示/非表示は中央タップのトグルだけで駆動する（2026-07-16 実機フィードバックで
    // スクロール量・速度連動の出没を廃止＝「出たり引っ込んだり」する複雑な挙動をやめる）。
    // scrollBehavior を残すのは TopAppBar に渡して heightOffsetLimit（バー実高の負値）を
    // 実測させるためだけ——nestedScroll 接続はどこにも張らないためスクロールでは一切動かない。
    // snapAnimationSpec = null: 内蔵スナップも無効化（動きは settleTopBar の spring が一元所有）。
    // 注意: state は必ず既定値で生む。M3 の TopAppBarLayout は自身の layout 高さを
    // 「バー実高 + heightOffset」で計算する（AppBar.kt:2206）ため、実高が未測定のうちに実高超の
    // 負オフセットを初期値で仕込むと負サイズ（Size out of range）で即クラッシュする（2026-07-16 実測＝
    // sentinel -10000f 案は本を開いた瞬間に落ちた）。よって「実測完了までの見た目」は state でなく
    // barsVisualReady の alpha ゲート（Content 側）で隠す。
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        topAppBarState,
        snapAnimationSpec = null,
    )

    // 入場時既定=「無」（d-chrome Design/09-A）。章題は本文先頭の ChapterHeader が担うため、
    // 入場時に上部バーを見せる必要はない。TopAppBar の実測（heightOffsetLimit が既定 -Float.MAX_VALUE から
    // 実負値へ更新）を待って一度だけ全退避する。ただしメニュー表示中に前章/次章で章を跨いだ場合
    //（chromeVisibleInitial=true）は退避しない＝「一度出したら再度タップするまで残る」の章跨ぎ維持。
    // なぜ rememberSaveable の guard か: ユーザーが一度バーを出した後（プロセス再生成の復元含む）に
    // 再び勝手に畳んで操作を奪わないため。topAppBarState 自体も heightOffset を復元するので二重に安全。
    var didInitialCollapse by rememberSaveable { mutableStateOf(false) }
    // 実測完了までの見た目ゲート: 既定 state は offset=0（＝表示位置）で生まれるため、没入入場では
    // 初期退避が効くまでの数フレーム、バー/システムバーが一瞬見えてから消える表示バグになる
    //（没入のままスワイプ章送りでメニューが一瞬出る＝2026-07-16 実機。従来は章切替の再パース待ちの
    // 無地フレームが覆い隠しており、章キャッシュのシームレス化で露出した）。state はM3の不変式
    //（layout 高=実高+offset）に縛られ先に畳めないため、退避完了まで描画側 alpha で隠す。
    // メニュー維持入場（chromeVisibleInitial=true）は最初から表示が正なので即 ready。
    val barsVisualReady = didInitialCollapse || chromeVisibleInitial
    LaunchedEffect(topAppBarState) {
        if (didInitialCollapse) return@LaunchedEffect
        // 既定値 -Float.MAX_VALUE を除外し「実測された」限界値だけを待つ（既定値のまま畳むと
        // offset が巨大負値へ落ち、M3 の layout 計算が負サイズでクラッシュしうる）。
        snapshotFlow { topAppBarState.heightOffsetLimit }
            .first { it < 0f && it != -Float.MAX_VALUE }
        if (!chromeVisibleInitial) {
            topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        }
        // 退避と同一スナップショットで ready 化＝「alpha 解除」と「畳み済み位置」が同フレームで揃う。
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
        // didInitialCollapse でゲート: 既定 state は表示位置で生まれるため、没入入場の実測待ち中に
        // fraction≈0 で「show」が流れてシステムバーが一瞬出る（メニュー一瞬表示バグの片割れ）のを防ぐ。
        snapshotFlow { (didInitialCollapse || chromeVisibleInitial) && topAppBarState.collapsedFraction < 0.5f }
            .distinctUntilChanged()
            .collect { chromeVisible ->
                if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
                else controller.hide(WindowInsetsCompat.Type.systemBars())
                // 章跨ぎ維持: トグル結果を親（ReadingScreen）の保持状態へ還流する。
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
    val chromeHintPrefs = remember { context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE) }
    var chromeHintConsumed by remember {
        mutableStateOf(chromeHintPrefs.getBoolean(PrefKeys.IMMERSIVE_HINT_SHOWN, false))
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
                    chromeHintPrefs.edit().putBoolean(PrefKeys.IMMERSIVE_HINT_SHOWN, true).apply()
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
        // 素通しの3束（文字組・テーマ・なろう紐付け）は route が受けたものをそのまま渡す。
        typography = typography,
        theme = theme,
        ncodeLink = ncodeLink,
        // 没入クローム／本文スクロールの state holder は route が所有する（副作用と共有するため）。
        chrome = ReadingChrome(
            lazyListState = lazyListState,
            topAppBarState = topAppBarState,
            scrollBehavior = scrollBehavior,
            barsVisualReady = barsVisualReady,
            showChromeHint = showChromeHint,
        ),
        // 章ナビは route が tocEntries から算出する（隣章・活性条件・章位置）。
        nav = ChapterNav(
            prevFile = prevFile,
            nextFile = nextFile,
            navEnabled = navEnabled,
            isLastChapter = isLastChapter,
            // スキンM の章扉「第 N 話」と上端結線進捗の材料（目次未ロード中は null＝出さない）。
            chapterNumber = if (currentIndex >= 0) currentIndex + 1 else null,
            totalChapters = tocEntries.size.takeIf { it > 0 },
            onNavigateTo = onNavigateTo,
            onNavigateToBookshelf = onNavigateToBookshelf,
        ),
        // 継続導線。Custom Tabs 起動（再入ガード付き）は route の2つのコールバックが担う。
        continuationCta = ContinuationCta(
            continuationInfo = continuationInfo,
            onReadContinuation = onReadContinuation,
            onOpenWorkPage = onOpenWorkPage,
        ),
        // 覗きの初期位置は着地と同じ resolveInitialScroll で焼き込む（覗き＝遷移後表示の完全一致）。
        prevPeek = prevPreview?.let { c ->
            val (index, offset) = resolveInitialScroll(prevFile)
            ChapterPeek(c, index, offset)
        },
        nextPeek = nextPreview?.let { c ->
            val (index, offset) = resolveInitialScroll(nextFile)
            ChapterPeek(c, index, offset)
        },
        // 参照ジャンプ中（C1）は「続きに戻る」チップを表示する。
        showReturnChip = referenceMode,
        onReturnToContinuation = onReturnToContinuation,
        onRetryParse = { retryKey++ },
    )
}
