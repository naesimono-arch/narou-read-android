package com.novelreader.ui.discovery

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.narouEpisodeUrl
import com.novelreader.narou.narouWorkUrl
import com.novelreader.narou.parseNarouEpisodeNumber
import com.novelreader.viewmodel.WebReaderViewModel

/**
 * なろう作品をアプリ内 WebView で読む画面（機能②・ADR 0012）。
 *
 * 目的: Custom Tabs では観測できない「今どの話を開いているか」を WebView の onPageFinished の**URL**から
 * 割り出し、読書位置(web_reading_progress)へ自動記録する。二度目以降はこの記録した話へ直接着地して再開する。
 *
 * 【規約 厳守（ADR 0010/0012）】この読書 WebView は取り込み用（PdfImportScreen）と違い**JS を一切注入しない**
 * （scrollIntoView すら行わない）。なろうの閲覧ページを**加工せずそのまま**表示し（広告も含め）、アプリが触る
 * のは onPageFinished で渡る URL 文字列だけ（URL 観測＝「本文の機械的取得」でも「ページ加工」でもない）。
 * ここに CSS 注入・DOM 改変・広告除去・evaluateJavascript を足すことは規約違反のため絶対にしないこと。
 *
 * 設計方針（route/Content の stateless 分割をあえて採らない理由）: 実体は AndroidView(WebView)＝命令的 View で
 * インスタンス保持・戻る制御が描画と不可分（PdfImportScreen と同じ判断）。VM(Room 記録)と View(WebView)の
 * 責務分離は保つ。
 *
 * @param ncode 読む作品の Nコード。
 * @param startEpisode 起動時に開く話数。0 以下＝作品トップ(目次)。>0＝その話へ直接着地（続きから再開）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebReaderScreen(
    ncode: Ncode,
    startEpisode: Int,
    viewModel: WebReaderViewModel,
    onBack: () -> Unit,
) {
    // WebView インスタンスへの参照。BackHandler の goBack と、再コンポーズを跨いだ保持に使う。
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    // 起動時に開く URL。startEpisode>0 なら該当話へ直接（続きから再開）、0 以下なら目次。
    val startUrl = remember(ncode, startEpisode) {
        if (startEpisode > 0) narouEpisodeUrl(ncode, startEpisode) else narouWorkUrl(ncode)
    }

    // WebView はネイティブリソースを持ち、AndroidView はコンポジション離脱時に View をツリーから外すだけで
    // destroy() を呼ばないため、明示破棄しないと画面を閉じてもネイティブ側が残りリークする。ここで確実に破棄する。
    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.destroy()
            webViewHolder.value = null
        }
    }

    // なろうは「次へ」等でページ内遷移するため、システム back はまず WebView 履歴を戻す。履歴が無ければ画面 pop。
    BackHandler {
        val wv = webViewHolder.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "なろうで読む", fontSize = 17.sp) },
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
                        // なろうの閲覧ページ描画に JS・DOM storage が要る（取り込み画面と同前提）。ただし本画面は
                        // 【規約厳守】注入・改変を一切しない＝ページはなろうの提供そのまま（広告含む）を表示するだけ。
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // User-Agent はデフォルトのまま（偽装しない＝実機の実挙動と乖離させない）。

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                // 【規約厳守】ここで evaluateJavascript / DOM 改変 / 広告除去は絶対にしない。
                                // URL 文字列から話数を割り出して読書位置に記録するだけ（URL 観測＝加工に当たらない）。
                                // 話ページ(.../<ncode>/N/)以外（目次・感想・外部リンク）は parse が null を返し記録しない。
                                //
                                // 戻り遷移（goBack で履歴を遡って表示したページ）は記録しない: onPageFinished は
                                // goBack でも再発火するため、読み進めた後に戻る連打で退出すると記録がセッション
                                // 先頭話へ巻き戻る（2026-07-11 実機で2作品再現・ADR 0012 追補）。判定は「戻り操作中」
                                // フラグでなく履歴スタック位置の構造判定（currentIndex が末尾でない＝forward 履歴が
                                // 残っている＝戻りで到達）。新しいリンクを踏めば forward 履歴は切り詰められ末尾に
                                // 戻るので、目次等から意図的に開き直した話は従来どおり記録される。フラグ方式は
                                // 戻る連打時に onPageFinished が発火分と一致せず抑制の漏れ/残留が起きるため不採用。
                                if (url != null && view != null) {
                                    val history = view.copyBackForwardList()
                                    val reachedByBack = history.currentIndex < history.size - 1
                                    if (!reachedByBack) {
                                        parseNarouEpisodeNumber(url, ncode)?.let { episode ->
                                            viewModel.onEpisodeReached(ncode, episode)
                                        }
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false // 横取りせず WebView 自身に読ませる（なろう内の話遷移をそのまま辿らせる）。
                        }

                        webViewHolder.value = this
                        loadUrl(startUrl)
                    }
                },
            )
        }
    }
}
