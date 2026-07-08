package com.novelreader.spike

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * PDF取り込み導線 案B の使い捨て技術検証スパイク。
 *
 * 検証したい唯一の問い: なろう公式の縦書きPDF DL 多段フロー
 *   目次ページ → PDF作成POST → 生成完了ページ → DLリンク(XHR) → 実体 pdfnovels.net の PDF
 * の末端で WebView の setDownloadListener が最終 PDF URL で発火するか。
 *
 * 発火しない場合に備え、1回の実機セッションで診断材料を全部取れるよう観測点を全て仕込む
 * （shouldOverrideUrlLoading / onPageStarted/Finished / shouldInterceptRequest / onConsoleMessage）。
 *
 * これは検証専用の使い捨てコードであり debug ソースセット限定。本番 APK には混入しない。
 * タップ操作（PDF作成ボタン→DLボタン）はユーザーが手動で行う。スパイクは操作を自動化しない。
 */
class PdfDownloadSpikeActivity : Activity() {

    // logcat タグは統一（`adb logcat -s PdfSpike` で全観測点を一括収集するため）。
    private val tag = "PdfSpike"

    // SuppressLint("SetJavaScriptEnabled"): なろうの PDF 生成フローは JS 必須（フォーム送信・XHR 中間ページ・
    // 自動スクロール）のため JS 有効化は本検証の前提。使い捨て debug スパイクなので lint 抑制で妥当。
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // extra 未指定時のデフォルトは n2959ki（ゴールデン検証で使う既知作品）。
        val ncode = (intent?.getStringExtra("ncode") ?: "n2959ki").lowercase()
        val menuUrl = "https://ncode.syosetu.com/$ncode/"

        val webView = WebView(this)
        // 全画面表示するだけの最小構成（Compose 不要）。
        setContentView(webView)

        webView.settings.apply {
            // JS 有効: PDF 生成フォーム送信・XHR 中間ページ・自動スクロールの全てが JS 依存のため。
            javaScriptEnabled = true
            // DOM storage 有効: なろう側が sessionStorage/localStorage を使う可能性に備える（多段トークン制のため）。
            domStorageEnabled = true
            // User-Agent はデフォルトのまま（要件どおり。UA を偽装すると挙動が実機と乖離し検証にならない）。
        }

        webView.webViewClient = object : WebViewClient() {

            // ページ遷移の観測。PDF 作成 POST → 生成完了ページ → DLリンクへの遷移を追跡する。
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                Log.i(tag, "shouldOverrideUrlLoading: ${request?.url}")
                // false=WebView 自身に読み込ませる（横取りしない。観測のみ）。
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.i(tag, "onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(tag, "onPageFinished: $url")

                // 目次ページ（ncode.syosetu.com/<ncode>/ 形）に到達したときだけ自動スクロールを注入。
                // PDF作成フォームは div.c-under-nav 内にあり、名前付きアンカーが無いため JS スクロールが要る。
                //
                // 【重要】注入 JS はビューポート移動(scrollIntoView)のみに厳格限定する。
                // なぜ: CSS 注入・DOM 改変・広告除去はなろうの規約違反になるため絶対禁止。
                // ここでスクロール以外の DOM 操作を足すと規約リスクが生じる。
                if (url != null && Regex("^https://ncode\\.syosetu\\.com/$ncode/?$").matches(url)) {
                    view?.evaluateJavascript(
                        "document.querySelector('.c-under-nav')?.scrollIntoView();",
                        null
                    )
                    Log.i(tag, "injected scrollIntoView(.c-under-nav) on menu page")
                }
            }

            // リクエスト観測（横取りはせず null を返す）。全リクエストをログすると洪水になるため、
            // pdfnovels.net または novelpdf を含むものだけに絞る（PDF DL 経路の中核だけを可視化）。
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (reqUrl != null && (reqUrl.contains("pdfnovels.net") || reqUrl.contains("novelpdf"))) {
                    Log.i(tag, "shouldInterceptRequest: method=${request.method} url=$reqUrl")
                }
                // null=横取りせず WebView に通常処理させる（観測専用）。
                return null
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // JS コンソール観測。blob:/a[download] 方式で DL される場合はここに痕跡が出る可能性があるため。
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.i(
                    tag,
                    "console: ${consoleMessage?.message()} " +
                        "@${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}"
                )
                return true
            }
        }

        // 本丸: 多段フロー末端で最終 PDF URL(pdfnovels.net/...) を掴むか。全パラメータをログ。
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            Log.i(
                tag,
                "setDownloadListener FIRED\n" +
                    "  url=$url\n" +
                    "  userAgent=$userAgent\n" +
                    "  contentDisposition=$contentDisposition\n" +
                    "  mimetype=$mimetype\n" +
                    "  contentLength=$contentLength"
            )
        }

        Log.i(tag, "loading menu page: $menuUrl")
        webView.loadUrl(menuUrl)
    }
}
