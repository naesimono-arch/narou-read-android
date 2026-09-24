package com.novelreader.ui.discovery

import android.annotation.SuppressLint
import android.os.Bundle
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelreader.narou.model.Ncode
import com.novelreader.narou.narouWorkUrl
import com.novelreader.viewmodel.PdfImportEvent
import com.novelreader.viewmodel.PdfImportUiState
import com.novelreader.viewmodel.PdfImportViewModel
import com.novelreader.ui.intro.IntroGroup
import com.novelreader.ui.intro.LocalIntroController
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.theme.FontTopBarTitle
import com.novelreader.ui.theme.Spacing

/**
 * scrollIntoView の寄せ位置（`block` オプション）。**スクロール後の見えは、ここ1行の差し替えで切り替わる。**
 *
 * 【なぜ center か】引数なしの `scrollIntoView()` は既定が `block:"start"`＝対象要素の**上端をビューポート上端へ
 * 貼り付ける**ため、目的地のフォームが画面の一番端に来て「どこへ着いたのか分かりにくい」（2026-09-04 実機所感）。
 * 中央に置けば上下に地の文脈（目次の末尾・フォーム下のフッタ）が残り、着地点として読み取りやすい。
 * 【代替 nearest を残す理由】.c-under-nav はフォーム＋ボタンの塊で高さがあり、center は**要素の中心**を画面中心へ
 * 合わせる＝要素高がビューポートより大きいと上端の「縦書きPDF」見出しが画面外へ切れうる。実機で切れて見えたら
 * "nearest"（既に見えていれば動かさず、外に在るときだけ最小移動で収める）へ倒す。
 * 【なぜ2つの JS で定数を共有するか】主経路とフォールバックで寄せ位置が食い違うと、どちらが走ったかで見えが変わる
 * ——そしてどちらが走ったかは実機からは判別できない。2箇所に literal を書かず、必ずここを参照させる。
 * 【規約】block は「ビューポートをどう寄せるか」の指定にすぎず CSS 注入でも DOM 改変でもない＝ADR 0010/0011 の線の内側。
 */
private const val AUTO_SCROLL_BLOCK = "center"

/**
 * 目次到達時に PDF 生成フォーム(.c-under-nav)へビューポートを寄せる注入 JS（onPageCommitVisible 主経路用）。
 *
 * 【なぜ要素出現駆動か】旧実装は onPageFinished（画像・広告含む全リソース読込完了）でのみ scrollIntoView して
 * いたため、重い目次ページでは全読込までの数秒がまるごとスクロール開始ラグになっていた。初描画時点で注入し、
 * 対象要素が既に DOM に在れば即スクロール、無ければ MutationObserver で出現を待って1回だけ寄せることで、
 * 全読込を待たずに体感ラグを消す。
 * 【冪等化】window.__nrAutoScrollDone フラグで onPageFinished フォールバックとの二重スクロールを防ぐ。
 * 【監視リーク防止】要素が来なくても10秒で必ず observer.disconnect()（DOM 常駐の監視を残さない）。
 * 【規約厳守】ビューポート移動(scrollIntoView)のみ。CSS 注入・DOM 改変・広告除去は ADR 0010/0011 違反。
 */
private const val AUTO_SCROLL_JS_ON_VISIBLE = """
(function(){
  if (window.__nrAutoScrollDone) return;
  function tryScroll(){
    if (window.__nrAutoScrollDone) return true;
    var el = document.querySelector('.c-under-nav');
    if (el) { el.scrollIntoView({block:'$AUTO_SCROLL_BLOCK'}); window.__nrAutoScrollDone = true; return true; }
    return false;
  }
  if (tryScroll()) return;
  if (window.__nrAutoScrollObserving) return;
  window.__nrAutoScrollObserving = true;
  var obs = new MutationObserver(function(){ if (tryScroll()) obs.disconnect(); });
  obs.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(function(){ obs.disconnect(); }, 10000);
})();
"""

/**
 * onPageFinished フォールバック用 JS。onPageCommitVisible 非対応環境・注入失敗への防御。
 * window.__nrAutoScrollDone を見て冪等（主経路が既にスクロール済みなら何もしない）。
 */
private const val AUTO_SCROLL_JS_FALLBACK = """
(function(){
  if (window.__nrAutoScrollDone) return;
  var el = document.querySelector('.c-under-nav');
  if (el) { el.scrollIntoView({block:'$AUTO_SCROLL_BLOCK'}); window.__nrAutoScrollDone = true; }
})();
"""

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
    // 目次ページ判定用の URL スラッグ形 ncode（narouWorkUrl も同じ Ncode.urlSlug でパスを組むため WebView の url と一致する）。
    val lowerNcode = remember(ncode) { ncode.urlSlug }
    // 目次ページ URL 判定用の正規表現。onPageCommitVisible と onPageFinished の双方で使うため hoist（重複回避）。
    val menuUrlRegex = remember(lowerNcode) { Regex("^https://ncode\\.syosetu\\.com/$lowerNcode/?$") }

    // ── 教示「はじめに」組D（1 枚）＝この画面を初めて開いたときだけ出す（正本 §2/§3・2026-09-04 反転）──
    // null＝教示のホストが居ない構成（プレビュー・個別画面だけの Robolectric）＝何も出さない。
    val introController = LocalIntroController.current
    // カードを出す契機。**背景が描き切ってから出す**（正本 §8「置きかた」）に対して、この画面で取れる最良点。
    // 【なぜ onPageCommitVisible＋menuUrlRegex 一致か】他の組は着地の信号を持つ（組B＝deferHeavyContent が
    // 閉じた／組C＝push 遷移窓が閉じた）が、この画面の背景は WebView で、**Kotlin 側は自動送りが終わったことを
    // 知らない**（AUTO_SCROLL_JS_* を evaluateJavascript へ投げっぱなしで結果コールバックを受けていない）。
    // 目次ページの**初描画**は取れるので、自動送りの JS を投げるのと同じ瞬間をカードの契機にする。
    // 【担保できること】①なろうの目次ページに着いた後にしか出ない（作品ページ以外の多段フロー中には出ない）
    //   ②白いままの画面には載らない（onPageCommitVisible＝最初のピクセルが出た後）。
    // 【担保できないこと】**カードが載る瞬間、背後はまだ目次の上の方**のことがある——scrollIntoView が
    //   走り切ったかを知る手段が無いため。カード文言「開いた位置にある ［縦書きPDF］ の枠から」は
    //   **閉じた後の状態**を指しており、スクロールはカードの下で進むので実害は出ない想定だが、
    //   これはモックでは確かめられない＝実機の二段検分で見る（visual-language 恒久ルール5）。
    // 【なぜこれ以上踏み込まないか】JS からコールバックを返せば「寄せ終わった」を取れるが、それは注入 JS の
    //   役割を〈ビューポート移動のみ〉から広げる話＝ADR 0010/0011 の線に触れる。踏み込むなら別 ADR。
    val menuPageCommitted = remember { mutableStateOf(false) }
    LaunchedEffect(menuPageCommitted.value) {
        // requestAuto 自身が〈未消費か〉〈他のカードを出していないか〉を見るので、ここで条件を重ねない
        // （重ねると同じ規則が 2 箇所に散り、片方だけ直されて腐る）。再訪でも true のままなので発火は 1 回。
        if (menuPageCommitted.value) introController?.requestAuto(IntroGroup.IMPORT)
    }

    // 構成変更（回転・ダーク切替・fontScale 変更）で Activity が再生成されると WebView も破棄される。
    // 旧実装は plain remember＋無条件 loadUrl(menuUrl) だったため、なろうの多段フロー
    // （作品ページ→縦書きPDF→書式設定→生成）の途中で構成変更が起きると作品ページ先頭へ巻き戻っていた。
    // WebView.saveState/restoreState で「履歴スタック（＝フローのどこに居るか）」ごと持ち回る
    // （隣の WebReaderScreen が 2026-07-12 persist Major で塞いだ同機序の移植。以下の判断も同源）。
    // 【規約厳守（ADR 0010/0011）】saveState/restoreState はネイティブ WebView の状態シリアライズ API であり、
    // evaluateJavascript でも DOM 改変でもない＝注入 JS を scrollIntoView に限る規約を一切侵さない。
    // なぜ custom Saver でライブの WebView から取り出すか: 状態保存フェーズ（onSaveInstanceState 経由）は
    // onDispose より前に走るため、onDispose で Bundle へ書いても初回の構成変更に間に合わない。Saver.save を
    // 「保存フェーズ時点で生存中の WebView へ saveState する」形にして、その時点の最新状態を確実に捕える。
    // なぜ空 Bundle をセンチネルにするか: rememberSaveable の型パラメータは T : Any（null 不可）のため
    // 「未保存＝null」が表現できない。「未保存＝空 Bundle」で代替し、消費側は isEmpty で初回判定する
    // （WebView 不在/saveState 失敗時も空のまま＝安全側で menuUrl ロードに落ちる）。
    val restoredState = rememberSaveable(
        saver = Saver<Bundle, Bundle>(
            save = { webViewHolder.value?.let { wv -> Bundle().apply { wv.saveState(this) } } ?: Bundle() },
            restore = { it },
        )
    ) { Bundle() }

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
    // PredictiveBackHandler にしない理由: WebView の履歴 pop に進捗連動で描けるプレビュー面が無い
    //（goBack は確定時に一括で走る）。確定時発火の BackHandler が意味的に正しい。
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
                            // 初描画時点で注入する主経路。目次ページ（ncode.syosetu.com/<ncode>/ 形）到達時のみ発火。
                            // 【なぜ onPageCommitVisible か】旧実装は onPageFinished（画像・広告含む全リソース読込完了）で
                            // のみ scrollIntoView していたため、重い目次ページでは全読込までの数秒がまるごとスクロール開始
                            // ラグになっていた。初描画時点で注入し「対象要素の DOM 出現」を駆動源に変えることで全読込を待たない。
                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                if (url != null && menuUrlRegex.matches(url)) {
                                    view?.evaluateJavascript(AUTO_SCROLL_JS_ON_VISIBLE, null)
                                    // 教示カード（組D）の契機。ここは UI スレッドなので snapshot state を直接触れる。
                                    // requestAuto を直接呼ばず state 経由にするのは、コントローラを触るのを
                                    // コンポジション側（LaunchedEffect）に寄せるため＝他の入口と同じ形にする。
                                    menuPageCommitted.value = true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                // フォールバック（温存）: onPageCommitVisible が呼ばれない環境・注入失敗への防御。
                                // JS 側の window.__nrAutoScrollDone フラグで主経路と冪等化（二重スクロールしない）。
                                // 【規約厳守】注入 JS はビューポート移動(scrollIntoView)のみ。CSS 注入・DOM 改変・
                                // 広告除去は ADR 0010/0011 の規約違反のため絶対に足さない。
                                if (url != null && menuUrlRegex.matches(url)) {
                                    view?.evaluateJavascript(AUTO_SCROLL_JS_FALLBACK, null)
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
                        // 復元状態があれば履歴スタックごと復元（構成変更でPDF生成フローが巻き戻るのを防ぐ）。
                        // 無ければ初回として作品ページをロードする。復元後に目次ページが再描画された場合も
                        // 自動スクロール注入は menuUrlRegex 判定＋__nrAutoScrollDone 冪等化で従来どおり安全。
                        if (!restoredState.isEmpty) restoreState(restoredState) else loadUrl(menuUrl)
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
                        .padding(Spacing.S24)
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
