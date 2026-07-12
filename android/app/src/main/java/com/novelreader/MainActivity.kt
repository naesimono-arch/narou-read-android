package com.novelreader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.novelreader.model.BookId
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.Ncode
import com.novelreader.ui.BookshelfScreen
import com.novelreader.ui.ReadingErrorScreen
import com.novelreader.ui.ReadingScreen
import com.novelreader.ui.discovery.DiscoveryGenreScreen
import com.novelreader.ui.discovery.DiscoveryHomeScreen
import com.novelreader.ui.discovery.DiscoveryResultScreen
import com.novelreader.ui.discovery.DiscoverySearchScreen
import com.novelreader.ui.discovery.NovelDetailScreen
import com.novelreader.ui.discovery.PdfImportScreen
import com.novelreader.ui.discovery.WebReaderScreen
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.colors
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.DiscoveryViewModel
import com.novelreader.viewmodel.ResultContext
import com.novelreader.viewmodel.ResultSource
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    // 変換完了通知タップの deep link 対象 bookId（M11）。onCreate/onNewIntent で更新し、
    // Compose 側（NovelReaderApp）が消費して該当の本の読書画面へ着地する。
    // なぜ State か: launchMode=singleTop で既存インスタンスへ onNewIntent 経由で新しい対象が
    // 届くため、値の変化を Compose ツリーへ伝える必要がある。消費後は null に戻す（再ナビ防止）。
    private val deepLinkBookId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知（プロセス再生成を伴う cold start 含む）からの deep link 対象を取り込む。
        // savedInstanceState==null に限定する理由: 構成変更（回転）や process death 復元では
        // 同じ起動 Intent が再配達され getStringExtra が再び非 null になるため、毎回 deep link を
        // 再発火してしまう（復元中の読書画面を勝手に置き換える）。初回生成のみ処理し、以後の
        // 復元は Navigation のバックスタック復元に委ねる。稼働中の再タップは onNewIntent が拾う。
        if (savedInstanceState == null) {
            deepLinkBookId.value = intent?.getStringExtra(EXTRA_BOOK_ID)
        }

        // 強制終了リカバリ（孤立HTML掃除＋未完了ジョブの通知・再開）。Activity 起動時に
        // 呼ぶのは FGS のバックグラウンド起動制限を避けるため（詳細は実装側の doc コメント）。
        // 実処理はプロセスごとに1回・IO スレッドで走る。
        (application as NovelReaderApplication).runStartupRecoveryOnce()

        // 設定スキーマ版を記録（evolve・予防的）。将来 ReadingTheme 等の enum を改名した際、
        // 保存済みの「生 enum 名」がどの版の綴りかを移行コードが判別できるようにするための版番号。
        // 未記録の既存/新規インストールは現行スキーマ＝v1 として一度だけ刻む（以後の移行がこの版を
        // 読んで綴りを変換する）。app_prefs は他の読書設定（reading_theme 等）と同じ置き場。
        val settingsPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!settingsPrefs.contains(KEY_SETTINGS_SCHEMA_VERSION)) {
            settingsPrefs.edit().putInt(KEY_SETTINGS_SCHEMA_VERSION, SETTINGS_SCHEMA_VERSION).apply()
        }

        // Edge-to-Edge 表示を有効化（ステータスバー・ナビバー領域までコンテンツを描画）
        // NovelReaderTheme 内で WindowCompat.getInsetsController を使うため、
        // setDecorFitsSystemWindows は setContent より前に呼ぶ必要がある
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // 見た目テーマの正本を MainActivity へ巻き上げ、本棚(NovelReaderTheme)と読書(ReadingScreen)で
            // 単一の状態を共有する。これにより設定シート/本棚どちらで変えても全体が同期する。
            // （旧: 本棚=システム追従・読書=独立prefの2系統で不一致だった＝handover B「11 本棚テーマ追従」を解消）
            // 既定: reading_theme 未保存時はシステムのライト/ダークに追従。以後はユーザー選択を永続。
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var appTheme by remember { mutableStateOf(loadInitialTheme(prefs, systemDark)) }
            // 「システムに従う」状態＝reading_theme 未保存。明示選択で解除、追従選択でキー削除により復帰。
            // なぜキー削除で表すか: 「未宣言＝追従」という loadInitialTheme の既存規約をそのまま正本にし、
            // 第4の enum 値や別フラグを増やさない（設定は「開かれない」が理想＝UX/19）。
            var followingSystem by remember { mutableStateOf(prefs.getString("reading_theme", null) == null) }
            val onThemeChange: (ReadingTheme) -> Unit = { theme ->
                appTheme = theme
                followingSystem = false
                prefs.edit().putString("reading_theme", theme.name).apply()
            }
            val onFollowSystem: () -> Unit = {
                prefs.edit().remove("reading_theme").apply()
                followingSystem = true
                appTheme = if (systemDark) ReadingTheme.DARK else ReadingTheme.LIGHT
            }

            // Material3 配色もテーマ3値（ライト/セピア/ダーク）へ追従させる。
            // 旧実装はセピア時にライト配色を流用しており、本棚・発見系で「ライトとセピアの
            // 色味に差がない」実機フィードバック（2026-07-07）の主因だった。読書側の固有色
            // （ReadingColors）とは別系統だが、同じ琥珀紙トーンに揃えている（Theme.kt 参照）。
            NovelReaderTheme(theme = appTheme) {
                NovelReaderApp(
                    appTheme = appTheme,
                    onThemeChange = onThemeChange,
                    followingSystem = followingSystem,
                    onFollowSystem = onFollowSystem,
                    // .value の読み取りを composable 内で行うことで onNewIntent の更新が再コンポーズを誘発する。
                    deepLinkBookId = deepLinkBookId.value,
                    onDeepLinkConsumed = { deepLinkBookId.value = null },
                )
            }
        }
    }

    // launchMode=singleTop のため、Activity 稼働中に通知タップが来ると新規インスタンスを作らず
    // ここへ届く。setIntent で getIntent を最新化しつつ deep link 対象を差し替える。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_BOOK_ID)?.let { deepLinkBookId.value = it }
    }

    companion object {
        /** 変換完了通知 → 読書画面 deep link 用の bookId extra キー（M11）。 */
        const val EXTRA_BOOK_ID = "com.novelreader.extra.BOOK_ID"

        /** 設定スキーマ版（evolve）。enum の生 String 保存の改名耐性のため prefs に記録する現行版。 */
        const val SETTINGS_SCHEMA_VERSION = 1

        /** 設定スキーマ版の prefs キー。 */
        const val KEY_SETTINGS_SCHEMA_VERSION = "settings_schema_version"
    }
}

/**
 * 初期テーマ決定。reading_theme 未保存ならシステムのライト/ダークへ追従し、
 * 保存済みならそれを採用する（不正値・enum名変更時はシステム追従へフォールバック）。
 */
private fun loadInitialTheme(prefs: SharedPreferences, systemDark: Boolean): ReadingTheme {
    val systemFallback = if (systemDark) ReadingTheme.DARK else ReadingTheme.LIGHT
    val saved = prefs.getString("reading_theme", null) ?: return systemFallback
    return runCatching { ReadingTheme.valueOf(saved) }.getOrDefault(systemFallback)
}

@Composable
private fun NovelReaderApp(
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    deepLinkBookId: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext
    val viewModel: BookshelfViewModel = viewModel()
    // 発見系（ホーム/ジャンル/結果一覧）はクエリ文脈を画面間で受け渡すため単一VMを共有する。
    // ロードは ensureHomeLoaded の遅延型なので、ここで生成しても本棚起動時に通信は発生しない。
    val discoveryViewModel: DiscoveryViewModel = viewModel()

    // 変換完了通知タップからの deep link 着地（M11）。状態依存の非決定な着地を排し、
    // 常に「本棚を起点に該当の本の読書画面」へ疑似バックスタックで着地させる。
    LaunchedEffect(deepLinkBookId) {
        val bookId = deepLinkBookId ?: return@LaunchedEffect
        // Content 確定（DB 初回発行後）まで待つ。Loading のまま解決すると存在する本でも
        // 「無い」と誤判定して本棚に落ちるため。books は hot StateFlow で cold start でも収束する。
        val content = viewModel.uiState.first { it is BookshelfUiState.Content } as BookshelfUiState.Content
        val book = content.books.firstOrNull { it.id == bookId }
        if (book != null) {
            // 通知テレポートの着地が果たされた時点で stale 通知を取り下げる（UX監査 notify Minor）。
            // なぜここか: deep link の意図（この本を開く）が満たされた確定点で、変換完了通知と
            // 当該作品の新着通知は「呼び出し状」としての役目を終えているため（押しても同じ場所に来るだけ）。
            (appContext as NovelReaderApplication).let { app ->
                app.cancelCompletionNotification()
                book.ncode?.let { app.cancelNewEpisodeNotification(it) }
            }
            // 読書位置は保存済み進捗を尊重する（生命線）。未読なら index.html。
            // 境界: bookId は deep link 由来の String＝型付き API へ渡す直前に BookId へ包む。
            val startFile = viewModel.getLastRead(BookId(bookId)) ?: "index.html"
            navController.navigate("reading/$bookId/$startFile") {
                launchSingleTop = true
                // bookshelf を残して起点を固定＝Back が必ず本棚へ戻る（固定起点の保証）。
                popUpTo("bookshelf") { inclusive = false }
            }
        } else {
            // 削除済み等で本が無い確定ケース: 最低限の保証として固定起点（本棚）へ着地する。
            navController.popBackStack("bookshelf", false)
        }
        // ナビ後に消費済みへ（null で再ナビを防ぐ。key 変化で本 Effect は即再実行され早期 return する）。
        onDeepLinkConsumed()
    }

    NavHost(navController = navController, startDestination = "bookshelf") {

        composable("bookshelf") {
            BookshelfScreen(
                viewModel = viewModel,
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                onOpenBook = { bookId, startFile ->
                    // launchSingleTop: 二度押しで同一読書画面がバックスタックに二重 push されるのを防ぐ（M1）。
                    navController.navigate("reading/$bookId/$startFile") { launchSingleTop = true }
                },
                onOpenDiscovery = {
                    navController.navigate("discovery") { launchSingleTop = true }
                },
                // (b) Web由来カードの「縦書きPDFを取り込む」→ 既存の取り込み画面ルートへ直行
                // （詳細画面経由の onImportPdf と同じ着地＝ADR 0011 の WebView 取り込み）。
                onImportWebNovel = { ncode ->
                    navController.navigate("discovery/detail/$ncode/import") { launchSingleTop = true }
                },
                // 機能②: Web カードの読書＝アプリ内 WebView（ADR 0012）。startEpisode 0=目次(初回)／>0=続きから。
                onReadWebNovel = { ncode, startEpisode ->
                    navController.navigate("web-reader/$ncode/$startEpisode") { launchSingleTop = true }
                },
            )
        }

        composable("discovery") {
            DiscoveryHomeScreen(
                viewModel = discoveryViewModel,
                onBack = { navController.popBackStack() },
                // 境界: nav ルートは String。Ncode を .value でほどいてパスへ載せる。
                onOpenDetail = { ncode -> navController.navigate("discovery/detail/${ncode.value}") { launchSingleTop = true } },
                onOpenGenre = { navController.navigate("discovery/genre") { launchSingleTop = true } },
                onPickBiggenre = { code, label ->
                    discoveryViewModel.openResult(
                        ResultContext(title = label, query = DiscoveryQuery(biggenres = setOf(code)), source = ResultSource.GENRE)
                    )
                    navController.navigate("discovery/result") { launchSingleTop = true }
                },
                onOpenSearch = { navController.navigate("discovery/search") { launchSingleTop = true } },
                onPickMood = { preset ->
                    discoveryViewModel.openResult(preset.toResultContext())
                    navController.navigate("discovery/result") { launchSingleTop = true }
                },
            )
        }

        composable("discovery/search") {
            DiscoverySearchScreen(
                viewModel = discoveryViewModel,
                onBack = { navController.popBackStack() },
                onSearchExecuted = { navController.navigate("discovery/result") { launchSingleTop = true } },
            )
        }

        composable("discovery/genre") {
            DiscoveryGenreScreen(
                onBack = { navController.popBackStack() },
                onPickBiggenre = { code, label ->
                    discoveryViewModel.openResult(
                        ResultContext(title = label, query = DiscoveryQuery(biggenres = setOf(code)), source = ResultSource.GENRE)
                    )
                    navController.navigate("discovery/result") { launchSingleTop = true }
                },
                onPickGenre = { code, label ->
                    discoveryViewModel.openResult(
                        ResultContext(title = label, query = DiscoveryQuery(genres = setOf(code)), source = ResultSource.GENRE)
                    )
                    navController.navigate("discovery/result") { launchSingleTop = true }
                },
            )
        }

        composable("discovery/result") {
            DiscoveryResultScreen(
                viewModel = discoveryViewModel,
                // F-D: App bar の ← は経路に依らず発見ホームへ固定 Up する。全ての結果経路
                // （検索/ジャンル/気分/キーワード）が discovery を必ず下位に持つため、discovery まで
                // pop すれば一段上の親へ一貫して戻れる。履歴 Back（onBack）は端末 Back と「条件を変更」に委ねる。
                onUp = { navController.popBackStack("discovery", false) },
                onBack = { navController.popBackStack() },
                // 境界: nav ルートは String。Ncode を .value でほどいてパスへ載せる。
                onOpenDetail = { ncode -> navController.navigate("discovery/detail/${ncode.value}") { launchSingleTop = true } },
            )
        }

        composable(
            route = "discovery/detail/{ncode}",
            arguments = listOf(navArgument("ncode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val ncode = backStackEntry.arguments?.getString("ncode") ?: return@composable
            NovelDetailScreen(
                // 境界: nav 引数は String。詳細画面へは型付き Ncode へ包んで渡す。
                ncode = Ncode(ncode),
                viewModel = viewModel(),
                onSearchKeywords = { words ->
                    // 複数キーワード（フィードバック2）: word へ半角スペース連結して inKeyword で検索する。
                    // 選択0件で呼ばれることはない（アクションは1件以上のときのみ表示）が、防御的に空を弾く。
                    if (words.isNotEmpty()) {
                        // title は1件なら従来どおり「「語」」、複数件は語の羅列だと見切れるため件数表記に畳む。
                        val title = if (words.size == 1) "「${words.first()}」" else "キーワード${words.size}件"
                        discoveryViewModel.openResult(ResultContext(
                            title = title, subtitle = "キーワードから",
                            source = ResultSource.KEYWORD,
                            query = DiscoveryQuery(word = words.joinToString(" "), inKeyword = true),
                        ))
                        navController.navigate("discovery/result") {
                            launchSingleTop = true
                            // why(F-A): キーワードタップの結果一覧は「どの経路で detail に来たか」で Back 先が
                            // 割れていた（result 経由なら result が隠れて残り、home 直行なら残らない）。
                            // popUpTo("discovery", inclusive=false) で discovery より上（既存 result・detail）を
                            // 全て畳んでから result を1枚積むことで、両経路とも [bookshelf, discovery, result] に固定する。
                            // resultContext は VM 単一保持のため、result を常に1枚に保つ SSOT もこれで維持される。
                            popUpTo("discovery") { inclusive = false }
                        }
                    }
                },
                // 縦書きPDF取り込み（ADR 0011）へ遷移。ここでの ncode は既にパス由来の String。
                onImportPdf = { navController.navigate("discovery/detail/$ncode/import") { launchSingleTop = true } },
                // 機能②: なろうをアプリ内 WebView で読む（ADR 0012）。目次(初回)＝0／続きから＝記録話 N を渡す。
                onReadFromToc = { navController.navigate("web-reader/$ncode/0") { launchSingleTop = true } },
                onResumeReading = { episode -> navController.navigate("web-reader/$ncode/$episode") { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "discovery/detail/{ncode}/import",
            arguments = listOf(navArgument("ncode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val ncode = backStackEntry.arguments?.getString("ncode") ?: return@composable
            PdfImportScreen(
                // 境界: nav 引数は String。取り込み画面へは型付き Ncode へ包んで渡す。
                ncode = Ncode(ncode),
                viewModel = viewModel(),
                // 取り込み投入後／戻る のいずれも取り込み画面を pop する（詳細画面へ戻る）。
                onImportStarted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        // 機能②: なろう作品をアプリ内 WebView で読む（読書位置の自動記録＋続きから再開＝ADR 0012）。
        // startEpisode: 0=目次(初回)／>0=その話へ直接着地(続きから)。境界: nav 引数は String/Int。
        composable(
            route = "web-reader/{ncode}/{startEpisode}",
            arguments = listOf(
                navArgument("ncode") { type = NavType.StringType },
                navArgument("startEpisode") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val ncode = backStackEntry.arguments?.getString("ncode") ?: return@composable
            val startEpisode = backStackEntry.arguments?.getInt("startEpisode") ?: 0
            WebReaderScreen(
                ncode = Ncode(ncode),
                startEpisode = startEpisode,
                viewModel = viewModel(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "reading/{bookId}/{startFile}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("startFile") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val startFile = backStackEntry.arguments?.getString("startFile") ?: return@composable

            // uiState は BookshelfScreen がバックスタック上で subscribe 中のため hot StateFlow。
            // collectAsStateWithLifecycle() で第1フレームから現在値（StateFlow.value）を即時派生させることで、
            // LaunchedEffect の1フレーム遅延を排除し初期描画のちらつき（左上ジャンプ）を防ぐ。
            // なぜ books ではなく uiState か（F-M）: 旧実装は初期値 emptyList の books を使い book==null に
            // else が無かったため、process death 復元中や削除済みの本で白画面デッドエンドになっていた。
            // uiState は Loading（DB 初回発行前）と Content（確定）を型で区別できるため、
            // 「まだ判らない（Loading）」と「本当に無い（Content かつ不在）」を分けて扱える。
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            when (val state = uiState) {
                // DB 初回発行前（cold start / process death 復元中）。白画面を出さず最小のローディングを描く。
                is BookshelfUiState.Loading -> ReadingLoadingPlaceholder(readingTheme = appTheme)
                is BookshelfUiState.Content -> {
                    val book = state.books.firstOrNull { it.id == bookId }
                    if (book != null) {
                        ReadingScreen(
                            // 境界: bookId は nav 引数由来の String＝読書画面へは型付き BookId へ包んで渡す
                            // （book.ncode→Ncode の包み方と同方針）。
                            bookId = BookId(bookId),
                            startFile = startFile,
                            htmlDirPath = book.htmlDirPath,
                            bookTitle = book.title,
                            // 紐付け確定/解除は uiState(hot StateFlow) 経由でここへ還流し、読書画面の
                            // 継続導線が再コンポーズで即座に切り替わる。
                            // 境界: book.ncode は Room 由来の String?。読書画面へは型付き Ncode? へ包んで渡す。
                            ncode = book.ncode?.let { Ncode(it) },
                            viewModel = viewModel,
                            readingTheme = appTheme,
                            onThemeChange = onThemeChange,
                            followingSystem = followingSystem,
                            onFollowSystem = onFollowSystem,
                            onNavigateToBookshelf = { navController.popBackStack("bookshelf", false) },
                        )
                    } else {
                        // 確定して本が存在しない（削除済み／復元不能）ケース。白画面デッドエンドを残さず、
                        // 既存のエラー画面（本棚へ戻る導線つき）を流用する（F-M）。意匠は発明しない。
                        ReadingErrorScreen(
                            message = "この書籍は見つかりませんでした",
                            colors = appTheme.colors,
                            onNavigateToBookshelf = { navController.popBackStack("bookshelf", false) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 読書画面の最小ローディング表示（F-M）。DB 初回発行前（cold start / process death 復元中）に
 * 白画面を出さないための場つなぎ。本棚スケルトンと同思想＝「未確定」を可視化し、Content 確定で
 * 本の有無に分岐する。意匠を発明しないため配色は読書テーマのトークン（background/accent）のみを使う。
 */
@Composable
private fun ReadingLoadingPlaceholder(readingTheme: ReadingTheme) {
    val colors = readingTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = colors.accent)
    }
}
