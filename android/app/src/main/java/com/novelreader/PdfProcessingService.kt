package com.novelreader

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.novelreader.viewmodel.BookImportError
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PdfProcessingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

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

        // CPUをスリープさせないWakeLock（OPPOのバックグラウンド強制停止対策）
        // 取得失敗時はログのみ出してWakeLockなしで継続（スリープ対策が効かなくなるだけで処理自体は継続）
        try {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovelReader::PdfProcessing")
                .also { it.acquire(10 * 60 * 1000L) } // 最大10分
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock取得に失敗（スリープ対策なしで継続）", e)
        }

        val app = application as NovelReaderApplication
        val repository = app.repository

        scope.launch {
            try {
                val result = repository.addBook(uri, onProgress = { step, stepLocalPercent, phase ->
                    val progress = (step * 25 + stepLocalPercent * 25).toInt().coerceIn(0, 100)
                    updateProgressNotification(progress, "ステップ ${step + 1}/4 - $phase")
                    app.updateProcessingState(ProcessingState(true, step, 4, stepLocalPercent, phase))
                })

                result.fold(
                    onSuccess = { book ->
                        showCompletionNotification(book.title)
                        app.updateProcessingState(null)
                    },
                    onFailure = { e ->
                        val msg = if (e is BookImportError) e.userMessage
                                  else e.message ?: "PDF処理に失敗しました"
                        showErrorNotification(msg)
                        app.updateErrorState(msg)
                        app.updateProcessingState(null)
                    },
                )
            } finally {
                wakeLock?.release()
                wakeLock = null
                isProcessing.set(false)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.release()
        wakeLock = null
        scope.cancel()
        // Service が突然終了した場合のフェイルセーフ：処理状態と排他フラグをリセット
        (application as? NovelReaderApplication)?.updateProcessingState(null)
        isProcessing.set(false)
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

    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("変換失敗")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.novelreader.action.START_PROCESSING"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "PdfProcessingService"
    }
}
