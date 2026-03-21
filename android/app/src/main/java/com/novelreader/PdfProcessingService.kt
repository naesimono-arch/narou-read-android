package com.novelreader

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.novelreader.repository.BookRepository
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PdfProcessingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isProcessing = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 多重起動ガード
        if (intent?.action != ACTION_START || !isProcessing.compareAndSet(false, true)) {
            return START_NOT_STICKY
        }

        val uri = intent.data ?: run {
            isProcessing.set(false)
            return START_NOT_STICKY
        }

        // API 34+ 対応: ServiceCompat で型を明示
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildProgressNotification(0, "準備中…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        val app = application as NovelReaderApplication
        val repository = BookRepository(applicationContext)

        scope.launch {
            val result = repository.addBook(uri, onProgress = { step, stepLocalPercent, phase ->
                val progress = (step * 25 + stepLocalPercent * 25).toInt().coerceIn(0, 100)
                updateProgressNotification(progress, "ステップ ${step + 1}/4 - $phase")
                app.processingState.value = ProcessingState(true, step, 4, stepLocalPercent, phase)
            })

            result.fold(
                onSuccess = { book ->
                    showCompletionNotification(book.title)
                    app.processingState.value = null
                },
                onFailure = { e ->
                    showErrorNotification()
                    app.errorState.value = e.message ?: "PDF処理に失敗しました"
                    app.processingState.value = null
                },
            )
            isProcessing.set(false)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    /** タップでアプリを開くPendingIntent（OPPO等のOEMは必須の場合がある） */
    private fun openAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun buildProgressNotification(progress: Int, text: String): Notification {
        return NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("小説を変換中...")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun updateProgressNotification(progress: Int, text: String) {
        notificationManager().notify(NOTIFICATION_ID, buildProgressNotification(progress, text))
    }

    private fun showCompletionNotification(title: String) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("変換完了")
            .setContentText("$title を追加しました")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification() {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("変換失敗")
            .setContentText("ファイルを確認してください")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.novelreader.action.START_PROCESSING"
        const val NOTIFICATION_ID = 1001
    }
}
