package com.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DiscoveryUiState {
    object Loading : DiscoveryUiState
    data class Content(val allcount: Int, val novels: List<NarouNovel>) : DiscoveryUiState
    object Empty : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
}

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NovelReaderApplication
    private val repository = app.novelApiRepository

    // ── 発見ホーム（orderタブ×ランキング） ──

    private val _homeOrder = MutableStateFlow(NarouOrder.WEEKLY)
    val homeOrder: StateFlow<NarouOrder> = _homeOrder.asStateFlow()

    private val _homeState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val homeState: StateFlow<DiscoveryUiState> = _homeState.asStateFlow()

    private var homeLoadRequested = false

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

    private fun loadHome() {
        viewModelScope.launch {
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
}
