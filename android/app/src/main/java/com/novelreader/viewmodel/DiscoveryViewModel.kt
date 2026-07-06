package com.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.model.NarouNovel
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

    private val _uiState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        loadWeeklyRanking()
    }

    fun refresh() {
        loadWeeklyRanking()
    }

    private fun loadWeeklyRanking() {
        viewModelScope.launch {
            _uiState.value = DiscoveryUiState.Loading
            try {
                // Retrofit の suspend 関数は呼び出しスレッドをブロックしない main-safe な設計（内部で OkHttp の
                // ディスパッチャへ退避し結果だけ再開する）ため、ここで withContext(Dispatchers.IO) は不要。
                // 逆に実 IO ディスパッチャへ切り替えると、テストの TestDispatcher の制御が及ばず状態遷移を
                // 検証できなくなる（＝機能上もテスト容易性上も、既定ディスパッチャ上で待つのが正しい）。
                val result = repository.weeklyRanking()
                _uiState.value = if (result.novels.isEmpty()) {
                    DiscoveryUiState.Empty
                } else {
                    DiscoveryUiState.Content(result.allcount, result.novels)
                }
            } catch (e: NarouApiException) {
                // なぜ NarouApiException のみを捕捉するか:
                // ネットワークやHTTP由来の期待された例外のみを捕捉して安全にUIにエラーを表示し、
                // それ以外の想定外のランタイム例外などを握り潰さずクラッシュさせて開発時に気付けるようにするため。
                _uiState.value = DiscoveryUiState.Error(e.userMessage)
            }
        }
    }
}
