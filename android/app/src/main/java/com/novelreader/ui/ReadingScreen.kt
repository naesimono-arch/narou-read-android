package com.novelreader.ui

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.novelreader.viewmodel.BookshelfViewModel
import java.io.File

/** WebView を用いた読書画面。 */
@Composable
fun ReadingScreen(
    bookId: String,
    startFile: String,
    htmlDirPath: String,
    viewModel: BookshelfViewModel,
    onNavigateToBookshelf: () -> Unit,
) {
    // 本棚に戻る特殊 URL（html_exporter.py と一致させること）
    val bookshelfUrl = "https://novelreader.app/bookshelf"

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    @Suppress("SetJavaScriptEnabled")
                    allowFileAccess = true
                    // file:// オリジン間アクセスを許可（ローカルファイルのみ使用）
                    @Suppress("deprecation")
                    allowUniversalAccessFromFileURLs = true
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()

                        // 本棚へ戻るリンクを傍受
                        if (url == bookshelfUrl) {
                            onNavigateToBookshelf()
                            return true
                        }

                        // chap_N.html への遷移を傍受して進捗保存
                        val fileName = request.url.lastPathSegment ?: return false
                        if (fileName.startsWith("chap_") && fileName.endsWith(".html")) {
                            viewModel.saveProgress(bookId, fileName)
                        }
                        return false
                    }
                }

                // 初回ロード
                val startPath = File(htmlDirPath, startFile).absolutePath
                loadUrl("file://$startPath")
            }
        },
    )
}
