package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.novelreader.repository.BookRepository
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class NovelReaderApplication : Application() {

    /** 書籍データアクセス層のシングルトン（Service/ViewModel 共用） */
    val repository: BookRepository by lazy { BookRepository(this) }

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
