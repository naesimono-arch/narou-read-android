package com.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.data.WebNovelEntity
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NovelDetailUiState {
    object Loading : NovelDetailUiState
    /**
     * @param fetchedAtMillis この詳細を API から取得し終えた時刻（epoch ms）。
     *   なぜ保持するか（M6/公理5 SSOT）: 詳細は一覧値の写しではなく取り直した最新値であり、
     *   画面に「いつ時点の情報か」を出して一覧との別取得由来の食い違いを判別可能にするため。
     */
    data class Content(val novel: NarouNovel, val fetchedAtMillis: Long) : NovelDetailUiState
    /** ncode に該当する作品が API に存在しない（削除・検索除外設定など）。 */
    object NotFound : NovelDetailUiState
    data class Error(val message: String) : NovelDetailUiState
}

/**
 * 作品詳細（discovery/detail/{ncode}）。ナビ引数の ncode を load() で受けて全項目を取得する。
 * DiscoveryViewModel から分離している理由: 詳細はルート引数だけで自己完結し、
 * 発見系の共有状態（ホーム/結果一覧）と寿命が異なるため。
 */
class NovelDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NovelReaderApplication
    private val repository = app.novelApiRepository

    // (b) Web由来・未取込カード: 「本棚に置く/外す」の永続先（蔵書 Room の web_novels）。
    private val bookRepository = app.repository

    private val _uiState = MutableStateFlow<NovelDetailUiState>(NovelDetailUiState.Loading)
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    private var loadedNcode: Ncode? = null

    // load() された ncode の Flow 版。onShelf/isImported の購読切り替え（flatMapLatest）の起点にする。
    private val ncodeFlow = MutableStateFlow<Ncode?>(null)

    /** 現在の作品が Web由来カードとして本棚に置かれているか（固定バーのトグル表示用）。
     *  比較は保存時正規化（trim+uppercase）と同じ形＝表記ゆれで「置いたのにトグルが戻る」事故を防ぐ。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val onShelf: StateFlow<Boolean> = ncodeFlow
        .flatMapLatest { nc ->
            if (nc == null) flowOf(false)
            else bookRepository.webNovels.map { list ->
                val normalized = nc.value.trim().uppercase()
                list.any { it.ncode == normalized }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 現在の作品が既に蔵書（PDF 取込済み・ncode 紐付け）か。
     *  取込済みなら「取り込む」「本棚に置く」の2アクションは冗長のため固定バーから隠す（モック注記）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isImported: StateFlow<Boolean> = ncodeFlow
        .flatMapLatest { nc ->
            if (nc == null) flowOf(false)
            else bookRepository.allBooks.map { books ->
                books.any { it.ncode?.trim()?.equals(nc.value.trim(), ignoreCase = true) == true }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 「本棚に置く/外す」トグル。Content 未取得（Loading/Error）では何もしない
     *  （置くのに必要な title/writer/話数が無く、ボタン自体も Content でしか出ない）。 */
    fun toggleShelf() {
        val state = _uiState.value as? NovelDetailUiState.Content ?: return
        val nc = loadedNcode ?: return
        viewModelScope.launch {
            if (onShelf.value) {
                bookRepository.removeWebNovel(nc)
            } else {
                bookRepository.putWebNovel(
                    WebNovelEntity(
                        // 保存正規化は NcodeLinkSheet の紐付けと同系（trim+uppercase）＝二重カード防止。
                        ncode = nc.value.trim().uppercase(),
                        title = state.novel.title ?: nc.value,
                        writer = state.novel.writer ?: "",
                        generalAllNo = state.novel.generalAllNo ?: 0,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    // なぜ Job を保持してキャンセルするか: 別 ncode で load() を連打すると、遅い旧リクエストの応答が
    // 後着して新しい結果を上書きしうる（応答の完了順は要求順を保証しない）。先行ロードをキャンセルして
    // 「最後の要求だけが状態を書く」を DiscoveryViewModel（homeLoadJob/resultLoadJob）と同じ方式で構造的に保証する。
    private var loadJob: Job? = null

    /** 同一 ncode の再呼び出し（再コンポーズ等）ではロードし直さない。 */
    fun load(ncode: Ncode) {
        if (loadedNcode == ncode && _uiState.value !is NovelDetailUiState.Error) return
        loadedNcode = ncode
        ncodeFlow.value = ncode
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = NovelDetailUiState.Loading
            _uiState.value = try {
                val novel = repository.novelDetail(ncode)
                if (novel != null) {
                    NovelDetailUiState.Content(novel, System.currentTimeMillis())
                } else {
                    NovelDetailUiState.NotFound
                }
            } catch (e: NarouApiException) {
                NovelDetailUiState.Error(e.userMessage)
            }
        }
    }

    fun retry() {
        val ncode = loadedNcode ?: return
        loadedNcode = null
        load(ncode)
    }
}
