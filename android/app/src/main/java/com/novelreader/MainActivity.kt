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
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.ui.BookshelfScreen
import com.novelreader.ui.ReadingErrorScreen
import com.novelreader.ui.ReadingScreen
import com.novelreader.ui.discovery.DiscoveryGenreScreen
import com.novelreader.ui.discovery.DiscoveryHomeScreen
import com.novelreader.ui.discovery.DiscoveryResultScreen
import com.novelreader.ui.discovery.DiscoverySearchScreen
import com.novelreader.ui.discovery.NovelDetailScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 強制終了リカバリ（孤立HTML掃除＋未完了ジョブの通知・再開）。Activity 起動時に
        // 呼ぶのは FGS のバックグラウンド起動制限を避けるため（詳細は実装側の doc コメント）。
        // 実処理はプロセスごとに1回・IO スレッドで走る。
        (application as NovelReaderApplication).runStartupRecoveryOnce()

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
            val onThemeChange: (ReadingTheme) -> Unit = { theme ->
                appTheme = theme
                prefs.edit().putString("reading_theme", theme.name).apply()
            }

            // Material3 配色もテーマ3値（ライト/セピア/ダーク）へ追従させる。
            // 旧実装はセピア時にライト配色を流用しており、本棚・発見系で「ライトとセピアの
            // 色味に差がない」実機フィードバック（2026-07-07）の主因だった。読書側の固有色
            // （ReadingColors）とは別系統だが、同じ琥珀紙トーンに揃えている（Theme.kt 参照）。
            NovelReaderTheme(theme = appTheme) {
                NovelReaderApp(appTheme = appTheme, onThemeChange = onThemeChange)
            }
        }
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
) {
    val navController = rememberNavController()
    val viewModel: BookshelfViewModel = viewModel()
    // 発見系（ホーム/ジャンル/結果一覧）はクエリ文脈を画面間で受け渡すため単一VMを共有する。
    // ロードは ensureHomeLoaded の遅延型なので、ここで生成しても本棚起動時に通信は発生しない。
    val discoveryViewModel: DiscoveryViewModel = viewModel()

    NavHost(navController = navController, startDestination = "bookshelf") {

        composable("bookshelf") {
            BookshelfScreen(
                viewModel = viewModel,
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                onOpenBook = { bookId, startFile ->
                    navController.navigate("reading/$bookId/$startFile")
                },
                onOpenDiscovery = {
                    navController.navigate("discovery")
                },
            )
        }

        composable("discovery") {
            DiscoveryHomeScreen(
                viewModel = discoveryViewModel,
                onBack = { navController.popBackStack() },
                onOpenDetail = { ncode -> navController.navigate("discovery/detail/$ncode") },
                onOpenGenre = { navController.navigate("discovery/genre") },
                onPickBiggenre = { code, label ->
                    discoveryViewModel.openResult(
                        ResultContext(title = label, query = DiscoveryQuery(biggenres = setOf(code)), source = ResultSource.GENRE)
                    )
                    navController.navigate("discovery/result")
                },
                onOpenSearch = { navController.navigate("discovery/search") },
                onPickMood = { preset ->
                    discoveryViewModel.openResult(preset.toResultContext())
                    navController.navigate("discovery/result")
                },
            )
        }

        composable("discovery/search") {
            DiscoverySearchScreen(
                viewModel = discoveryViewModel,
                onBack = { navController.popBackStack() },
                onSearchExecuted = { navController.navigate("discovery/result") },
            )
        }

        composable("discovery/genre") {
            DiscoveryGenreScreen(
                onBack = { navController.popBackStack() },
                onPickBiggenre = { code, label ->
                    discoveryViewModel.openResult(
                        ResultContext(title = label, query = DiscoveryQuery(biggenres = setOf(code)), source = ResultSource.GENRE)
                    )
                    navController.navigate("discovery/result")
                },
                onPickGenre = { code, label ->
                    discoveryViewModel.openResult(
                        ResultContext(title = label, query = DiscoveryQuery(genres = setOf(code)), source = ResultSource.GENRE)
                    )
                    navController.navigate("discovery/result")
                },
            )
        }

        composable("discovery/result") {
            DiscoveryResultScreen(
                viewModel = discoveryViewModel,
                onBack = { navController.popBackStack() },
                onOpenDetail = { ncode -> navController.navigate("discovery/detail/$ncode") },
            )
        }

        composable(
            route = "discovery/detail/{ncode}",
            arguments = listOf(navArgument("ncode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val ncode = backStackEntry.arguments?.getString("ncode") ?: return@composable
            NovelDetailScreen(
                ncode = ncode,
                viewModel = viewModel(),
                onKeywordTap = { kw ->
                    discoveryViewModel.openResult(ResultContext(
                        title = "「$kw」", subtitle = "キーワードから",
                        source = ResultSource.KEYWORD,
                        query = DiscoveryQuery(word = kw, inKeyword = true),
                    ))
                    navController.navigate("discovery/result") {
                        // why: resultContext は VM 単一保持のため、result をスタックに重ねると戻ったとき別の結果が表示される。popUpTo で result を常に1枚に保ち、状態と画面スタックを整合させる。
                        popUpTo("discovery/result") { inclusive = true }
                    }
                },
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
                            bookId = bookId,
                            startFile = startFile,
                            htmlDirPath = book.htmlDirPath,
                            bookTitle = book.title,
                            // 紐付け確定/解除は uiState(hot StateFlow) 経由でここへ還流し、読書画面の
                            // 継続導線が再コンポーズで即座に切り替わる。
                            ncode = book.ncode,
                            viewModel = viewModel,
                            readingTheme = appTheme,
                            onThemeChange = onThemeChange,
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
