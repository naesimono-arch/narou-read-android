package com.novelreader

import android.annotation.SuppressLint
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
import com.novelreader.narou.model.Ncode
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
    // キュー要素は URI に加え「紐付ける ncode（縦書きPDF取り込み ADR 0011 経由のみ非 null）」を持つ。
    // なぜ Uri 単独から拡張したか: 取り込み経路では DL 元作品の ncode を新規登録時に紐付けたいが、
    // Service は bookId を UI に返さないため insert 時に一緒に運ぶ必要がある（手動 UPDATE 経路しかない現状の穴埋め）。
    private val uriQueue = ArrayDeque<QueuedUri>()
    // 二重取込のべき等ガード（UX監査 F-G 公理3）: キュー待ち＋処理中（未完了）の URI 集合。
    // 同一 URI の再投入を変換前に弾く。lock で保護（uriQueue と同じ排他下で読み書きする）。
    private val activeUris = ActiveUriTracker()
    private var isLoopRunning = false   // lock で保護
    // ループ世代印（lock で保護）。onTimeout がスコープを差し替えるたびに +1 する。
    // なぜ必要か: onTimeout は旧ループを cancel（＝遅延死。次のキャンセル点まで finally は走らない）
    // しつつ scope を再生成するため、旧ループの finally より先に新 ACTION_START が新ループを
    // 起動しうる「唯一の」経路。この世代印が無いと、遅延死した旧ループの finally が新ループの
    // isLoopRunning を潰し無引数 stopSelf() でサービスごと止めてしまう（＝開始した取り込みが
    // 理由なく消える）。各ループは起動時の世代を閉じ込め、finally で現行世代と照合して
    // 「自分は旧世代か」を判定し、旧世代なら新世代の状態（isLoopRunning・stopSelf）に触れず退場する。
    private var loopGeneration = 0
    private var totalCount = 0          // 現バッチの総件数（通知用、lock で保護）
    private var doneCount = 0           // 現バッチの完了件数（通知用、lock で保護）
    private var isStopping = false      // 全体停止操作後の「停止しています…」状態（lock で保護）
    // 処理中の1冊を包む子 Job（lock で保護）。ACTION_STOP はこれを cancel することで、
    // 進捗コールバック経由の ensureActive()（BookRepository.addBook 内）に次のページ境界で
    // CancellationException を投げさせ、処理中の PDF を即中断する。
    private var currentBookJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // なぜ InlinedApi 抑制が安全か: FOREGROUND_SERVICE_TYPE_DATA_SYNC は API 29+ の定数だが
    // コンパイル時に int へインライン化され、ServiceCompat.startForeground は SDK<29 では
    // 2引数版 Service.startForeground(id, notification) へフォールバックして type 引数を
    // 参照しない（core-1.12.0 のバイトコードを javap で実証済み・2026-07-08）。
    // よって API 26-28 端末でもこの定数が評価される経路は存在しない。
    @SuppressLint("InlinedApi")
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
                // enqueue の記帳との直列化は clearPendingJobs 内の pendingJobMutex が担う（旧 pendingJobDispatcher は
                // Room の再ディスパッチで DB 着地を直列化できず「追加直後に停止」で破棄済みジョブが復活する窓があった）。
                app.applicationScope.launch { app.repository.clearPendingJobs() }
            }
            val running = lock.withLock {
                uriQueue.clear()
                activeUris.clear()  // べき等ガードの在庫も破棄（停止後の同一URI再投入は新規扱い）
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
        // 縦書きPDF取り込み（ADR 0011）から渡る紐付け対象 ncode。通常のファイル選択取り込みでは未指定＝null。
        val ncode = intent.getStringExtra(EXTRA_NCODE)

        // べき等ガード（UX監査 F-G 公理3）: 既にキュー待ち／処理中の同一 URI は変換前に弾く。
        // これで「同じ PDF を連続で追加」→ 蔵書2冊＋変換二重実行（旧 uriQueue.add の重複チェック無）を防ぐ。
        // OpenDocument の永続 URI は同一ファイルなら安定するため URI 一致で重複判定できる。URI が変わる
        // 再選択・別セッション再取込は変換後にタイトル＋著者で BookRepository がさらに弾く（多層防御）。
        // register/add とループ起動判定は uriQueue と同一 lock 下でアトミックに行う。
        val (isDuplicate, shouldStart) = lock.withLock {
            if (!activeUris.register(uri.toString())) {
                Pair(true, false)
            } else {
                uriQueue.add(QueuedUri(uri, ncode))
                totalCount++
                isStopping = false  // 新規追加で停止状態を解除（同一インスタンス再利用時の取りこぼし防止）
                val start = if (!isLoopRunning) { isLoopRunning = true; true } else false
                Pair(false, start)
            }
        }

        if (isDuplicate) {
            // 黙って捨てず「取込済み」を通知でフィードバックする（UX監査要件）。表示名の解決は
            // ContentProvider への query のためメインスレッドを避け IO で行い、進行中の変換の
            // ongoing 通知を潰さないよう専用 ID（DUPLICATE_NOTIFICATION_ID）で出す。
            (application as? NovelReaderApplication)?.applicationScope?.launch {
                showDuplicateNotification(resolveDisplayName(uri), DUPLICATE_NOTIFICATION_ID)
            }
            return START_NOT_STICKY
        }

        // 再開用に処理キューへ記帳する（OEM kill/OOM/onTimeout からの復元材料。削除は変換の
        // 成否確定時に BookRepository 側で、明示停止時は上の ACTION_STOP で行う）。
        // applicationScope で走らせるのは ACTION_STOP の全消しと同じ理由（scope.cancel 非依存）。
        // resolveDisplayName は ContentProvider への query のためメインスレッドの
        // onStartCommand では呼ばず、IO の launch 内で解決する。
        // 妥協（ADR 0011）: pending_jobs には ncode を記帳しない。記帳すると列追加＝Room migration が要るが、
        // 縦書きPDF取り込みの ncode 紐付けのためだけに DB スキーマを変える価値は薄い。帰結として
        // 「取り込み中に強制終了→次回起動リカバリで再開」した本は ncode 無しで登録される（稀なケース）。
        // その本は既存の手動紐付け（NcodeLinkSheet）で回復可能なため、この欠落は許容する。
        (application as? NovelReaderApplication)?.let { app ->
            // 全消し(clearPendingJobs)との直列化は addPendingJob 内の pendingJobMutex が担う（旧 pendingJobDispatcher 撤去）。
            app.applicationScope.launch {
                app.repository.addPendingJob(uri.toString(), resolveDisplayName(uri))
            }
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
                    activeUris.clear()  // 破棄するキューと在庫を揃える（次回投入を新規扱いに戻す）
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
            // スコープ差し替えに合わせて世代を進める。これ以降に起動する新ループは新世代を
            // 閉じ込め、遅延死する旧ループ（旧世代）の finally は下の照合で新世代の状態に触れない。
            // scope 再生成と loopGeneration++ を必ず対で行う（両者がズレると照合が破綻する）。
            loopGeneration++
            uriQueue.clear()
            activeUris.clear()  // タイムアウトで捨てるキューと在庫を揃える。pending_jobs は残すため
                                // 次回起動リカバリの再投入は新規 URI 扱いで正しく通る
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
        // このループの世代を起動時点の主スレッドで確定して閉じ込める。onStartCommand も onTimeout も
        // 主スレッドコールバックのため両者はインターリーブせず、起動と世代採番の間に onTimeout が
        // 割り込んで採番がズレるレースは無い（launch 本体は IO で後から走るが、世代は launch 前に確定）。
        val myGeneration = lock.withLock { loopGeneration }
        scope.launch {
            var isNormalExit = false
            try {
                while (true) {
                    // キューからの取り出しと、空の場合の isLoopRunning リセットをアトミックに行う
                    val item = lock.withLock {
                        if (uriQueue.isEmpty()) {
                            isLoopRunning = false
                            isNormalExit = true
                            null
                        }
                        else uriQueue.removeFirst()
                    } ?: break
                    val uri = item.uri

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
                            // myGeneration を渡す: このループが旧世代化（onTimeout でスコープ差し替え）した後に
                            // 遅延キャンセルされた1冊の finally が、新世代バッチの doneCount を汚さないようにするため。
                            val bookJob = launch { processSingleUri(uri, item.ncode, myGeneration) }
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
                    if (myGeneration != loopGeneration) {
                        // onTimeout でスコープごと差し替えられた旧世代ループ。onTimeout 自身が
                        // 状態リセットと stopSelf を済ませ、新世代の別ループが動いている可能性がある。
                        // 新世代の isLoopRunning / stopSelf を絶対に触らず即退場する（道連れ停止の防止）。
                        // ここに来るのは onTimeout 経路のみ（旧ループ生存と新ループ稼働が同時成立する唯一の経路）。
                        false
                    } else if (!isNormalExit && isLoopRunning) {
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

    private suspend fun processSingleUri(uri: Uri, ncode: String? = null, myGeneration: Int = loopGeneration) {
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
            // ncode（縦書きPDF取り込み ADR 0011 経由のみ非 null）を新規登録時の紐付けとして伝搬する。
            val result = repository.addBook(uri, ncode?.let { Ncode(it) }, onProgress = { step, stepLocalPercent, phase, title ->
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
                onSuccess = { outcome ->
                    when (outcome) {
                        // 新規登録は従来どおり「変換完了」を通知する。タップで該当の本の
                        // 読書画面へ deep link するため bookId も渡す（M11）。
                        is com.novelreader.repository.BookRepository.AddBookResult.Added ->
                            showCompletionNotification(outcome.book.id, outcome.book.title)
                        // 既に蔵書済み（べき等スキップ）は完了ではなく「取込済み」を通知する。
                        // この URI の処理スロットは終わったので進行中通知と同じ ID で上書きしてよい。
                        is com.novelreader.repository.BookRepository.AddBookResult.Duplicate ->
                            showDuplicateNotification(outcome.existing.title, NOTIFICATION_ID)
                    }
                    app.updateProcessingState(null)
                },
                onFailure = { e ->
                    // 原文はログに残す（握り潰さず診断性を維持）。ユーザーには平易な日本語のみ出す（M8）。
                    Log.e(TAG, "PDF処理失敗", e)
                    val msg = normalizeImportErrorMessage(e)
                    showErrorNotification(msg)
                    // retryUri を添えて Snackbar に「再試行」を出す（M7）。この URI は finally で
                    // activeUris から release されるため、再投入は重複扱いにならない。
                    app.emitError(msg, uri.toString())
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
            // この URI の処理が完了（成功/重複/失敗/キャンセル）したのでべき等ガードの在庫から外し、
            // 完了件数を1つ進める。これ以降に同一 URI が再投入されたら新規変換として受け付ける（＝キュー
            // 重複ガードはあくまで「取込中の二重投入」を防ぐもので、完了後の再取込は蔵書照合側に委ねる）。
            // 世代照合（f70b937 のループ世代ガードの横展開）: onTimeout はこの1冊を遅延キャンセル（次の
            // 中断点まで finally は走らない）しつつ doneCount=0 リセット＋新バッチを起動しうる。旧世代の
            // finally が新世代の doneCount を進めると新バッチ初冊の通知が「2/1」等と一時的に汚れ、
            // activeUris からも新世代が再登録した同一 URI を誤って外しかねない。旧世代なら新世代の
            // 状態（doneCount・activeUris）に一切触れず退場する。通常経路（タイムアウトなし）は世代が
            // 進まないため従来と完全一致。
            lock.withLock {
                if (myGeneration == loopGeneration) {
                    doneCount++
                    activeUris.release(uri.toString())
                }
            }
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

    /** 変換完了通知のタップ着地を決定化する（M11）。従来の openAppIntent は「アプリを開く」だけで
     *  着地先が直前の画面状態に依存して非決定だった。ここでは MainActivity へ bookId を明示 Intent で
     *  渡し、該当の本の読書画面へ deep link させる（MainActivity 側が疑似バックスタックで本棚起点を保証）。
     *  requestCode を openApp(0)/stop(1) と分けて PendingIntent の取り違えを防ぐ。
     *  FLAG_ACTIVITY_SINGLE_TOP|CLEAR_TOP＋launchMode=singleTop で新規インスタンスを積まず多重起動を避け、
     *  既に前面なら onNewIntent 経由で対象を差し替える。FLAG_UPDATE_CURRENT で最新完了の bookId を反映する
     *  （完了通知は NOTIFICATION_ID 単一で上書きされるため同時併存はしない）。 */
    private fun openBookIntent(bookId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this, 2, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 取込失敗を平易な日本語へ正規化する（M8）。生の例外メッセージ（"No such file…" 等）を
     *  ユーザーに晒さないための emit 境界。原文は呼び出し側で Log 済み（診断性維持・握り潰さない）。
     *  addBook の失敗は BookRepository.classifyError が BookImportError へ分類済みのため通常は
     *  userMessage を返す。想定外の非分類例外に備え、既知の生メッセージも防御的にマップして raw を漏らさない。 */
    private fun normalizeImportErrorMessage(e: Throwable): String {
        if (e is BookImportError) return e.userMessage
        val raw = e.message.orEmpty()
        return when {
            raw.contains("No space left", ignoreCase = true) ->
                "ストレージの空き容量が不足しています"
            raw.contains("No such file", ignoreCase = true) || raw.contains("ENOENT", ignoreCase = true) ->
                "ファイルが見つかりません。もう一度ファイルを選択してください"
            raw.contains("Permission denied", ignoreCase = true) || raw.contains("EACCES", ignoreCase = true) ->
                "ファイルへのアクセス権限がありません。もう一度ファイルを選択してください"
            else -> "PDF処理に失敗しました"
        }
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

    private fun showCompletionNotification(bookId: String, title: String) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("変換完了")
            .setContentText("$title を追加しました")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            // タップで該当の本の読書画面へ deep link する（M11）。
            .setContentIntent(openBookIntent(bookId))
            .build()
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    /** 二重取込のフィードバック（UX監査 F-G）。「変換完了」と誤解させないよう文面を分ける。
     *  通知 ID は呼び出し側が指定する: 進行中変換中の重複投入は専用 ID で ongoing 通知を潰さず、
     *  処理スロット完了時（蔵書照合ヒット）は進行中通知の ID を上書きする。 */
    private fun showDuplicateNotification(title: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("取込済み")
            .setContentText("「$title」は既に取り込み済みです")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager().notify(notificationId, notification)
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
        // 縦書きPDF取り込み（ADR 0011）で、取り込む本に紐付ける ncode を Intent で運ぶ extra キー。
        const val EXTRA_NCODE = "com.novelreader.extra.NCODE"
        const val NOTIFICATION_ID = 1001
        // 二重取込の通知は進行中変換の ongoing 通知（NOTIFICATION_ID）を潰さないよう別 ID にする。
        const val DUPLICATE_NOTIFICATION_ID = 1002
        private const val TAG = "PdfProcessingService"
    }
}

/**
 * 処理キューの1要素。取り込み対象 URI と、それに紐付ける ncode（縦書きPDF取り込み ADR 0011 経由のみ非 null）。
 * べき等ガード（activeUris）は URI キーのままにし、ncode は insert 時の付帯情報としてのみ運ぶ。
 */
private data class QueuedUri(val uri: Uri, val ncode: String?)

/**
 * 取込中（キュー待ち＋変換中）の URI 集合を管理する純ロジック。二重取込のべき等ガード
 * （UX監査 F-G 公理3）の中核で、Android 依存を持たず単体テスト可能にするため Service から分離する。
 * スレッド安全性は持たない（呼び出し側 PdfProcessingService が既存の lock 下で使う前提）。
 */
internal class ActiveUriTracker {
    private val active = HashSet<String>()

    /** 新規なら登録して true を返す。既に取込中なら false（＝重複でスキップすべき）。 */
    fun register(uri: String): Boolean = active.add(uri)

    /** 処理完了で在庫から外す（以降の同一 URI 再投入は新規扱いになる）。 */
    fun release(uri: String) { active.remove(uri) }

    /** 全消し（停止・タイムアウト・foreground 起動失敗でキューごと破棄するとき）。 */
    fun clear() { active.clear() }
}
