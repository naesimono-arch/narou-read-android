package com.novelreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.novelreader.data.AppDatabase
import com.novelreader.ui.BookshelfScreen
import com.novelreader.ui.ReadingScreen
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.viewmodel.BookshelfViewModel
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge 表示を有効化（ステータスバー・ナビバー領域までコンテンツを描画）
        // NovelReaderTheme 内で WindowCompat.getInsetsController を使うため、
        // setDecorFitsSystemWindows は setContent より前に呼ぶ必要がある
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Chaquopy の初期化（アクティビティのコンテキストが必要）
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            NovelReaderTheme {
                NovelReaderApp()
            }
        }
    }
}

@Composable
private fun NovelReaderApp() {
    val navController = rememberNavController()
    val viewModel: BookshelfViewModel = viewModel()

    NavHost(navController = navController, startDestination = "bookshelf") {

        composable("bookshelf") {
            BookshelfScreen(
                viewModel = viewModel,
                onOpenBook = { bookId, startFile ->
                    navController.navigate("reading/$bookId/$startFile")
                },
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

            // Room から htmlDirPath を非同期で取得（null = ロード中）
            var htmlDirPath by remember(bookId) { mutableStateOf<String?>(null) }
            LaunchedEffect(bookId) {
                htmlDirPath = AppDatabase.getDatabase(navController.context)
                    .bookDao()
                    .getAllBooks()
                    .first()
                    .firstOrNull { it.id == bookId }
                    ?.htmlDirPath
            }

            if (htmlDirPath != null) {
                ReadingScreen(
                    bookId = bookId,
                    startFile = startFile,
                    htmlDirPath = htmlDirPath!!,
                    viewModel = viewModel,
                    onNavigateToBookshelf = { navController.popBackStack("bookshelf", false) },
                )
            }
        }
    }
}
