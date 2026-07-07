package com.novelreader

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.novelreader.viewmodel.BookImportError
import com.novelreader.viewmodel.ProcessingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PdfProcessingService : Service() {

    // var: onTimeout がキャンセル後に再生成して差し替えるため（理由は onTimeout 内コメント参照）。
    // アクセスは全て main スレッド（onStartCommand/onTimeout/onDestroy）なので同期は不要。
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // キューと状態を1つの lock で保護（「追加+起動判定」と「取り出し+終了判定」をアトミックにし競合ゼロにする）
    private val lock = ReentrantLock()
    private val uriQueue = ArrayDeque<Uri>()
    private var isLoopRunning = false   // lock で保護
    private var totalCount = 0          // 現バッチの総件数（通知用、lock で保護）
    private var doneCount = 0           // 現バッチの完了件数（通知用、lock で保護）
    private var isStopping = false      // 全体停止操作後の「停止しています…」状態（lock で保護）
    // 処理中の1冊を包む子 Job（lock で保護）。ACTION_STOP はこれを cancel することで、
    // 進捗コールバック経由の ensureActive()（BookRepository.addBook 内）に次のページ境界で
    // CancellationException を投げさせ、処理中の PDF を即中断する。
    private var currentBookJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 全体停止: キュー待ちを破棄し、処理中の1冊はページ境界で即中断する
        // （純 Kotlin 化で可能になった割り込み。旧 Chaquopy/JNI は割り込み不能で PDF 境界
        //   停止しかできなかった）。中断後はループ次周回が空キューを検知して正常終了→stopSelf する。
        // 実行中でなければ何も処理が無いので、自前で foreground を片付けて停止する。
        if (intent?.action == ACTION_STOP) {
            // ユーザーの明示停止＝「再開してほしくない」意思なので、永続キュー（pending_jobs）も
            // 全消しする（残すと次回起動のリカバリが破棄済みの変換を勝手に再開してしまう）。
            // Service の scope ではなく applicationScope で走らせる: この直後の stopSelf →
            // onDestroy の scope.cancel に巻き込まれると全消しが中断されるため。
            (application as? NovelReaderApplication)?.let { app ->
                // pendingJobDispatcher（並列度1）で enqueue の記帳と直列化する。素の IO だと
                // 「追加直後に停止」で insert が全消しの後に着地し、破棄済みジョブが復活する。
                app.applicationScope.launch(app.pendingJobDispatcher) { app.repository.clearPendingJobs() }
            }
            val running = lock.withLock {
                uriQueue.clear()
                isStopping = true
                // 処理中の1冊を即中断。cancel はフラグを立てるだけ（join しない）ので lock 内で安全。
                // 実際の中断は次のページ境界（onProgress → ensureActive）で起こる。
                currentBookJob?.cancel()
                isLoopRunning
            }
            if (running) {
                // 即時フィードバック: バナーと通知を「停止しています…」へ。
                // 通知バーは onProgress が高頻度で上書きするが、停止フラグはフィールド側に
                // 保持してあるので onProgress も isStopping を読んで巻き戻さない。
                (application as? NovelReaderApplication)?.let {
                    it.processingState.value?.let { st -> it.updateProcessingState(st.copy(isStopping = true)) }
                }
                updateProgressNotification(0, "", isStopping = true)
            } else {
                lock.withLock { isStopping = false; totalCount = 0; doneCount = 0 }
                (application as? NovelReaderApplication)?.updateProcessingState(null)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val uri = intent.data ?: return START_NOT_STICKY

        // 再開用に処理キューへ記帳する（OEM kill/OOM/onTimeout からの復元材料。削除は変換の
        // 成否確定時に BookRepository 側で、明示停止時は上の ACTION_STOP で行う）。
        // applicationScope で走らせるのは ACTION_STOP の全消しと同じ理由（scope.cancel 非依存）。
        // resolveDisplayName は ContentProvider への query のためメインスレッドの
        // onStartCommand では呼ばず、IO の launch 内で解決する。
        (application as? NovelReaderApplication)?.let { app ->
            app.applicationScope.launch(app.pendingJobDispatcher) {
                app.repository.addPendingJob(uri.toString(), resolveDisplayName(uri))
            }
        }

        // キューへの追加とループ起動判定をアトミックに行う
        val shouldStart = lock.withLock {
            uriQueue.add(uri)
            totalCount++
            isStopping = false  // 新規追加で停止状態を解除（同一インスタンス再利用時の取りこぼし防止）
            if (!isLoopRunning) { isLoopRunning = true; true } else false
        }

        if (shouldStart) {
            // API 34+ 対応: ServiceCompat で型を明示。
            // startForeground は Android 12+ のバックグラウンド起動制限により
            // ForegroundServiceStartNotAllowedException(IllegalStateException のサブクラス)を
            // 投げうる。現状はユーザー操作(前面)からの起動のみだが、将来 background 起動経路を
            // 追加したときにアプリをクラッシュさせないよう防御し、失敗時は積んだキューと
            // 状態をリセットして静かに終了する。
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildProgressNotification(0, "準備中…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } catch (e: IllegalStateException) {
                Log.e(TAG, "フォアグラウンド開始に失敗（処理を中止）", e)
                lock.withLock {
                    uriQueue.clear()
                    isLoopRunning = false
                    totalCount = 0
                    doneCount = 0
                    isStopping = false
                }
                (application as? NovelReaderApplication)?.updateProcessingState(null)
                stopSelf()
                return START_NOT_STICKY
            }
            startProcessingLoop()
        }

        return START_NOT_STICKY
    }

    // Android 14+ の dataSync 型 FGS は1日累計の実行時間上限(約6時間)に達すると
    // onTimeout が呼ばれる。放置するとシステムに強制終了され通知・状態が残るため、
    // 実行中ループをキャンセルし(各 PDF の finally で WakeLock 解放)、状態をリセットして
    // 明示的に停止する。ユーザーには中断を通知して再試行を促す。
    // 注: API 34 で追加されたコールバックのため、それ未満の端末では呼ばれない。
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "FGS タイムアウト(dataSync 実行時間上限)により処理を中断")
        scope.cancel()
        // なぜ cancel 直後に再生成するか: stopSelf() は非同期で、onDestroy 前に新しい
        // ACTION_START が同一 Service インスタンスへ届き得る。キャンセル済みスコープへの
        // launch は例外もログも出さず何も実行しない（コルーチンの仕様＝サイレント失敗）ため、
        // 再生成しないと次回起動が isLoopRunning=true・通知「準備中…」のまま永久に処理されない。
        // onDestroy 経由の cancel は再生成不要（インスタンスごと破棄され次回は新スコープ）。
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        lock.withLock {
            uriQueue.clear()
            isLoopRunning = false
            totalCount = 0
            doneCount = 0
            isStopping = false
        }
        // pending_jobs は意図的に残す: onTimeout はユーザー意思でない中断なので、記帳が残って
        // いれば次回アプリ起動時のリカバリが検出して再開できる（ACTION_STOP の全消しとは逆の扱い）。
        (application as? NovelReaderApplication)?.let {
            it.updateProcessingState(null)
            it.emitError("変換が時間制限により中断されました。アプリを開き直すと再開します。")
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startProcessingLoop() {
        scope.launch {
            var isNormalExit = false
            try {
                while (true) {
                    // キューからの取り出しと、空の場合の isLoopRunning リセットをアトミックに行う
                    val uri = lock.withLock {
                        if (uriQueue.isEmpty()) {
                            isLoopRunning = false
                            isNormalExit = true
                            null
                        }
                        else uriQueue.removeFirst()
                    } ?: break

                    // WakeLock は PDF 1件ごとに取得・解放する。
                    // なぜループ単位でなく PDF 単位か: キューに複数 PDF を積むと合計処理が
                    // 10分を超えうるが、ループ全体で1度だけ acquire(10分) すると途中で自動解放され、
                    // OPPO 等にバックグラウンド kill されて残りの PDF が孤立する。1件ごとに取り直すことで
                    // バッチ全体が長時間でも各処理中は確実に WakeLock を保持できる。
                    // ローカル変数で管理（フィールド共有だと新ループ起動時に旧ループが誤解放するため）。
                    // 取得失敗時はログのみ出して WakeLock なしで継続（スリープ対策が効かなくなるだけで処理は継続）。
                    val wl: PowerManager.WakeLock? = try {
                        (getSystemService(Context.POWER_SERVICE) as PowerManager)
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovelReader::PdfProcessing")
                            .also { it.acquire(10 * 60 * 1000L) } // 1件あたり最大10分
                    } catch (e: Exception) {
                        Log.e(TAG, "WakeLock取得に失敗（スリープ対策なしで継続）", e)
                        null
                    }
                    try {
                        // processSingleUri を子 Job として起動し currentBookJob へ登録する。
                        // なぜ子 Job か: ACTION_STOP はループ全体ではなく「処理中の1冊」だけを
                        // cancel したい。ループ Job ごと cancel すると、cancel〜finally の隙間に
                        // ACTION_START が来た場合（isLoopRunning がまだ true で新ループが起動されない）
                        // に積まれた URI を取りこぼすレースがあるため。子 Job 方式ならループ自体は
                        // 生き続け、次周回が空キュー（STOP が clear 済み）を検知して正常終了する。
                        coroutineScope {
                            val bookJob = launch { processSingleUri(uri) }
                            // 登録と停止済み再確認をアトミックに行う（launch 直後・登録前に
                            // ACTION_STOP が来た場合の cancel 取り逃しを防ぐ）。
                            lock.withLock {
                                currentBookJob = bookJob
                                if (isStopping) bookJob.cancel()
                            }
                        } // coroutineScope が子の完了を待つ。子のキャンセルは親に伝播しない
                    } finally {
                        lock.withLock { currentBookJob = null }
                        wl?.release()
                    }
                }
            } finally {
                // 異常終了時（クラッシュ等）のフェールセーフ
                val shouldStopSelf = lock.withLock {
                    if (!isNormalExit && isLoopRunning) {
                        isLoopRunning = false // 例外でループが破綻した場合は確実にフラグを下ろす
                        true // 自分以外にループがいない状態に戻したので停止する
                    } else {
                        // 正常に isEmpty() で終了した場合は、直後に新しいリクエストが来て起動していなければ停止
                        !isLoopRunning
                    }
                }
                if (shouldStopSelf) {
                    val wasStopping = lock.withLock {
                        val s = isStopping
                        totalCount = 0; doneCount = 0; isStopping = false
                        s
                    }
                    // 停止操作で終わった場合は「停止しています…」の ongoing 通知を確実に消す。
                    // 正常終了時は REMOVE しない: 完了/エラー通知（非 ongoing・NOTIFICATION_ID
                    // 上書き済み）をユーザーが後から確認できるよう残す既存挙動を維持するため。
                    if (wasStopping) {
                        ServiceCompat.stopForeground(this@PdfProcessingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    }
                    stopSelf()
                }
            }
        }
    }

    private suspend fun processSingleUri(uri: Uri) {
        val app = application as NovelReaderApplication
        val repository = app.repository
        // この本の位置（分子）は開始時点のスナップショットで固定する。
        // doneCount は完了時（finally）にしか増えないため、処理中は常にこの本の番号を指す。
        val currentNumber = lock.withLock { doneCount + 1 }
        // Python step0 で実タイトルが判明するまでの即時フォールバック表示名。
        val displayName = resolveDisplayName(uri)
        // 変換開始直後にバナー/通知へ表示名を出す（step0 のコールバックを待たない即時フィードバック）。
        app.updateProcessingState(
            ProcessingState(true, 0, 4, 0f, "準備中…", displayName, currentNumber, lock.withLock { totalCount })
        )

        try {
            val result = repository.addBook(uri, onProgress = { step, stepLocalPercent, phase, title ->
                val progress = (step * 25 + stepLocalPercent * 25).toInt().coerceIn(0, 100)
                // 分母（総件数）は毎回ライブ読みする。なぜスナップショットにしないか:
                // この本の処理中にキューへ追加された分（totalCount 増加）を即座に「n/m」へ
                // 反映するため。開始時固定だと2冊目を追加しても /m が1冊完了まで更新されない。
                // 分母と停止フラグを同一ロックで読む。停止フラグはフィールドから読むことで、
                // 停止タップ直後にこの高頻度コールバックが false へ巻き戻すのを防ぐ。
                val (liveTotal, stopping) = lock.withLock { Pair(totalCount, isStopping) }
                // 実タイトル未判明（step0 前）は表示名で代替する。
                val shownTitle = title.ifEmpty { displayName }
                updateProgressNotification(progress, "ステップ ${step + 1}/4 - $phase", currentNumber, liveTotal, shownTitle, stopping)
                app.updateProcessingState(
                    ProcessingState(true, step, 4, stepLocalPercent, phase, shownTitle, currentNumber, liveTotal, isStopping = stopping)
                )
            })

            result.fold(
                onSuccess = { book ->
                    showCompletionNotification(book.title)
                    app.updateProcessingState(null)
                },
                onFailure = { e ->
                    Log.e(TAG, "PDF処理失敗", e)
                    val msg = if (e is BookImportError) e.userMessage
                              else e.message ?: "PDF処理に失敗しました"
                    showErrorNotification(msg)
                    app.emitError(msg)
                    app.updateProcessingState(null)
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                // 停止操作（ACTION_STOP → currentBookJob.cancel）による中断。書きかけの出力
                // ディレクトリは addBook 側の catch が掃除済み。バナーはここで畳む
                // （fold に到達しないため onSuccess/onFailure の updateProcessingState(null) が走らない）。
                app.updateProcessingState(null)
                throw e // キャンセルはそのまま伝播させ子 Job を終了させる（ループ自体は継続する）
            }
            // 予期しない例外: ログして次の URI の処理に継続
            Log.e(TAG, "予期しないエラー（処理継続）", e)
            app.updateProcessingState(null)
        } finally {
            lock.withLock { doneCount++ }
        }
    }

    override fun onDestroy() {
        // WakeLock は startProcessingLoop のローカル変数で管理されるため、ここでの解放は不要
        scope.cancel()
        // Service が突然終了した場合のフェイルセーフ：処理状態をリセット
        (application as? NovelReaderApplication)?.updateProcessingState(null)
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

    private fun buildProgressNotification(progress: Int, text: String, current: Int = 1, total: Int = 1, title: String = "", isStopping: Boolean = false): Notification {
        // 複数件キューイングされている場合のみ件数を表示
        val queueInfo = if (total > 1) " ($current/$total)" else ""
        // タイトル判明時は「『タイトル』を変換中」、未判明時は従来の「小説を変換中」
        val subject = if (title.isNotEmpty()) "「$title」を" else "小説を"
        val builder = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle(if (isStopping) "停止しています…" else "${subject}変換中...$queueInfo")
            .setContentText(if (isStopping) "現在のページの処理を終えて停止します" else text)
            .setSmallIcon(R.drawable.ic_notification)
            // 停止中は残り時間が読めないため不確定バーにする
            .setProgress(100, progress, isStopping)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
        // 停止中は連打防止のため「停止」アクションを出さない
        if (!isStopping) {
            builder.addAction(0, "停止", stopPendingIntent())
        }
        return builder.build()
    }

    private fun updateProgressNotification(progress: Int, text: String, current: Int = 1, total: Int = 1, title: String = "", isStopping: Boolean = false) {
        notificationManager().notify(NOTIFICATION_ID, buildProgressNotification(progress, text, current, total, title, isStopping))
    }

    /** 通知の「停止」アクション用 PendingIntent（ACTION_STOP を自分自身へ送る）。
     *  openAppIntent() と requestCode を分けないと PendingIntent が共有され取り違える。 */
    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, PdfProcessingService::class.java).apply { action = ACTION_STOP }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** content:// URI から表示名（拡張子除去）を取得する。変換中タイトルの即時フォールバック用。
     *  URI 権限喪失や ContentProvider のクラッシュで query が例外を投げうるため runCatching で
     *  防御し、失敗時は lastPathSegment、それも無ければ「未知のファイル」を返す。 */
    private fun resolveDisplayName(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val raw = name ?: uri.lastPathSegment ?: "未知のファイル"
        return raw.removeSuffix(".pdf").removeSuffix(".PDF")
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
        const val ACTION_STOP = "com.novelreader.action.STOP_PROCESSING"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "PdfProcessingService"
    }
}
