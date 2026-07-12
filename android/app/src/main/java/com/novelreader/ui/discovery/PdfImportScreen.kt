package com.novelreader.ui.discovery

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.narouWorkUrl
import com.novelreader.viewmodel.PdfImportEvent
import com.novelreader.viewmodel.PdfImportUiState
import com.novelreader.viewmodel.PdfImportViewModel
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.FontTopBarTitle

/**
 * 縦書きPDF取り込み画面（ADR 0011・案B）。取り込み専用の使い捨て WebView を1画面だけ持つ。
 *
 * 設計方針（route/Content の stateless 分割をあえて採らない理由）:
 *   既存の VM 直結画面は「route が副作用と collect を持ち、Content が stateless 描画」に分けるが、
 *   本画面の実体は AndroidView（WebView）＝命令的 View であり、WebView インスタンスの保持・戻る制御・
 *   setDownloadListener 配線が描画と不可分に絡む。stateless な Content へ切り出すと WebView の
 *   ライフサイクルと state を跨いで持ち回る不自然さが増すため、実用上ここでは1関数にまとめる。
 *   VM（ネットワーク副作用）と View（WebView）の責務分離は保っている。
 *
 * 規約（ADR 0010/0011 厳守）: 注入する JS はビューポート移動（scrollIntoView）のみ。
 * CSS 注入・DOM 改変・広告除去は規約違反のため絶対に足さないこと。
 *
 * @param onImportStarted 取り込みを Service へ投入した後に呼ぶ（画面を pop する）。
 * @param onBack システム back で WebView 履歴が無いとき、および戻る操作で画面を pop する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PdfImportScreen(
    ncode: Ncode,
    viewModel: PdfImportViewModel,
    onImportStarted: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // WebView インスタンスへの参照。BackHandler の goBack と、再コンポーズを跨いだ保持に使う。
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    val menuUrl = remember(ncode) { narouWorkUrl(ncode) }
    // 目次ページ判定用に小文字化した ncode（narouWorkUrl も小文字でパスを組むため onPageFinished の url と一致する）。
    val lowerNcode = remember(ncode) { ncode.value.trim().lowercase() }

    // 取り込み開始イベント: Toast を出して画面を閉じる（一度きり）。
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PdfImportEvent.ImportStarted -> {
                    Toast.makeText(context, "取り込みを開始しました（本棚で進捗表示）", Toast.LENGTH_SHORT).show()
                    onImportStarted()
                }
            }
        }
    }

    // WebView はネイティブリソースを持ち、AndroidView はコンポジション離脱時に View をツリーから外すだけで
    // destroy() を呼ばないため、明示破棄しないと画面を閉じてもネイティブ側が残りリークする。ここで確実に破棄する。
    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.destroy()
            webViewHolder.value = null
        }
    }

    // 多段フロー（目次→生成→completed）のため、システム back はまず WebView 履歴を戻す。履歴が無ければ画面 pop。
    BackHandler {
        val wv = webViewHolder.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "縦書きPDFを取り込む", fontSize = FontTopBarTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webViewHolder.value
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // なろうの PDF 生成フローは JS・DOM storage 必須（実機スパイクで確定＝ADR 0011「スパイク結果」）。
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // User-Agent はデフォルトのまま（ADR 0011。偽装すると挙動が実機と乖離する）。

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                // 目次ページ（ncode.syosetu.com/<ncode>/ 形）到達時のみ PDF フォーム位置へ自動スクロール。
                                // 【規約厳守】注入 JS はビューポート移動(scrollIntoView)のみ。CSS 注入・DOM 改変・
                                // 広告除去は ADR 0010/0011 の規約違反のため絶対に足さない。
                                if (url != null &&
                                    Regex("^https://ncode\\.syosetu\\.com/$lowerNcode/?$").matches(url)
                                ) {
                                    view?.evaluateJavascript(
                                        "document.querySelector('.c-under-nav')?.scrollIntoView();",
                                        null
                                    )
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false // 横取りせず WebView 自身に読ませる（多段フローをそのまま辿らせる）。
                        }

                        // DL 捕捉（ADR 0011 スパイクで発火確定）。最終 PDF URL・UA・Content-Disposition と
                        // 当該 URL の Cookie を VM へ渡す（PDF 判定・OkHttp DL・取り込み合流は VM 側）。
                        setDownloadListener { downloadUrl, userAgent, contentDisposition, _, _ ->
                            val cookie = runCatching { CookieManager.getInstance().getCookie(downloadUrl) }.getOrNull()
                            viewModel.onDownloadRequested(
                                url = downloadUrl,
                                userAgent = userAgent,
                                contentDisposition = contentDisposition,
                                cookie = cookie,
                                ncode = ncode,
                            )
                        }

                        webViewHolder.value = this
                        loadUrl(menuUrl)
                    }
                },
            )

            // DL 中インジケータ（半透明のオーバーレイは付けず、中央スピナーのみ＝動く最小）。
            if (uiState is PdfImportUiState.Downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 失敗時は文言を出して画面に残留させる（DL ボタン再タップで再試行可能）。
            (uiState as? PdfImportUiState.Error)?.let { error ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = error.message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = FontSubTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
