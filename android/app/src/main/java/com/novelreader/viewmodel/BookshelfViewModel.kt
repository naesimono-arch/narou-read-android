package com.novelreader.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.PrefKeys
import com.novelreader.domain.FolderScanReport
import com.novelreader.domain.ReimportPlan
import com.novelreader.domain.ScanProgress
import com.novelreader.domain.ScanTarget
import com.novelreader.domain.activeWebNovels
import com.novelreader.domain.buildReimportPlans
import com.novelreader.domain.buildScanTargets
import com.novelreader.domain.pruneReimportSeenIds
import com.novelreader.domain.scanPdfFolder
import com.novelreader.domain.shouldConsumeSweepBanner
import com.novelreader.domain.shouldShowReimportSweep
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.bookIdFromWebNewEpisodeMarkKey
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.repository.BookRepository
import com.novelreader.repository.PdfTreeScanner
import com.novelreader.repository.SourceDeleteOutcome
import com.novelreader.scrape.ScrapeStructureException
import com.novelreader.scrape.SiteAdapterRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 本 VM のログタグ（ログ検索の継続性のため従来の文字列を維持）。 */
private const val TAG = "BookshelfViewModel"

/** PDF取込時のエラー種別。UI層でユーザー向けメッセージに変換する。 */
sealed class BookImportError(val userMessage: String) : Exception(userMessage) {
    class EncryptedPdf        : BookImportError("パスワード付きPDFは現在サポートしていません")
    class CorruptedPdf        : BookImportError("PDFファイルが破損しているか、読み取れません")
    class InsufficientStorage : BookImportError("ストレージの空き容量が不足しています")
    class UriPermissionDenied : BookImportError("ファイルへのアクセス権限がありません。もう一度ファイルを選択してください")
    class StorageWriteFailure : BookImportError("ファイルの書き込みに失敗しました")
    class Unknown(val detail: String?) : BookImportError("PDF処理に失敗しました")
}

/**
 * 複数PDF取込で「なろう形式でないPDF」が混在したときの確認プロンプト状態。
 * narou＝なろう形式（Nコード名）／nonNarou＝それ以外。いずれも本棚に巻順で並ぶ投入順で保持する。
 */
data class PdfImportPrompt(
    val narou: List<Uri>,
    val nonNarou: List<Uri>,
)

/**
 * 本棚の一覧状態。Loading（DB 初回発行前）と Content（発行後の確定）を型で区別する。
 * なぜ必要か（F-O）: 旧実装は books を初期値 emptyList の StateFlow で公開していたため、
 * cold start の「DB 初回発行前」と「蔵書ゼロ」が両方 emptyList になり見分けられず、
 * 起動直後に空状態がフラッシュしていた。Loading を初期値にすることで初回発行前を明示する。
 * MainActivity 側（読書画面 book==null の白画面対策）もこの区別を利用する。
 */
sealed interface BookshelfUiState {
    /** DB からの初回発行前。この間はスケルトンを出し、空状態フラッシュを避ける。 */
    data object Loading : BookshelfUiState
    /** DB 発行後の確定状態。books が空なら「蔵書ゼロ」を表す（Loading とは別物）。
     *  webNovels は (b) Web由来・未取込カード（融合本棚）。既定 emptyList は既存テスト・
     *  呼び出しの互換のため（Web カード非対応の経路は蔵書のみで従来どおり成立する）。
     *
     *  不変条件（2026-07-29）: webNovels は**「自然昇格」適用後の正味一覧**＝蔵書へ取込済み
     *  （books.ncode 一致）の行を含まない（[com.novelreader.domain.activeWebNovels]）。よって
     *  `books.size + webNovels.size` は棚に実際に出るカード枚数と一致し、ヘッダ冊数・状態チップ件数を
     *  この2リストから素直に数えてよい。この不変条件を壊すと冊数が実カード枚数より多く出る。 */
    data class Content(
        val books: List<BookEntity>,
        val webNovels: List<WebNovelEntity> = emptyList(),
        // 機能②: ncode(正規化済み大文字)→最後に開いた話。Web カードの「続きから読む 第N話」に使う（未記録は 0＝未読）。
        val webReadingProgress: Map<String, Int> = emptyMap(),
        // ncode(正規化済み大文字)→web 読書の最終接触時刻。web カードの並びキー＝触った web は接触時刻・未記録は addedAt で並ぶ（ShelfItems.webRecencyKeyOf）。
        val webLastReadAt: Map<String, Long> = emptyMap(),
    ) : BookshelfUiState
}

/**
 * UI へ配送する一度きりのエラー通知（Snackbar）。
 * なぜ String でなくデータクラスか（M7）: 取込失敗の Snackbar に「再試行」を出すには、どの URI が
 * 失敗したかを UI まで運ぶ必要がある。retryUri が非 null のときだけ再試行アクションを出し、同一 URI で
 * 取込を再投入する。復元系の情報通知（retryUri=null）は従来どおり文言のみを表示する。
 *
 * openUrl（破損監視・層2）: Web 取込がサイト構造変更の疑い（ScrapeStructureException）で失敗したとき、
 * 「公式サイトで読む」＝作品URLを外部ブラウザで開く逃げ道を出すために運ぶ。retryUri と排他（どちらも
 * アクション付きだが用途が別。retryUri は同一 URI 再取込・openUrl は ACTION_VIEW での外部送客）。
 */
data class AppErrorEvent(
    val message: String,
    val retryUri: String? = null,
    val openUrl: String? = null,
    // 一過性フラグ: 取込完了/取込済み等の情報通知は UI 側で actionLabel を付けず Short で自動消滅させる目印。
    // actionLabel 付き Snackbar は Material3 で duration 既定が Indefinite になり画面へ残留するため（案d）。
    val transient: Boolean = false,
    // 同型集約の印（2026-07-29 裁定④）: 同じ key の一括投入（複数PDF再取込→全件「取り込み済み」）を
    // UI へ渡す手前で「N件は取り込み済みです」1本に集約する（aggregateErrorEvents）。
    // 2026-07-16 実機確定の複合＝Channel(BUFFERED) の直列消費で「閉じた直後に同型が即再表示」が対象。
    // retryUri/openUrl（アクション持ち）は集約すると操作を失うため key を付けない運用が前提。
    val aggregationKey: String? = null,
) {
    companion object {
        /** 重複取込（既に蔵書済み）通知の集約キー。PDF（Service 側2箇所）と Web 取込の Duplicate が共有する。 */
        const val KEY_DUPLICATE_IMPORT = "duplicate-import"
    }
}

/**
 * 同一バッチ（いま届いた1件＋バッファ済みの吸い出し分）のエラーイベントを同型集約する純関数。
 * aggregationKey が同じイベントが2件以上あれば、最初の出現位置に「N件は取り込み済みです」1件へ畳む
 * （1件だけなら原文のまま＝「「title」は既に取り込み済みです」の個別情報を捨てない）。
 * key 無し（失敗・再試行・案内などアクション持ち含む）は順序ごと素通しする。
 * transient は全員一致のときだけ引き継ぐ: PDF 重複（「閉じる」残置）と Web 重複（Short 自動消滅）が
 * 混在したとき、勝手に自動消滅へ倒して見落としを作らないため（安全側＝残置）。
 * 注意: 集約文言は現状唯一のキー（KEY_DUPLICATE_IMPORT）専用。キーを増やすときはここで文言を分岐する。
 */
internal fun aggregateErrorEvents(events: List<AppErrorEvent>): List<AppErrorEvent> {
    if (events.size < 2) return events
    val countByKey = events.mapNotNull { it.aggregationKey }.groupingBy { it }.eachCount()
    val emitted = mutableSetOf<String>()
    return events.mapNotNull { e ->
        val key = e.aggregationKey
        when {
            key == null || countByKey.getValue(key) < 2 -> e
            emitted.add(key) -> {
                val group = events.filter { it.aggregationKey == key }
                AppErrorEvent(
                    message = "${group.size}件は取り込み済みです",
                    transient = group.all { it.transient },
                    aggregationKey = key,
                )
            }
            else -> null // 同キーの2件目以降は集約済み＝落とす
        }
    }
}

/**
 * なろう紐付けシート（NcodeLinkSheet）の候補検索の状態。
 * なぜ VM 側の型として公開するか（依存注入漏れの解消）: 以前はシート Composable が
 * NovelApiRepository を直接受け取り produceState で検索まで回していた（テスト不能・依存注入漏れ）。
 * 検索実行を VM へ吊り上げ、シートは「この state ＋ callback」を受け取るだけの葉にするため、
 * 状態表現を VM とシートで共有できるようここに置く。
 */
sealed interface NcodeSearchUiState {
    /** 検索実行中（初回照会・再検索・再試行のいずれも一旦ここを通す）。 */
    data object Loading : NcodeSearchUiState
    /** 検索成功。空クエリのときは allcount=0・novels=空の Success を表す（旧 produceState と同一）。 */
    data class Success(val result: DiscoveryResult) : NcodeSearchUiState
    /** なろう API 由来の失敗（NarouApiException.userMessage を保持）。 */
    data class Error(val message: String) : NcodeSearchUiState
}

data class ProcessingState(
    val isProcessing: Boolean = false,
    val stepIndex: Int = 0,
    val stepTotal: Int = 4,
    val stepLocalPercent: Float = 0f,
    val phase: String = "",
    // 変換中の本のタイトル（step0 で判明する実タイトル。判明前は表示名フォールバック）。
    val title: String = "",
    // キュー情報（通知と同じ「N件目/全M件」をアプリ内バナーにも出すため）
    val queueCurrent: Int = 1,
    val queueTotal: Int = 1,
    // 停止操作後、処理中の1冊が完了するまでの「停止しています…」状態。
    val isStopping: Boolean = false,
    // 供給元（PDF=Service の4段ステップ変換／WEB=章単位取得）。バナーのステッパー出し分けと
    // 停止操作のディスパッチ先の判定に使う。既定 PDF は既存の Service 構築呼び出しを不変に保つ互換値
    // （実際の刻印は ProcessingStateHub.update が supply 元スロットで強制する＝設定忘れでもズレない）。
    val source: ProcessingSource = ProcessingSource.PDF,
)

// ioDispatcher: 進捗保存チャネルの消費など「テストで advanceUntilIdle→coVerify の順に
// 検証する fire-and-forget な IO 書き込み」の実行ディスパッチャを注入可能にする。
// なぜ注入か: init の progressChannel 消費を素の Dispatchers.IO で回すと、テストの
// TestDispatcher（setMain 済み）管理外のスレッドで走るため advanceUntilIdle が消費完了を
// 待てず coVerify とレースしてフレーキーになる（本番は既定 Dispatchers.IO のまま＝挙動不変）。
// @JvmOverloads: Compose の viewModel()/AndroidViewModelFactory は (Application) 単一引数
// コンストラクタをリフレクションで探すため、既定引数だけでは生成できない単一引数版を残す。
class BookshelfViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val app = application as NovelReaderApplication
    private val repository = app.repository

    // なろう紐付けシートの候補検索に使う（旧: シート Composable が直接受け取っていた依存を VM へ移設）。
    private val novelApiRepository = app.novelApiRepository

    // 一覧の正本。Loading を初期値にして「DB 初回発行前」を明示する（F-O）。
    // (b) 融合本棚: 蔵書と Web由来（未取込）を combine で束ねる。Room の各 Flow とも初回発行は
    // 即時のため、combine 待ちが Loading を不当に長引かせることはない。
    val uiState: StateFlow<BookshelfUiState> =
        combine(
            repository.allBooks, repository.webNovels, repository.webReadingProgress,
        ) { books, webNovels, webReadingProgress ->
            BookshelfUiState.Content(
                books = books,
                // 「自然昇格」をここで一度だけ適用する（2026-07-29 実機報告『ヘッダの冊数が実際とずれる』の対処）。
                // 取込が済んでも web_novels 行は残す設計のため、素の webNovels には棚に1枚も出ないゴースト行が
                // 混じる。旧実装は昇格を一覧生成（mergeShelfItems）の内側でしか適用しておらず、同じリストを素で
                // 数えるヘッダ冊数（books.size + webNovels.size）と状態チップ件数がゴーストごと数えて実カード枚数
                // より多く出ていた。供給点で落とせば、数える側・絞る側・並べる側が同一の正味リストを見る。
                webNovels = activeWebNovels(books, webNovels),
                // ncode→最後に開いた話へ畳む（描画層は Map を引くだけ＝mergeShelfItems が Web カードへ載せる）。
                webReadingProgress = webReadingProgress.associate { it.ncode to it.lastReadEpisode },
                // ncode→最終接触時刻。触った web カードを接触時刻で並べるのに使う（表示用 episode とは別量）。
                webLastReadAt = webReadingProgress.associate { it.ncode to it.lastReadAt },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookshelfUiState.Loading)

    // 既存の呼び出し側（MainActivity の読書画面ルート等）向けの素の books ビュー。
    // uiState から派生させて上流の DB 購読を一本化する（Content 以外は空リスト）。
    val books: StateFlow<List<BookEntity>> = uiState
        .map { (it as? BookshelfUiState.Content)?.books ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 各本の章数（chap_N.html の枚数）を bookId→章数 で公開する。
    // なぜ VM へ吊り上げるか: 従来はカードごとに produceState で同じファイル数え上げを重複IOしていた。
    // さらに状態フィルタ（よみかけ/未読/読了）は棚レベルで各本の章数を必要とするため、章数え上げを
    // VM に一本化し、カード表示（BookProgressRow）とフィルタ判定（readingStatusFor）の単一真実源にする。
    // Regex はループ外で1回だけ生成する（本の数だけコンパイルし直さない）。走査は IO へ逃がす。
    val chapterCountMap: StateFlow<Map<String, Int>> = books
        .map { list ->
            val chapPattern = Regex("chap_\\d+\\.html")
            list.associate { book ->
                book.id to (File(book.htmlDirPath).listFiles { f -> f.name.matches(chapPattern) }?.size ?: 0)
            }
        }
        // ioDispatcher 注入: 素の Dispatchers.IO だと章数え上げが TestDispatcher 管理外のスレッドで走り、
        // advanceUntilIdle が完了を待てずに chapterCountMap を観測するテストがフレーキーになる
        // （deleteBook/deleteBooks と同理由。本番は既定値＝Dispatchers.IO のまま＝挙動不変）。
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // 進捗行の割合計算（F-N）にスクロール位置も要るため、値を lastReadFilename 文字列ではなく
    // ProgressEntity 全体（scrollIndex/scrollOffset を含む）で公開する。
    val progressMap: StateFlow<Map<String, ProgressEntity>> = repository.allProgress
        .map { list -> list.associateBy { it.bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        // WhileSubscribed(5_000) に統一（Lazily はサブスクライバーゼロでもDBクエリが流れ続けるため）

    // 本棚カードの「続きありバッジ」用に、紐付け済み作品（ncode 非null）の詳細をまとめて照会する。
    // key = ncode（String）／value = なろう詳細。バッジの新着話数はカード側で
    // computeContinuation(手元PDFの章数, 詳細) により算出する（章数のファイル走査はカードが持つため）。
    //
    // なぜカード単位の produceState から VM の一括照会へ移したか（アーキ監査残課題1・テスト容易性）:
    // 旧実装は BookCard が produceState 内で novelApiRepository.novelDetail を直接叩いており、
    // カード枚数ぶん Repository を直撃していた（テスト不能・本棚を開くたびカードごとに並列発火）。
    // 照会を VM へ吊り上げ、カードは Map から自分の ncode 分を引くだけの純粋表示にする。
    //
    // なぜ逐次照会のままにするか: Repository キャッシュは Mutex でスレッド安全化済み（U1 対応）だが、
    // 紐付け作品ぶんの詳細照会を並列発火させると、なろうAPIの転送量マナー（narou_api_manual.md §6）に
    // 反するため、逐次（1件ずつ）で回す方針は維持する。dispatcher は viewModelScope 既定で足りる
    // （API 呼び出しは Retrofit suspend＝内部で IO へ逃げるため Main を塞がない）。
    // 失敗（NarouApiException＝オフライン等）は静かに無視しバッジ非表示にする（旧 produceState と同一方針）。
    @OptIn(ExperimentalCoroutinesApi::class)
    val newEpisodeNovelMap: StateFlow<Map<String, WorkSummary>> = books
        .map { list -> list.mapNotNull { it.ncode }.distinct() }
        // 本棚の並び替え等で ncode 集合が不変なら再照会しない（6h TTL キャッシュへの無駄叩き回避）。
        .distinctUntilChanged()
        // 本棚リスト（ncode 集合）変化時に前回の照会を破棄して最新集合で回し直す。
        .mapLatest { ncodes ->
            val result = LinkedHashMap<String, WorkSummary>()
            for (ncode in ncodes) {
                try {
                    // バッジ計算（continuation）に要るのは要約（ncode/話数/連載状態）だけ＝詳細の summary を保持。
                    novelApiRepository.novelDetail(Ncode(ncode))?.let { result[ncode] = it.summary }
                } catch (e: NarouApiException) {
                    // オフライン等の失敗はバッジ非表示で静かに握り潰す（本棚を通信エラーで騒がせない）。
                }
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // 「続きあり」バッジの Web 蔵書側データ源（bookId→Worker が最後に観測したサイト総話数）。
    //
    // なぜ newEpisodeNovelMap と別系統か: なろう紐付け本は詳細 API を実時間で引ける（上の照会）が、
    // Web 蔵書はサイトへの再フェッチを1日1回の Worker に限っている（低頻度アクセス＝ADR 0024）ため、
    // 端末が持つ観測値は new_episode_marks の基準値だけ。同じ「続きあり」判定へ載せるには、その行を
    // 本棚が購読して読む以外に手が無い（U1 は書く側だけが結線済みで、読む側が欠けていた＝本配線の真因）。
    // なろうの基準値行（キー＝正規化 ncode）は bookIdFromWebNewEpisodeMarkKey が null を返して自然に落ちる。
    val webNewEpisodeTotalMap: StateFlow<Map<String, Int>> = repository.newEpisodeMarks
        .map { marks ->
            marks.mapNotNull { mark ->
                bookIdFromWebNewEpisodeMarkKey(mark.ncode)?.let { it to mark.lastNotifiedAllNo }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ── 本文欠落→再取込提案（2026-07-29 裁定・案B バッジ＋案C 一括バナー）──────────────────
    // 検出定義: books 行はあるが本文実体（index.html）が無い（機序＝uninstall→Auto Backup が DB のみ復元）。
    // 分類・指紋の純ロジックは domain/ReimportPlan.kt（JVM テスト対象）。ここは Android 依存（ファイル実在・
    // 永続権限・prefs）の注入と配線だけを持つ。

    private val appPrefs = application.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)

    // 再検出の合図。取込/再取込の完走（isProcessing の true→false 立ち下がり）で bump する。
    // なぜ必要か: ハッシュ一致の復元は行の値が1つも変わらないことがあり（同一パス・同一指紋・同一 sourceUri
    // の UPDATE）、books StateFlow が equals 同値で再発行しない＝ファイル実在の再確認をこの tick で強制する。
    private val reimportRecheckTick = MutableStateFlow(0)

    /** 欠落本の bookId→復旧手段（案B バッジ・案C バナーの共通データ源）。null＝初回検出前。
     *  なぜ null 初期値か: emptyMap 初期値だと「検出未完了」と「欠落ゼロ」が区別できず、
     *  下の onEach が seen 指紋を誤って空へ刈り込む。 */
    val reimportPlans: StateFlow<Map<String, ReimportPlan>?> =
        combine(uiState, reimportRecheckTick) { s, _ -> (s as? BookshelfUiState.Content)?.books }
            .map { list ->
                list?.let {
                    val filesDir = getApplication<Application>().filesDir
                    val resolver = getApplication<Application>().contentResolver
                    buildReimportPlans(
                        it,
                        isContentMissing = { book -> !book.hasContent(filesDir) },
                        hasPersistedRead = { uri ->
                            resolver.persistedUriPermissions.any { p ->
                                p.uri.toString() == uri && p.isReadPermission
                            }
                        },
                    )
                }
            }
            // 検出が確定するたび seen 指紋を現欠落へ刈り込む（seen = seen ∩ missing）。
            // なぜ: 一度復旧した本が将来また欠落したとき、それは「新規の検出」＝バナーを出し直すべきイベント。
            // なぜ init の常駐 collector でなくチェーン内 onEach か（監督ゲート FAIL 2026-07-29 の真因是正）:
            // 常駐 collector は WhileSubscribed を事実上 Eagerly 化し、VM 構築と同時に uiState 上流（DB 購読）
            // まで hot にしてしまう＝「未購読なら uiState は Loading のまま」という F-O 契約を破り、
            // 検出のファイル走査も購読の無い場面（JVM テスト含む）で走ってしまう。刈り込みは「現在欠落でない
            // id を seen から外すだけ」＝現在の表示判定 (missing−seen) を変えないため、購読中にだけ走れば足りる。
            .onEach { plans ->
                if (plans == null) return@onEach // 初回検出前は指紋に触れない（null 初期値の why 参照）
                val pruned = pruneReimportSeenIds(sweepSeenIds.value, plans.keys)
                if (pruned != sweepSeenIds.value) persistSweepSeenIds(pruned)
            }
            // ファイル実在チェックは IO。注入 ioDispatcher なのはテストで advanceUntilIdle が検出完了を待てるように
            //（progressChannel 消費と同理由。本番は既定 Dispatchers.IO＝挙動不変）。
            .flowOn(ioDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 案C バナーの提示済み指紋（欠落 bookId 集合）。prefs 正本・StateFlow はその鏡（combine で反応させるため）。
    private val sweepSeenIds = MutableStateFlow(
        appPrefs.getStringSet(PrefKeys.REIMPORT_SWEEP_SEEN_IDS, emptySet())?.toSet() ?: emptySet()
    )

    // ── 案X: 「PDFのある場所」の記憶とフォルダ走査（domain/PdfFolderScan.kt の Android 側配線）──────
    // 真因（実機確定）: uninstall→Auto Backup では永続 URI 権限が戻らないため、PDF 本は全て②へ落ち
    // ①の自動対象は構造的に0冊になる。さらに実機の sourceUri は MediaStore Documents 由来でファイル名の
    // 手がかりすら残らない（sourceFileNameHint の KDoc）＝人間には選び直す材料が無い。
    // よって「場所を1回教える→アプリが内容指紋で全冊照合」を復旧の主経路にする。

    /** 記憶済みの PDF 保管フォルダ（SAF ツリー URI 文字列）。null＝まだ教わっていない。 */
    private val _pdfFolderTreeUri = MutableStateFlow(
        appPrefs.getString(PrefKeys.PDF_LIBRARY_TREE_URI, null)?.takeIf { saved ->
            // 権限が生きているツリーだけを「記憶済み」と扱う（覚えているふりをすると走査が
            // SecurityException で全滅し、ユーザーには「0冊でした」と嘘の結果が出る）。
            application.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == saved && it.isReadPermission
            }
        }
    )
    val pdfFolderTreeUri: StateFlow<String?> = _pdfFolderTreeUri.asStateFlow()

    /** 走査中の進捗（null＝走査していない）。バナーの「N件中M件」と停止ボタンの表示条件。 */
    private val _folderScan = MutableStateFlow<ScanProgress?>(null)
    val folderScan: StateFlow<ScanProgress?> = _folderScan.asStateFlow()

    /** 走査結果（null＝結果ダイアログ非表示）。回転で消えないよう Channel でなく状態で持つ（_importPrompt と同流儀）。 */
    private val _folderScanReport = MutableStateFlow<FolderScanReport?>(null)
    val folderScanReport: StateFlow<FolderScanReport?> = _folderScanReport.asStateFlow()

    // 停止要求。なぜコルーチン cancel でなくフラグか: cancel すると途中経過（どこまで調べて何冊当たったか）を
    // 巻き戻してしまい「停止したら何も起きなかった」ことになる。フラグならファイル境界で協調的に抜けて
    // 部分成果をそのまま結果として返せる（PDF 取込の ACTION_STOP がページ境界で止まるのと同じ意味論）。
    // AtomicBoolean なのは走査が IO ディスパッチャ、停止操作が Main から来るため。
    private val scanCancelRequested = AtomicBoolean(false)
    private var folderScanJob: Job? = null

    /** 案C バナーの表示可否。「新規に検出した際に一度だけ」＝seen 指紋に無い欠落があるときだけ true。
     *  走査中は出さない（同じヘッダ直下スロットに走査バナーが出るため＝二重表示を作らない）。 */
    val sweepBannerVisible: StateFlow<Boolean> =
        combine(reimportPlans, sweepSeenIds, _folderScan) { plans, seen, scan ->
            plans != null && scan == null && shouldShowReimportSweep(plans.keys, seen)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun persistSweepSeenIds(ids: Set<String>) {
        sweepSeenIds.value = ids
        // getStringSet が返した参照を書き戻さないよう常にコピーを渡す（SharedPreferences の既知の罠）。
        appPrefs.edit().putStringSet(PrefKeys.REIMPORT_SWEEP_SEEN_IDS, ids.toSet()).apply()
    }

    /** 案C バナー「あとで」: 現欠落集合を提示済み指紋として保存（同一集合では再表示しない）。
     *  バッジ（案B）は消えない＝バナーを閉じても復旧手段はカード起点で残る設計。 */
    fun dismissSweepBanner() {
        reimportPlans.value?.let { persistSweepSeenIds(it.keys.toSet()) }
    }

    /**
     * 一括復旧の実行（案C＋案X）。
     * ①元PDF＋④Web を既存の取込経路へ投入し、指紋照合できる欠落本（②③のうち contentSha256 あり）が
     * 残っていれば PDF 保管フォルダを走査する。
     *
     * @param folder 走査に使うフォルダ。省略時は記憶済みツリー（無ければ走査しない＝呼び出し側が
     *   フォルダ選択を出してから [rememberPdfFolder] 経由で入り直す）。
     */
    fun runSweepReimport(folder: Uri? = null) {
        val plans = reimportPlans.value ?: return
        val actedIds = plans.keys.toSet()
        val submitted = submitAutoReimports(plans)
        val targets = buildScanTargets(books.value, plans)
        val tree = folder ?: _pdfFolderTreeUri.value?.let(Uri::parse)
        if (tree != null && targets.isNotEmpty()) {
            // 走査を始める操作では消費判定を走査完了まで遅らせる（一致冊数がまだ判らないため）。
            startFolderScan(tree, targets, actedIds, autoSubmitted = submitted)
            return
        }
        // 走査へ進まない場合はここで判定する（規則の正本＝domain の shouldConsumeSweepBanner）。
        if (shouldConsumeSweepBanner(submitted, scanMatched = 0)) persistSweepSeenIds(actedIds)
    }

    /** ①元PDF＋④Web を既存の取込経路へ投入する（戻り値＝投入した冊数）。
     *  直列化は既存機構に乗せる: PDF＝FGS の ArrayDeque キュー（Service が逐次処理）／
     *  Web＝importWebNovels の単一コルーチン逐次実行＝新しい並列実行を発明しない。 */
    private fun submitAutoReimports(plans: Map<String, ReimportPlan>): Int {
        val autoPdf = plans.values.filterIsInstance<ReimportPlan.AutoPdf>()
        autoPdf.forEach { addBook(Uri.parse(it.sourceUri)) }
        val webUrls = plans.values.filterIsInstance<ReimportPlan.AutoWeb>().map { it.sourceUrl }
        if (webUrls.isNotEmpty()) importWebNovels(webUrls)
        return autoPdf.size + webUrls.size
    }

    /**
     * フォルダ選択（ACTION_OPEN_DOCUMENT_TREE）の結果を記憶する（走査は呼ばない＝呼び出し側が続けて
     * [runSweepReimport] か [scanFolderForBook] を回す）。
     * ツリー権限の永続化が案X の要: これが成立して初めて「次回以降はフォルダを選ばずに自動走査」になる。
     */
    fun rememberPdfFolder(treeUri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val taken = runCatching {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!taken) {
            // 永続化できないプロバイダでも「今回の走査」は Intent の一時権限で成立する＝機能は落とさず
            // 記憶だけ諦める（次回はまたフォルダ選択から）。黙らせずログに残す。
            android.util.Log.w(TAG, "ツリー権限を永続化できませんでした（今回限りの走査になります）: $treeUri")
            return
        }
        // 別の場所へ乗り換えたら古いツリー権限は返す（永続権限は端末上限128件の共有予算＝1件に保つ）。
        _pdfFolderTreeUri.value
            ?.takeIf { it != treeUri.toString() }
            ?.let { old ->
                runCatching {
                    resolver.releasePersistableUriPermission(
                        Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        _pdfFolderTreeUri.value = treeUri.toString()
        appPrefs.edit().putString(PrefKeys.PDF_LIBRARY_TREE_URI, treeUri.toString()).apply()
    }

    /**
     * 案B ダイアログ②③（案X の単体版）: この1冊だけを「PDFのある場所」から探す。
     * @return false＝走査できなかった（指紋が無い本／使えるフォルダが無い）。呼び出し側はこの戻り値で
     *   フォルダ選択を出すか個別選択へ落とすかを決める（VM から picker は起動できないため）。
     */
    fun scanFolderForBook(book: BookEntity, folder: Uri? = null): Boolean {
        val plans = reimportPlans.value ?: return false
        val target = buildScanTargets(listOf(book), plans).firstOrNull() ?: return false
        val tree = folder ?: _pdfFolderTreeUri.value?.let(Uri::parse) ?: return false
        // actedIds は空＝この経路ではバナー指紋に触れない（1冊の復旧で一括バナーを消費しない）。
        startFolderScan(tree, listOf(target), actedIds = emptySet(), autoSubmitted = 0)
        return true
    }

    /** 走査の停止（走査バナーの「停止」）。実際の中断は「今読んでいる1件の完了後」＝部分成果は結果に残る。 */
    fun cancelFolderScan() {
        scanCancelRequested.set(true)
    }

    /** 走査結果ダイアログを閉じる。 */
    fun dismissFolderScanReport() {
        _folderScanReport.value = null
    }

    /**
     * フォルダ走査の実体。列挙とハッシュ計算（Android 依存）は [PdfTreeScanner] へ、
     * 照合・集計（純ロジック）は [scanPdfFolder] へ委ね、ここは配線と結果の後始末だけを持つ。
     *
     * @param actedIds バナー消費の対象 id 集合（空＝この走査ではバナー指紋に触れない＝1冊復旧の経路）。
     * @param autoSubmitted 同じ操作で①④の自動再取込へ投入した冊数（走査が0冊でも消費してよい根拠）。
     */
    private fun startFolderScan(
        treeUri: Uri,
        targets: List<ScanTarget>,
        actedIds: Set<String>,
        autoSubmitted: Int,
    ) {
        // 走査は同時1本（進捗バナーが1つしかなく、二重起動は表示も停止操作も破綻する）。
        if (folderScanJob?.isActive == true) return
        scanCancelRequested.set(false)
        _folderScan.value = ScanProgress(hashed = 0, total = 0, matched = 0)
        folderScanJob = viewModelScope.launch(ioDispatcher) {
            val scanner = PdfTreeScanner(getApplication<Application>())
            val report = try {
                scanPdfFolder(
                    targets = targets,
                    enumerate = { scanner.enumeratePdfs(treeUri, isCancelled = scanCancelRequested::get) },
                    hashOf = { scanner.sha256Of(Uri.parse(it.uri)) },
                    // 蔵書PDFらしいファイル名（なろう縦書きPDF の Nコード命名）を先に照合する順序付け。
                    // ファイル名の手がかり（fileNameHint）が取れないプロバイダ（MediaStore Documents）では
                    // これが唯一の優先材料になる＝早期終了の効きを支える。
                    isLikelyNovelPdf = ::isNarouPdfFileName,
                    isCancelled = scanCancelRequested::get,
                    onProgress = { _folderScan.value = it },
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 根が読めない＝権限失効・場所ごと消えた等。「0冊でした」と混同させず失敗として告げる
                // （症状を隠すと「このフォルダに本は無い」と誤って伝わる）。真因はログへ。
                android.util.Log.e(TAG, "PDFフォルダの走査に失敗", e)
                emitSnackbar("フォルダを読み取れませんでした。場所をもう一度選び直してください")
                // 使えないと判った場所を覚え続けない（次回はフォルダ選択からやり直す）。
                forgetPdfFolder()
                null
            } finally {
                _folderScan.value = null
            }
            if (report != null) {
                // 一致した本を既存の復元経路へ投入する（新しい復元経路は発明しない）:
                // addBook→FGS キュー→PdfBookImporter の restoreByHash が既存行を保持したまま本文だけ
                // 再生成する（進捗・栞・追加日は updateRestoredContent の部分 UPDATE で不変）。
                // addBook は startForegroundService＋権限取得＝UI 側の操作のため Main で1件ずつ呼ぶ
                // （投入順＝Service のキュー順を確定させる。addBooks と同じ作法）。
                withContext(Dispatchers.Main) {
                    report.matches.forEach { addBook(Uri.parse(it.candidate.uri)) }
                }
                _folderScanReport.value = report
                // 何かが動いたときだけバナーを消費する（規則の正本＝domain の shouldConsumeSweepBanner）。
                // actedIds が空＝1冊復旧の経路＝一括バナーの指紋には触れない（空集合で上書きしない）。
                if (actedIds.isNotEmpty() && shouldConsumeSweepBanner(autoSubmitted, report.matchedCount)) {
                    persistSweepSeenIds(actedIds)
                }
            }
        }
    }

    /** 記憶している場所を忘れる（権限も返す）。走査が根から失敗したときに呼ぶ。 */
    private fun forgetPdfFolder() {
        _pdfFolderTreeUri.value?.let { old ->
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        _pdfFolderTreeUri.value = null
        appPrefs.edit().remove(PrefKeys.PDF_LIBRARY_TREE_URI).apply()
    }

    /** 案B ダイアログ①: 記録済み取込元 PDF から自動再取込（既存 FGS キューに乗り、repository 層の
     *  ハッシュ一致→復元モードが既存行を保持したまま本文だけ再生成する）。 */
    fun reimportFromSource(book: BookEntity) {
        book.sourceUri?.let { addBook(Uri.parse(it)) }
    }

    // Application の StateFlow を購読して processingState を提供
    val processingState: StateFlow<ProcessingState> = app.processingState
        .map { it ?: ProcessingState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessingState())

    // エラーは一度きりのイベント。Application の Channel を購読し、同型集約（裁定④）を挟んで UI へ流す:
    // いま届いた1件に加えバッファ済みの同時投入分をその場で吸い出し、同 aggregationKey の2件以上を
    // 「N件は取り込み済みです」1本へ畳む。actionLabel 付き Snackbar（Indefinite）表示中に後続が
    // Channel(BUFFERED) へ溜まる構造（2026-07-16 実機確定）ゆえ、閉じた直後の次イベント処理時には
    // 残りが揃って吸い出せる＝最悪でも「個別1本＋集約1本」に収まる。
    // トレードオフ（why）: 吸い出した分はローカルリストに移る＝表示途中で画面が破棄されると残りは失われるが、
    // 対象は情報通知（重複・案内）で、アクション持ち（retryUri/openUrl）も downstream の逐次表示で
    // 1件ずつ dismiss されるまで emit が suspend するため従来と同じ滞留挙動になる。
    val errorEvents: Flow<AppErrorEvent> = flow {
        app.errorEvents.collect { first ->
            aggregateErrorEvents(listOf(first) + app.drainPendingErrorEvents()).forEach { emit(it) }
        }
    }

    // 進捗（章移動＋章内スクロール位置）の保存要求を単一チャネルに集約する。
    // なぜ1本に統合するか: 以前は章移動用とスクロール用で2本のチャネル＋2コルーチンに
    // 分かれていたが、両者は同じ progress 行を REPLACE で上書きするため、
    // 2チャネル跨ぎでは書き込み順序が保証されず（順序保証はチャネル内のみ）、
    // 章送り直後に旧章のスクロール書き込みが後着すると lastReadFilename が
    // 旧章へ巻き戻る競合があった。単一チャネルにすることで「最後に送られた操作＝
    // 最新のユーザー操作」が確実に最後に書き込まれる。
    // CONFLATED により中間値は捨てられ最新値のみが処理される（単一行の現在位置
    // 表現としてこの破棄は意味的に正しい）。
    private val progressChannel = Channel<ProgressEntity>(Channel.CONFLATED)

    init {
        // 取込完走（isProcessing の true→false 立ち下がり）で欠落を再検出する（reimportRecheckTick の why）。
        // 生の app.processingState を読むのは cancelProcessing と同じ理由（stateIn の反映遅れを避ける）。
        viewModelScope.launch {
            var wasProcessing = false
            app.processingState.collect { s ->
                val now = s?.isProcessing == true
                if (wasProcessing && !now) reimportRecheckTick.value++
                wasProcessing = now
            }
        }
        // なぜ ioDispatcher 注入か: 素の Dispatchers.IO だとテストの TestDispatcher 管理外で走り、
        // advanceUntilIdle がチャネル消費完了を待てず coVerify とレースする（本番は既定＝IO のまま）。
        viewModelScope.launch(ioDispatcher) {
            for (p in progressChannel) {
                // ProgressEntity（Room 実体）をチャネルの搬送体に流用しているため中身は String。
                // 永続化境界の repository へ渡す直前に BookId/ChapterFilename へ包み直す（型付き API への再包み）。
                repository.saveScrollPosition(
                    BookId(p.bookId), ChapterFilename(p.lastReadFilename), p.scrollIndex, p.scrollOffset,
                )
            }
        }
    }

    // ncode: 縦書きPDF取り込み（ADR 0011）から呼ぶときのみ非 null。取り込む本になろう作品を紐付ける。
    // 通常のファイル選択取り込みでは省略（null）＝従来どおり紐付けはユーザーが後から NcodeLinkSheet で行う。
    fun addBook(uri: Uri, ncode: Ncode? = null) {
        // 強制終了からの再開（起動時リカバリの再投入）にはプロセスを跨いで有効な読み取り権限が
        // 必要なため、intent の FLAG_GRANT（一時権限＝プロセス消滅で失効）に加えて永続権限を取る。
        // picker は OpenDocument なので取得可能だが、プロバイダによっては SecurityException を
        // 投げるため防御する（取れなくても通常の変換は一時権限で成立し、再開だけが不可になる）。
        // なお FileProvider の content:// URI（取り込み経路）は persistable permission を取れず
        // ここは runCatching で無害に失敗する＝その本の再開が効かないだけ（PdfImportViewModel 側と同じ前提）。
        //
        // 取込元PDF削除（本削除時に取込元も消す）のため、まず READ|WRITE を試みる。書込権限まで保持できた本だけが
        // 後で DocumentsContract.deleteDocument できる（DefaultBookRepository.addBook が persistedUriPermissions を
        // 照会して sourceUri を記録する判定材料になる）。picker には write フラグを付与済み（pdfPicker の contract）。
        // 書込非対応プロバイダでは READ|WRITE が SecurityException になるため、READ のみへフォールバックして
        // 少なくとも再開用の読み取り権限は確保する（この本は取込元PDF削除の対象外＝sourceUri は NULL のまま）。
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_START
            data = uri
            // ncode を積むのは新規登録時の紐付け用（Service→repository.addBook へ伝搬）。null なら積まない。
            ncode?.let { putExtra(PdfProcessingService.EXTRA_NCODE, it.value) }
            // content:// URI の読み取り権限を Service に委譲
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    // 複数PDF取込で「なろう形式でないPDF」が混在したときの確認プロンプト（null=非表示）。
    // なぜ StateFlow か: エラー通知（errorEvents）と違いユーザーが選ぶまで表示を保持する必要があるため
    // 一度きりの Channel でなく状態として持つ（回転・再購読でもダイアログが消えない）。
    private val _importPrompt = MutableStateFlow<PdfImportPrompt?>(null)
    val importPrompt: StateFlow<PdfImportPrompt?> = _importPrompt.asStateFlow()

    /**
     * 複数PDFの同時取込エントリ（picker=OpenMultipleDocuments から受ける）。
     * ファイル名から「なろう公式縦書きPDF（Nコード名）」かどうかで仕分けし、なろうでないものが
     * 混在するときだけ確認プロンプトを出す（全てなろう形式なら無摩擦で取込む）。picker はファイル名や
     * 中身での絞り込みができない（OS 制約＝MIME のみ）ため、絞り込みは取込段のこの判定で行う。
     * 表示名の解決は ContentProvider への query＝IO なので Dispatchers.IO で回す。
     */
    fun addBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // ioDispatcher 注入は deleteBook と同理由（素の Dispatchers.IO は TestDispatcher 管理外で走る）。
        viewModelScope.launch(ioDispatcher) {
            val resolver = getApplication<Application>().contentResolver
            val names = uris.map { resolveDisplayName(resolver, it) }
            val plan = planNarouPdfImport(names)
            val narou = plan.narouOrder.map { uris[it] }
            val nonNarou = plan.nonNarouOrder.map { uris[it] }
            // addBook は startForegroundService＋takePersistableUriPermission＝UI 側の操作。
            // 逐次投入の順序（＝Service のキュー順）を確定させるためメインに戻して1件ずつ呼ぶ。
            withContext(Dispatchers.Main) {
                if (nonNarou.isEmpty()) {
                    narou.forEach { addBook(it) }
                } else {
                    _importPrompt.value = PdfImportPrompt(narou = narou, nonNarou = nonNarou)
                }
            }
        }
    }

    /** 確認プロンプトでの決定。includeNonNarou=true でなろう形式でないPDFも含めて取り込む。 */
    fun confirmImport(includeNonNarou: Boolean) {
        val prompt = _importPrompt.value ?: return
        _importPrompt.value = null
        // なろう形式を先に、非なろうを後に投入する。各群内の巻順は planNarouPdfImport が
        // 本棚（addedAt DESC）で正しく見えるよう並べ済み（群を跨いだ厳密な混在順は保証しない）。
        prompt.narou.forEach { addBook(it) }
        if (includeNonNarou) prompt.nonNarou.forEach { addBook(it) }
    }

    /** 確認プロンプトをキャンセル（何も取り込まない）。 */
    fun dismissImportPrompt() {
        _importPrompt.value = null
    }

    // 表示名（ファイル名）を ContentProvider から解決する。取れなければ URI 末尾でフォールバックし、
    // なろう判定・ソートの材料にする。query 失敗（プロバイダ差異）を握り潰しても取込自体は成立する。
    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()

    // 変換の全体停止（バナーの「停止」）。表示中バナーの供給元で停止先を選ぶ:
    // PDF＝Service へ ACTION_STOP（キュー破棄＋処理中の1冊をページ境界で中断）／
    // WEB＝viewModelScope の取込ジョブを cancel（章境界＝次の suspend 点で中断）。
    // 真因（2026-07-29 裁定①）: Web 取込は Service でなく viewModelScope 起動のため、従来の
    // ACTION_STOP 一辺倒では cancel がどこにも届かず「停止」が Web に対して無効だった。
    // PDF/Web 並走時は表示優先（PDF）の側だけ止まる＝PDF 完了後に Web バナーが浮上すれば改めて停止できる。
    fun cancelProcessing() {
        // VM 公開の processingState（stateIn 経由）でなく app の生 StateFlow を読む:
        // stateIn は購読状況次第で反映が遅れうるが、停止判定は「今この瞬間の表示元」で行う必要がある。
        if (app.processingState.value?.source == ProcessingSource.WEB) {
            cancelWebImports()
            return
        }
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_STOP
        }
        // 既に前面で動作中の FGS への命令送信。新規起動が不要なため startService を使う。
        getApplication<Application>().startService(intent)
    }

    // 実行中の Web 取込ジョブ（停止から cancel するための保持）。通常は0〜1件だが、取込中に別 URL を
    // 共有すると並行しうるため List で持つ。viewModelScope（Main）内でのみ触る＝同期不要。
    private val webImportJobs = mutableListOf<Job>()

    // 実行中 Web 取込の本数（Main 限定で増減）。最後の1本が終わるまで WEB スロットを畳まないための計数。
    // webImportJobs.count { isActive } で代用しない理由: finally 実行時点の自ジョブは
    // 「まだ completed でない」ため自分を数えてしまい、最後の1本の判定が不能になる。
    private var activeWebImports = 0

    /** Web 取込の全停止。PDF の ACTION_STOP と同じ意味論に合わせる:
     *  即時に「停止しています…」を出し（停止ボタンも同フラグで消える＝連打防止）、実中断は次の章境界
     *  （fetchToc/fetchChapter の suspend 点）。部分取込は残さない＝取得ループ中は未書込・HTML 生成後の
     *  中断は WebBookImporter の catch(Throwable) が出力ディレクトリごと掃除して rethrow する。 */
    private fun cancelWebImports() {
        app.processingStateOf(ProcessingSource.WEB)?.let {
            app.updateProcessingState(it.copy(isStopping = true), ProcessingSource.WEB)
        }
        webImportJobs.forEach { it.cancel() }
    }

    // 取込失敗 Snackbar の「再試行」（M7）。失敗した URI をそのまま再投入する。
    // Service 側のべき等ガード（ActiveUriTracker）は失敗確定時に該当 URI を release 済みのため、
    // この再投入は「重複」で弾かれず新規変換として受け付けられる（processSingleUri の finally で release）。
    fun retryImport(uriString: String) {
        addBook(Uri.parse(uriString))
    }

    // ────── P3 取込導線: 共有(SEND)/リンク(VIEW)からの Web 小説 URL 取込（確定事項②のルーティング）──────
    // registry は UI 層で規約ゲートを引くための単一インスタンス（stateless・default adapters）。
    // repository.addWebBook も内部で同じ registry を防御的に引くが、UI 側は Blocked（公式サイト導線）／
    // Unsupported（未対応案内）を repository 呼び出し前に出し分けるため、ここで手前で resolve する。
    private val siteRegistry = SiteAdapterRegistry()

    /** 共有/リンクの URL を解決する。呼び出し側（ルート Composable）が Supported/Blocked/Unsupported で分岐する。 */
    fun resolveWebImport(url: String): SiteAdapterRegistry.Resolution = siteRegistry.resolve(url)

    /** ルーティング結果の案内文を app 共有 Snackbar チャネルへ流す薄い委譲（本棚 SnackbarHost が購読）。
     *  Blocked/Unsupported の案内・取込中/完了/失敗の通知に共通で使う（PDF 取込の重複通知と同じ経路）。 */
    // transient=true は一過性の情報通知（取込完了/取込済み）＝UI 側で Short 自動消滅にする。
    // 既定 false は従来どおり「閉じる」付きで残置（Blocked/Unsupported 案内・強制終了リカバリ等＝挙動不変）。
    // aggregationKey は同型一括投入の集約印（AppErrorEvent.aggregationKey 参照）。
    fun emitSnackbar(message: String, transient: Boolean = false, aggregationKey: String? = null) =
        app.emitError(message, transient = transient, aggregationKey = aggregationKey)

    /**
     * Supported 確定後の実取込（P3）。取得は必ず [BookRepository.addWebBook] 経由＝UI 層で fetch しない。
     *
     * 取込中の表示（案d・2026-07-23 裁定）: 取込中は app 共有の [ProcessingState] へ載せ、全スキン共通の
     * ProcessingBanner（`processingState.isProcessing` 駆動）で見せる。PDF 取込と同じ器を流用する。
     *
     * なぜ旧「取込中スナックバー」をやめたか（残留バグの真因）: 「取り込み中です…」を Snackbar で出すと、
     * 本棚の errorEvents collect が actionLabel（「閉じる」）付き showSnackbar＝Material3 で duration 既定が
     * Indefinite になり dismiss まで suspend、その間に届く完了文『…を追加しました』が Channel(BUFFERED) に
     * 埋没して表示されなかった。バナーは非ブロッキングのため collect を塞がず、完了スナックバーが確実に出る。
     *
     * なぜ viewModelScope か: 本 VM は NovelReaderApp 直下で Activity スコープに生成され構成変更・画面遷移を
     * 跨いで生存する（アプリ滞在中は継続）。FGS で背面存続まではさせない（最小実装の割り切り）。
     *
     * バナーは供給元別スロット（WEB）へ書く: 旧実装は PDF（Service）と単一 StateFlow を直接共有し、
     * 並走時に相互上書きしていた（裁定③の真因）。分離の設計判断＝ProcessingStateHub の KDoc。
     * 停止（裁定①）: 起動したジョブを webImportJobs に保持し cancelProcessing → cancelWebImports が cancel する。
     */
    fun importWebNovel(url: String) = importWebNovels(listOf(url))

    /** 複数 URL の逐次取込（案C の Web 一括再取得が使う）。1コルーチンで順に回す＝相手サイトへ並列アクセスを
     *  作らない（低頻度アクセスの原則）・重い取込ジョブは直列化する既存方針の Web 版。停止（cancelProcessing→
     *  cancelWebImports）はこのジョブごと cancel＝残りの URL も含めて止まる。 */
    fun importWebNovels(urls: List<String>) {
        // 完了済みジョブの参照を機会的に掃除する（cancel 対象を実行中だけに保ち、リストを溜めない）。
        webImportJobs.removeAll { it.isCompleted }
        val job = viewModelScope.launch {
            urls.forEach { runWebImport(it) }
        }
        webImportJobs += job
    }

    // importWebNovel(s) の1冊分の実体（旧 importWebNovel の launch 本体を suspend 化して逐次実行できるようにした
    // だけ＝バナー・停止・エラー処理のロジックは不変）。
    private suspend fun runWebImport(url: String) {
        // run{} は移設した旧 launch 本体の字下げを不変に保つための無操作スコープ（diff を最小化し
        // ProcessingStateHub 配線ロジックへの実質変更が無いことをレビューで確認しやすくする）。
        run {
            activeWebImports++
            // 取込中バナーの初期状態。source=WEB でステッパー（PDF 4段の器）は出さず、章進捗（phase）へ
            // 一本化する（裁定②＝Web で「ステップ 1/4」が凍結表示されていた問題の解消。出し分けは
            // ProcessingBanner 側が source で行う＝新しい意匠は発明しない）。
            // title を空にすると D バナーが「PDF処理中…」へフォールバックし Web で誤表示になるため非空にする。
            // 作品題名は addWebBook 完了まで判らない（onProgress は章番号と文言のみで題名を運ばない）ので
            // 汎用ラベルを出し、章取得の進捗は phase へ差し込む（バナー文言はデータ差し込みのみ・意匠不変）。
            val banner = ProcessingState(
                isProcessing = true, title = "Web小説", phase = "取り込み中です…",
                source = ProcessingSource.WEB,
            )
            app.updateProcessingState(banner, ProcessingSource.WEB)
            try {
                repository.addWebBook(url, onProgress = { _, text ->
                    // 章取得の進捗（「章 i/N 取得中」）を副見出しへ流す。isStopping は現スロットから引き継ぐ:
                    // 停止タップ直後にこの進捗コールバックが banner の初期値（false）で巻き戻すのを防ぐ
                    // （PDF 側 onProgress が停止フラグをライブ読みするのと同じ機序）。
                    val stopping = app.processingStateOf(ProcessingSource.WEB)?.isStopping == true
                    app.updateProcessingState(
                        banner.copy(phase = text, isStopping = stopping), ProcessingSource.WEB,
                    )
                }).fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            // 完了は一過性の情報通知＝Short で自動消滅させる（transient=true）。
                            // restored＝本文欠落からの再取得（案B④/案C）は「追加」でなく「復元」と告げる
                            // （既存行保持＝重複行を作らない意味論をユーザー文言でも正しく表す）。
                            is BookRepository.AddBookResult.Added ->
                                emitSnackbar(
                                    if (outcome.restored) "「${outcome.book.title}」を復元しました"
                                    else "「${outcome.book.title}」を追加しました",
                                    transient = true,
                                )
                            // 同一作品 URL は addWebBook が重い取得の前に sourceUrl で弾いて Duplicate を返す。
                            // 集約キー: PDF 側の重複通知と同型＝一括投入時は「N件は取り込み済みです」へ畳む対象。
                            is BookRepository.AddBookResult.Duplicate ->
                                emitSnackbar(
                                    "取り込み済みです", transient = true,
                                    aggregationKey = AppErrorEvent.KEY_DUPLICATE_IMPORT,
                                )
                        }
                    },
                    onFailure = { e ->
                        // 真因はログに残す（握り潰さない）。Blocked/Unsupported は呼び出し前ゲートで除外済みのため、
                        // ここに来るのは取得/解析/構造疑い等の失敗。失敗系は従来どおり「閉じる」付きで残置（transient なし）。
                        android.util.Log.e(TAG, "Web取込失敗", e)
                        // 破損監視（層2）: サイト構造変更の疑い（ScrapeStructureException＝ScrapeException 派生）だけは
                        // 「公式サイトで読む」逃げ道を添える（作品URLを外部ブラウザで開く＝U3 Blocked と同じ ACTION_VIEW 流儀）。
                        // 逃げ道が保険の実体（脆さ織り込み）。それ以外の一過性失敗は従来どおり平易な失敗通知のみ
                        // （リトライ＝ユーザーの再共有操作＝確定事項）。
                        if (e is ScrapeStructureException) {
                            app.emitError("取得に失敗しました。サイト構造が変わった可能性があります", openUrl = url)
                        } else {
                            emitSnackbar("取り込みに失敗しました")
                        }
                    },
                )
            } finally {
                // 成功/失敗/コルーチンキャンセルのいずれでも取込中バナーを必ず畳む（バナー残留防止）。
                // updateProcessingState は非 suspend の値代入のためキャンセル巻き戻し中でも確実に完了する。
                // 並行 Web 取込がまだ生きている間は畳まない: 先に終わった側の null 書きが後続の表示を
                // 潰す（裁定③と同型の Web/Web 版）を最後の1本の判定で防ぐ。
                activeWebImports--
                if (activeWebImports == 0) app.updateProcessingState(null, ProcessingSource.WEB)
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        // ioDispatcher 注入: deleteBook もテストで advanceUntilIdle→coVerify の順に検証するため、
        // 素の Dispatchers.IO だと TestDispatcher 管理外で走りレースする（本番は既定＝IO のまま）。
        viewModelScope.launch(ioDispatcher) { repository.deleteBook(book) }
    }

    // 複数選択→まとめて削除（残8）。選択モードの削除確認ダイアログを確定したときに呼ぶ。1コルーチンで
    // 順次 deleteBook（各々が本文HTML・DB行・進捗をIOで消す不可逆操作）。books は hot StateFlow のため
    // 消えた分だけ本棚へ即時反映される。Undo は持たない＝確認ダイアログで事前同意を取る設計（案B裁定）。
    // deleteSource=true のとき、取込元 URI を保持する本は取込元PDF本体も削除する（ダイアログのチェック）。
    // ioDispatcher 注入は単体 deleteBook と同理由（テストで advanceUntilIdle→検証の順序を成立させる）。
    fun deleteBooks(books: List<BookEntity>, deleteSource: Boolean = false) {
        viewModelScope.launch(ioDispatcher) {
            var failed = 0
            books.forEach {
                if (repository.deleteBook(it, deleteSource) == SourceDeleteOutcome.Failed) failed++
            }
            // 取込元PDFの削除に失敗した本があれば Snackbar で知らせる（本削除自体は成立済み＝handover 提起③）。
            // 既に移動/削除済み・権限失効・削除非対応プロバイダなど、アプリでは救えない外部要因が主因のため通知に留める。
            if (failed > 0) {
                app.emitError("取込元PDFの削除に失敗しました（${failed}件・移動/削除済みか、削除に対応しない保存先の可能性）")
            }
        }
    }

    // (b) Web由来・未取込カードを本棚から外す。webNovels は hot に uiState へ combine 済みのため、
    // 削除すれば本棚から即時に消える（deleteBook と同じ配送経路）。
    fun removeWebNovel(ncode: String) {
        // ioDispatcher 注入は deleteBook と同理由（素の Dispatchers.IO は TestDispatcher 管理外で走る）。
        viewModelScope.launch(ioDispatcher) { repository.removeWebNovel(Ncode(ncode)) }
    }

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。
    // books は hot StateFlow のため、書き込めば読書画面の継続導線へ自動で反映される。
    fun linkNcode(bookId: BookId, ncode: Ncode?) {
        // ioDispatcher 注入は deleteBook と同理由（素の Dispatchers.IO は TestDispatcher 管理外で走る）。
        viewModelScope.launch(ioDispatcher) { repository.linkNcode(bookId, ncode) }
    }

    // ────── なろう紐付けシートの候補検索（旧 NcodeLinkSheet の produceState を VM へ移設）──────
    // なぜ紐付け系メソッド（linkNcode）の隣に置くか: 候補検索は「紐付け候補の提示」＝linkNcode と同じ
    // 「なろう紐付け」ドメインの一部で、新規 VM を増やさずここへ集約するのが自然なため。
    private val _ncodeSearchState = MutableStateFlow<NcodeSearchUiState>(NcodeSearchUiState.Loading)
    val ncodeSearchState: StateFlow<NcodeSearchUiState> = _ncodeSearchState.asStateFlow()

    // 直近のクエリ（再試行が「同じクエリでの再実行」になるよう保持する。旧 retryKey 相当）。
    private var lastNcodeQuery: String = ""

    // 実行中の検索ジョブ。新しい検索・再試行のたびに前回をキャンセルする
    // （旧 produceState はキー(activeQuery/retryKey)変更時に前コルーチンを自動キャンセルしていた＝それと等価）。
    private var ncodeSearchJob: Job? = null

    /**
     * なろう作品候補を検索して [ncodeSearchState] に反映する。
     * 旧 NcodeLinkSheet の produceState と挙動を等価に保つ:
     * - 空クエリは通信せず Success(空) を返す（初期の bookTitle が空でも一覧が空表示になるだけ）。
     * - 検索前に Loading を出す。
     * - NarouApiException のみ捕捉して Error に落とす。CancellationException は捕捉せず伝播させ、
     *   ジョブキャンセル時に一瞬エラー表示が挟まらないようにする（旧実装のコメントと同じ理由）。
     */
    fun searchNcodeCandidates(query: String) {
        lastNcodeQuery = query
        // 先行検索が走っていればキャンセル（キー変更時の produceState 自動キャンセルの再現）。
        ncodeSearchJob?.cancel()
        // 空クエリは通信せず即座に空 Success（旧 produceState と同一：Loading を挟まない）。
        if (query.isBlank()) {
            _ncodeSearchState.value = NcodeSearchUiState.Success(DiscoveryResult(0, emptyList()))
            return
        }
        // 非空クエリは Loading を「同期的に」反映する。なぜ launch 内でなく手前で更新するか:
        // シート再オープン時、VM の状態は前回の検索結果を保持している。Loading を launch（次の
        // ディスパッチ）まで遅らせると前回結果が一瞬ちらつくため、ここで即座に Loading へ落とす
        // （旧実装は再オープンごとに produceState が initialValue=Loading で作り直され残像が出なかった）。
        _ncodeSearchState.value = NcodeSearchUiState.Loading
        ncodeSearchJob = viewModelScope.launch {
            // NarouApiException のみ捕捉（NovelDetailViewModel と同じ方針）。CancellationException は
            // 捕捉せず伝播させ、ジョブキャンセル時に一瞬エラー表示が挟まらないようにする（旧実装と同理由）。
            _ncodeSearchState.value = try {
                val res = novelApiRepository.discover(
                    DiscoveryQuery(
                        word = query,
                        inTitle = true,
                        order = NarouOrder.TOTAL,
                        limit = 20,
                    )
                )
                NcodeSearchUiState.Success(res)
            } catch (e: NarouApiException) {
                NcodeSearchUiState.Error(e.userMessage)
            }
        }
    }

    /** 直近クエリで検索し直す（旧 retryKey++ 相当。ネットワークエラー時のワンタップ復旧）。 */
    fun retryNcodeSearch() = searchNcodeCandidates(lastNcodeQuery)

    suspend fun getLastRead(bookId: BookId): String? = repository.getLastRead(bookId)

    suspend fun getProgress(bookId: BookId): ProgressEntity? = repository.getProgress(bookId)

    // In-App Review の打診イベント（one-shot）。errorEvents/PdfImportEvent と同じ Channel 流儀＝
    // 受信時に消費され、画面回転の再購読で再発火しない。CONFLATED: 打診は1回で足りるため
    // UI が未購読の一瞬に複数積まれても最新1件だけ残ればよい。
    private val reviewPromptChannel = Channel<Unit>(Channel.CONFLATED)
    val reviewPromptEvents: Flow<Unit> = reviewPromptChannel.receiveAsFlow()

    // 同一セッション（VM 生存中）に打診を1回へ絞る腕木（true=装填済み）。
    // なぜ AtomicBoolean: markReachedEnd は IO ディスパッチャ上の並行 launch から呼ばれるため、
    // 素の var の check-then-set では複数冊のほぼ同時読了で二重打診しうる。CAS で構造的に潰す。
    private val reviewPromptArmed = AtomicBoolean(true)

    // 読了記録（最終章の末尾到達＝『了』印・読了フィルタの正本／ssot Major 2026-07-12）。
    // なぜ progressChannel（CONFLATED）を経由せず独立 launch か: チャネルは位置エンティティ搬送用で
    // CONFLATED により中間値を捨てる。読了は位置とは別次元の一度きりのフラグ立てで、位置更新と競合しても
    // 別列（reachedEnd）を UPDATE するため互いを潰さない。冪等な UPDATE なので多重呼び出しも無害。
    // なぜ素の Dispatchers.IO でなく注入 ioDispatcher か: レビュー打診イベントの発火有無を JVM テストが
    // advanceUntilIdle で決定的に観測するため（本番は既定値が Dispatchers.IO＝挙動不変）。
    fun markReachedEnd(bookId: BookId) {
        viewModelScope.launch(ioDispatcher) {
            // In-App Review のトリガ（監督裁定: 読了の瞬間＝reachedEnd false→true 遷移のみ）。
            // UPDATE 前に現在値を読むのは「初めての読了」だけを満足ピークとして拾うため
            // （既読了本の再読・章再入場の冪等呼び出しでは打診しない）。進捗行なし（null）は未読了と同義。
            val firstCompletion = repository.getProgress(bookId)?.reachedEnd != true
            repository.markReachedEnd(bookId)
            // Play 側にも表示クォータ管理はあるが、呼び出し自体を満足ピーク1点に絞るのが本機能の設計
            //（章送りトリガを入れない裁定と同根＝連続読了でも同一セッションでは1回しか打診しない）。
            if (firstCompletion && reviewPromptArmed.compareAndSet(true, false)) {
                reviewPromptChannel.trySend(Unit)
            }
        }
    }

    // 章移動時の保存。スクロール位置は default 0 のまま送ることで章先頭にリセットする。
    // ProgressEntity（Room 実体）はチャネル搬送体のため String 列。ここが型付き引数→String の境界。
    fun saveProgress(bookId: BookId, filename: ChapterFilename) {
        progressChannel.trySend(ProgressEntity(bookId.value, filename.value))
    }

    fun saveScrollPosition(bookId: BookId, filename: ChapterFilename, scrollIndex: Int, scrollOffset: Int) {
        progressChannel.trySend(ProgressEntity(bookId.value, filename.value, scrollIndex, scrollOffset))
    }
}
