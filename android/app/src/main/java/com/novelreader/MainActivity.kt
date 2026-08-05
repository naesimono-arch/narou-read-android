package com.novelreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import com.novelreader.ui.tabs.TabPagerHost
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.novelreader.model.BookId
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.webNewEpisodeMarkKey
import com.novelreader.narou.model.Ncode
import com.novelreader.review.PlayReviewPrompter
import com.novelreader.scrape.SiteAdapterRegistry
import com.novelreader.ui.BookshelfScreen
import com.novelreader.ui.ReadingErrorScreen
import com.novelreader.ui.ReadingScreen
import com.novelreader.ui.WardrobeScreen
import com.novelreader.ui.discovery.DiscoveryGenreScreen
import com.novelreader.ui.discovery.DiscoveryHomeScreen
import com.novelreader.ui.discovery.DiscoveryResultScreen
import com.novelreader.ui.discovery.DiscoverySearchScreen
import com.novelreader.ui.discovery.NovelDetailScreen
import com.novelreader.ui.discovery.PdfImportScreen
import com.novelreader.ui.discovery.WebReaderScreen
import com.novelreader.ui.skins.k.KBottomNav
import com.novelreader.ui.skins.k.KTab
import com.novelreader.ui.skins.k.SettingsScreenK
import com.novelreader.ui.skins.m.LocalSkyParallax
import com.novelreader.ui.skins.m.SkyBackdropM
import com.novelreader.ui.skins.m.SkyParallaxController
import com.novelreader.ui.skins.m.SkyParallaxFactor
import com.novelreader.ui.theme.MotionDurationKTabSwitch
import com.novelreader.ui.theme.MotionDurationNavTransition
import com.novelreader.ui.theme.MotionDurationSeizuFadeIn
import com.novelreader.ui.theme.MotionDurationSeizuFadeInDelay
import com.novelreader.ui.theme.MotionDurationSeizuFadeOut
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.skinFromName
import com.novelreader.ui.theme.rememberReadingColors
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

    // P3 取込導線: 共有(ACTION_SEND)/対応サイトのリンク(ACTION_VIEW)で渡された Web 小説 URL。
    // deepLinkBookId と同じ流儀（onCreate/onNewIntent で更新→Compose が消費して null 戻し）で扱う。
    // なぜ State か: singleTop で稼働中に onNewIntent 経由で新しい共有が届くため、値変化を Compose ツリーへ伝える。
    private val pendingWebImportUrl = mutableStateOf<String?>(null)

    // 背面 PDF 取込の「取込済み（上書き確認待ち）」通知タップ→上書き確認ダイアログへの直接テレポート要求
    // （U1 残り 2026-08-06・機序と extras 設計の why は OverwriteConfirmTeleport）。deepLinkBookId と
    // 同型の消費流儀（onCreate/onNewIntent で立て→Compose の着地エフェクトが消費して false 戻し）。
    private val pendingOverwriteConfirmNav = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知（プロセス再生成を伴う cold start 含む）からの deep link 対象を取り込む。
        // savedInstanceState==null に限定する理由: 構成変更（回転）や process death 復元では
        // 同じ起動 Intent が再配達され getStringExtra が再び非 null になるため、毎回 deep link を
        // 再発火してしまう（復元中の読書画面を勝手に置き換える）。初回生成のみ処理し、以後の
        // 復元は Navigation のバックスタック復元に委ねる。稼働中の再タップは onNewIntent が拾う。
        if (savedInstanceState == null) {
            deepLinkBookId.value = intent?.getStringExtra(EXTRA_BOOK_ID)
            // P3: 共有/リンクからの Web 小説取込対象を取り込む。EXTRA_BOOK_ID と同じ理由で savedInstanceState==null
            // に限定する＝構成変更（回転）や process death 復元では同じ起動 Intent が再配達され、
            // 制限しないと取込を二重発火してしまう（初回生成のみ処理し、以後の再処理は消費フラグで防ぐ）。
            pendingWebImportUrl.value = extractWebImportUrl(intent)
            // 上書き確認テレポート（cold start 経路）。savedInstanceState==null 限定の理由は上2つと同じ
            // ＝復元時の再配達 Intent で着地を再発火させない（復元中の深い画面を勝手に畳まない）。
            pendingOverwriteConfirmNav.value = OverwriteConfirmTeleport.isRequested(intent)
        }

        // 強制終了リカバリ（孤立HTML掃除＋未完了ジョブの通知・再開）。Activity 起動時に
        // 呼ぶのは FGS のバックグラウンド起動制限を避けるため（詳細は実装側の doc コメント）。
        // 実処理はプロセスごとに1回・IO スレッドで走る。
        (application as NovelReaderApplication).runStartupRecoveryOnce()

        // 設定スキーマ版を記録（evolve・予防的）。将来 ReadingTheme 等の enum を改名した際、
        // 保存済みの「生 enum 名」がどの版の綴りかを移行コードが判別できるようにするための版番号。
        // 未記録の既存/新規インストールは現行スキーマ＝v1 として一度だけ刻む（以後の移行がこの版を
        // 読んで綴りを変換する）。app_prefs は他の読書設定（reading_theme 等）と同じ置き場。
        val settingsPrefs = getSharedPreferences(PrefKeys.FILE_APP_PREFS, MODE_PRIVATE)
        if (!settingsPrefs.contains(PrefKeys.SETTINGS_SCHEMA_VERSION)) {
            settingsPrefs.edit().putInt(PrefKeys.SETTINGS_SCHEMA_VERSION, SETTINGS_SCHEMA_VERSION).apply()
        }

        // Edge-to-Edge 表示を有効化（ステータスバー・ナビバー領域までコンテンツを描画）
        // NovelReaderTheme 内で WindowCompat.getInsetsController を使うため、
        // setDecorFitsSystemWindows は setContent より前に呼ぶ必要がある
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 没入トグルの上端ちらつき対策（幾何側の根治・2026-07-16）: カットアウト（実機実測=上端160pxの
        // パンチホール帯）は既定モードだと「ステータスバー表示中のみ」アプリ描画が入る＝読書の没入トグルで
        // systemBars を hide/show するたびに上端 160px が letterbox⇄アプリ描画で伸縮し、その window リサイズ
        // 過渡フレームがちらつきとして見える（window 背景をテーマ色にした fc27ce2 は色差の緩和のみで伸縮は残存）。
        // 常時カットアウト帯まで描画するモードへ固定し、バー出没で window 幾何が一切変わらないようにする。
        // ALWAYS は API30+（横画面の長辺カットアウトにも効く）・28-29 は SHORT_EDGES（縦画面はこれで同等）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContent {
            // 見た目テーマの正本を MainActivity へ巻き上げ、本棚(NovelReaderTheme)と読書(ReadingScreen)で
            // 単一の状態を共有する。これにより設定シート/本棚どちらで変えても全体が同期する。
            // （旧: 本棚=システム追従・読書=独立prefの2系統で不一致だった＝handover B「11 本棚テーマ追従」を解消）
            // 既定: reading_theme 未保存時はシステムのライト/ダークに追従。以後はユーザー選択を永続。
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var appTheme by remember { mutableStateOf(loadInitialTheme(prefs, systemDark)) }
            // 「システムに従う」状態＝reading_theme 未保存。明示選択で解除、追従選択でキー削除により復帰。
            // なぜキー削除で表すか: 「未宣言＝追従」という loadInitialTheme の既存規約をそのまま正本にし、
            // 第4の enum 値や別フラグを増やさない（設定は「開かれない」が理想＝UX/19）。
            var followingSystem by remember { mutableStateOf(prefs.getString(PrefKeys.READING_THEME, null) == null) }
            val onThemeChange: (ReadingTheme) -> Unit = { theme ->
                appTheme = theme
                followingSystem = false
                prefs.edit().putString(PrefKeys.READING_THEME, theme.name).apply()
            }
            val onFollowSystem: () -> Unit = {
                prefs.edit().remove(PrefKeys.READING_THEME).apply()
                followingSystem = true
                appTheme = if (systemDark) ReadingTheme.DARK else ReadingTheme.LIGHT
            }
            // UIスキン（着せ替え）。reading_theme と同じ「MainActivity へ巻き上げた単一状態＋prefs 永続」流儀。
            // キー不在＝既定装いの明快K（2026-07-23 の既定変更に追従した記述の是正）。装着は装いの間（wardrobe）からのみ変更され、
            // 公開ビルドでは skinFromName が保存値ごと明快K へクランプする（ADR 0027 適用点3）。
            var appSkin by remember { mutableStateOf(skinFromName(prefs.getString(PrefKeys.APP_SKIN, null))) }
            val onSkinChange: (Skin) -> Unit = { skin ->
                appSkin = skin
                prefs.edit().putString(PrefKeys.APP_SKIN, skin.name).apply()
            }
            // 高負荷スカイ（星図M・試作／ADR 0023）。reading_theme・app_skin と同じ「MainActivity 巻き上げ＋prefs 永続」流儀。
            // なぜ BuildConfig.DEBUG で潰すか: release ではトグル UI を出さず値も常に false＝出荷時は現行の空のまま
            //（試作は debug 限定の実機探索）。debug でのみ prefs 値を尊重して backdrop へ引数で渡す。
            var highLoadSkyM by remember {
                mutableStateOf(BuildConfig.DEBUG && prefs.getBoolean(PrefKeys.SKY_HIGH_LOAD_M, false))
            }
            val onHighLoadSkyChange: (Boolean) -> Unit = { on ->
                highLoadSkyM = on
                prefs.edit().putBoolean(PrefKeys.SKY_HIGH_LOAD_M, on).apply()
            }

            // Material3 配色もテーマ3値（ライト/セピア/ダーク）へ追従させる。
            // 旧実装はセピア時にライト配色を流用しており、本棚・発見系で「ライトとセピアの
            // 色味に差がない」実機フィードバック（2026-07-07）の主因だった。読書側の固有色
            // （ReadingColors）とは別系統だが、同じ琥珀紙トーンに揃えている（Theme.kt 参照）。
            NovelReaderTheme(skin = appSkin, theme = appTheme) {
                NovelReaderApp(
                    appTheme = appTheme,
                    onThemeChange = onThemeChange,
                    followingSystem = followingSystem,
                    onFollowSystem = onFollowSystem,
                    appSkin = appSkin,
                    onSkinChange = onSkinChange,
                    highLoadSkyM = highLoadSkyM,
                    onHighLoadSkyChange = onHighLoadSkyChange,
                    // .value の読み取りを composable 内で行うことで onNewIntent の更新が再コンポーズを誘発する。
                    deepLinkBookId = deepLinkBookId.value,
                    onDeepLinkConsumed = { deepLinkBookId.value = null },
                    // P3: 共有/リンクからの Web 小説取込対象（deepLinkBookId と同型の消費流儀）。
                    pendingWebImportUrl = pendingWebImportUrl.value,
                    onWebImportConsumed = { pendingWebImportUrl.value = null },
                    // 上書き確認テレポート（通知タップ→確認ダイアログ・同型の消費流儀で false 戻し）。
                    navigateToOverwriteConfirm = pendingOverwriteConfirmNav.value,
                    onOverwriteConfirmNavConsumed = { pendingOverwriteConfirmNav.value = false },
                )
            }
        }
    }

    // フレーム落ち計測（JankStats）の window 接続。
    // なぜ onCreate ではなく onResume か: JankStats は window と **View 階層** の両方に繋ぐため、
    // 階層が確実に存在する時点で張る必要がある（setContent 直後は composition がまだ走っていない）。
    // Activity 再生成で複数回呼ばれるが、多重購読はラッパ側（JankTracker.attach）が前の購読を
    // 止めてから張り直すので同一フレームの多重計上にはならない。
    override fun onResume() {
        super.onResume()
        (application as NovelReaderApplication).jankTracker
            .attach(window, findViewById(android.R.id.content))
    }

    // launchMode=singleTop のため、Activity 稼働中に通知タップが来ると新規インスタンスを作らず
    // ここへ届く。setIntent で getIntent を最新化しつつ deep link 対象を差し替える。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_BOOK_ID)?.let { deepLinkBookId.value = it }
        // 稼働中のアプリへ新たに共有/リンクが来たら取込対象を差し替える。?.let で非該当 Intent（null 抽出）では
        // 上書きしない＝EXTRA_BOOK_ID の消費流儀と同型（無関係な再来 Intent で保留中の取込を潰さない）。
        extractWebImportUrl(intent)?.let { pendingWebImportUrl.value = it }
        // 上書き確認テレポート（稼働中経路）。if 立てのみ＝無関係な再来 Intent で保留中の要求を潰さない
        // （上2つの「非該当では上書きしない」流儀と同型）。
        if (OverwriteConfirmTeleport.isRequested(intent)) pendingOverwriteConfirmNav.value = true
    }

    /**
     * P3 取込導線: Intent から取込対象 URL を取り出す（純抽出は [WebImportIntentParser] へ委譲）。
     * VIEW は intent.data がそのまま作品/話 URL。SEND は共有テキスト（EXTRA_TEXT）から最初の http(s) URL を抽出。
     * それ以外（MAIN/LAUNCHER 等）は null＝取込対象なし。
     */
    private fun extractWebImportUrl(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data?.toString()
        Intent.ACTION_SEND -> WebImportIntentParser.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        else -> null
    }

    companion object {
        /** 変換完了通知 → 読書画面 deep link 用の bookId extra キー（M11）。 */
        const val EXTRA_BOOK_ID = "com.novelreader.extra.BOOK_ID"

        /** 設定スキーマ版（evolve）。enum の生 String 保存の改名耐性のため prefs に記録する現行版。
         *  キー文字列は [PrefKeys.SETTINGS_SCHEMA_VERSION]（全設定キーの正本＝PrefKeys へ集約済み）。 */
        const val SETTINGS_SCHEMA_VERSION = 1
    }
}

/**
 * 初期テーマ決定。reading_theme 未保存ならシステムのライト/ダークへ追従し、
 * 保存済みならそれを採用する（不正値・enum名変更時はシステム追従へフォールバック）。
 */
private fun loadInitialTheme(prefs: SharedPreferences, systemDark: Boolean): ReadingTheme {
    val systemFallback = if (systemDark) ReadingTheme.DARK else ReadingTheme.LIGHT
    val saved = prefs.getString(PrefKeys.READING_THEME, null) ?: return systemFallback
    return runCatching { ReadingTheme.valueOf(saved) }.getOrDefault(systemFallback)
}

/**
 * 装いの間ルート（"wardrobe"）の登録可否を1か所に閉じる（ADR 0027 適用点2）。
 *
 * なぜ NavHost 直下に素の `if` を書かず拡張関数へ切り出すか: [NovelReaderApp] の NavHost は ViewModel を
 * 要求する塊で JVM テストから組めず、「フラグ off でルートが存在しない」ことを固定できない。ここだけ
 * 切り出せば小さな NavHost へ載せて `graph.findNode` で両値を検証できる（WardrobeRouteGateTest）。
 *
 * 到達不能にする意味: 入口（設定「きせかえ」行）を隠し忘れても、ルートが無ければ画面自体に着けない。
 */
internal fun NavGraphBuilder.wardrobeRoute(
    skinSwitchingEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!skinSwitchingEnabled) return
    composable("wardrobe") { content() }
}

@Composable
private fun NovelReaderApp(
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    followingSystem: Boolean,
    onFollowSystem: () -> Unit,
    appSkin: Skin,
    onSkinChange: (Skin) -> Unit,
    // 高負荷スカイ試作（ADR 0023）: backdrop へ渡す現在値＋設定画面（本棚⋮）のトグルが呼ぶ更新。debug 限定は呼び出し元で潰す。
    highLoadSkyM: Boolean,
    onHighLoadSkyChange: (Boolean) -> Unit,
    deepLinkBookId: String?,
    onDeepLinkConsumed: () -> Unit,
    // P3 取込導線: 共有(SEND)/リンク(VIEW)からの Web 小説 URL（deepLinkBookId と同型・消費後に呼び元が null 戻し）。
    pendingWebImportUrl: String?,
    onWebImportConsumed: () -> Unit,
    // 上書き確認テレポート（通知タップ→確認ダイアログ・U1 残り 2026-08-06）。同型の消費流儀＝消費後 false 戻し。
    navigateToOverwriteConfirm: Boolean,
    onOverwriteConfirmNavConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    // 公開スコープ機能ゲート（ADR 0027）。読み口は Features 1点で、ここから適用点（ルート登録・設定行）へ配る
    // ＝課金実装後に判定が「購入したか」へ変わっても、書き換えるのは Features の中身だけで済む。
    val skinSwitchingEnabled = Features.skinSwitchingEnabled
    val appContext = LocalContext.current.applicationContext
    val activityContext = LocalContext.current
    val viewModel: BookshelfViewModel = viewModel()
    // 発見系（ホーム/ジャンル/結果一覧）はクエリ文脈を画面間で受け渡すため単一VMを共有する。
    // ロードは ensureHomeLoaded の遅延型なので、ここで生成しても本棚起動時に通信は発生しない。
    val discoveryViewModel: DiscoveryViewModel = viewModel()

    // In-App Review の打診（読了 reachedEnd false→true の one-shot イベント）。
    // なぜここで受けるか: launchReviewFlow は Activity 参照が要るため VM には持ち込めず、
    // イベントを Activity 直下の composition（＝どの画面に居ても購読が生きる場所）で受けて
    // ReviewPrompter へ渡す。NovelReaderApp は MainActivity.setContent 直下でのみ構成される＝
    // LocalContext は必ず Activity（as? は Robolectric 等で万一 Activity でない場合の無害化）。
    val reviewPrompter = remember { PlayReviewPrompter() }
    LaunchedEffect(Unit) {
        val activity = activityContext as? Activity ?: return@LaunchedEffect
        viewModel.reviewPromptEvents.collect { reviewPrompter.promptReview(activity) }
    }

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
                app.cancelCompletionNotification(bookId)
                book.ncode?.let { app.cancelNewEpisodeNotification(it) }
                // Web 蔵書の新着通知は "web:<bookId>" キー（NewEpisodeCheckLogic.webNewEpisodeMarkKey）
                // で発行されるため、ncode 経路とは別に取り下げる（U1 Web 統合 2026-07-29 の追従）。
                if (book.sourceUrl != null) app.cancelNewEpisodeNotification(webNewEpisodeMarkKey(book.id))
            }
            // 読書位置は保存済み進捗を尊重する（生命線）。未読なら index.html。
            // 境界: bookId は deep link 由来の String＝型付き API へ渡す直前に BookId へ包む。
            val startFile = viewModel.getLastRead(BookId(bookId)) ?: "index.html"
            navController.navigate("reading/$bookId/$startFile") {
                launchSingleTop = true
                // tabs（タブ層）を残して起点を固定＝Back が必ずタブ層へ戻る（固定起点の保証。旧 "bookshelf" ルートのタブ Pager 化に追従）。
                popUpTo(TAB_HOST_ROUTE) { inclusive = false }
            }
        } else {
            // 削除済み等で本が無い確定ケース: 最低限の保証として固定起点（タブ層＝本棚ページ）へ着地する。
            navController.popBackStack(TAB_HOST_ROUTE, false)
        }
        // ナビ後に消費済みへ（null で再ナビを防ぐ。key 変化で本 Effect は即再実行され早期 return する）。
        onDeepLinkConsumed()
    }

    // P3 取込導線: 共有(SEND)/リンク(VIEW)からの Web 小説取込ルーティング（確定事項②）。
    // registry を UI 層（VM）で直接引き、repository.addWebBook 呼び出し前に3値へ出し分ける。
    // 二重発火防止は deepLinkBookId と同型: key=pendingWebImportUrl・処理後 onWebImportConsumed() で null 戻し。
    LaunchedEffect(pendingWebImportUrl) {
        val url = pendingWebImportUrl ?: return@LaunchedEffect
        when (val res = viewModel.resolveWebImport(url)) {
            // Supported: 取込は必ず repository.addWebBook 経由（VM 内で完了/重複/失敗を Snackbar 通知）。
            is SiteAdapterRegistry.Resolution.Supported -> viewModel.importWebNovel(url)
            // Blocked: 自前 DL しない旨を案内し、逃げ道として公式サイトを外部ブラウザで開く。
            is SiteAdapterRegistry.Resolution.Blocked -> {
                viewModel.emitSnackbar("このサイト（${res.hostLabel}）からの取込は行いません。公式サイトでお読みください")
                // 素の ACTION_VIEW（createChooser なし）で開く。当アプリの VIEW フィルタは kakuyomu ホスト限定のため
                // Blocked（なろう等）URL では本アプリが候補に出ず自己ループしない。ブラウザ不在の稀ケースは
                // ActivityNotFoundException を握って無害化（案内 Snackbar は既に出した＝症状隠しではない）。
                runCatching { activityContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            // Unsupported: 未整備サイト。未対応である旨だけ知らせる。
            SiteAdapterRegistry.Resolution.Unsupported -> viewModel.emitSnackbar("未対応のサイトです")
        }
        onWebImportConsumed()
    }

    // M星図の常駐 backdrop（空の一枚化・2026-07-19 ユーザー裁定）: skin==SEIZU_M のとき NavHost の背後へ
    // 「動かない不変の空」を1枚だけ置く。視差オフセットは backdrop 側の controller が rememberSaveable で保持し、
    // 画面遷移では触らない＝スクロール視差が遷移でリセットされない。他スキンは backdrop 無し（controller=null）。
    val isSeizu = appSkin == Skin.SEIZU_M
    val density = LocalDensity.current
    // reduce-motion（アニメーター無効設定/省電力）で視差・流星を止める（各 M 画面の脈動判定と同じ源）。
    val reduceMotion = remember {
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // トーラス周期の初期推定＝画面高（backdrop が onSizeChanged で実測補正）。旧 40dp クランプは撤廃（無限スクロール・裁定①）。
    val configuration = LocalConfiguration.current
    val initialTilePx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val skyParallax = if (isSeizu) rememberSaveable(
        saver = SkyParallaxController.Saver(initialTilePx, SkyParallaxFactor, reduceMotion),
    ) { SkyParallaxController(0f, initialTilePx, SkyParallaxFactor, reduceMotion) } else null

    // 画面遷移: M はフェードスルー（退出 fadeOut 先行→進入 fadeIn。固定天球ゆえ slide だと空ごと動く＝ADR 0019 追記
    // 「M星図の例外」）＝コンテンツのみがシームレスに差し替わる。他スキンは横スライド push 不変（ADR 0019・方向で階層移動を伝える）。
    // 恒常ボトムナビ（3タブ・plan default-ui-clarity-K）を全スキンへ伝播（2026-07-23・K形の構造装置を
    // D/C/M/P/J へ一般化）。現在ルートがタブ3画面のときだけ NavHost の「外」（Column の下端）に静止表示する。
    // 画面側に持たせない理由: 画面遷移アニメと一緒にバーが滑ると「別ページへ移動した」と読めてしまう＝
    // タブバー静止がタブの標準文法のため。
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // タブ3面（本棚/さがす/設定）は NavHost のルート分岐から Pager（"tabs" 単一ルート）へ移行
    // （2026-07-24 ユーザー裁定＝横スワイプでシームレスにタブ移動・ADR 0022 スロット契約）。
    // Pager 状態は枠（ここ）が所有する＝ボトムナビの現在タブ表示と選択遷移が同じ1状態から導出される。
    // rememberPagerState は内部 Saver 持ち＝回転/プロセス死でも現在タブを保持（旧 saveState/restoreState の代替）。
    val tabPagerState = rememberPagerState(pageCount = { KTab.entries.size })
    val tabScope = rememberCoroutineScope()
    // 深い画面（読書等）では null＝恒常ナビを消して没入を守る（従来と同じ判定を「tabs ルートか」へ置換）。
    val currentTab = if (currentRoute == TAB_HOST_ROUTE) KTab.entries[tabPagerState.currentPage] else null

    // 診断への画面名の供給（フレーム落ちの画面別集計＋異常終了の「どの画面で消えたか」）。
    // タブ層を route 名 "tabs" のままにせず Pager のページ名まで分解するのは、本棚/さがす/設定が
    // 1つの名前に混ざると「どのタブが重いか」が見えず、タブスワイプの体感ジャンク（2026-07-24 報告）に
    // 実測で当たれないため。深い画面（読書・目次・発見詳細）は route がそのまま画面名になる。
    val diagnosticsScreen = currentTab?.let { "tabs/${it.name.lowercase()}" } ?: currentRoute ?: "(none)"
    val diagnosticsApp = activityContext.applicationContext as NovelReaderApplication
    LaunchedEffect(diagnosticsScreen) {
        diagnosticsApp.jankTracker.setScreen(diagnosticsScreen)
        diagnosticsApp.sessionWatch.noteScreen(diagnosticsScreen)
    }
    // タブ選択＝Pager のスクロール（タップでもスワイプでも同じスライド運動言語。旧 crossfade は
    // Pager のスワイプ追従と矛盾するため廃止＝isTabSwitch 分岐ごと撤去）。同一タブ再タップは
    // animateScrollToPage が同ページで no-op＝旧 navigateKTab の重複抑止と同じ挙動が構造的に出る。
    val onSelectTab: (KTab) -> Unit = { tab ->
        tabScope.launch {
            tabPagerState.animateScrollToPage(tab.ordinal, animationSpec = tween(MotionDurationKTabSwitch))
        }
    }

    // 上書き確認テレポートの着地（通知タップ→確認ダイアログ・U1 残り 2026-08-06）。
    // deepLinkBookId の Effect（上方）と違いここに置くのは tabPagerState が要るため（着地＝popToTab）。
    OverwriteConfirmLandingEffect(
        requested = navigateToOverwriteConfirm,
        navController = navController,
        tabPagerState = tabPagerState,
        onConsumed = onOverwriteConfirmNavConsumed,
    )

    val d = MotionDurationNavTransition
    Box(modifier = Modifier.fillMaxSize()) {
        if (skyParallax != null) SkyBackdropM(skyParallax, highLoadSkyM, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalSkyParallax provides skyParallax) {
            // 画面ルートに Surface を敷いて LocalContentColor を配色へ接地する。素の Box/Column 直下では
            // 既定が黒のままで、明示色を持たない Text（K本棚タイトル等）が全テーマで黒く沈む＝2026-07-23
            // ユーザー指摘「ダークで本棚タイトルが見えない」の真因。M星図だけは常駐 backdrop（後ろの空）を
            // 透過で見せる必要があるため透明＋現在色の素通しにする（挙動不変）。
            Surface(
                color = if (isSeizu) Color.Transparent else MaterialTheme.colorScheme.background,
                contentColor = if (isSeizu) LocalContentColor.current else MaterialTheme.colorScheme.onBackground,
            ) {
            Column(Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = TAB_HOST_ROUTE,
        modifier = Modifier.weight(1f),
        // タブ間はもう NavHost 遷移でない（Pager が担う）＝isTabSwitch 分岐は不要になった。
        // 残る分岐＝M星図のフェードスルー（固定天球ゆえ slide だと空ごと動く・ADR 0019 追記）と他スキンの横スライド push。
        enterTransition = {
            if (isSeizu) fadeIn(tween(MotionDurationSeizuFadeIn, delayMillis = MotionDurationSeizuFadeInDelay))
            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(d))
        },
        exitTransition = {
            if (isSeizu) fadeOut(tween(MotionDurationSeizuFadeOut))
            else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(d))
        },
        popEnterTransition = {
            if (isSeizu) fadeIn(tween(MotionDurationSeizuFadeIn, delayMillis = MotionDurationSeizuFadeInDelay))
            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(d))
        },
        popExitTransition = {
            if (isSeizu) fadeOut(tween(MotionDurationSeizuFadeOut))
            else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(d))
        },
    ) {

        // タブ層＝単一ルート "tabs"（本棚/さがす/設定の3面を TabPagerHost のスロットへ・ADR 0022 スロット契約）。
        // 深い画面（読書・目次・発見詳細等）は従来どおり NavHost ルート＝この上へ push される。
        composable(TAB_HOST_ROUTE) {
            // 遷移ジャンク対策（P2・Perfetto 2026-07-16 で主因確定）: 本棚グリッドの初回 measure（実測51ms/
            // フレーム）が slide push のアニメフレームと同居して落ちるため、「tabs が enter アニメ中」の間だけ
            // 重いグリッドをスケルトンへ差替える（BookshelfContent 側の分岐参照）。currentState/targetState は
            // 遷移の端点でしか変化しない離散 State＝毎フレーム recompose を増やさない（連続 fraction は読まない）。
            // exit（本棚→先へ進む）は既測グリッドで安価なため対象外＝実カードのままスライドアウトし視覚劣化なし。
            val deferHeavyContent by remember {
                derivedStateOf {
                    transition.targetState == EnterExitState.Visible &&
                        transition.currentState != EnterExitState.Visible
                }
            }
            TabPagerHost(
                pagerState = tabPagerState,
                // 遷移ジャム対策の続き（2026-07-26）: enter アニメ中は隣タブ面の常駐コンポーズも凍結する
                //（本棚グリッドの skeleton 差替えと同じ信号を共有。機序は TabPagerHost 側のコメント参照）。
                deferNeighborPages = deferHeavyContent,
                pages = listOf(
                    {
                    BookshelfScreen(
                viewModel = viewModel,
                deferHeavyContent = deferHeavyContent,
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                // 「システムに従う」の単一真実源を本棚⋮のテーマ4択へ素通し（読書設定シートへ渡すのと同じ状態＝
                // reading_theme 未宣言かどうか。別状態を新設せず二重管理を避ける・2026-07-17 ユーザー裁定②）。
                followingSystem = followingSystem,
                onFollowSystem = onFollowSystem,
                // 高負荷スカイ試作トグル（ADR 0023）＝本棚⋮メニュー（テーマ・通知の並ぶ設定面）に debug 限定で出す。
                highLoadSkyM = highLoadSkyM,
                onHighLoadSkyChange = onHighLoadSkyChange,
                onOpenBook = { bookId, startFile ->
                    // launchSingleTop: 二度押しで同一読書画面がバックスタックに二重 push されるのを防ぐ（M1）。
                    navController.navigate("reading/$bookId/$startFile") { launchSingleTop = true }
                },
                onOpenDiscovery = {
                    // タブ化に伴い「さがすへ」＝Pager のページ切替（ルート遷移でなくなった）。
                    onSelectTab(KTab.DISCOVER)
                },
                // 着せ替えの入口は本棚トップバーのみ（意図的設計＝ADR 0021 決定7。設定シート内には置かない）。
                onOpenWardrobe = {
                    navController.navigate("wardrobe") { launchSingleTop = true }
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
                    },
                    {
                    DiscoveryHomeScreen(
                viewModel = discoveryViewModel,
                // タブ化に伴い「戻る」＝階層 up＝本棚ページへ（システム Back は TabPagerHost の BackHandler が同じ契約で受ける）。
                onBack = { onSelectTab(KTab.BOOKSHELF) },
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
                    },
                    {
                    // 設定タブ（旧 composable("settings") から移設・引数不変。KDoc は SettingsScreenK 参照）。
                    SettingsScreenK(
                        appTheme = appTheme,
                        onThemeChange = onThemeChange,
                        followingSystem = followingSystem,
                        onFollowSystem = onFollowSystem,
                        currentSkin = appSkin,
                        onOpenWardrobe = { navController.navigate("wardrobe") { launchSingleTop = true } },
                        skinSwitchingEnabled = skinSwitchingEnabled,
                    )
                    },
                ),
            )
        }

        // 装いの間（UIスキン選択・ADR 0021 決定7）。引数なしフルスクリーン＋戻るの最小形（discovery と同型）。
        // 公開ビルドでは登録ごと落とす（ADR 0027 適用点2）。
        // なぜ navigate 側（上の M/J 本棚トップバー・設定タブ）を個別に潰さないか: 設定タブの入口は行ごと消え、
        // 本棚トップバーの入口は M/J の面にしか無く、skinFromName のクランプで M/J 自体が到達不能になるため
        // ＝残る navigate は呼ばれる経路を持たない。適用点は3つに保つ（ADR 0027 決定2）。
        wardrobeRoute(skinSwitchingEnabled) {
            WardrobeScreen(
                currentSkin = appSkin,
                onSkinChange = onSkinChange,
                onBack = { navController.popBackStack() },
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
            // 階層 up 一本化（2026-07-29 ユーザー裁定「わかりやすく」・ADR 0026）: ← もシステム Back も
            // 「一段上＝発見ホーム」へ。旧「←＝発見ホーム固定 Up／Back＝履歴 pop」の二本立て
            // （D 統一 2026-07-12）は廃止し、読書側（章→目次→本棚・2026-07-23 統一）と同じ
            // 「両操作とも階層を1段上がる」一規則へ揃えた。
            // 検索/ジャンル画面は結果の親でなく「条件編集の横道」＝up はそれらを飛ばして畳む
            // （全ての結果経路はタブ層「さがす」ページを必ず下位に持つ＝popToTab で一貫して一段上へ）。
            // 検索画面へ戻るのは「条件を変更」（onEditConditions）だけが明示導線として担う。
            // 旧ルート名リテラル pop の黙殺バグ（2026-07-27）と封鎖の経緯は popToTab の KDoc。
            val upToDiscoverHome = { popToTab(navController, tabPagerState, KTab.DISCOVER) }
            BackHandler { upToDiscoverHome() }
            DiscoveryResultScreen(
                viewModel = discoveryViewModel,
                onUp = upToDiscoverHome,
                onEditConditions = { navController.popBackStack() },
                // 境界: nav ルートは String。Ncode を .value でほどいてパスへ載せる。
                onOpenDetail = { ncode -> navController.navigate("discovery/detail/${ncode.value}") { launchSingleTop = true } },
            )
        }

        composable(
            route = "discovery/detail/{ncode}",
            arguments = listOf(navArgument("ncode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val ncode = backStackEntry.arguments?.getString("ncode") ?: return@composable
            // 階層 up 一本化（2026-07-29 ユーザー裁定・ADR 0026）: 作品詳細の ← もシステム Back も
            // 「一段上＝直近の結果一覧」へ（発見ホーム直行入場だけは一段上＝発見ホーム）。
            // 旧「←＝発見ホーム固定 Up／Back＝履歴 pop」の二本立て（D 統一 2026-07-12）は廃止。
            // 分岐が安全である機序（詳細の直下は必ず〈結果一覧 or タブ層〉）は upFromDiscoveryDetail の KDoc。
            val upFromDetail = { upFromDiscoveryDetail(navController, tabPagerState) }
            BackHandler { upFromDetail() }
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
                            // タブ層より上（既存 result・detail）を全て畳んでから result を1枚積むことで、
                            // 両経路とも [tabs, result] に固定する。
                            // resultContext は VM 単一保持のため、result を常に1枚に保つ SSOT もこれで維持される。
                            // 旧 popUpTo("discovery") はタブ Pager 化でルートが消えた後も残留＝畳みが無言で
                            // 効かず、経路によってスタックが割れたままだった（← 無反応と同根の残留リテラル）。
                            popUpTo(TAB_HOST_ROUTE) { inclusive = false }
                        }
                    }
                },
                // 縦書きPDF取り込み（ADR 0011）へ遷移。ここでの ncode は既にパス由来の String。
                onImportPdf = { navController.navigate("discovery/detail/$ncode/import") { launchSingleTop = true } },
                // 機能②: なろうをアプリ内 WebView で読む（ADR 0012）。目次(初回)＝0／続きから＝記録話 N を渡す。
                onReadFromToc = { navController.navigate("web-reader/$ncode/0") { launchSingleTop = true } },
                onResumeReading = { episode -> navController.navigate("web-reader/$ncode/$episode") { launchSingleTop = true } },
                onUp = upFromDetail,
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

            // 遷移ジャンク対策（案A・2026-07-29 裁定）: 本棚→読書（目次/本文）push の enter アニメ中だけ
            // 重い実内容を構造骨へ差し替える信号（tabs ルートの P2 と同じ離散2値＝遷移の端点でしか変化せず
            // 毎フレーム recompose を増やさない）。この route の上へ積むルートは無い＝popEnter で再入場する
            // 経路が存在しないため、この信号は push 窓でのみ立つ（pop 対象外の裁定と機構的に整合）。
            // 差し替えタイミングは P2 先例と同じ settle+0ms を採用（理由は ReadingScreen 側の同信号コメント）。
            val deferHeavyContent by remember {
                derivedStateOf {
                    transition.targetState == EnterExitState.Visible &&
                        transition.currentState != EnterExitState.Visible
                }
            }

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
                            // 目次→本棚の脱出。旧 popBackStack("bookshelf") はタブ化でルートが消え黙殺されていた
                            // （真因と2段構成の理由＝popToTab の KDoc）。
                            onNavigateToBookshelf = { popToTab(navController, tabPagerState, KTab.BOOKSHELF) },
                            // push 遷移窓の骨差し替え（案A）。startFile が目次なら目次骨・章なら本文骨に
                            // ReadingScreen 側で振り分ける。
                            deferHeavyContent = deferHeavyContent,
                        )
                    } else {
                        // 確定して本が存在しない（削除済み／復元不能）ケース。白画面デッドエンドを残さず、
                        // 既存のエラー画面（本棚へ戻る導線つき）を流用する（F-M）。意匠は発明しない。
                        ReadingErrorScreen(
                            message = "この書籍は見つかりませんでした",
                            colors = rememberReadingColors(appTheme),
                            onNavigateToBookshelf = { popToTab(navController, tabPagerState, KTab.BOOKSHELF) },
                        )
                    }
                }
            }
        }
    }
            // 恒常ナビ（全スキン・タブ3画面のときのみ・深い画面では消えて没入を守る）。配色は KBottomNav 側で
            // colorScheme トークン（primary/onSurfaceVariant/surface）を参照＝各スキンの署名色で選択ピル/tint が
            // 自然に染まる（スキン専用の意匠発明はしない・タスク裁定「トークン追従で近似」）。
            if (currentTab != null) {
                KBottomNav(
                    current = currentTab,
                    // タブ選択＝Pager スクロール（onSelectTab）。旧 navigateKTab（NavHost ルート入替＋スナップショット
                    // 遅延の防御）は、タブがルートでなくなったため機構ごと退役＝レースの土壌が消えた。
                    onSelect = onSelectTab,
                )
            }
            } // Column（NavHost ＋ K恒常ナビ）
            } // Surface（画面ルートの配色接地）
        } // CompositionLocalProvider(LocalSkyParallax)
    } // Box（backdrop ＋ NavHost）
}

/**
 * タブ層（本棚/さがす/設定 Pager）の NavHost ルート名の単一正本。
 * なぜ定数か: 2026-07-24 のタブ Pager 化で本棚がルート "bookshelf" からページへ変わった際、
 * 読書フローの脱出だけが旧ルート名文字列のまま残り、スタックに無いルートへの pop として黙殺された
 * （2026-07-25 実機バグ・目次に幽閉）。pop 先とルート登録を同一定数で結び、リネーム時の取り残しを型で封じる。
 */
internal const val TAB_HOST_ROUTE = "tabs"

/**
 * 深い画面（読書・目次・発見の結果一覧/作品詳細）から「タブ層の特定タブへ階層 up する」単一実装。
 * K タブ化（2026-07-24・ADR 0022）で本棚・さがすは NavHost ルートでなく tabs 内 Pager のページになったため、
 * 「タブ層へ pop」＋「Pager を目的タブへスナップ」の2段で階層 up を表現する。
 * スナップが要る理由: deep link（通知）入場では Pager が他タブに居ることがあり、pop だけでは
 * 「目次→さがす/設定」のように着地が化けて契約が破れる（同じタブから入場した通常経路では no-op）。
 * requestScrollToPage は非 suspend で Pager 非表示中（深い画面が前面）でも安全＝次の合成で適用される。
 *
 * なぜ「タブ名を [KTab] で受ける1関数」に集約するか（2026-07-27 実機バグの再発防止）:
 * 消えたルート名（"bookshelf"/"discovery"）への [NavController.popBackStack] は例外を投げず false を返して
 * 黙殺されるため、押しても何も起きない ← ボタンとしてコンパイルも通り、テストも緑のまま出荷される。
 * タブ層への pop 先を「文字列リテラルでは書けない」形（enum＋[TAB_HOST_ROUTE] 定数）へ閉じることで、
 * ルートの改名・廃止で pop 先が宙に浮く欠陥クラス自体を表現不能にする。
 */
internal fun popToTab(navController: NavController, tabPagerState: PagerState, tab: KTab) {
    tabPagerState.requestScrollToPage(tab.ordinal)
    navController.popBackStack(TAB_HOST_ROUTE, false)
}

/**
 * 上書き確認テレポートの着地エフェクト（通知タップ→上書き確認ダイアログ・U1 残り 2026-08-06）。
 * 着地＝タブ層・本棚ページへの [popToTab] だけ: ダイアログ本体は BookshelfViewModel.overwritePrompt の
 * 状態駆動で、ホスト（本棚ページの BookshelfScreen）が compose された時点で自動表示される
 * （帰還時確認と同一機序＝表示経路を増やさない。extras 設計の why は [OverwriteConfirmTeleport]）。
 * 深い画面（読書等）は pop で畳まれ、Pager が他タブに居ても本棚ページへスナップする（[popToTab] の契約）。
 * cold start（スタック＝tabs のみ・page 0）では pop もスナップも no-op で安全＝両経路を1実装で受ける。
 *
 * なぜ [NovelReaderApp] から切り出すか: NovelReaderApp の NavHost は ViewModel を要求する塊で JVM テストから
 * 組めず、「旗→着地→消費」の結線を固定できない（[wardrobeRoute] を切り出したのと同じ理由）。
 * ここだけ切り出せば最小 NavHost＋Pager に載せて検証できる（OverwriteConfirmTeleportTest）。
 */
@Composable
internal fun OverwriteConfirmLandingEffect(
    requested: Boolean,
    navController: NavController,
    tabPagerState: PagerState,
    onConsumed: () -> Unit,
) {
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        popToTab(navController, tabPagerState, KTab.BOOKSHELF)
        // 着地後に消費（false 戻し）＝再コンポーズ・復帰での再発火を防ぐ（deepLinkBookId と同型）。
        onConsumed()
    }
}

/**
 * 発見・作品詳細からの階層 up（← とシステム Back の共通実装・2026-07-29 一本化＝ADR 0026）。
 * 発見の階層は〈発見ホーム（タブ）→ 結果一覧 → 作品詳細〉で、詳細の一段上＝「直近の結果一覧」。
 * 発見ホームから直接開いた詳細（直下がタブ層）だけは一段上＝発見ホーム＝Pager スナップ込みの [popToTab]
 * （素の pop だと deep link 相当で Pager が他タブに居るとき着地が化けるため）。
 *
 * なぜ「1 pop」が常に一段上になるか: キーワード再検索（詳細のキーワードタップ）は結果一覧を
 * [tabs, result] へ畳んでから積む（onSearchKeywords の popUpTo・[DiscoveryUpNavigationTest] 契約④）ため、
 * 結果一覧・詳細が多段に重なることはなく、詳細の直下は必ず〈結果一覧 or タブ層〉の2択に保たれている。
 * 「再検索の重なりは同じ結果一覧段＝up は履歴を全部は遡らない」という裁定もこの畳みが機構的に担う。
 * なぜ [TAB_HOST_ROUTE] 定数比較か: 「タブ層直上か」の判定をルート名リテラルで書かない
 * （2026-07-27 のリテラル封鎖＝[popToTab] KDoc と同じ規律。定数はルート登録と同一の単一正本）。
 */
internal fun upFromDiscoveryDetail(navController: NavController, tabPagerState: PagerState) {
    if (navController.previousBackStackEntry?.destination?.route == TAB_HOST_ROUTE) {
        popToTab(navController, tabPagerState, KTab.DISCOVER)
    } else {
        navController.popBackStack()
    }
}

/**
 * 読書画面の最小ローディング表示（F-M）。DB 初回発行前（cold start / process death 復元中）に
 * 白画面を出さないための場つなぎ。本棚スケルトンと同思想＝「未確定」を可視化し、Content 確定で
 * 本の有無に分岐する。意匠を発明しないため配色は読書テーマのトークン（background/accent）のみを使う。
 */
@Composable
private fun ReadingLoadingPlaceholder(readingTheme: ReadingTheme) {
    val colors = rememberReadingColors(readingTheme)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = colors.accent)
    }
}
