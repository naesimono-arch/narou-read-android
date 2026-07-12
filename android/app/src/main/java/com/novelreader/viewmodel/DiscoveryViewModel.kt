package com.novelreader.viewmodel

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.SearchHistory
import com.novelreader.narou.SearchHistoryStore
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouGenres
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

sealed interface DiscoveryUiState {
    object Loading : DiscoveryUiState
    // paging は結果一覧のフルページング（F-J）用のフッタ状態。ページングを持たない経路（ホーム等）は
    // 既定の Complete＝フッタ非表示のまま。
    data class Content(
        val allcount: Int,
        val novels: List<NarouNovel>,
        val paging: PagingState = PagingState.Complete,
    ) : DiscoveryUiState
    object Empty : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
}

/**
 * 結果一覧の追加読み込み（フルページング）フッタ状態。Content に内包し、既存結果を保持したまま遷移する。
 * なぜ Content 内包か: 追加読み込み中・追加失敗でも「取得済みの一覧」を捨てないため
 *（失敗時は Idle/Error へ戻して再試行可能に保つ）。
 */
sealed interface PagingState {
    /** 続きがある＝「さらに読み込む」ボタンを出す。 */
    object Idle : PagingState
    /** 追加ページを取得中。 */
    object LoadingMore : PagingState
    /** 追加取得に失敗（既存結果は保持。message を出して再試行可能に）。 */
    data class LoadMoreError(val message: String) : PagingState
    /** 全件表示済み＝フッタなし。 */
    object Complete : PagingState
    /** なろうAPIの取得上限（st>2000 等）に到達＝全件には届かないが以降は取得できない旨を明示。 */
    object ApiLimitReached : PagingState
}

enum class ResultSource { SEARCH, KEYWORD, GENRE, MOOD }

/**
 * 結果一覧（discovery/result）の文脈。検索・ジャンル・気分プリセットの共通着地に
 * 「何の結果を見ているか」（明朝見出し＋補足＋実クエリ）を運ぶ。
 */
// なぜ Parcelable か: process death からの復帰時に「何の結果を見ているか」を SavedStateHandle
// （＝Bundle）から復元するため（F-C）。ncode を SavedState で持つ NovelDetail と対称にする。
@Parcelize
data class ResultContext(
    val title: String,
    val subtitle: String? = null,
    val source: ResultSource,
    val query: DiscoveryQuery,
) : Parcelable

// なぜ SavedStateHandle をコンストラクタで受けるか: process death で失われるメモリ上の状態
// （結果一覧の文脈・検索ドラフト）を退避／復元するため（F-C/F-E）。default 値は既存の単体テスト
// （DiscoveryViewModel(app) 直呼び）と、SavedStateViewModelFactory 非経由の生成を壊さないため。
// 本番の viewModel() は Activity 既定の SavedStateViewModelFactory が (Application, SavedStateHandle)
// シグネチャを解決して Activity の SavedStateRegistry 由来のハンドルを注入する＝追加ファクトリ不要。
class DiscoveryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : AndroidViewModel(application) {
    private val app = application as NovelReaderApplication
    private val repository = app.novelApiRepository

    private companion object {
        // SavedStateHandle のキー（process death 復元用）
        const val KEY_RESULT_CONTEXT = "result_context"
        const val KEY_SEARCH_DRAFT = "search_draft"
    }

    // ── 発見ホーム（orderタブ×ランキング） ──

    private val _homeOrder = MutableStateFlow(NarouOrder.WEEKLY)
    val homeOrder: StateFlow<NarouOrder> = _homeOrder.asStateFlow()

    private val _homeState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val homeState: StateFlow<DiscoveryUiState> = _homeState.asStateFlow()

    private var homeLoadRequested = false

    // なぜ Job を保持してキャンセルするか: 先行ロードを走らせたまま新ロードを launch すると、
    // 遅い旧クエリの応答が後着したとき「タブ・見出しは新、リスト本体は旧」の食い違い表示になる
    // （応答の完了順は要求順を保証しない）。キャンセルで「最後の要求だけが状態を書く」を構造的に保証する。
    private var homeLoadJob: Job? = null
    private var resultLoadJob: Job? = null

    /**
     * ホームのランキングを初回のみロードする（画面表示時に呼ぶ）。
     * なぜ init でロードしないか: この VM は発見系画面群で共有するため上位（ナビ側）で生成されるが、
     * ユーザーが発見画面を開くまで API 通信を発生させない（なろうAPIの転送量マナー＋無駄な通信回避）。
     */
    fun ensureHomeLoaded() {
        if (homeLoadRequested) return
        homeLoadRequested = true
        loadHome()
    }

    /** orderタブ切替。同じタブの再タップでは再取得しない（Repository キャッシュとは別の抑制）。 */
    fun setHomeOrder(order: NarouOrder) {
        if (_homeOrder.value == order) return
        _homeOrder.value = order
        loadHome()
    }

    fun refreshHome() {
        loadHome()
    }

    // ── 結果一覧（検索/ジャンル/気分プリセットの共通着地） ──

    private val _resultContext = MutableStateFlow<ResultContext?>(null)
    val resultContext: StateFlow<ResultContext?> = _resultContext.asStateFlow()

    private val _resultState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val resultState: StateFlow<DiscoveryUiState> = _resultState.asStateFlow()

    /** 結果一覧の文脈を差し替えてロードする（呼び出し側はこのあと discovery/result へ navigate する）。 */
    fun openResult(context: ResultContext) {
        _resultContext.value = context
        persistResultContext()
        loadResult()
    }

    // 現在の結果文脈を SavedStateHandle へ退避する（process death 復帰の元データ・F-C）。
    // 文脈が変わる全経路（openResult／order・genre のその場変更）から呼ぶ。
    private fun persistResultContext() {
        savedStateHandle[KEY_RESULT_CONTEXT] = _resultContext.value
    }

    fun refreshResult() {
        loadResult()
    }

    fun changeResultOrder(order: NarouOrder) {
        // なぜ update か: 最新値を基点に read-modify-write をアトミックに行い、並行更新の取りこぼしを防ぐ。
        // 文脈未確立（null）なら変更もロードもしないので事前に弾く。
        if (_resultContext.value == null) return
        _resultContext.update { current ->
            if (current == null) return@update null
            current.copy(query = current.query.copy(order = order))
        }
        persistResultContext()
        loadResult()
    }

    fun changeResultGenreFilter(biggenres: Set<Int>, genres: Set<Int>) {
        // なぜ update か: 最新値を基点に read-modify-write をアトミックに行い、並行更新の取りこぼしを防ぐ。
        // 文脈未確立（null）なら変更もロードもしないので事前に弾く。
        if (_resultContext.value == null) return
        _resultContext.update { current ->
            // なぜ nextTitle を update 内で算出するか: 見出しは current の source/title から導く派生値のため、
            // update が再試行されても常に最新状態を基点に計算する（副作用なし・冪等なので再実行しても安全）。
            if (current == null) return@update null
            // なぜ: SEARCH/KEYWORD 発の結果の見出しは検索語（『「最強」』等）であり、ジャンルをその場変更しても“何を検索したか”は変わらないため見出しは維持する。GENRE 発のみ見出し＝ジャンル名なので追従させる。
            val nextTitle = if (current.source == ResultSource.GENRE) {
                when {
                    genres.size == 1 -> NarouGenres.genreLabel(genres.first()) ?: current.title
                    biggenres.size == 1 -> NarouGenres.biggenreLabel(biggenres.first()) ?: current.title
                    genres.isEmpty() && biggenres.isEmpty() -> "すべての作品"
                    else -> current.title
                }
            } else {
                current.title
            }
            current.copy(
                title = nextTitle,
                query = current.query.copy(biggenres = biggenres, genres = genres)
            )
        }
        persistResultContext()
        loadResult()
    }

    // 追加読み込み（フルページング）のジョブ。新クエリのロード時にキャンセルして無効化する。
    private var loadMoreJob: Job? = null

    private fun loadResult() {
        val ctx = _resultContext.value ?: return
        resultLoadJob?.cancel()
        // なぜ loadMoreJob もキャンセルするか: 並び順・ジャンル変更や再取得でクエリが変わると、進行中の
        // 追加読み込み（旧クエリの続き）は無効。放置すると旧ページが新結果へ後着追記される破綻を招く。
        loadMoreJob?.cancel()
        resultLoadJob = viewModelScope.launch {
            _resultState.value = DiscoveryUiState.Loading
            _resultState.value = fetchResultFirstPage(ctx.query)
        }
    }

    /**
     * 結果一覧の初回ページを取得して UiState へ畳む。ページング用に paging を初期化する。
     * 初回は offset=0（st 省略）＝通常取得のため、件数上限(30)<APIオフセット上限(2000)で
     * 初回に取得上限へ達することはない（reachedApiLimit=false 固定）。
     */
    private suspend fun fetchResultFirstPage(query: DiscoveryQuery): DiscoveryUiState {
        return try {
            val result = repository.discover(query)
            if (result.novels.isEmpty()) {
                DiscoveryUiState.Empty
            } else {
                DiscoveryUiState.Content(
                    allcount = result.allcount,
                    novels = result.novels,
                    paging = resolvePaging(
                        loaded = result.novels.size,
                        allcount = result.allcount,
                        gotItems = true,
                        reachedApiLimit = false,
                    ),
                )
            }
        } catch (e: NarouApiException) {
            DiscoveryUiState.Error(e.userMessage)
        }
    }

    /**
     * 追加読み込みの一手。結果一覧フッタ「さらに読み込む」／追加失敗の再試行から呼ぶ。
     * なぜ明示ボタン方式（スクロール末尾検知でない）か: 末尾検知は高速スクロールやレイアウト再測定で
     * 多重発火しやすく、余分なページ取得を誘発する（なろうAPIの転送量マナーに反する）。明示ボタンは
     * 発火点が一意で再入も自明に閉じられ、UX原則（ユーザー主導・予測可能）と API マナーの双方に適う。
     */
    fun loadMoreResults() {
        val current = _resultState.value
        // Content 以外（Loading/Empty/Error）では追加読み込みしない。
        if (current !is DiscoveryUiState.Content) return
        // 追加できるのは Idle（続きがある）か LoadMoreError（失敗後の再試行）だけ。
        // LoadingMore/Complete/ApiLimitReached からは発火させない＝連打での二重取得を型で弾く。
        if (current.paging != PagingState.Idle && current.paging !is PagingState.LoadMoreError) return
        // 進行中ジョブがあれば無視（高速スクロール・連打で loadMore が二重に走る再入を防ぐ）。
        if (loadMoreJob?.isActive == true) return
        val ctx = _resultContext.value ?: return

        val offset = current.novels.size
        _resultState.value = current.copy(paging = PagingState.LoadingMore)
        loadMoreJob = viewModelScope.launch {
            val next = try {
                repository.discoverPage(ctx.query, offset = offset)
            } catch (e: NarouApiException) {
                // なぜ current を基点に戻すか: 追加取得の失敗で既存の取得済み結果を捨てない（再試行可能に保つ）。
                _resultState.value = current.copy(paging = PagingState.LoadMoreError(e.userMessage))
                return@launch
            }
            val mergedNovels = current.novels + next.novels
            _resultState.value = current.copy(
                novels = mergedNovels,
                // 総数はページングで変わらない＝初回ページの allcount を維持（総件数表示が自然に追従する）。
                paging = resolvePaging(
                    loaded = mergedNovels.size,
                    allcount = current.allcount,
                    gotItems = next.novels.isNotEmpty(),
                    reachedApiLimit = next.reachedApiLimit,
                ),
            )
        }
    }

    /**
     * ページ取得後の次フッタ状態を決める。
     * 優先順位: 取得上限 > 全件到達 > サーバ打ち止め > まだ続く。
     */
    private fun resolvePaging(
        loaded: Int,
        allcount: Int,
        gotItems: Boolean,
        reachedApiLimit: Boolean,
    ): PagingState = when {
        // API エンベロープ（st>2000／マージ経路 500）で続きが取れない＝全件には届かないが打ち止め。
        reachedApiLimit -> PagingState.ApiLimitReached
        // 取得済みが総数に到達＝全件表示済み。
        loaded >= allcount -> PagingState.Complete
        // 総数上は続きがあるはずだがサーバが空を返した（allcount の遅延・揺れ）＝これ以上は増えないので打ち止め。
        !gotItems -> PagingState.Complete
        else -> PagingState.Idle
    }

    // ── 検索ドラフト（検索語＋範囲＋条件シート） ──

    private val _searchDraft = MutableStateFlow(SearchDraft())
    val searchDraft: StateFlow<SearchDraft> = _searchDraft.asStateFlow()

    fun setSearchDraft(draft: SearchDraft) {
        _searchDraft.value = draft
        // process death 復帰でドラフト（検索語＋範囲＋条件）を失わないよう SavedStateHandle へミラー（F-E）。
        savedStateHandle[KEY_SEARCH_DRAFT] = draft
    }

    // ── カスタム文字数/読了時間の入力（SSOT: draft が生入力テキストの唯一の保持先） ──
    // なぜ VM 経由か: 以前は画面 remember＋LaunchedEffect で filters.length/time と生テキストを
    // 双方向同期していた二重真実源を draft 一本へ畳むため（SearchDraft のフィールド説明参照）。
    // 入力を正規化（半角数字のみ）→ 送出用レンジへ組み立て → 生テキストと length/time を一括更新する。

    /** カスタム文字数の生入力テキストを更新する（10000＝万字への係数）。 */
    fun setLengthCustomText(min: String, max: String) {
        val normMin = normalizeCustomRangeInput(min)
        val normMax = normalizeCustomRangeInput(max)
        val range = buildCustomRange(normMin, normMax, 10000)
        val current = _searchDraft.value
        setSearchDraft(
            current.copy(
                lengthCustomMin = normMin,
                lengthCustomMax = normMax,
                filters = current.filters.withLength(range),
            )
        )
    }

    /** カスタム読了時間の生入力テキストを更新する（60＝分への係数）。 */
    fun setTimeCustomText(min: String, max: String) {
        val normMin = normalizeCustomRangeInput(min)
        val normMax = normalizeCustomRangeInput(max)
        val range = buildCustomRange(normMin, normMax, 60)
        val current = _searchDraft.value
        setSearchDraft(
            current.copy(
                timeCustomMin = normMin,
                timeCustomMax = normMax,
                filters = current.filters.withTime(range),
            )
        )
    }

    /**
     * 文字数の段階プリセットをトグルする。
     * なぜ next==null（トグルで単一レンジが解消）時に生テキストも掃くか: 旧実装は LaunchedEffect が
     * length→生テキストを一方向で追随して掃いていた。SSOT 化でその追随を廃するため、length を null に
     * 戻すこの経路自身が生テキストを掃く責務を負う（掃かないと再度カスタムを開いた際に残骸が復活する）。
     */
    fun toggleLengthStep(index: Int) {
        val current = _searchDraft.value
        val next = toggleRangeStep(current.filters.length, index, LENGTH_STEPS)
        setSearchDraft(
            current.copy(
                lengthCustomMin = if (next == null) "" else current.lengthCustomMin,
                lengthCustomMax = if (next == null) "" else current.lengthCustomMax,
                filters = current.filters.withLength(next),
            )
        )
    }

    /** 読了時間の段階プリセットをトグルする（[toggleLengthStep] の時間版）。 */
    fun toggleTimeStep(index: Int) {
        val current = _searchDraft.value
        val next = toggleRangeStep(current.filters.time, index, TIME_STEPS)
        setSearchDraft(
            current.copy(
                timeCustomMin = if (next == null) "" else current.timeCustomMin,
                timeCustomMax = if (next == null) "" else current.timeCustomMax,
                filters = current.filters.withTime(next),
            )
        )
    }

    /** 現在のドラフトで検索を実行し、結果一覧の文脈を差し替える。実行できたら true。 */
    fun executeSearch(): Boolean {
        val draft = _searchDraft.value
        if (!draft.canSearch) return false
        openResult(ResultContext(title = draft.resultTitle(), query = draft.toQuery(), source = ResultSource.SEARCH))
        // 検索語があれば履歴へ（条件のみの検索は語が無いので残さない）
        draft.word.trim().takeIf { it.isNotBlank() }?.let { word ->
            viewModelScope.launch { historyStore.addRecent(word) }
        }
        return true
    }

    // ── 検索履歴＋ピン留め（D1） ──

    private val historyStore: SearchHistoryStore get() = app.searchHistoryStore

    /**
     * なぜ lazy＋WhileSubscribed か: 検索画面が collect するまで DataStore を読まないようにするため
     * （このVMは上位共有で本棚起動時にも生成される。ホームだけ使う限りディスクにも触れない）。
     */
    val searchHistory: StateFlow<SearchHistory> by lazy {
        historyStore.history.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SearchHistory(),
        )
    }

    fun pinWord(word: String) {
        viewModelScope.launch { historyStore.pin(word) }
    }

    fun unpinWord(word: String) {
        viewModelScope.launch { historyStore.unpin(word) }
    }

    fun removeRecentWord(word: String) {
        viewModelScope.launch { historyStore.removeRecent(word) }
    }

    /** 履歴チップのタップ: 検索語をドラフトへ移して即実行する。 */
    fun searchFromHistory(word: String): Boolean {
        // なぜ update か: 最新のドラフトを基点に word だけ差し替える read-modify-write をアトミックに行う。
        _searchDraft.update { it.copy(word = word) }
        // ドラフト変更経路なので SavedStateHandle にもミラーする（F-E）。
        savedStateHandle[KEY_SEARCH_DRAFT] = _searchDraft.value
        return executeSearch()
    }

    private fun loadHome() {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            _homeState.value = DiscoveryUiState.Loading
            _homeState.value = fetch(DiscoveryQuery(order = _homeOrder.value))
        }
    }

    /**
     * discover を叩いて UiState へ畳み込む共通経路。
     * Retrofit の suspend 関数は呼び出しスレッドをブロックしない main-safe な設計（内部で OkHttp の
     * ディスパッチャへ退避し結果だけ再開する）ため、ここで withContext(Dispatchers.IO) は不要。
     * 逆に実 IO ディスパッチャへ切り替えると、テストの TestDispatcher の制御が及ばず状態遷移を
     * 検証できなくなる（＝機能上もテスト容易性上も、既定ディスパッチャ上で待つのが正しい）。
     */
    private suspend fun fetch(query: DiscoveryQuery): DiscoveryUiState {
        return try {
            val result = repository.discover(query)
            if (result.novels.isEmpty()) {
                DiscoveryUiState.Empty
            } else {
                DiscoveryUiState.Content(result.allcount, result.novels)
            }
        } catch (e: NarouApiException) {
            // なぜ NarouApiException のみを捕捉するか:
            // ネットワークやHTTP由来の期待された例外のみを捕捉して安全にUIにエラーを表示し、
            // それ以外の想定外のランタイム例外などを握り潰さずクラッシュさせて開発時に気付けるようにするため。
            DiscoveryUiState.Error(e.userMessage)
        }
    }

    // process death 復帰時の状態復元。全プロパティ初期化子の後に走るよう末尾に置く
    // （init は宣言順で実行され、_resultContext・_searchDraft の初期化後である必要があるため）。
    init {
        // F-C: 結果一覧の文脈を復元し、そのまま再取得する（旧実装はここが空で ctx==null→前画面へ強制退去していた）。
        //
        // 監査 persist Minor（DiscoveryResultScreen 積み上げページ喪失）への裁定: 積み上がった novels＋paging は
        // SavedStateHandle へ **ミラーしない**。理由（＝近似で誤魔化さず停止して報告した結論）:
        //   ① NarouNovel は Moshi の @JsonClass モデルで Parcelable ではない。ミラーには JSON モデルを Parcelize
        //      する（責務違反）か手動シリアライズが要る。
        //   ② さらに「さらに読み込む」で数百件まで積み上がった一覧を Bundle に載せると savedInstanceState の
        //      Binder 上限に触れ TransactionTooLargeException を招きやすい（プロセス death 復帰でクラッシュ）。
        //   ③ 復帰時に積み上げ分を API 再取得すると、明示ボタン方式で守っている「なろうAPI 転送量マナー」
        //      （ユーザー主導・無駄取得回避）を復帰の度に破る。
        // よって監査が代替として挙げる「復帰時は先頭へ明示リセット」を採る＝下の loadResult() が初回ページを
        // 取り直し、一覧は先頭から再描画される（クラッシュせず静かに減衰＝グレースフルデグレード）。軽い文脈
        // （ResultContext）だけを持ち回り、重い結果本体は持ち回らないのが SSOT×可搬性のバランス点。
        savedStateHandle.get<ResultContext>(KEY_RESULT_CONTEXT)?.let { restored ->
            _resultContext.value = restored
            loadResult()
        }
        // F-E: 検索ドラフトを復元する（検索語＋範囲＋条件シート）。
        savedStateHandle.get<SearchDraft>(KEY_SEARCH_DRAFT)?.let { restored ->
            _searchDraft.value = restored
        }
    }
}
