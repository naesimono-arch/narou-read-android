package com.novelreader.ui

import android.Manifest
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.novelreader.NewEpisodeNotificationPreference
import com.novelreader.NovelReaderApplication
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.model.BookId
import com.novelreader.ui.discovery.FilterChipItem
import com.novelreader.narou.model.NarouNovel
import com.novelreader.ui.theme.FontButtonLabel
import com.novelreader.ui.theme.FontHomeTitle
import com.novelreader.ui.theme.FontSubTitle
import com.novelreader.ui.skins.m.BookshelfSkyM
import com.novelreader.ui.theme.Insets
import com.novelreader.ui.theme.LocalShelfColors
import com.novelreader.ui.theme.LocalSkin
import com.novelreader.ui.theme.LocalSkinTokens
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.MinchoFamily
import com.novelreader.ui.theme.Spacing
import com.novelreader.ui.theme.MotionDurationDismiss
import com.novelreader.ui.theme.MotionDurationReveal
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.viewmodel.BookshelfUiState
import com.novelreader.viewmodel.BookshelfViewModel
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ReadingStatus
import com.novelreader.viewmodel.ShelfItem
import com.novelreader.viewmodel.filterShelfByStatus
import com.novelreader.viewmodel.mergeShelfItems
import com.novelreader.viewmodel.readingStatusFor
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
    onOpenBook: (bookId: String, startFile: String) -> Unit,
    onOpenDiscovery: () -> Unit,
    // 装いの間（UIスキン選択）への入口。入口は本棚トップバーのみ（意図的設計＝ADR 0021 決定7）。
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // PDF ファイル選択ランチャー（複数同時選択対応）。
    // OpenMultipleDocuments はキャンセル時 空リストを返す。取込は addBooks へ委ね、なろう形式でない
    // PDF の仕分け・確認は VM 側で行う（OS ピッカーはファイル名/中身での絞り込み・ソート不可のため）。
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
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
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var batteryDialogDismissed by remember { mutableStateOf(prefs.getBoolean("battery_dialog_dismissed", false)) }

    // バッテリー最適化除外ダイアログの表示フラグ（onFabClickより先に宣言必須）。
    // rememberSaveable: 回転・ダーク切替（Activity 再生成）でダイアログと「二度と表示しない」
    // チェック入力が消えないよう保持する（M4）。
    var showBatteryOptDialog by rememberSaveable { mutableStateOf(false) }
    var doNotShowAgain by rememberSaveable { mutableStateOf(false) }

    // グリッド/リスト表示の切り替え状態（SharedPreferencesで永続化）。
    // 永続化を伴うためルート層で所有し、値とトグルを描画層へ渡す（描画層は VM/prefs 非依存に保つ）。
    // 既定=false（リスト＝文字目録）: 骨格3「文字目録」を本棚の既定骨格に採用（表紙を持たない
    // このアプリでは題字主役の目録が素直＝生成書影を捨てて装画を捏造しない）。グリッドは切替で残す
    // （実機で要否を詰める。将来グリッドを廃するならこのトグルと GridBookCard 経路ごと整理する）。
    var isGridView by remember { mutableStateOf(prefs.getBoolean("is_grid_view", false)) }

    // スキンM（星図）の 星図⇄一覧 モード（ADR 0022 §1・永続化）。既定 true＝M 装着時は星図で開く。
    // M 以外のスキンでは Content 側の分岐条件（LocalSkin==SEIZU_M）が成立せず、この値は眠ったまま。
    var mSkyView by remember { mutableStateOf(prefs.getBoolean("m_sky_view", true)) }

    // 通知権限 priming（notify Minor 2026-07-12）: システム権限ダイアログの前に理由説明を挟むためのフラグ。
    // 一度提示したら以後は出さない（notif_priming_shown で永続化）＝毎回のFABタップで問い直さない。
    var notifPrimingShown by remember { mutableStateOf(prefs.getBoolean("notif_priming_shown", false)) }
    var showNotifPriming by remember { mutableStateOf(false) }
    val markPrimingShown: () -> Unit = {
        notifPrimingShown = true
        prefs.edit().putBoolean("notif_priming_shown", true).apply()
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
        appTheme = appTheme,
        onThemeChange = onThemeChange,
        isGridView = isGridView,
        onToggleView = {
            isGridView = !isGridView
            prefs.edit().putBoolean("is_grid_view", isGridView).apply()
        },
        skyViewM = mSkyView,
        onToggleSkyM = {
            mSkyView = !mSkyView
            prefs.edit().putBoolean("m_sky_view", mSkyView).apply()
        },
        onFabClick = onFabClick,
        // 開く際の再開ファイル解決（suspend の DB 参照）はルート層の責務。描画層は BookEntity を渡すだけ。
        onOpenBook = { book ->
            scope.launch {
                // 境界: book.id は Room 由来の String＝型付き API へ渡す直前に BookId へ包む。
                val lastReadFile = viewModel.getLastRead(BookId(book.id)) ?: "index.html"
                onOpenBook(book.id, lastReadFile)
            }
        },
        onDeleteBooks = { viewModel.deleteBooks(it) },
        onOpenDiscovery = onOpenDiscovery,
        onOpenWardrobe = onOpenWardrobe,
        onCancelProcessing = { viewModel.cancelProcessing() },
        snackbarHostState = snackbarHostState,
        // (b)+機能②: Web由来カードのタップ＝なろうをアプリ内 WebView で開く（目次＝startEpisode 0・ADR 0012）。
        // 旧 Custom Tabs 送客(0010)から WebReader へ移行し、読書位置を URL 観測で記録できるようにする。
        onOpenWebNovel = { novel -> onReadWebNovel(novel.ncode, 0) },
        // 続きから読む＝記録した話(episode)へ WebView で直接着地する。
        onResumeWebNovel = { novel, episode -> onReadWebNovel(novel.ncode, episode) },
        onImportWebNovel = { novel -> onImportWebNovel(novel.ncode) },
        onRemoveWebNovel = { viewModel.removeWebNovel(it.ncode) },
        deferHeavyContent = deferHeavyContent,
    )

    // エラーは一度きりのイベントとして Channel から受信し Snackbar 表示する（VM イベント購読＝ルート層の責務）。
    // collect は画面の生存期間中ずっと購読し続ければよいので key は Unit。
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { event ->
            // 取込失敗（retryUri あり）は「再試行」を出し、押されたら同一 URI を再投入する（M7）。
            // それ以外（強制終了リカバリ等の情報通知）は従来どおり「閉じる」のみ。
            if (event.retryUri != null) {
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = "再試行",
                    // 失敗の再試行は見落とすと復旧手段を失うため長め表示にする。
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.retryImport(event.retryUri)
                }
            } else {
                snackbarHostState.showSnackbar(message = event.message, actionLabel = "閉じる")
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
        AlertDialog(
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
                prefs.edit().putBoolean("battery_dialog_dismissed", true).apply()
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
        AlertDialog(
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
        AlertDialog(
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
    newEpisodeNovelMap: Map<String, NarouNovel>,
    processingState: ProcessingState,
    appTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onFabClick: () -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onDeleteBooks: (List<BookEntity>) -> Unit,
    onOpenDiscovery: () -> Unit,
    // 装いの間への入口（既定 no-op＝既存テスト・呼び出しの互換のため）。
    onOpenWardrobe: () -> Unit = {},
    onCancelProcessing: () -> Unit,
    snackbarHostState: SnackbarHostState,
    // (b) Web由来・未取込カードの操作。既定 no-op は既存テスト・呼び出しの互換のため
    // （Web カードが無い状態では従来の描画・結線が完全に不変であることを保つ）。
    onOpenWebNovel: (WebNovelEntity) -> Unit = {},
    // 機能②: カードの「続きから読む 第N話」タップ＝記録した話(episode)へ WebView で直接着地する。
    onResumeWebNovel: (novel: WebNovelEntity, episode: Int) -> Unit = { _, _ -> },
    onImportWebNovel: (WebNovelEntity) -> Unit = {},
    onRemoveWebNovel: (WebNovelEntity) -> Unit = {},
    // 遷移ジャンク対策（P2・Perfetto 2026-07-16）: true の間（＝本棚の enter アニメ中）は重い Lazy コンテナを
    // スケルトンへ差替える。既定 false＝既存の呼出し・Robolectric テストの描画は完全に不変。
    deferHeavyContent: Boolean = false,
    // スキンM（星図）の表示モード（星図⇄一覧・ADR 0022 §1）。M 以外のスキンでは無視される。
    // 既定 true＝M 装着時は星図が既定（永続はルート層の onToggleSkyM に委譲）。
    skyViewM: Boolean = true,
    onToggleSkyM: () -> Unit = {},
) {
    val isLoading = uiState is BookshelfUiState.Loading
    val books = (uiState as? BookshelfUiState.Content)?.books ?: emptyList()
    val webNovels = (uiState as? BookshelfUiState.Content)?.webNovels ?: emptyList()
    // 機能②: Web カードの読書位置（ncode→最後に開いた話）。mergeShelfItems が各 Web カードへ載せる。
    val webReadingProgress = (uiState as? BookshelfUiState.Content)?.webReadingProgress ?: emptyMap()
    // 二層ソート用の web 最終接触時刻（ncode→lastReadAt）。触った web を下層へ沈める（層反転・2026-07-16）。
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

    // 各読書状態の件数（ia Minor 2026-07-12・0件チップの dim 判定用）。可視の蔵書で数える。
    val statusCounts = remember(visibleBooks, progressMap, chapterCountMap) {
        visibleBooks
            .groupingBy { readingStatusFor(progressMap[it.id], chapterCountMap[it.id] ?: 0) }
            .eachCount()
    }

    // スキンM「星図」: 星図ビュー時は画面丸ごと M 構造へ委譲する（ADR 0022 §1 の薄いルーター）。
    // 一覧トグル時はこの下の共有描画（D 構造へトークン写像＝bookshelf-M.html「一覧は可読フォールバック」）へ
    // 落ちる。複数選択削除・グリッド・Web カード操作は一覧側が全数担う（星図は閲覧と読書導線に徹する）。
    if (LocalSkin.current == Skin.SEIZU_M && skyViewM) {
        BookshelfSkyM(
            books = visibleBooks,
            progressMap = progressMap,
            chapterCountMap = chapterCountMap,
            newEpisodeNovelMap = newEpisodeNovelMap,
            processingState = processingState,
            webNovelCount = webNovels.size,
            selectedStatus = selectedStatus,
            statusCounts = statusCounts,
            onSelectStatus = { selectedStatusName = it?.name },
            onOpenBook = onOpenBook,
            onOpenDiscovery = onOpenDiscovery,
            onOpenWardrobe = onOpenWardrobe,
            onFabClick = onFabClick,
            onToggleList = onToggleSkyM,
            onCancelProcessing = onCancelProcessing,
            snackbarHostState = snackbarHostState,
            isLoading = isLoading,
        )
        return
    }

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
    // 前段で読書状態フィルタを噛ませる（選択中は該当蔵書のみ・Web カードは落とす＝filterShelfByStatus の why）。
    val shelfItems = remember(visibleBooks, webNovels, progressMap, selectedStatus, chapterCountMap, webReadingProgress, webLastReadAt) {
        val (filteredBooks, filteredWeb) =
            filterShelfByStatus(visibleBooks, webNovels, selectedStatus, progressMap, chapterCountMap)
        mergeShelfItems(filteredBooks, progressMap, filteredWeb, webReadingProgress, webLastReadAt)
    }
    val isProcessing = processingState.isProcessing

    // 本棚トップバーの⋮オーバーフロー（テーマ切替＋開発トグル）の開閉状態
    var showOverflowMenu by remember { mutableStateOf(false) }

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
                        // モック .top h1: 明朝・字間広め・中肉。余白主導のエディトリアル題字。
                        Text(
                            "本棚",
                            fontFamily = MinchoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = FontHomeTitle,
                            letterSpacing = 2.sp,
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenDiscovery) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                // 着地は発見ホーム（画面名「見つける」）＝ラベルと着地を一致させる
                                // （用語辞書 docs/patterns/discovery-terminology.md・「探す」は検索画面の語）。
                                contentDescription = "見つける",
                            )
                        }
                        // グリッド/リスト切り替え（モック .top の第1アクション）。永続化はルート層の onToggleView に委譲。
                        // スキンM の一覧フォールバック中はこのボタンが「星図へ戻る」になる（星図⇄一覧の2態＝
                        // bookshelf-M.html。M にグリッドは無い＝可読フォールバックは文字目録のみ）。
                        if (LocalSkin.current == Skin.SEIZU_M) {
                            IconButton(onClick = onToggleSkyM) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "星図表示に切替",
                                )
                            }
                        } else {
                            IconButton(onClick = onToggleView) {
                                Icon(
                                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                    contentDescription = if (isGridView) "リスト表示" else "グリッド表示",
                                )
                            }
                        }
                        // 装いの間（UIスキン選択）への入口。入口は本棚のみ＝意図的設計（ADR 0021 決定7）。
                        IconButton(onClick = onOpenWardrobe) {
                            Icon(
                                imageVector = Icons.Filled.Checkroom,
                                contentDescription = "着せ替え",
                            )
                        }
                        // ⋮ オーバーフロー（モック .top の第2アクション）。
                        // テーマ切替を本棚からも行えるようにする（読書と同じ単一正本 appTheme を変更）。
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                // 1変種スキン（C 夜行・M 星図等）ではテーマ3択が無意味＝節ごと畳む
                                // （ReadingSettingsSheet の skinHasThemeChoice と同じ機構。これまで本メニューだけ
                                // 畳み漏れで C でも3択が出ていた＝supportedThemes を単一真実源に是正）。
                                if (LocalSkinTokens.current.supportedThemes.size > 1) {
                                Text(
                                    "テーマ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
                                )
                                // ライト/セピア/ダーク。選択中に藍のチェックを付ける。
                                ReadingTheme.values().forEach { theme ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (theme) {
                                                    ReadingTheme.LIGHT -> "ライト"
                                                    ReadingTheme.SEPIA -> "セピア"
                                                    ReadingTheme.DARK -> "ダーク"
                                                }
                                            )
                                        },
                                        onClick = {
                                            onThemeChange(theme)
                                            showOverflowMenu = false
                                        },
                                        leadingIcon = {
                                            // 選択中のみチェック表示（未選択はアイコン領域を空けて字頭を揃える）
                                            if (appTheme == theme) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "選択中",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                Spacer(Modifier.width(Spacing.S24))
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider()
                                } // テーマ節ここまで（1変種スキンでは節ごと非表示）
                                // 新着話通知のオプトイン（UX監査 C3・公理13「沈黙が既定値」＝既定OFF）。
                                // なぜここか: 本アプリ唯一の常設メニュー面で、専用設定画面を新設せずに済む
                                // （UX/19: 設定面は増やさない）。トグルの説明文が priming を兼ねるため、
                                // ON 操作の直後に OS 権限ダイアログを出してよい（無説明の権限要求にならない）。
                                NewEpisodeNotificationMenuSection()
                            }
                        }
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
                        val allIds = shelfItems.filterIsInstance<ShelfItem.Book>().map { it.book.id }
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
                // 帯＋フィルタを Lazy コンテナの外＝固定ヘッダへ hoist する（2026-07-14 C② collapse 再設計・裁定＝完全退避）。
                // なぜ hoist か: 発見帯『新しい物語を見つける』は位置も役割も変わらない同一要素のため、スクロールで
                // restyle（箱→1行・墨→藍）すると「変わらないものが姿だけ変える」違和感を生む（2026-07-14 実機却下の
                // 本質原因）。よって帯は restyle せず、先頭到達時のみフル表示・下スクロールで AnimatedVisibility で畳んで
                // 退避する（フルの見た目のまま隠すだけ）。フィルタ行は常時表示＝sticky にして「フィルタが常に届く」を担保する。
                // LazyVerticalGrid に stickyHeader が無く本棚はグリッド/リスト2モードのため、Lazy の外への hoist が
                // 両モード一律 sticky の素直な解（発見は第二の柱＝いずれ再調整予定・handover ★残1 の設計メモ）。
                val density = LocalDensity.current
                val showBand by remember(isGridView, density) {
                    derivedStateOf {
                        // 先頭の書影が最上部付近にある時のみ帯フル表示。8dp は微小スクロールでの帯のちらつきを防ぐ
                        // デッドゾーン（余白トークンでなく操作の hysteresis 閾値のため .dp 直書きでよい・spacing lint 対象外）。
                        val threshold = with(density) { Spacing.S8.toPx() }
                        val (index, offset) = if (isGridView) {
                            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                        } else {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }
                        index == 0 && offset < threshold
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    // 発見帯（完全退避）: restyle でなく「高さを 0 へ畳んで」退避する（fade 併用・reveal250/dismiss150＝Motion.kt が正本）。
                    // なぜ slide でなく shrink/expand か: slideOut+fadeOut だと AnimatedVisibility 既定の size 縮小が
                    // 置き換わり、退避アニメ中は帯の占有スペースがフルのまま予約され続け、終了の瞬間に一気に除去される
                    // →下のグリッドが最後にカクッと跳ねる（2026-07-14 実機所見「消える瞬間のくっと」の真因）。自作モックの
                    // max-height:0 遷移に忠実な高さ連続縮小に戻し、スクロールと連続して畳むことでスナップを無くす。
                    // shrinkTowards/expandFrom=Top＝上端（バー側）を固定し下端を畳む＝フィルタ以下が滑らかに追従する。
                    AnimatedVisibility(
                        visible = showBand,
                        enter = expandVertically(
                            animationSpec = tween(MotionDurationReveal),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(MotionDurationReveal)),
                        exit = shrinkVertically(
                            animationSpec = tween(MotionDurationDismiss),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(MotionDurationDismiss)),
                    ) {
                        FindGuideBand(
                            onClick = onOpenDiscovery,
                            modifier = Modifier.padding(
                                start = Spacing.S24, top = Spacing.S12, end = Spacing.S24, bottom = Spacing.S4,
                            ),
                        )
                    }
                    // 読書状態フィルタのチップ行（sticky＝常時表示。帯が退避しても残し「すべて」へ戻れる導線を保つ）。
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
                            items(shelfItems, key = { it.key }) { item ->
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
                            items(shelfItems, key = { it.key }) { item ->
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
                                    )
                                    // グリッドと同じ判断（確認ダイアログ無し＝失うものが無く即座に戻せる）。
                                    is ShelfItem.Web -> WebListBookCard(
                                        novel = item.novel,
                                        // 機能②: 記録があれば「続きから読む 第N話」を出す（0＝未読で非表示）。
                                        lastReadEpisode = item.lastReadEpisode,
                                        onOpen = { onOpenWebNovel(item.novel) },
                                        onResume = { onResumeWebNovel(item.novel, item.lastReadEpisode) },
                                        onImport = { onImportWebNovel(item.novel) },
                                        onRemove = { onRemoveWebNovel(item.novel) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 複数選択削除の確認ダイアログ（残8・案B裁定）。不可逆（本文データも消える）を本文コピーで明示。
    // 寒色D に danger-red は無い＝破壊確定「削除する」を主CTA塗りにせず藍で扱い、「やめる」を低摩擦の
    // 逃げ道として置く（正本 .dlg）。確定で選択本をまとめて削除し選択モードを抜ける（Undo は持たない）。
    if (showDeleteConfirm) {
        val targets = books.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("選択した${targets.size}冊を本棚から削除しますか？") },
            text = { Text("変換済みの本文データも削除されます。この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteBooks(targets)
                    exitSelection()
                }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("やめる") }
            },
        )
    }
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
// 見つける導線帯（モック bookshelf-fusion-D .find-guide）
// 本棚先頭に置く静かな発見入口。TopAppBar の🔍と役割が重なるが、モックは両方持つ
// （帯＝発見機能を知らない人への明示導線／🔍＝知っている人の常設ショートカット）。
// ============================================================
@Composable
private fun FindGuideBand(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S16, vertical = Spacing.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(Spacing.S8))
        Text(
            text = "新しい物語を見つける",
            fontSize = FontButtonLabel,
            color = MaterialTheme.colorScheme.onSurface,
            // フォントスケール拡大時の窮屈対策（2026-07-08 実機所見）: weight(1f) の文字箱は
            // シェブロンの縁まで届くため、拡大で字面が右端アイコンに密着して見えた。
            // 1行固定＋末尾省略で高さ崩れも防ぐ（帯は導線であり全文可読が必須の文言ではない）。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 拡大時もテキストとシェブロンの間に最低8dpの呼吸を確保（先頭アイコン側と対称）
        Spacer(Modifier.width(Spacing.S8))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
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
 * ⋮メニューの「通知」節（見出し＋新着話通知トグル＋権限ダイアログ結線）。
 * D 構造のトップバーと M 星図（BookshelfSkyM）の両メニューが同一実装を使うために抽出した
 * （スキン構造分岐＝ADR 0022 §1 で画面が割れても、通知オプトインの挙動は1系統に保つ）。
 */
@Composable
internal fun NewEpisodeNotificationMenuSection() {
    Text(
        "通知",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.S16, top = Spacing.S8, bottom = Spacing.S4),
    )
    val notifContext = LocalContext.current
    var newEpisodeNotifEnabled by remember {
        mutableStateOf(NewEpisodeNotificationPreference.isEnabled(notifContext))
    }
    val newEpisodeNotifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒否されても Worker は動かす＝バッジ側の提示は生きる（通知だけ出ない） */ }
    NewEpisodeNotificationToggle(
        enabled = newEpisodeNotifEnabled,
        onEnabledChange = { enabled ->
            newEpisodeNotifEnabled = enabled
            (notifContext.applicationContext as NovelReaderApplication)
                .setNewEpisodeNotificationEnabled(enabled)
            // API33+ で未付与なら OS 権限ダイアログ（<33 は常に GRANTED＝発火しない）。
            if (enabled && ContextCompat.checkSelfPermission(
                    notifContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                newEpisodeNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        modifier = Modifier.padding(horizontal = Spacing.S16),
    )
}
