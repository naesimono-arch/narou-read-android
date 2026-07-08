package com.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.model.NarouNovel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow<NovelDetailUiState>(NovelDetailUiState.Loading)
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    private var loadedNcode: String? = null

    // なぜ Job を保持してキャンセルするか: 別 ncode で load() を連打すると、遅い旧リクエストの応答が
    // 後着して新しい結果を上書きしうる（応答の完了順は要求順を保証しない）。先行ロードをキャンセルして
    // 「最後の要求だけが状態を書く」を DiscoveryViewModel（homeLoadJob/resultLoadJob）と同じ方式で構造的に保証する。
    private var loadJob: Job? = null

    /** 同一 ncode の再呼び出し（再コンポーズ等）ではロードし直さない。 */
    fun load(ncode: String) {
        if (loadedNcode == ncode && _uiState.value !is NovelDetailUiState.Error) return
        loadedNcode = ncode
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
