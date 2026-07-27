package com.novelreader

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novelreader.data.AppDatabase
import com.novelreader.data.NewEpisodeMarkEntity
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NewEpisodeAlert
import com.novelreader.narou.computeNewEpisodeAlerts
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.flow.first

/**
 * U1: 紐付け済み蔵書（books.ncode 非 null）の新着話を1日1回チェックし、増分をローカル通知する Worker。
 * スケジュールは [NovelReaderApplication.onCreate]（PeriodicWork 24h・ネットワーク制約・KEEP）。
 *
 * なぜ FGS でなく WorkManager か: PDF 変換（PdfProcessingService）は「ユーザーが今まさに待つ重処理」
 * だから前景サービスだが、新着チェックは1リクエストのバルク照会だけの軽処理で、実行時刻の正確さも
 * 要らない。OS のバッチング（Doze 対応）に乗る WorkManager が電池・行儀の両面で適する。
 */
class NewEpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovelReaderApplication
        val dao = AppDatabase.getDatabase(applicationContext).newEpisodeMarkDao()

        val books = app.repository.allBooks.first()
        // 正規化済み ncode → (bookId, 蔵書タイトル)。通知の文言・着地先はローカルの本に揃える。
        val linkedBooks = books.mapNotNull { book ->
            book.ncode?.let { Ncode(it).storageKey }?.takeIf { it.isNotEmpty() }
                ?.let { it to (book.id to book.title) }
        }.toMap()

        if (linkedBooks.isEmpty()) {
            // 紐付けゼロなら基準値も全掃除する。空リストの NOT IN は SQL が不正になるため、
            // ncode として決して現れない空文字を番兵に渡す（＝実質全削除）。
            dao.pruneExcept(listOf(""))
            return Result.success()
        }

        val marks = dao.getAll().associate { it.ncode to it.lastNotifiedAllNo }
        val currents = try {
            app.novelApiRepository.novelDetailsBulk(linkedBooks.keys.map { Ncode(it) })
        } catch (e: NarouApiException) {
            // オフライン等はこの周期を静かにスキップし翌日に任せる。Result.retry() にしない理由:
            // 指数バックオフでも失敗が続く限り再試行が繰り返され、1日1回というレート自制の建付けが
            // 崩れるため（新着通知は1日遅れても実害がない）。
            Log.w(TAG, "新着チェックをスキップ: ${e.userMessage}")
            return Result.success()
        }

        val (alerts, newMarks) = computeNewEpisodeAlerts(linkedBooks, marks, currents)
        alerts.forEach { showNotification(it) }

        // 基準値は通知の成否（権限の有無）に関わらず前進させる。権限が無い間の増分を溜め込むと、
        // 後日許可された瞬間に古い更新まで雪崩のように通知される方が体験が悪いため。
        val now = System.currentTimeMillis()
        dao.upsertAll(newMarks.map { (ncode, allNo) -> NewEpisodeMarkEntity(ncode, allNo, now) })
        // 紐付け解除・削除済みの本の基準値を掃除（放置すると再紐付け時に古い基準で誤診する）。
        dao.pruneExcept(linkedBooks.keys.toList())
        return Result.success()
    }

    private fun showNotification(alert: NewEpisodeAlert) {
        // Android 13+ は POST_NOTIFICATIONS が無いと notify が SecurityException になり得るため先に弾く
        // （権限フローは本棚の取込導線に既存＝ここでは静かに諦めるだけでよい）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        // タップで該当の本の読書画面へ（最終章に継続カード＝なろうへの導線がある）。
        // ContentIntent はOPPO等のOEMで必須（無いと通知自体が表示されない＝task_diary #2）。
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_BOOK_ID, alert.bookId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // requestCode を ncode ハッシュで分ける: 複数の本の通知が同じ PendingIntent に
        // 上書き合流して全通知が同じ本へ飛ぶ取り違えを防ぐ（FLAG_UPDATE_CURRENT の既知の罠）。
        val pending = PendingIntent.getActivity(
            applicationContext, alert.ncode.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, NovelReaderApplication.NEW_EPISODE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.bookTitle)
            .setContentText("続きが ${alert.newCount} 話更新されています（全${alert.totalAllNo}話）")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // tag=ncode × 固定 id: 同じ本の通知は上書き・別の本は並ぶ。既存の PDF変換系 ID（1001/1002）
        // と数値衝突しない管理にするため id でなく tag で識別する。
        NotificationManagerCompat.from(applicationContext)
            .notify(notificationTag(alert.ncode), NEW_EPISODE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "NewEpisodeCheckWorker"
        const val UNIQUE_WORK_NAME = "new_episode_check"
        const val NEW_EPISODE_NOTIFICATION_ID = 2001

        /** 新着話通知の tag を組む単一の正本。通知の発行（showNotification）と取り下げ
         *  （NovelReaderApplication.cancelNewEpisodeNotification）で必ず同じ文字列にするため関数化する。
         *  ncode は linkedBooks のキー（正規化済み）だが、取り下げ側は book.ncode を直接渡しうるため
         *  ここでも Ncode.storageKey（trim+大文字）を掛けてズレを吸収する。 */
        fun notificationTag(ncode: String): String = "new_episode_${Ncode(ncode).storageKey}"
    }
}
