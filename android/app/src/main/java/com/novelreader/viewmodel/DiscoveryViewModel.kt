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
    data class Content(val allcount: Int, val novels: List<NarouNovel>) : DiscoveryUiState
    object Empty : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
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

    private fun loadResult() {
        val ctx = _resultContext.value ?: return
        resultLoadJob?.cancel()
        resultLoadJob = viewModelScope.launch {
            _resultState.value = DiscoveryUiState.Loading
            _resultState.value = fetch(ctx.query)
        }
    }

    // ── 検索ドラフト（検索語＋範囲＋条件シート） ──

    private val _searchDraft = MutableStateFlow(SearchDraft())
    val searchDraft: StateFlow<SearchDraft> = _searchDraft.asStateFlow()

    fun setSearchDraft(draft: SearchDraft) {
        _searchDraft.value = draft
        // process death 復帰でドラフト（検索語＋範囲＋条件）を失わないよう SavedStateHandle へミラー（F-E）。
        savedStateHandle[KEY_SEARCH_DRAFT] = draft
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
