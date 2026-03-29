package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.novelreader.repository.BookRepository
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NovelReaderApplication : Application() {

    /** 書籍データアクセス層のシングルトン（Service/ViewModel 共用） */
    val repository: BookRepository by lazy { BookRepository(this) }

    /** サービス↔ViewModel間の処理状態共有（書き込みは updateProcessingState のみ） */
    private val _processingState = MutableStateFlow<ProcessingState?>(null)
    val processingState: StateFlow<ProcessingState?> = _processingState.asStateFlow()

    /** エラーメッセージ共有（書き込みは updateErrorState / clearError のみ） */
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    fun updateProcessingState(state: ProcessingState?) { _processingState.value = state }
    fun updateErrorState(msg: String?) { _errorState.value = msg }
    fun clearError() { _errorState.value = null }

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
