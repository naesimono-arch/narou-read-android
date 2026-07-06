package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.novelreader.narou.DataStoreSearchHistoryStore
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.SearchHistoryStore
import com.novelreader.repository.BookRepository
import com.novelreader.viewmodel.ProcessingState
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class NovelReaderApplication : Application() {

    /** 書籍データアクセス層のシングルトン（Service/ViewModel 共用） */
    val repository: BookRepository by lazy { BookRepository(this) }

    /** なろうAPIを利用したディスカバリ用リポジトリのシングルトン（既存 repository と別系統） */
    val novelApiRepository: NovelApiRepository by lazy { NovelApiRepository() }

    /** 検索履歴＋ピン留め（発見機能 D1）のシングルトン。 */
    val searchHistoryStore: SearchHistoryStore by lazy { DataStoreSearchHistoryStore(this) }

    /** サービス↔ViewModel間の処理状態共有（書き込みは updateProcessingState のみ） */
    private val _processingState = MutableStateFlow<ProcessingState?>(null)
    val processingState: StateFlow<ProcessingState?> = _processingState.asStateFlow()

    // エラーは一度きりのイベント。StateFlow だと構成変更（画面回転）で再表示され、
    // 複数購読時に重複する恐れがあるため、単一コンシューマ向けの Channel で配送する。
    // 受信時に消費され状態として残らないので clearError は不要。
    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents: Flow<String> = _errorEvents.receiveAsFlow()

    fun updateProcessingState(state: ProcessingState?) { _processingState.value = state }
    fun emitError(msg: String) { _errorEvents.trySend(msg) }

    override fun onCreate() {
        super.onCreate()
        // PDFBox-Android のフォント/CMap 資産ローダを初期化する。ToUnicode CMap 非搭載の CID フォントを
        // グリフ→Unicode 解決するのに AAR 同梱資産を使うため、あらゆる PDDocument.load より前に一度だけ必要
        // （task_diary #31）。PdfProcessingService は MainActivity 無しでも走る（プロセス再生成・サービス起動
        // 経路）ため、全コンポーネントより先に必ず走る Application で先行初期化し、Service が最初の PDF を
        // 処理する前に init 済みを保証する。
        PDFBoxResourceLoader.init(applicationContext)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PDF変換",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pdf_processing_channel"
    }
}
