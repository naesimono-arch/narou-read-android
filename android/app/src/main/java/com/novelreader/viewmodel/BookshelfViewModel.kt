package com.novelreader.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     *  呼び出しの互換のため（Web カード非対応の経路は蔵書のみで従来どおり成立する）。 */
    data class Content(
        val books: List<BookEntity>,
        val webNovels: List<WebNovelEntity> = emptyList(),
        // 機能②: ncode(正規化済み大文字)→最後に開いた話。Web カードの「続きから読む 第N話」に使う（未記録は 0＝未読）。
        val webReadingProgress: Map<String, Int> = emptyMap(),
    ) : BookshelfUiState
}

/**
 * UI へ配送する一度きりのエラー通知（Snackbar）。
 * なぜ String でなくデータクラスか（M7）: 取込失敗の Snackbar に「再試行」を出すには、どの URI が
 * 失敗したかを UI まで運ぶ必要がある。retryUri が非 null のときだけ再試行アクションを出し、同一 URI で
 * 取込を再投入する。復元系の情報通知（retryUri=null）は従来どおり文言のみを表示する。
 */
data class AppErrorEvent(val message: String, val retryUri: String? = null)

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
)

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

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
                webNovels = webNovels,
                // ncode→最後に開いた話へ畳む（描画層は Map を引くだけ＝mergeShelfItems が Web カードへ載せる）。
                webReadingProgress = webReadingProgress.associate { it.ncode to it.lastReadEpisode },
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
        .flowOn(Dispatchers.IO)
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
    val newEpisodeNovelMap: StateFlow<Map<String, NarouNovel>> = books
        .map { list -> list.mapNotNull { it.ncode }.distinct() }
        // 本棚の並び替え等で ncode 集合が不変なら再照会しない（6h TTL キャッシュへの無駄叩き回避）。
        .distinctUntilChanged()
        // 本棚リスト（ncode 集合）変化時に前回の照会を破棄して最新集合で回し直す。
        .mapLatest { ncodes ->
            val result = LinkedHashMap<String, NarouNovel>()
            for (ncode in ncodes) {
                try {
                    novelApiRepository.novelDetail(Ncode(ncode))?.let { result[ncode] = it }
                } catch (e: NarouApiException) {
                    // オフライン等の失敗はバッジ非表示で静かに握り潰す（本棚を通信エラーで騒がせない）。
                }
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Application の StateFlow を購読して processingState を提供
    val processingState: StateFlow<ProcessingState> = app.processingState
        .map { it ?: ProcessingState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessingState())

    // エラーは一度きりのイベント。Application の Channel を購読してそのまま UI へ流す。
    val errorEvents: Flow<AppErrorEvent> = app.errorEvents

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
        viewModelScope.launch(Dispatchers.IO) {
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
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
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
        viewModelScope.launch(Dispatchers.IO) {
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

    // 変換の全体停止。キュー待ちを破棄し、処理中の1冊もページ境界で即中断する
    // （純 Kotlin 化で割り込みが可能になった）。Service へ STOP を送るだけ。
    fun cancelProcessing() {
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_STOP
        }
        // 既に前面で動作中の FGS への命令送信。新規起動が不要なため startService を使う。
        getApplication<Application>().startService(intent)
    }

    // 取込失敗 Snackbar の「再試行」（M7）。失敗した URI をそのまま再投入する。
    // Service 側のべき等ガード（ActiveUriTracker）は失敗確定時に該当 URI を release 済みのため、
    // この再投入は「重複」で弾かれず新規変換として受け付けられる（processSingleUri の finally で release）。
    fun retryImport(uriString: String) {
        addBook(Uri.parse(uriString))
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) }
    }

    // (b) Web由来・未取込カードを本棚から外す。webNovels は hot に uiState へ combine 済みのため、
    // 削除すれば本棚から即時に消える（deleteBook と同じ配送経路）。
    fun removeWebNovel(ncode: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.removeWebNovel(Ncode(ncode)) }
    }

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。
    // books は hot StateFlow のため、書き込めば読書画面の継続導線へ自動で反映される。
    fun linkNcode(bookId: BookId, ncode: Ncode?) {
        viewModelScope.launch(Dispatchers.IO) { repository.linkNcode(bookId, ncode) }
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

    // 読了記録（最終章の末尾到達＝『了』印・読了フィルタの正本／ssot Major 2026-07-12）。
    // なぜ progressChannel（CONFLATED）を経由せず独立 launch か: チャネルは位置エンティティ搬送用で
    // CONFLATED により中間値を捨てる。読了は位置とは別次元の一度きりのフラグ立てで、位置更新と競合しても
    // 別列（reachedEnd）を UPDATE するため互いを潰さない。冪等な UPDATE なので多重呼び出しも無害。
    fun markReachedEnd(bookId: BookId) {
        viewModelScope.launch(Dispatchers.IO) { repository.markReachedEnd(bookId) }
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
