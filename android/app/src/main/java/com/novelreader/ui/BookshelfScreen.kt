package com.novelreader.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.novelreader.PrefKeys
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.model.BookId
import com.novelreader.ui.discovery.FilterChipItem
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontHomeTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.skins.rememberShelfFace
import com.novelreader.ui.skins.rememberShelfViewToggle
import com.novelreader.ui.skins.ShelfActions
import com.novelreader.ui.skins.ShelfChrome
import com.novelreader.ui.skins.ShelfData
import com.novelreader.ui.skins.ShelfFace
import com.novelreader.ui.skins.ShelfSelection
import com.novelreader.ui.skins.ShelfWebActions
import com.novelreader.ui.skins.ThemeControl
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.NovelReaderAlertDialog
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.domain.MissingContentDeleteWarning
import com.novelreader.domain.ReadingStatus
import com.novelreader.domain.ReimportPlan
import com.novelreader.domain.ScanProgress
import com.novelreader.domain.ShelfItem
import com.novelreader.domain.countMissingContentTargets
import com.novelreader.domain.deleteConfirmBody
import com.novelreader.domain.deleteConfirmLabel
import com.novelreader.domain.filterShelfByStatus
import com.novelreader.domain.mergeShelfItems
import com.novelreader.domain.missingContentDeleteWarning
import com.novelreader.domain.readingStatusFor
import com.novelreader.domain.reimportBreakdown
import com.novelreader.domain.reimportStatusLabel
import com.novelreader.domain.shelfStatusCounts
import com.novelreader.domain.webNcodesInSelection
import kotlinx.coroutines.launch

/**
 * 本棚画面のルート層（state-holder / UI 分割の route）。
 * ViewModel の受け取り・状態の collect・エラーイベントの購読・PDF 選択/通知権限/バッテリー最適化ダイアログ
 * といったプラットフォーム副作用と永続化（SharedPreferences）を担い、純粋な描画は [BookshelfContent] に委ねる。
 * なぜ分割するか: 描画層を state+callback の葉にして VM 非依存にすることで、Robolectric の JVM UI テスト
 * （ADR 0009）で空/一覧の分岐やコールバック結線を検証できるようにするため（chrisbanes state-holder-ui-split）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    // 見た目テーマの単一正本（読書と共有）。本棚の⋮メニューからも切り替えられるようにする。
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    // 「システムに従う」の単一真実源（reading_theme 未宣言＝追従）。読書設定シートと同じ MainActivity 状態を
    // そのまま受け取り、本棚⋮のテーマ節でも同じ4択を共有する（別状態を新設せず二重管理を避ける）。
    // 既定 false / no-op は既存呼出し・テストの互換のため（MainActivity が実値を渡して初めて有効化される）。
    followingSystem: Boolean = false,
    onFollowSystem: () -> Unit = {},
    // 高負荷スカイ試作トグル（ADR 0023）。⋮メニュー（設定面）へ debug 限定で出す。既定 false / no-op は既存呼出し・テスト互換。
    highLoadSkyM: Boolean = false,
    onHighLoadSkyChange: (Boolean) -> Unit = {},
    onOpenBook: (bookId: String, startFile: String) -> Unit,
    onOpenDiscovery: () -> Unit,
    // 装いの間（UIスキン選択）への入口。2026-07-29 K形正本追従で本棚の入口は撤去し設定タブ「きせかえ」へ移管
    //（ADR 0021 追記）。D 自身は使わないがここで受け続けるのは、モックが温存するスキン署名の入口
    //（M 銘クラスタの4条星・J デッキ面クローム）が ShelfActions 経由で使うため。onOpenDiscovery も同様
    //（発見は「さがす」タブへ分離済み・J デッキの発見扉だけが残置導線として使う）。
    onOpenWardrobe: () -> Unit = {},
    // (b) Web由来カードの「縦書きPDFを取り込む」→ 取り込み画面（discovery/detail/{ncode}/import）への
    // ナビゲーション。navController は MainActivity が握るためコールバックで委譲する。
    onImportWebNovel: (ncode: String) -> Unit = {},
    // 機能②: Web カードの読書導線＝なろうをアプリ内 WebView で開く（ADR 0012）。startEpisode 0=目次(初回)／
    // >0=記録した話へ直接(続きから)。navController は MainActivity が握るためコールバックで委譲する。
    onReadWebNovel: (ncode: String, startEpisode: Int) -> Unit = { _, _ -> },
    // 遷移ジャンク対策（P2）: enter アニメ中だけ重いグリッドをスケルトンへ差替える指示。算出は
    // NavHost の transition を持つ MainActivity の責務（ここは素通し）。既定 false＝既存呼出し不変。
    deferHeavyContent: Boolean = false,
) {
    // Loading と Empty を型で区別する（F-O）。Loading 中はスケルトンを出し、
    // DB から Content(空) が確定して初めて空状態を表示することで cold start の空フラッシュを防ぐ。
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    // 各本の章数（bookId→章数）。カードの進捗行表示と状態フィルタ判定の単一真実源（VM が一括で数える）。
    val chapterCountMap by viewModel.chapterCountMap.collectAsStateWithLifecycle()
    // 続きありバッジ用のなろう詳細（key=ncode）。VM が本棚一覧の紐付け作品をまとめて照会し配布する。
    val newEpisodeNovelMap by viewModel.newEpisodeNovelMap.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    // 複数PDF取込で「なろう形式でないPDF」が混在したときの確認プロンプト（null=非表示）。
    val importPrompt by viewModel.importPrompt.collectAsStateWithLifecycle()
    // 本文欠落→再取込（2026-07-29 案B＋案C、同日 案X 増補）。bookId→復旧手段（null=初回検出前＝空扱い）。
    val reimportPlans = viewModel.reimportPlans.collectAsStateWithLifecycle().value ?: emptyMap()
    val sweepBannerVisible by viewModel.sweepBannerVisible.collectAsStateWithLifecycle()
    // 案X: フォルダ走査の進捗（null=走査していない）・結果（null=結果ダイアログ非表示）・記憶済みの場所。
    val folderScan by viewModel.folderScan.collectAsStateWithLifecycle()
    val folderScanReport by viewModel.folderScanReport.collectAsStateWithLifecycle()
    val pdfFolderTreeUri by viewModel.pdfFolderTreeUri.collectAsStateWithLifecycle()
    // 案B: 欠落カードのタップで開く復旧ダイアログの対象（null=非表示）。案C: 一括内訳ダイアログの開閉。
    // ダイアログは M3 AlertDialog（route 層所有）＝削除確認・取込プロンプトと同じ扱いで全スキンに被さる。
    var reimportTarget by remember { mutableStateOf<BookEntity?>(null) }
    var showSweepDialog by remember { mutableStateOf(false) }
    // フォルダ選択の結果を「この1冊の走査」へ回すか「一括復旧」へ回すかの行き先（案X）。
    // なぜ状態で持つか: ピッカーのコールバックは launch 時の文脈を受け取れないため、どちらの導線から
    // 開いたかをここで覚えておく（null=一括復旧）。
    var pendingScanBook by remember { mutableStateOf<BookEntity?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 分岐②③（権限失効/記録なし）の PDF 選び直しピッカー（単数）。write 要求は通常取込と同理由
    // （OpenPdfWithWrite 参照）。選んだ PDF は通常の addBook へ＝repository 層のハッシュ/題名照合が
    // 既存行への復元に合流させる（同一判定は既存機序＝新しい突合を発明しない）。
    // 案X 以後この導線は「自分で選びたい人」向けの副経路になった（主経路はフォルダ走査）。
    val reimportPdfPicker = rememberLauncherForActivityResult(OpenPdfWithWrite()) { uri ->
        if (uri != null) viewModel.addBook(uri)
    }

    // 案X: 「PDFのある場所」のフォルダ選択（ACTION_OPEN_DOCUMENT_TREE）。
    // 選ばれたツリーは VM が永続化し、以後の欠落は選び直さずに走査できる（＝案X の要）。
    val pdfFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val target = pendingScanBook
        pendingScanBook = null
        if (treeUri == null) return@rememberLauncherForActivityResult
        viewModel.rememberPdfFolder(treeUri)
        if (target != null) {
            // 場所を選んでいる間にその本が復旧を終えた等で走査対象から外れることがある。
            // 無反応で終わらせず理由を告げる（せっかくフォルダを選んだのに何も起きない、を作らない）。
            if (!viewModel.scanFolderForBook(target, treeUri)) {
                viewModel.emitSnackbar("この本は再取込の対象ではなくなりました", transient = true)
            }
        } else {
            viewModel.runSweepReimport(treeUri)
        }
    }

    // 欠落本1冊をフォルダ走査で探す共通導線（案B ダイアログ②③の主ボタン）。
    // 場所を記憶済みならその場で走査し、未記憶ならフォルダ選択を出す（＝2度目以降は選ばせない）。
    val scanForBook: (BookEntity) -> Unit = { book ->
        if (!viewModel.scanFolderForBook(book)) {
            pendingScanBook = book
            pdfFolderPicker.launch(null)
        }
    }

    // PDF ファイル選択ランチャー（複数同時選択対応）。
    // OpenMultipleDocuments はキャンセル時 空リストを返す。取込は addBooks へ委ね、なろう形式でない
    // PDF の仕分け・確認は VM 側で行う（OS ピッカーはファイル名/中身での絞り込み・ソート不可のため）。
    // contract は write 権限付き（OpenMultiplePdfWithWrite）＝取込元PDF削除機能のため、後から
    // DocumentsContract.deleteDocument できるよう書込永続権限を要求する（write 非対応プロバイダでは
    // take 側の BookshelfViewModel.addBook が READ へフォールバック＝その本は取込元PDF削除の対象外）。
    val pdfPicker = rememberLauncherForActivityResult(
        OpenMultiplePdfWithWrite()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addBooks(uris)
    }

    // 通知権限ランチャー（Android 13+）。権限結果に関わらずPDF選択を開始
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pdfPicker.launch(arrayOf("application/pdf"))
    }

    // 「二度と表示しない」フラグをSharedPreferencesで永続化、mutableStateOfでリアルタイム反映
    val prefs = remember { context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, android.content.Context.MODE_PRIVATE) }
    var batteryDialogDismissed by remember { mutableStateOf(prefs.getBoolean(PrefKeys.BATTERY_DIALOG_DISMISSED, false)) }

    // バッテリー最適化除外ダイアログの表示フラグ（onFabClickより先に宣言必須）。
    // rememberSaveable: 回転・ダーク切替（Activity 再生成）でダイアログと「二度と表示しない」
    // チェック入力が消えないよう保持する（M4）。
    var showBatteryOptDialog by rememberSaveable { mutableStateOf(false) }
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }

    // スキン別ビュー状態（is_grid_view/k_grid_view/m_sky_view/p_rack_view/j_deck_view）はここには置かない:
    // 各スキンが自分の足元で所有する（skins/ShelfViewToggle.kt・p_hinge_detent と同流儀＝2026-07-27 移設）。

    // 通知権限 priming（notify Minor 2026-07-12）: システム権限ダイアログの前に理由説明を挟むためのフラグ。
    // 一度提示したら以後は出さない（notif_priming_shown で永続化）＝毎回のFABタップで問い直さない。
    var notifPrimingShown by remember { mutableStateOf(prefs.getBoolean(PrefKeys.NOTIF_PRIMING_SHOWN, false)) }
    var showNotifPriming by remember { mutableStateOf(false) }
    val markPrimingShown: () -> Unit = {
        notifPrimingShown = true
        prefs.edit().putBoolean(PrefKeys.NOTIF_PRIMING_SHOWN, true).apply()
    }

    // PDF選択を実際に開始するヘルパー（通知権限チェック後に呼ぶ）
    val launchPdfPicker: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                pdfPicker.launch(arrayOf("application/pdf"))
            } else if (!notifPrimingShown) {
                // notify Minor: いきなりシステムの権限ダイアログを出さず、まず何に使うかを説明する（priming）。
                // C3 と整合＝通知は既定OFF思想のため、変換の進捗/完了通知という利益の文脈で同意を問う。
                showNotifPriming = true
            } else {
                // 既に priming 済み（通知を見送った）＝以後は問い直さずそのままピッカーへ（graceful degradation）。
                pdfPicker.launch(arrayOf("application/pdf"))
            }
        } else {
            pdfPicker.launch(arrayOf("application/pdf"))
        }
    }

    // FAB タップ時: バッテリー案内で割り込まず、そのまま PDF 選択へ（add Major・公理12 2026-07-12）。
    // なぜ最初の add からバッテリーダイアログを外すか: 1冊も選ぶ前に「長文手順＋二度と表示しない＋設定へ離脱」
    // を最初の一手に挟むのは「最初の価値への段差」の禁止に反する。バッテリー最適化の案内は、実際に長い変換が
    // 背景へ回り得る文脈（＝下の processingState 監視で変換開始時）まで遅延する。
    // なお処理中も FAB をブロックしない: Service 側がキュー（ArrayDeque）で複数PDFを逐次処理でき、
    // 追加分はキュー末尾に積まれてバナー/通知に「N件目/全M件」として反映される。
    val onFabClick: () -> Unit = { launchPdfPicker() }

    // バッテリー最適化案内の遅延トリガ（add Major）: 実際に変換が始まった文脈でだけ、未確認かつ
    // バッテリー最適化が有効なら一度だけ案内を出す。isProcessing の false→true 立ち上がりで判定する。
    // なぜ FAB でなくここか: 「長い変換が背景へ回る」局面こそがこの助言の価値がある文脈で、
    // 取込の入口（最初の価値）を段差で塞がないため（さらに厳密な ON_STOP/OEM kill 検知は今後の配線）。
    LaunchedEffect(processingState.isProcessing) {
        if (processingState.isProcessing && !batteryDialogDismissed) {
            val pm = context.getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                doNotShowAgain = false
                showBatteryOptDialog = true
            }
        }
    }

    BookshelfContent(
        uiState = uiState,
        progressMap = progressMap,
        chapterCountMap = chapterCountMap,
        newEpisodeNovelMap = newEpisodeNovelMap,
        processingState = processingState,
        // 画面操作の束（全フィールド必須＝配線忘れはコンパイルエラー。既定値を置かない理由は ShelfFace.kt 冒頭）。
        actions = ShelfActions(
            // 開く際の再開ファイル解決（suspend の DB 参照）はルート層の責務。描画層は BookEntity を渡すだけ。
            onOpenBook = { book ->
                if (reimportPlans.containsKey(book.id)) {
                    // 本文欠落本のタップ＝読書画面（本文が無く空になる）でなく復旧ダイアログへ（案B）。
                    // route 層で差し替えるのは、バッジ未表出のスキン（M/P/J＝モック未裁定）でも
                    // タップ起点の復旧が全スキン共通に成立するため。
                    reimportTarget = book
                } else scope.launch {
                    // 境界: book.id は Room 由来の String＝型付き API へ渡す直前に BookId へ包む。
                    val lastReadFile = viewModel.getLastRead(BookId(book.id)) ?: "index.html"
                    onOpenBook(book.id, lastReadFile)
                }
            },
            onFabClick = onFabClick,
            onOpenDiscovery = onOpenDiscovery,
            onOpenWardrobe = onOpenWardrobe,
            onCancelProcessing = { viewModel.cancelProcessing() },
        ),
        // (b)+機能②: Web由来カードのタップ＝なろうをアプリ内 WebView で開く（目次＝startEpisode 0・ADR 0012）。
        // 旧 Custom Tabs 送客(0010)から WebReader へ移行し、読書位置を URL 観測で記録できるようにする。
        webActions = ShelfWebActions(
            onOpenWebNovel = { novel -> onReadWebNovel(novel.ncode, 0) },
            // 続きから読む＝記録した話(episode)へ WebView で直接着地する。
            onResumeWebNovel = { novel, episode -> onReadWebNovel(novel.ncode, episode) },
            onImportWebNovel = { novel -> onImportWebNovel(novel.ncode) },
            onRemoveWebNovel = { viewModel.removeWebNovel(it.ncode) },
        ),
        theme = ThemeControl(
            appTheme = appTheme,
            onThemeChange = onThemeChange,
            followingSystem = followingSystem,
            onFollowSystem = onFollowSystem,
        ),
        onDeleteBooks = { booksToDelete, deleteSource -> viewModel.deleteBooks(booksToDelete, deleteSource) },
        snackbarHostState = snackbarHostState,
        highLoadSkyM = highLoadSkyM,
        onHighLoadSkyChange = onHighLoadSkyChange,
        deferHeavyContent = deferHeavyContent,
        reimportPlans = reimportPlans,
        sweepBannerVisible = sweepBannerVisible,
        onSweepLater = { viewModel.dismissSweepBanner() },
        onSweepConfirm = { showSweepDialog = true },
        folderScan = folderScan,
        onScanStop = { viewModel.cancelFolderScan() },
    )

    // エラーは一度きりのイベントとして Channel から受信し Snackbar 表示する（VM イベント購読＝ルート層の責務）。
    // collect は画面の生存期間中ずっと購読し続ければよいので key は Unit。
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { event ->
            when {
                // 取込失敗（retryUri あり）は「再試行」を出し、押されたら同一 URI を再投入する（M7）。
                event.retryUri != null -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "再試行",
                        // 失敗の再試行は見落とすと復旧手段を失うため長め表示にする。
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.retryImport(event.retryUri)
                    }
                }
                // 破損監視（層2）: サイト構造変更の疑いは「公式サイトで読む」を出し、作品URLを外部ブラウザで開く
                // （U3 Blocked と同じ素 ACTION_VIEW 流儀）。ブラウザ不在の稀ケースは ActivityNotFoundException を
                // 握って無害化（案内文は既に表示済み＝症状隠しでない）。見落とすと逃げ道を失うため長め表示。
                event.openUrl != null -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "公式サイトで読む",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.openUrl))) }
                    }
                }
                // 一過性の情報通知（取込完了/取込済み）は actionLabel を付けず Short で自動消滅させる。
                // actionLabel を付けると Material3 の duration 既定が Indefinite になり画面へ残留するため
                // （Web 取込の「取り込み中」残留バグと同根＝案d）。
                event.transient ->
                    snackbarHostState.showSnackbar(message = event.message, duration = SnackbarDuration.Short)
                // それ以外（Blocked/Unsupported 案内・強制終了リカバリ等の情報通知）は従来どおり「閉じる」のみ。
                else -> snackbarHostState.showSnackbar(message = event.message, actionLabel = "閉じる")
            }
        }
    }

    // 通知権限 priming ダイアログ（notify Minor）: システム権限ダイアログの前に、通知が何に使われるかを
    // 利益の言葉で先に説明する。いずれの選択でもファイル選択は続行する（通知は取込・読書に必須ではない）。
    if (showNotifPriming) {
        val openPickerDirectly: () -> Unit = {
            markPrimingShown()
            showNotifPriming = false
            pdfPicker.launch(arrayOf("application/pdf"))
        }
        NovelReaderAlertDialog(
            onDismissRequest = openPickerDirectly,
            title = { Text("変換の進捗を通知でお知らせできます") },
            text = {
                Text(
                    "PDFの変換には時間がかかることがあります。通知を許可すると、他のアプリを使っている間も" +
                        "進捗と完了をお知らせします。通知はあとで設定からいつでもオフにできます。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    markPrimingShown()
                    showNotifPriming = false
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("通知を許可") }
            },
            dismissButton = {
                TextButton(onClick = openPickerDirectly) { Text("今はしない") }
            },
        )
    }

    // バッテリー最適化除外ダイアログ（プラットフォーム設定への遷移を伴うためルート層に置く）。
    // add Major: PDF 選択の入口ではなく「変換が始まった文脈」で出す純案内へ役割変更。もう
    // ファイル選択を閉じない（launchPdfPicker はここから呼ばない）。
    if (showBatteryOptDialog) {
        val dismiss: (openSettings: Boolean) -> Unit = { openSettings ->
            if (doNotShowAgain) {
                prefs.edit().putBoolean(PrefKeys.BATTERY_DIALOG_DISMISSED, true).apply()
                batteryDialogDismissed = true  // リアルタイムで状態に反映
            }
            showBatteryOptDialog = false
            if (openSettings) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
        NovelReaderAlertDialog(
            onDismissRequest = { dismiss(false) },
            title = { Text("バックグラウンド処理について") },
            text = {
                Column {
                    Text("ホーム画面に移動するとPDF変換が途中で止まる場合があります。\n\n【推奨設定】\n設定 → バッテリー → アプリごとの消費管理 → NovelReader → バックグラウンドアクティビティを許可\n\n「設定を開く」でバッテリー設定画面に移動します。")
                    Spacer(Modifier.height(Spacing.S8))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = doNotShowAgain,
                            onCheckedChange = { doNotShowAgain = it },
                        )
                        Text("二度と表示しない", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dismiss(true) }) { Text("設定を開く") }
            },
            dismissButton = {
                TextButton(onClick = { dismiss(false) }) { Text("閉じる") }
            },
        )
    }

    // 複数PDF取込で「なろう形式でないPDF」が混在したときの確認ダイアログ（プラットフォーム連携は無いが
    // VM 状態に紐づく決定 UI のためルート層に置く）。既定＝なろう形式のみ取込（安全側）＋「すべて取り込む」
    // で改名済みの正当ななろうPDFも救済できるようにする（取りこぼしを不可逆にしない）。
    importPrompt?.let { prompt ->
        val total = prompt.narou.size + prompt.nonNarou.size
        NovelReaderAlertDialog(
            onDismissRequest = { viewModel.dismissImportPrompt() },
            title = { Text("なろう形式でないPDFがあります") },
            text = {
                Text(
                    "選択した ${total} 件のうち ${prompt.nonNarou.size} 件は、なろうの縦書きPDF" +
                        "（ファイル名が「N＋数字＋英字」の形式）ではありません。" +
                        "うまく変換できない場合があります。",
                )
            },
            // なろう形式が1件以上あるなら既定を「なろう形式のみ」に。0件（全て非なろう）なら
            // 主ボタンを「すべて取り込む」にして選択が無駄にならないようにする。
            confirmButton = {
                if (prompt.narou.isNotEmpty()) {
                    TextButton(onClick = { viewModel.confirmImport(includeNonNarou = false) }) {
                        Text("なろう形式のみ (${prompt.narou.size}件)")
                    }
                } else {
                    TextButton(onClick = { viewModel.confirmImport(includeNonNarou = true) }) {
                        Text("すべて取り込む (${total}件)")
                    }
                }
            },
            dismissButton = {
                Row {
                    // なろう形式が在るときだけ副ボタンとして「すべて取り込む」を出す（0件時は主ボタン化済み）。
                    if (prompt.narou.isNotEmpty()) {
                        TextButton(onClick = { viewModel.confirmImport(includeNonNarou = true) }) {
                            Text("すべて (${total}件)")
                        }
                    }
                    TextButton(onClick = { viewModel.dismissImportPrompt() }) { Text("キャンセル") }
                }
            },
        )
    }

    // ── 本文欠落→再取込ダイアログ（案B・正本 bookshelf-reimport-badge-D の4分岐）────────────────
    // 対象の plan が消えたら（背後で復旧が完走した等）ダイアログごと静かに消える＝古い操作を残さない。
    // 全分岐で「読書位置としおりは残る」を明記（C2・進捗 DB に触れない実装保証は BookDao.updateRestoredContent）。
    reimportTarget?.let { book ->
        reimportPlans[book.id]?.let { plan ->
            val dismiss = { reimportTarget = null }
            when (plan) {
                is ReimportPlan.AutoPdf -> NovelReaderAlertDialog(
                    onDismissRequest = dismiss,
                    title = { Text("『${book.title}』を元のPDFから再取込しますか？") },
                    text = {
                        Text("記録されている取込元 PDF からもう一度変換します。読書位置としおりは残ります。")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.reimportFromSource(book); dismiss() }) { Text("再取込する") }
                    },
                    dismissButton = { TextButton(onClick = dismiss) { Text("やめる") } },
                )
                // ②③（PDF 由来で取込元へ到達できない）は「内容の指紋を持つか」で導線が割れる（案X）。
                // 分岐名（権限失効／記録なし）で割らないのは、人にとってはどちらも同じ操作になったため。
                is ReimportPlan.PickPdfPermissionLost, is ReimportPlan.PickPdfNoRecord -> {
                    if (plan.scanSha256 != null) {
                        // 指紋あり＝フォルダを1回教えれば自動で見つかる（主経路）。
                        val hint = (plan as? ReimportPlan.PickPdfPermissionLost)?.fileNameHint
                        NovelReaderAlertDialog(
                            onDismissRequest = dismiss,
                            title = { Text("PDFのある場所から探しますか？") },
                            text = {
                                Column {
                                    // 文言は短く（ユーザー裁定 2026-07-29「長すぎる」）。どの本かは背後のカードで
                                    // 分かるため書名を繰り返さず、「何をすれば戻るか」と「失わないもの」だけを言う。
                                    Text(
                                        if (pdfFolderTreeUri != null) {
                                            "教えていただいたフォルダを調べて復元します。読書位置としおりは残ります。"
                                        } else {
                                            "フォルダを教えていただければ、中身を照合して自動で見つけます。" +
                                                "読書位置としおりは残ります。"
                                        },
                                    )
                                    // 取込元の手がかり行は、ファイル名として妥当な文字列を復元できたときだけ出す。
                                    // 実機の主要プロバイダ（MediaStore Documents）では復元できず null になる
                                    // ＝内部 ID を「取込元の PDF」と称して見せない（sourceFileNameHint の KDoc）。
                                    hint?.let {
                                        Spacer(Modifier.height(Spacing.S12))
                                        Text(
                                            "取込元の PDF: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { scanForBook(book); dismiss() }) { Text("場所から探す") }
                            },
                            dismissButton = {
                                Row {
                                    // 自分で選びたい人向けの副経路（従来のピッカー）は残す＝選択肢を奪わない。
                                    TextButton(onClick = {
                                        reimportPdfPicker.launch(arrayOf("application/pdf")); dismiss()
                                    }) { Text("自分で選ぶ") }
                                    TextButton(onClick = dismiss) { Text("やめる") }
                                }
                            },
                        )
                    } else {
                        // 指紋なし（v11 前の旧取込）＝機械照合の材料が無い唯一の分岐。
                        // 「探せません」を黙らず理由ごと伝える（案X で救えない本を隠さない）。
                        NovelReaderAlertDialog(
                            onDismissRequest = dismiss,
                            title = { Text("PDFを選んで再取込") },
                            text = {
                                Text(
                                    "内容の記録がない古い取込のため、自動では探せません。" +
                                        "同じ PDF を選んでください。読書位置としおりは残ります。",
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    reimportPdfPicker.launch(arrayOf("application/pdf")); dismiss()
                                }) { Text("PDFを選ぶ") }
                            },
                            dismissButton = { TextButton(onClick = dismiss) { Text("やめる") } },
                        )
                    }
                }
                is ReimportPlan.AutoWeb -> NovelReaderAlertDialog(
                    onDismissRequest = dismiss,
                    title = { Text("『${book.title}』をWebから再取得しますか？") },
                    text = {
                        Text("作品ページからもう一度取得して復元します。読書位置としおりは残ります。")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.importWebNovel(plan.sourceUrl); dismiss() }) { Text("再取得する") }
                    },
                    dismissButton = { TextButton(onClick = dismiss) { Text("やめる") } },
                )
            }
        }
    }

    // ── 一括再取込の内訳確認ダイアログ（案C＋案X・正本 bookshelf-reimport-sweep-D の .roll）──────────
    // 内訳は sourceUri の分岐名でなく「復旧経路」で並べる（案X 以後、②③はどちらも『場所を1回教える』
    // という同じ操作で戻るため、分岐名で分けると人には嘘になる）。
    // 機械では戻せない群（内容指紋なし＝v11 前の旧取込）だけを中空ドットで正直に残す。
    if (showSweepDialog) {
        val breakdown = reimportBreakdown(reimportPlans.values)
        val closeSweep = { showSweepDialog = false }
        val hasFolder = pdfFolderTreeUri != null
        val canScan = breakdown.scannable > 0
        // フォルダを選ばせる導線（未記憶で走査対象がある／「別の場所を選ぶ」）の共通アクション。
        val chooseFolder = {
            pendingScanBook = null
            pdfFolderPicker.launch(null)
            closeSweep()
        }
        NovelReaderAlertDialog(
            onDismissRequest = closeSweep,
            title = { Text("${breakdown.total}冊をまとめて再取込しますか？") },
            text = {
                Column {
                    // 内訳（0冊の系統は出さない＝存在しない分類で数字を水増ししない）。
                    // 藍ドット＝機械で戻る群／中空ドット＝人が1冊ずつ選ぶしかない群（モック .roll の translation）。
                    ReimportBreakdownRow(breakdown.autoPdf, true, "元のPDFから自動で再変換（取込元の記録と権限あり）")
                    ReimportBreakdownRow(breakdown.autoWeb, true, "Webから自動で再取得（Web作品）")
                    ReimportBreakdownRow(breakdown.scannable, true, "PDFのある場所から自動で見つけて戻す")
                    ReimportBreakdownRow(
                        breakdown.unscannable, false,
                        "1冊ずつPDFを選ぶ必要（内容の記録がない古い取込＝自動照合できません）",
                    )
                    Spacer(Modifier.height(Spacing.S12))
                    Text(
                        // 文言は短く（ユーザー裁定 2026-07-29「長すぎる」）＝機序の説明を削り、
                        // 「次に何が起きるか」と「失わないもの」だけを残す。内訳は上の行が既に語っている。
                        buildString {
                            when {
                                canScan && !hasFolder -> append("フォルダを一度だけ教えてください。中身を照合して自動で見つけます。")
                                canScan -> append("教えていただいたフォルダを調べて ${breakdown.recoverableTotal}冊 を戻します。")
                                breakdown.autoTotal > 0 -> append("自動で戻せる ${breakdown.autoTotal}冊 を再取込します。")
                                else -> append("この方法で戻せる本はありません。")
                            }
                            if (breakdown.unscannable > 0) {
                                append("残り ${breakdown.unscannable}冊 はカードから個別に。")
                            }
                            append("読書位置としおりは残ります。")
                        },
                    )
                }
            },
            confirmButton = {
                when {
                    // 走査対象があり場所が未記憶＝まず場所を教わる（自動分もその後まとめて実行される）。
                    canScan && !hasFolder ->
                        TextButton(onClick = chooseFolder) { Text("PDFのある場所を選ぶ") }
                    // 場所を知っている＝選ばせず即実行（案X が目指した「2度目以降は無操作」）。
                    canScan ->
                        TextButton(onClick = { viewModel.runSweepReimport(); closeSweep() }) {
                            Text("${breakdown.recoverableTotal}冊を再取込する")
                        }
                    breakdown.autoTotal > 0 ->
                        TextButton(onClick = { viewModel.runSweepReimport(); closeSweep() }) {
                            Text("${breakdown.autoTotal}冊を再取込する")
                        }
                    // 実行するものが無い＝内訳の提示だけが役目なので「閉じる」1本にする。
                    else -> TextButton(onClick = closeSweep) { Text("閉じる") }
                }
            },
            dismissButton = {
                if (breakdown.recoverableTotal > 0) {
                    Row {
                        // 記憶した場所に無い本があるとき用の逃げ道（別の保存先を教え直す）。
                        if (canScan && hasFolder) {
                            TextButton(onClick = chooseFolder) { Text("別の場所を選ぶ") }
                        }
                        TextButton(onClick = closeSweep) { Text("やめる") }
                    }
                }
            },
        )
    }

    // ── フォルダ走査の結果ダイアログ（案X・正本 bookshelf-reimport-sweep-D「走査結果」）────────────
    // 一致分は既に取込キューへ投入済み（VM）＝ここは「何が戻り、何が戻らなかったか」を告げる場。
    folderScanReport?.let { report ->
        val closeReport = { viewModel.dismissFolderScanReport() }
        NovelReaderAlertDialog(
            onDismissRequest = closeReport,
            title = {
                Text(
                    when {
                        report.matchedCount > 0 -> "${report.matchedCount}冊が見つかりました"
                        report.cancelled -> "途中で停止しました"
                        else -> "一致するPDFが見つかりませんでした"
                    },
                )
            },
            text = {
                Column {
                    Text(
                        // 文言は短く（ユーザー裁定 2026-07-29）。件数は事実として要るので残し、説明を削る。
                        buildString {
                            append("${report.candidateCount}件 のうち ${report.hashedCount}件 を調べました。")
                            if (report.matchedCount > 0) {
                                append("${report.matchedCount}冊 を戻します。終わった本から読めるようになります。")
                                append("読書位置としおりは残ります。")
                            } else if (!report.cancelled) {
                                // 0冊のときは「なぜ」を言う＝症状だけ告げて放り出さない。
                                append("同じ内容のPDFがありませんでした。別の場所にあるかもしれません。")
                            }
                        },
                    )
                    if (report.unmatchedCount > 0 || report.unreadableCount > 0) {
                        Spacer(Modifier.height(Spacing.S12))
                        ReimportBreakdownRow(
                            report.unmatchedCount, false,
                            "見つかりませんでした（別の場所にあるか、PDFがもう端末にありません）",
                        )
                        ReimportBreakdownRow(
                            report.unreadableCount, false,
                            "読み取れないファイルがありました（破損／権限の穴）",
                        )
                    }
                }
            },
            confirmButton = {
                // 戻らなかった本が残るときだけ、次の一手（別の場所）を主ボタンに置く。
                if (report.unmatchedCount > 0) {
                    TextButton(onClick = { closeReport(); pendingScanBook = null; pdfFolderPicker.launch(null) }) {
                        Text("別の場所を選ぶ")
                    }
                } else {
                    TextButton(onClick = closeReport) { Text("閉じる") }
                }
            },
            dismissButton = {
                if (report.unmatchedCount > 0) TextButton(onClick = closeReport) { Text("閉じる") }
            },
        )
    }
}

/** 一括確認ダイアログの内訳1行（モック .roll .r）。count=0 の系統は描かない。auto=藍ドット／manual=中空ドット。 */
@Composable
private fun ReimportBreakdownRow(count: Int, auto: Boolean, label: String) {
    if (count == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = Spacing.S4),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .then(
                    if (auto) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, androidx.compose.foundation.shape.CircleShape),
                ),
        )
        Spacer(Modifier.width(Spacing.S8))
        Text("${count}冊", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(Spacing.S8))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/**
 * 本棚の描画層（stateless / UI 分割の content）。BookshelfScreen からの純移動。
 * VM や SharedPreferences・ランチャー等のプラットフォーム依存を持たず、[uiState]＋コールバックだけで
 * 一覧/空/スケルトンの分岐と各操作の結線を描画する葉。これにより Robolectric UI テスト（ADR 0009）が
 * VM 抜きで組める。メニュー開閉・FAB 展開・削除確認の対象といった画面ローカルな UI 状態のみ内部に残す
 * （過剰な hoisting は避ける）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfContent(
    uiState: BookshelfUiState,
    progressMap: Map<String, ProgressEntity>,
    // 各本の章数（bookId→章数）。カードの進捗行と状態フィルタ判定が共有する。既定 emptyMap は
    // 既存テスト・呼び出しの互換のため（章数 0＝進捗行「未読」表示で従来どおり成立する）。
    chapterCountMap: Map<String, Int> = emptyMap(),
    // 続きありバッジ用のなろう詳細（key=ncode）。カードは自分の book.ncode 分を引いて突き合わせる。
    newEpisodeNovelMap: Map<String, WorkSummary>,
    processingState: ProcessingState,
    // 画面操作／Webカード操作／テーマ4択の束（2026-07-27 純構造リファクタ）。旧・個別引数の
    // 「既定 no-op＝互換のため」は配線忘れを沈黙させる欠陥クラスだったため、束は全フィールド必須
    //（理由の詳細＝skins/ShelfFace.kt 冒頭）。
    actions: ShelfActions,
    webActions: ShelfWebActions,
    theme: ThemeControl,
    // 削除は選択モードの終端操作＝骨格所有の選択状態機械（ShelfSelection）へここで束ねる（束の生成は本関数内）。
    onDeleteBooks: (List<BookEntity>, deleteSource: Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    // 高負荷スカイ試作トグル（ADR 0023）。M 構造（BookshelfSkyM/LogM）の⋮メニューへ debug 限定で素通し。
    // 既定 false / no-op を残す理由: debug 専用ノブで、未配線でも release 挙動が変わらない（欠陥クラス外）。
    highLoadSkyM: Boolean = false,
    onHighLoadSkyChange: (Boolean) -> Unit = {},
    // 遷移ジャンク対策（P2・Perfetto 2026-07-16）: true の間（＝本棚の enter アニメ中）は重い Lazy コンテナを
    // スケルトンへ差替える。既定 false＝既存の呼出し・Robolectric テストの描画は完全に不変。
    deferHeavyContent: Boolean = false,
    // 本文欠落→再取込（2026-07-29 案B＋案C）。既定値は既存呼び出し・テストの互換（空＝欠落なし表示で従来どおり）。
    reimportPlans: Map<String, ReimportPlan> = emptyMap(),
    sweepBannerVisible: Boolean = false,
    onSweepLater: () -> Unit = {},
    onSweepConfirm: () -> Unit = {},
    // 案X: フォルダ走査の進捗（null=走査していない）と停止。既定は「走査していない」＝従来どおりの描画。
    folderScan: ScanProgress? = null,
    onScanStop: () -> Unit = {},
) {
    val isLoading = uiState is BookshelfUiState.Loading
    val books = (uiState as? BookshelfUiState.Content)?.books ?: emptyList()
    val webNovels = (uiState as? BookshelfUiState.Content)?.webNovels ?: emptyList()
    // 機能②: Web カードの読書位置（ncode→最後に開いた話）。mergeShelfItems が各 Web カードへ載せる。
    val webReadingProgress = (uiState as? BookshelfUiState.Content)?.webReadingProgress ?: emptyMap()
    // web 最終接触時刻（ncode→lastReadAt）。触った web カードを接触時刻で並べる（webRecencyKeyOf・2026-07-26 裁定変更）。
    val webLastReadAt = (uiState as? BookshelfUiState.Content)?.webLastReadAt ?: emptyMap()

    // 読書状態フィルタの選択（「すべて/よみかけ/未読/読了」＝モック .filters）。回転・再生成で選択が
    // 飛ばないよう rememberSaveable で保持する。ReadingStatus enum は直接 Saveable でないため
    // name(String) で保存し、復元時に entries から引き直す（null＝「すべて」）。
    var selectedStatusName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedStatus = selectedStatusName?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } }

    // 複数選択削除（残8・案B裁定）: 長押しで選択モードに入り、下端バーの「削除」→確認ダイアログでまとめて
    // 削除する（Undo は持たず確認ダイアログで事前同意を取る＝旧「確認 < Undo」を上書き）。選択は
    // rememberSaveable で回転耐性を持たせる（String id）。選択0件になったら自動で選択モードを抜ける。
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedIds = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val exitSelection: () -> Unit = { selectionMode = false; selectedIds.clear() }
    val toggleSelect: (String) -> Unit = { id ->
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) selectionMode = false // 0件自動解除（変種B裁定の解除導線の1つ）
    }
    val enterSelection: (String) -> Unit = { id ->
        selectionMode = true
        if (id !in selectedIds) selectedIds.add(id)
    }
    // システム戻るで選択モードを解除（右上×非依存の解除導線・変種B裁定）。選択モード中のみ消費する。
    BackHandler(enabled = selectionMode) { exitSelection() }

    val visibleBooks = books

    // 各読書状態の件数（ia Minor 2026-07-12・0件チップの dim 判定用）。可視の蔵書に加え Web作品も
    // 合流して数える（全スキンが filterShelfByStatus に webReadingProgress を配線済み＝実フィルタが Web を
    // 含むため、チップ件数だけ蔵書のみだと件数と表示が食い違う）。判定は shelfStatusCounts 内で共有関数を使う。
    val statusCounts = remember(visibleBooks, webNovels, progressMap, chapterCountMap, webReadingProgress) {
        shelfStatusCounts(visibleBooks, webNovels, progressMap, chapterCountMap, webReadingProgress)
    }

    // ── 引数の束（2026-07-27 純構造リファクタ）: 面へ渡す契約を6束に集約（定義と Why＝skins/ShelfFace.kt）──
    val shelfData = ShelfData(
        books = visibleBooks,
        webNovels = webNovels,
        webReadingProgress = webReadingProgress,
        webLastReadAt = webLastReadAt,
        progressMap = progressMap,
        chapterCountMap = chapterCountMap,
        newEpisodeNovelMap = newEpisodeNovelMap,
        reimportPlans = reimportPlans,
    )
    val chrome = ShelfChrome(
        selectedStatus = selectedStatus,
        statusCounts = statusCounts,
        onSelectStatus = { selectedStatusName = it?.name },
        processingState = processingState,
        isLoading = isLoading,
        sweepBannerVisible = sweepBannerVisible,
        onSweepLater = onSweepLater,
        onSweepConfirm = onSweepConfirm,
        folderScan = folderScan,
        onScanStop = onScanStop,
    )
    val selection = ShelfSelection(
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onToggleSelect = toggleSelect,
        onEnterSelection = enterSelection,
        onExitSelection = exitSelection,
        // 全選択: 骨格所有の selectedIds をまとめて差し替える（対象 id の算出は一覧側が蔵書のみで行う）。
        onSelectAll = { ids -> selectedIds.clear(); selectedIds.addAll(ids) },
        onDeleteBooks = onDeleteBooks,
    )

    // 装着スキンの面へ委譲する受付（ADR 0022 §1 の薄いルーターの sealed 化）。スキン列挙と面のビュー切替は
    // rememberShelfFace（skins/）が持ち、ここは役割2分岐だけ＝没入面（閲覧専用）には選択・Web操作の束を
    // 渡すシグネチャ自体が無い（コンパイル時制約）。null＝D/C はこの下の共通描画（D 構造へトークン写像）。
    // 各面は選択削除・Webカード操作・状態フィルタ・PDF追加・取込中バナー・スナックバー・空状態を全数
    // 引き継ぐ（本骨格所有の単一状態機械を共有渡し＝二重実装回避。上の BackHandler も 1 本のまま効く）。
    when (val face = rememberShelfFace(highLoadSkyM, onHighLoadSkyChange)) {
        is ShelfFace.Immersive -> {
            face.content(shelfData, chrome, actions, theme, snackbarHostState)
            return
        }
        is ShelfFace.Listing -> {
            face.content(shelfData, chrome, actions, theme, selection, webActions, snackbarHostState)
            return
        }
        null -> Unit // 既定描画へ（この下の共通実装が D/C を描く）
    }

    // ── 束の展開（D/C 共通描画用の局所別名＝以降の本体参照を変えない） ──
    val onOpenBook = actions.onOpenBook
    val onFabClick = actions.onFabClick
    // onOpenDiscovery/onOpenWardrobe の局所別名は撤去済み（2026-07-29 K形正本追従＝D 共通描画は発見・装い導線を持たない）。
    val onCancelProcessing = actions.onCancelProcessing
    val onOpenWebNovel = webActions.onOpenWebNovel
    val onResumeWebNovel = webActions.onResumeWebNovel
    val onImportWebNovel = webActions.onImportWebNovel
    val onRemoveWebNovel = webActions.onRemoveWebNovel

    // グリッド/リスト表示の切り替え状態（旧 is_grid_view＝route 所有からの移設）。上の when で M/P/J/K は
    // return 済み＝ここから先は D/C 専用の共通描画で、この状態も D/C 構造だけの持ち物（K は専用 k_grid_view）。
    // 既定=false（リスト＝文字目録）: 骨格3「文字目録」を本棚の既定骨格に採用（表紙を持たない
    // このアプリでは題字主役の目録が素直＝生成書影を捨てて装画を捏造しない）。グリッドは切替で残す
    // （実機で要否を詰める。将来グリッドを廃するならこのトグルと GridBookCard 経路ごと整理する）。
    val gridToggle = rememberShelfViewToggle(PrefKeys.IS_GRID_VIEW, default = false)
    val isGridView = gridToggle.value

    // 了スタンプ（案A・ADR0014 §motion 追補）: 本棚がある本を「初めて読了として描く」瞬間に朱印を一度だけ押印するための記録。
    // 未読了で描かれた本の id を貯め、読了へ遷移したら押印し、完了で id を除去する（＝以後は静的表示）。
    // なぜ「未読了で見た」を鍵にするか: 読了への遷移は多くが読書画面（本棚は非表示）で起きるため、本棚が最後にその本を
    // 未読了で描いた事実を持ち越し、復帰時の初描画で一度だけ押す。起動時から既読了の本は記録に無い＝一斉には光らない。
    // ナビ往復・回転で記録が飛ばないよう rememberSaveable（SnapshotStateList は直接 Saveable でないため listSaver で List 化）。
    val sealSeenUnfinished = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }
    // キーは安定参照の books（uiState 由来＝データ不変なら同一参照）。visibleBooks は filterNot で毎コンポーズ
    // 新インスタンスになりコルーチンが無駄に再起動するため使わない。削除保留中の本を記録しても無害（＝books で足りる）。
    LaunchedEffect(books, progressMap, chapterCountMap) {
        books.forEach { b ->
            val fin = readingStatusFor(progressMap[b.id], chapterCountMap[b.id] ?: 0) == ReadingStatus.FINISHED
            if (!fin && !sealSeenUnfinished.contains(b.id)) sealSeenUnfinished.add(b.id)
        }
    }

    // 蔵書と Web由来を「最近の活動順」で1本に混在させる（bookshelf-fusion-D の並置。純関数で合成）。
    // 前段で読書状態フィルタを噛ませる（選択中は該当状態の蔵書＋該当状態の Web を残す）。
    // webReadingProgress を渡すことで Web も状態分類される（未渡し=null だと従来どおり Web 全落とし）。
    val shelfItems = remember(visibleBooks, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(visibleBooks, webNovels, selectedStatus, progressMap, chapterCountMap, webReadingProgress)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }
    val isProcessing = processingState.isProcessing

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // FAB 展開/縮小: 下方向スクロール中は縮小、上スクロールで展開
    // 先頭位置の有無ではなく「直近の移動方向」で判定するため snapshotFlow で追う
    var fabExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(isGridView) {
        // isGridView が変わったら展開状態をリセットし、新しいリストを監視し直す
        fabExpanded = true
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow {
            if (isGridView) {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            } else {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
        }.collect { (index, offset) ->
            // 前回より下に移動していれば縮小、それ以外（上移動/停止）は展開
            val scrollingDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
            fabExpanded = !scrollingDown
            prevIndex = index
            prevOffset = offset
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            Column {
                // TopAppBar を固定表示。スクロール連動を廃止。
                // scrollBehavior による吸着アニメーションがもたつき感の原因だったため完全に除去。
                TopAppBar(
                    title = {
                        // モック .top h1＋.count: 明朝題字＋薄く冊数（K形の明示冊数＝全スキン共通の構造装置）。
                        // 冊数はタイトルとベースラインで紐付ける（K の KHeader と同じ扱い＝小さくポツンと孤立させない）。
                        Row {
                            Text(
                                "本棚",
                                fontFamily = MinchoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = FontHomeTitle,
                                letterSpacing = 2.sp,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Spacer(Modifier.width(Spacing.S8))
                            // 冊数＝ライブラリ総数（蔵書＋Web由来）。フィルタ非依存の実データ件数（K の KHeader と同一定義）。
                            Text(
                                "${books.size + webNovels.size}冊",
                                style = MaterialTheme.typography.titleMedium,
                                color = LocalShelfColors.current.infoText,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    },
                    actions = {
                        // グリッド/リスト切り替え（モック .top の唯一のアクション）。この描画部は D/C 専用
                        // （M/P/J/K は上流の when で return 済み＝旧・スキン別トグル分岐は到達不能の残骸だったため撤去）。
                        IconButton(onClick = gridToggle::toggle) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                contentDescription = if (isGridView) "リスト表示" else "グリッド表示",
                            )
                        }
                        // 見つける🔍・装いの間（Checkroom）は撤去した（2026-07-29 ユーザー裁定＝K形正本 bookshelf-D.html 追従）:
                        // 発見は恒常ナビ「さがす」タブへ完全分離・装いの間の入口は設定タブ「きせかえ」へ移管（ADR 0021 追記）。
                        // ⋮ オーバーフロー（テーマ4択・新着通知・debug診断）は撤去済み（2026-07-24 K形伝播・系2）。
                        // なぜ撤去か（全スキンの⋮を貫く同基準）: これらは設定タブ（SettingsScreenK＝テーマ/通知/取込診断）へ
                        // 移行済みで、同一機能が本棚⋮と設定の2箇所に重複していた。設定を単一正本に寄せ、重複導線を断つ。
                        // D では⋮内の項目が上記3つだけ＝撤去すると空になるため、⋮ボタンごと除いた（空メニューを残さない）。
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                // PDF処理中バナー（TopAppBar直下からスライドイン）
                AnimatedVisibility(
                    visible = isProcessing,
                    // 入退場を Motion トークン経由の tween で明示（d-motion Minor 2026-07-12）。
                    // 既定の spring 任せだと enter/exit が同一曲線・同時間で「退場が入場より短い」則を満たさない。
                    // reveal(250)＝気づかせる長さ／dismiss(150)＝作業を邪魔しない短さ（Motion.kt が正本）。
                    enter = slideInVertically(
                        animationSpec = tween(MotionDurationReveal),
                        initialOffsetY = { -it },
                    ) + fadeIn(animationSpec = tween(MotionDurationReveal)),
                    exit = slideOutVertically(
                        animationSpec = tween(MotionDurationDismiss),
                        targetOffsetY = { -it },
                    ) + fadeOut(animationSpec = tween(MotionDurationDismiss)),
                ) {
                    ProcessingBanner(
                        processingState = processingState,
                        onStop = onCancelProcessing,
                        // 幅指定は呼び出し側の責務。従来の内部 fillMaxWidth と同じ描画を維持。
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 本文欠落の一括検出バナー（案C）。取込中バナーと同じヘッダ直下スロット・同じ Motion 出没。
                // 表示可否（新規検出の指紋）は VM が判定＝ここは可視フラグの写しを描くだけ。
                AnimatedVisibility(
                    visible = sweepBannerVisible,
                    enter = slideInVertically(
                        animationSpec = tween(MotionDurationReveal),
                        initialOffsetY = { -it },
                    ) + fadeIn(animationSpec = tween(MotionDurationReveal)),
                    exit = slideOutVertically(
                        animationSpec = tween(MotionDurationDismiss),
                        targetOffsetY = { -it },
                    ) + fadeOut(animationSpec = tween(MotionDurationDismiss)),
                ) {
                    ReimportSweepBanner(
                        missingCount = reimportPlans.size,
                        onLater = onSweepLater,
                        onReimport = onSweepConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // PDF フォルダ走査の進捗バナー（案X）。検出バナーと同じスロット・同じ Motion で排他表示。
                // 退場アニメの間 folderScan は既に null になっているため、直前の非 null 値を保持して描く
                // （素の null 参照だと最後のフレームで中身が消えて枠だけ残る）。
                // 保持箱をスナップショット状態にしないのは composition 中の state 書き込みを避けるため＝
                // 再コンポーズは folderScan の変化が既に起こすので、ここは読み書きできる箱で足りる。
                val lastScan = remember { arrayOfNulls<ScanProgress>(1) }
                folderScan?.let { lastScan[0] = it }
                AnimatedVisibility(
                    visible = folderScan != null,
                    enter = slideInVertically(
                        animationSpec = tween(MotionDurationReveal),
                        initialOffsetY = { -it },
                    ) + fadeIn(animationSpec = tween(MotionDurationReveal)),
                    exit = slideOutVertically(
                        animationSpec = tween(MotionDurationDismiss),
                        targetOffsetY = { -it },
                    ) + fadeOut(animationSpec = tween(MotionDurationDismiss)),
                ) {
                    lastScan[0]?.let { progress ->
                        ReimportScanBanner(
                            progress = progress,
                            onStop = onScanStop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // 選択モード中は追加FABを隠し、下端の選択アクションバー（bottomBar）へ場を譲る（残8・案B）。
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    text = { Text("PDFを追加") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onFabClick,
                    expanded = fabExpanded,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        bottomBar = {
            // 選択モードの下端固定アクションバー（残8・案B＋変種B）。解除は左端「キャンセル」＝右上×非依存。
            if (selectionMode) {
                SelectionActionBar(
                    count = selectedIds.size,
                    onCancel = exitSelection,
                    onSelectAll = {
                        // 全選択に Web由来カードも含める（系3）。選択キーは蔵書=bare book.id・Web=ShelfItem.Web.key("web:<ncode>")。
                        val allIds = shelfItems.map { item ->
                            when (item) {
                                is ShelfItem.Book -> item.book.id
                                is ShelfItem.Web -> item.key
                            }
                        }
                        selectedIds.clear()
                        selectedIds.addAll(allIds)
                    },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        },
        snackbarHost = {
            // スナックバーをスワイプで即消せるようにする（M3 既定は swipe-to-dismiss 無し＝通知が邪魔なとき
            // 払えない・実使用フィードバック 2026-07-14）。スワイプ確定＝data.dismiss()＝showSnackbar は
            // SnackbarResult.Dismissed を返す（取込失敗の再試行・情報通知に使う。削除は選択モードの確認ダイアログへ移行）。
            SnackbarHost(snackbarHostState) { data ->
                // key(data): スナックバーが入れ替わっても前のスワイプ位置を引き継がないよう毎回作り直す。
                key(data) {
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        // 背景は出さない＝スワイプで滑って消えるだけ（確定色や削除アイコンは意味的に不要）。
                        backgroundContent = {},
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Snackbar(data)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLoading) {
                // 初回DB発行前は表紙スケルトンを出す（F-O）。Content(空) が確定するまで空状態を出さない
                // ことで cold start の空フラッシュ（Loading と Empty の混同）を防ぐ。
                BookshelfSkeleton(isGridView = isGridView, modifier = Modifier.fillMaxSize())
            } else if (shelfItems.isEmpty() && !isProcessing && selectedStatus == null) {
                // 空状態。サイズ指定は呼び出し側の責務（fillMaxSize は従来と同じ描画）。
                // なぜ排他分岐で空のグリッド/リストを合成しないか: 以前は空状態の上にも fillMaxSize の
                // Lazy コンテナが重なっており、scrollable が hit test 上で下層の「PDFを追加する」ボタンを
                // 遮蔽してタップ不能だった（Robolectric の結線テストで検出した実バグ）。空棚では帯・フィルタも
                // Lazy も描くものが無いため、排他分岐にして遮蔽を根元から無くす（帯は EmptyBookshelf と重なる
                // ため空棚では出さない）。
                EmptyBookshelf(onAddClick = onFabClick, modifier = Modifier.fillMaxSize())
            } else {
                // 発見帯『新しい物語を見つける』（FindGuideBand）は撤去した（2026-07-29 ユーザー裁定＝K形正本
                // bookshelf-D.html 追従。発見は恒常ナビ「さがす」タブへ完全分離＝本棚は発見導線を持たない）。
                // フィルタ行の hoist（Lazy コンテナの外＝固定ヘッダ）は温存: LazyVerticalGrid に stickyHeader が無く
                // 本棚はグリッド/リスト2モードのため、外への hoist が両モード一律 sticky の素直な解（2026-07-14 裁定）。
                Column(modifier = Modifier.fillMaxSize()) {
                    // 読書状態フィルタのチップ行（sticky＝常時表示。「すべて」へ戻れる導線を保つ）。
                    StatusChipRow(
                        selectedStatus = selectedStatus,
                        onSelect = { selectedStatusName = it?.name },
                        statusCounts = statusCounts,
                        modifier = Modifier.padding(
                            start = Spacing.S24, top = Spacing.S8, end = Spacing.S24, bottom = Spacing.S12,
                        ),
                    )
                    if (selectedStatus != null && shelfItems.isEmpty()) {
                        // 状態フィルタ絞り込みで0件（蔵書ゼロではない）＝ヘッダは残しつつ静かな案内を出す。
                        StatusFilterEmptyText(modifier = Modifier.padding(horizontal = Spacing.S24))
                    } else if (deferHeavyContent) {
                        // 遷移ジャンク対策（P2）: slide push の enter アニメ中は Lazy グリッド/リストの初回 measure
                        //（実測51ms/フレーム＝ジャンク主因）がアニメフレームと同居して落ちる。Compose にサブツリーの
                        // measure 凍結 API は無いため、遷移中だけ非 Lazy・固定寸で measure が格安な既存スケルトンへ
                        // 差替え、実グリッドの measure をアニメ完了後の単独フレームへ移送する（版面＝左右24dp・列構成が
                        // 一致するため輪郭のジャンプは無く、「読込→着地」の自然な体感になる）。gridState/listState・
                        // フィルタ・選択は本分岐の外の remember(Saveable) が保持＝差替えを跨いでも失われない。
                        BookshelfSkeleton(isGridView = isGridView, modifier = Modifier.fillMaxSize())
                    } else if (isGridView) {
                        // ────── グリッドレイアウト ──────
                        LazyVerticalGrid(
                            // 幅適応（reach Major 2026-07-12）: 固定2列を廃し、窓幅から列数を自動導出する。
                            // minSize の逆算（Compose の Adaptive は列数 = floor((available + spacing)/(minSize + spacing))）:
                            //   available = 画面幅 - 左右 contentPadding(24+24=48dp)、spacing = 列間 24dp（F拡張7段で 20→24）。
                            //   よって列数 = floor((幅 - 24) / (minSize + 24))。minSize=124dp とすると
                            //   幅320dp→2列 / 360〜430dp(一般的なスマホ)→2列 / 480dp以上→3列 / 600dp→3列 / 768dp→5列。
                            //   gap 24 化で旧 minSize=126 のままだと 320dp が1列に落ちる（2*126+24=276 > 272=320-48
                            //   ＝境界ちょうどの較正だった）ため、列数表を保存するよう minSize を再導出した。
                            //   ＝一般的なスマホ(≤430dp)は従来どおり2列で影響0、大画面(≥600dp 等)で自然に多列化する。
                            columns = GridCells.Adaptive(minSize = 124.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            // 上端余白はヘッダ（フィルタ行の bottom）が持つため、top は書影の影クリアランス分のみ。
                            // bottom にFAB分の余白を足す（FABは浮動でレイアウト領域を予約しないため、無いと最終行が隠れる）。
                            // ナビバーインセットはScaffoldのinnerPadding(Box.padding)で吸収済みなので二重加算しない。
                            contentPadding = PaddingValues(start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = Insets.ScrollBottomForFab),
                            verticalArrangement = Arrangement.spacedBy(Spacing.S24),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S24),
                        ) {
                            // contentType=型: 蔵書/Web はカード構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                            items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
                                when (item) {
                                    is ShelfItem.Book -> {
                                        // 了スタンプ（案A）: 読了かつ「未読了で見た記録」がある本＝初めて読了として描く瞬間に一度だけ押印。
                                        val finished = readingStatusFor(
                                            progressMap[item.book.id], chapterCountMap[item.book.id] ?: 0,
                                        ) == ReadingStatus.FINISHED
                                        GridBookCard(
                                            book = item.book,
                                            progress = progressMap[item.book.id],
                                            novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                            totalChaps = chapterCountMap[item.book.id] ?: 0,
                                            onOpen = { onOpenBook(item.book) },
                                            // 削除時の詰め直しアニメ。旧animateItemPlacementはFoundation1.6系で高速フリング中に
                                            // カバーが画面外の古い位置から補間され重なる既知不具合があり一時撤去していたが、
                                            // BOM 2025.02.00(Foundation 1.7系)でstable化したanimateItem()に置き換えて復活（案B）。
                                            modifier = Modifier.animateItem(),
                                            selectionMode = selectionMode,
                                            selected = item.book.id in selectedIds,
                                            onToggleSelect = { toggleSelect(item.book.id) },
                                            onEnterSelection = { enterSelection(item.book.id) },
                                            playSealStamp = finished && sealSeenUnfinished.contains(item.book.id),
                                            onSealStamped = { sealSeenUnfinished.remove(item.book.id) },
                                            // 本文欠落（案B）: バッジ＋状態行の差し替え。文言は domain が正本。
                                            missingLabel = reimportPlans[item.book.id]?.let { reimportStatusLabel(it) },
                                        )
                                    }
                                    // (b) Web由来カード。外す操作は確認ダイアログを挟まない: 蔵書削除と違い
                                    // 読書進捗等の失うものが無く、詳細画面の「本棚に置く」で即座に戻せるため。
                                    is ShelfItem.Web -> WebGridBookCard(
                                        novel = item.novel,
                                        // 機能②: 記録があれば「続きから読む 第N話」を出す（0＝未読で非表示）。
                                        lastReadEpisode = item.lastReadEpisode,
                                        onOpen = { onOpenWebNovel(item.novel) },
                                        onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                        onImport = { onImportWebNovel(item.novel) },
                                        onRemove = { onRemoveWebNovel(item.novel) },
                                        modifier = Modifier.animateItem(),
                                        // 複数選択削除（系3）: Web由来カードも選択に参加。選択キーは ShelfItem.Web.key="web:<ncode>"。
                                        selectionMode = selectionMode,
                                        selected = item.key in selectedIds,
                                        onToggleSelect = { toggleSelect(item.key) },
                                        onEnterSelection = { enterSelection(item.key) },
                                    )
                                }
                            }
                        }
                    } else {
                        // ────── リストレイアウト ──────
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // bottom にFAB分の余白を足す（グリッドと同理由＝最終行がFABに隠れるのを防ぐ）。
                            // 行間スペーシングは置かない: 各行が自前の縦余白＋下ヘアラインで区切るモック .li 準拠のため。
                            contentPadding = PaddingValues(start = Spacing.S24, top = Spacing.S4, end = Spacing.S24, bottom = Insets.ScrollBottomForFab),
                        ) {
                            // contentType=型: 蔵書/Web はカード構成が別物のため、要素の再利用プールを型ごとに分ける（性能のみ・見た目不変）
                            items(shelfItems, key = { it.key }, contentType = { it::class }) { item ->
                                when (item) {
                                    is ShelfItem.Book -> ListBookCard(
                                        book = item.book,
                                        progress = progressMap[item.book.id],
                                        novelDetail = item.book.ncode?.let { newEpisodeNovelMap[it] },
                                        totalChaps = chapterCountMap[item.book.id] ?: 0,
                                        onOpen = { onOpenBook(item.book) },
                                        // グリッドと同理由: 1.7系でstable化したanimateItem()で詰め直しアニメを復活（案B）。
                                        modifier = Modifier.animateItem(),
                                        selectionMode = selectionMode,
                                        selected = item.book.id in selectedIds,
                                        onToggleSelect = { toggleSelect(item.book.id) },
                                        onEnterSelection = { enterSelection(item.book.id) },
                                        // 本文欠落（案B）: 状態行の差し替え（目録は書影なし＝バッジは出ない）。
                                        missingLabel = reimportPlans[item.book.id]?.let { reimportStatusLabel(it) },
                                    )
                                    is ShelfItem.Web -> WebListBookCard(
                                        novel = item.novel,
                                        // 機能②: 記録があれば「続きから読む 第N話」を出す（0＝未読で非表示）。
                                        lastReadEpisode = item.lastReadEpisode,
                                        onOpen = { onOpenWebNovel(item.novel) },
                                        onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                        onImport = { onImportWebNovel(item.novel) },
                                        onRemove = { onRemoveWebNovel(item.novel) },
                                        modifier = Modifier.animateItem(),
                                        // 複数選択削除（系3）: Web由来カードも選択に参加。選択キーは ShelfItem.Web.key="web:<ncode>"。
                                        selectionMode = selectionMode,
                                        selected = item.key in selectedIds,
                                        onToggleSelect = { toggleSelect(item.key) },
                                        onEnterSelection = { enterSelection(item.key) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 複数選択削除の確認ダイアログ（残8・案B裁定＋系3 の Web統合）。破壊確定「削除する」を主CTA塗りにせず藍で扱い、
    // 「やめる」を低摩擦の逃げ道に置く（正本 .dlg）。確定で蔵書は本文ごと削除・Web は本棚から外し、選択モードを抜ける（Undo なし）。
    if (showDeleteConfirm) {
        val bookTargets = books.filter { it.id in selectedIds }
        // Web由来（未取込）カードも選択削除の対象（系3）。選択キー "web:<ncode>" を ncode へ分解し webNovels と突合する。
        val webNcodes = webNcodesInSelection(selectedIds).toSet()
        val webTargets = webNovels.filter { it.ncode in webNcodes }
        // 取込元 URI を保持する（＝取込元PDFを削除できる）蔵書の件数。0 なら取込元削除チェックは出さない（Web は sourceUri を持たない＝不変）。
        val deletableCount = bookTargets.count { it.sourceUri != null }
        val total = bookTargets.size + webTargets.size
        // 欠落本を含む削除は「復元の最後の機会」を消す（機序＝domain/ReimportPlan.kt の該当節）。
        // 判定の根拠は棚バッジ・カードタップの復旧導線と同じ reimportPlans ＝欠落の定義を二重化しない。
        val lossWarning = missingContentDeleteWarning(
            missingCount = countMissingContentTargets(bookTargets.map { it.id }, reimportPlans),
            bookCount = bookTargets.size,
        )
        // 既定 OFF（ユーザー選択=削除ダイアログのチェック・破壊的なので明示 ON を要求）。ダイアログを開くたびリセット。
        var alsoDeleteSource by remember { mutableStateOf(false) }
        NovelReaderAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            // 蔵書とWebが混じり得るため中立の「件」で数える（蔵書のみでも自然）。
            title = { Text("選択した${total}件を本棚から削除しますか？") },
            text = {
                Column {
                    // 欠落本の警告は本文の先頭に置く（後段の一般文＝「取り消せません」より固有かつ重い情報のため）。
                    // 欠落0冊なら描画そのものが無い＝通常の削除ダイアログは従来と1ピクセルも変わらない。
                    MissingContentDeleteWarningText(lossWarning)
                    // 選択内訳（蔵書数・Web数）で本文を出し分け（系3）＝Web に「本文データも削除」の虚偽を出さない。
                    Text(deleteConfirmBody(bookTargets.size, webTargets.size))
                    DeleteSourcePdfOption(deletableCount, alsoDeleteSource) { alsoDeleteSource = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    // 蔵書は本文データごと削除／Web は本棚から外す（既存 removeWebNovel を一括適用）。空側は呼ばない。
                    if (bookTargets.isNotEmpty()) onDeleteBooks(bookTargets, alsoDeleteSource)
                    webTargets.forEach { onRemoveWebNovel(it) }
                    exitSelection()
                }) { Text(deleteConfirmLabel(lossWarning != null)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("やめる") }
            },
        )
    }
    // debug ヘルスボード（スクレイパー健全性診断）は本棚⋮撤去に伴い設定タブ（SettingsScreenK）の診断入口へ一本化した（系2）。
    // 本体 AdapterHealthBoardDialog は ui/AdapterHealthBoardDialog.kt へ移設済み＝SettingsScreenK が引き続き呼び出す。
}

// ============================================================
// 欠落本を含む削除の警告ブロック
// 正本＝bookshelf-multiselect-D「削除確認（欠落本を含む）」（削除確認そのものの正本）／
// 分岐4系統の文脈＝bookshelf-reimport-badge-D ⑤。
//
// なぜ削除ダイアログの部品として切り出すか: 削除確認ダイアログは D/C（本ファイル）と K/M/P/J（skins/）の
// 5実体に分かれており、警告を各所で書き下ろすと文言と判定が割れる。DeleteSourcePdfOption と同じく
// 「削除ダイアログの共有部品」として1つにし、各面は1行呼ぶだけにする。
// [warning] が null（＝欠落0冊）なら何も描かない＝通常の削除の見た目・高さを一切変えない。
//
// 意匠: モックの .dlg p は --ink-soft（＝役割トークン ShelfColors.infoText。2026-07-31 に本文既定は
// NovelReaderAlertDialog で M3 の onSurfaceVariant から infoText へ是正済み＝この行は既定と同値）で、その中の
// .warn だけが --ink（onSurface）＋font-weight 600。インラインの強調なので改行で分けず
// AnnotatedString の SpanStyle で翻訳する。段落間アキは削除確認の正本 multiselect-D の
// .dlg p{margin-bottom:24px} → Spacing.S24（reimport-badge 側の 16px は再取込ダイアログ群の律動で、
// ここは削除ダイアログの律動に合わせる）。
// ============================================================
@Composable
internal fun MissingContentDeleteWarningText(warning: MissingContentDeleteWarning?) {
    if (warning == null) return
    Text(
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) { append(warning.emphasis) }
            append(warning.detail)
        },
    )
    Spacer(Modifier.height(Spacing.S24))
}

// ============================================================
// 選択モードの下端アクションバー（残8・案B＋変種B。正本 bookshelf-multiselect-D .botbar）
// 左端「キャンセル」で解除（右上×非依存）／件数／全選択（ghost）／削除（藍フィル・押下は確認ダイアログを
// 開くだけの非破壊ステップ＝主張してよい）。navigationBarsPadding で端末ジェスチャバーを避ける。
// ============================================================
@Composable
private fun SelectionActionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.S16, vertical = Spacing.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text("キャンセル", fontSize = FontButtonLabel)
                }
                Text(
                    text = "${count}冊選択中",
                    fontSize = FontButtonLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.S8),
                )
                TextButton(onClick = onSelectAll) {
                    Text("全選択", fontSize = FontButtonLabel)
                }
                Spacer(Modifier.width(Spacing.S8))
                Button(
                    onClick = onDelete,
                    enabled = count > 0,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("削除", fontSize = FontButtonLabel)
                }
            }
        }
    }
}

// ============================================================
// 読書状態フィルタのチップ行（モック bookshelf-mokuroku-D .filters/.fc）
// 「すべて／よみかけ／未読／読了」の固定4チップをモック順に横スクロール1行で並べる。
// チップ意匠は検索画面の FilterChipItem（.rchip 翻訳の正本＝11.5sp・角丸2dp・ヘアライン→選択で藍）を
// そのまま流用する（モック .fc は同じ意匠系のため、本棚用に部品を増やさない）。
// ============================================================
@Composable
private fun StatusChipRow(
    selectedStatus: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier,
    // 各状態の件数（ia Minor）。0件の状態チップは dim（enabled=false）にして押下不能にし、
    // 「押せるのに空表示に落ちる袋小路」を予防する（件数併記でなく最小限の dim を選択）。
    statusCounts: Map<ReadingStatus, Int> = emptyMap(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S8),
    ) {
        // 「すべて」＝選択なし（null）。モックどおり既定選択。棚が非空のときだけ出る行なので常に押せる。
        FilterChipItem(
            selected = selectedStatus == null,
            label = "すべて",
            onClick = { onSelect(null) },
        )
        // よみかけ／未読／読了。ReadingStatus と表示名・並びの対応はここが唯一の正本（モック .filters 順）。
        // 0件の分類は enabled=false で淡く（disabled トークン）＝押しても空表示になる分類を先に塞ぐ。
        FilterChipItem(
            selected = selectedStatus == ReadingStatus.READING,
            label = "よみかけ",
            onClick = { onSelect(ReadingStatus.READING) },
            enabled = (statusCounts[ReadingStatus.READING] ?: 0) > 0,
        )
        FilterChipItem(
            selected = selectedStatus == ReadingStatus.UNREAD,
            label = "未読",
            onClick = { onSelect(ReadingStatus.UNREAD) },
            enabled = (statusCounts[ReadingStatus.UNREAD] ?: 0) > 0,
        )
        FilterChipItem(
            selected = selectedStatus == ReadingStatus.FINISHED,
            label = "読了",
            onClick = { onSelect(ReadingStatus.FINISHED) },
            enabled = (statusCounts[ReadingStatus.FINISHED] ?: 0) > 0,
        )
    }
}

// 状態フィルタで該当0件のときの静かな案内（EmptyBookshelf は蔵書ゼロ専用のため使わない）。
// 帯/フィルタを Lazy 外へ hoist したため、左右インセットは呼び出し側（ヘッダ）から modifier で受ける。
@Composable
private fun StatusFilterEmptyText(modifier: Modifier = Modifier) {
    Text(
        text = "この分類の本はありません",
        fontSize = FontSubTitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = Spacing.S16),
    )
}

// ============================================================
// 本棚スケルトン（F-O）: DB 初回発行前に表紙の場所取りを出し、cold start の空フラッシュを防ぐ。
// 既存カード寸法（グリッド=2列・書影2:3／リスト=46×69）に合わせた静的プレースホルダ。
// 意匠を発明しないため新規色は使わず surfaceVariant/outlineVariant トークンのみで構成する。
// シマー等のアニメは付けない（既存画面に同型の演出が無く、最小の同型要素に留めるため）。
// ============================================================
@Composable
private fun BookshelfSkeleton(
    isGridView: Boolean,
    modifier: Modifier = Modifier,
) {
    // プレースホルダの塗り（トークン経由・直書き回避）。書影は少し濃く、文字行は薄く。
    val blockColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = LocalShelfColors.current.hairline   // 本棚系 --hl

    if (isGridView) {
        // グリッド: 実カードと同じ左右24dp・列間20dp・行間26dp。3行ぶん(6枚)出す。
        Column(
            modifier = modifier.padding(start = Spacing.S24, top = Spacing.S12, end = Spacing.S24),
            verticalArrangement = Arrangement.spacedBy(Spacing.S24),
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S24)) {
                    repeat(2) {
                        Column(modifier = Modifier.weight(1f)) {
                            // 書影（2:3・角丸2px）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(blockColor),
                            )
                            Spacer(Modifier.height(Spacing.S12))
                            // タイトル2行ぶん
                            SkeletonLine(color = lineColor, widthFraction = 0.9f)
                            Spacer(Modifier.height(Spacing.S8))
                            SkeletonLine(color = lineColor, widthFraction = 0.6f)
                            Spacer(Modifier.height(Spacing.S8))
                            // 進捗行
                            SkeletonLine(color = lineColor, widthFraction = 0.7f)
                        }
                    }
                }
            }
        }
    } else {
        // リスト（文字目録）: 実行と同じ左右24dp・6行ぶん。表紙は無く、左端の色帯＋題字/進捗の場所取り。
        Column(modifier = modifier.padding(start = Spacing.S24, top = Spacing.S4, end = Spacing.S24)) {
            repeat(6) {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .padding(top = Spacing.S16, bottom = Spacing.S16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左端の色帯プレースホルダ（実カードの作品識別色の場所取り）。
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(blockColor),
                    )
                    Spacer(Modifier.width(Spacing.S16))
                    Column(modifier = Modifier.weight(1f)) {
                        // 明朝題字2行ぶん
                        SkeletonLine(color = lineColor, widthFraction = 0.85f)
                        Spacer(Modifier.height(Spacing.S8))
                        SkeletonLine(color = lineColor, widthFraction = 0.55f)
                        Spacer(Modifier.height(Spacing.S12))
                        // 進捗行
                        SkeletonLine(color = lineColor, widthFraction = 0.5f)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = lineColor)
            }
        }
    }
}

/** スケルトンの1行分プレースホルダ（角丸の細い横棒）。 */
@Composable
private fun SkeletonLine(
    color: androidx.compose.ui.graphics.Color,
    widthFraction: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(11.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/**
 * OpenMultipleDocuments に書き込み永続権限フラグを付ける ActivityResultContract。
 *
 * なぜ必要か: 取込元PDF削除機能（本削除時に取込元PDF本体も消す）には、選択された PDF に対し後から
 * DocumentsContract.deleteDocument できる書込権限が要る。素の OpenMultipleDocuments は読み取りしか要求
 * しないため、intent に FLAG_GRANT_WRITE_URI_PERMISSION を足して書込も要求する（読み取りは既定で付く）。
 * 実際に書込永続権限を保持できるかはプロバイダ次第で、take 側（BookshelfViewModel.addBook）が
 * READ|WRITE→READ のフォールバックで確定する（書込を持てない本は取込元PDF削除の対象外＝sourceUri は NULL）。
 */
private class OpenMultiplePdfWithWrite : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}

/**
 * 単数版（本文欠落→再取込の分岐②③＝PDF の選び直し・2026-07-29 案B）。write 要求の理由は
 * [OpenMultiplePdfWithWrite] と同一（選び直した PDF でも「取込元PDF削除」と以後の自動再取込を成立させる
 * ＝take 側 BookshelfViewModel.addBook の READ|WRITE→READ フォールバックも同経路で共有する）。
 */
private class OpenPdfWithWrite : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}
