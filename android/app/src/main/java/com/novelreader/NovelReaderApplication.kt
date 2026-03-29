package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.novelreader.repository.BookRepository
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.flow.MutableStateFlow

class NovelReaderApplication : Application() {

    /** 書籍データアクセス層のシングルトン（Service/ViewModel 共用） */
    val repository: BookRepository by lazy { BookRepository(this) }

    /** サービス↔ViewModel間の処理状態共有 */
    val processingState = MutableStateFlow<ProcessingState?>(null)

    /** エラーメッセージ共有 */
    val errorState = MutableStateFlow<String?>(null)

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
