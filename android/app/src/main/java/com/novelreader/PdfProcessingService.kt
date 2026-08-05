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
import com.novelreader.viewmodel.ProcessingSource
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

    // 進捗通知の間引き用スナップショット（lock で保護）。onProgress は高頻度で updateProgressNotification を
    // 呼ぶが、表示が変わらない再 notify はシステム/描画コストの無駄なので、前回と同一なら送らない
    // （UX監査 notify §2/§109）。progress/title/isStopping の3値で「表示が変わったか」を判定する。
    // -1 は「未送信」の番兵で、最初の1回は必ず送る。
    private var lastNotifiedProgress = -1
    private var lastNotifiedTitle = ""
    private var lastNotifiedStopping = false

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
                // enqueue の記帳との直列化は clearPendingJobs（実体は PendingJobStore）内の pendingJobMutex が担う（旧 pendingJobDispatcher は
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
                // 表示合成（processingState.value）でなく自スロット（PDF）を読む: Web 取込と並走中に
                // 他供給元の状態へ isStopping を刻む取り違えを構造的に断つ（供給元分離＝ProcessingStateHub）。
                (application as? NovelReaderApplication)?.let {
                    it.processingStateOf(ProcessingSource.PDF)
                        ?.let { st -> it.updateProcessingState(st.copy(isStopping = true)) }
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
        // 上書き確認済みの再投入（BookshelfViewModel.confirmOverwrite）のみ true。キュー項目ごとに運ぶ
        // ＝通常取込と上書き再投入が同一キューに混在しても互いの意味論を汚さない。
        val overwrite = intent.getBooleanExtra(EXTRA_OVERWRITE, false)

        // べき等ガード（UX監査 F-G 公理3）: 既にキュー待ち／処理中の同一 URI は変換前に弾く。
        // これで「同じ PDF を連続で追加」→ 蔵書2冊＋変換二重実行（旧 uriQueue.add の重複チェック無）を防ぐ。
        // OpenDocument の永続 URI は同一ファイルなら安定するため URI 一致で重複判定できる。URI が変わる
        // 再選択・別セッション再取込は変換後にタイトル＋著者で BookRepository がさらに弾く（多層防御）。
        // register/add とループ起動判定は uriQueue と同一 lock 下でアトミックに行う。
        val (isDuplicate, shouldStart) = lock.withLock {
            if (!activeUris.register(uri.toString())) {
                Pair(true, false)
            } else {
                uriQueue.add(QueuedUri(uri, ncode, overwrite))
                totalCount++
                isStopping = false  // 新規追加で停止状態を解除（同一インスタンス再利用時の取りこぼし防止）
                val start = if (!isLoopRunning) { isLoopRunning = true; true } else false
                // 新バッチ開始時は間引きスナップショットを未送信へ戻す（前バッチの完了通知が残ったまま
                // 偶然の一致で最初の進捗更新が握り潰されないように）。
                if (start) { lastNotifiedProgress = -1; lastNotifiedTitle = ""; lastNotifiedStopping = false }
                Pair(false, start)
            }
        }

        if (isDuplicate) {
            // 黙って捨てず「取込済み」をフィードバックする（UX監査要件）。前面はアプリ内 Snackbar・
            // 背面はトレイ通知（§86 の「前面は in-app 信号」原則を重複にも適用。従来は通知のみで、
            // 前面で操作しているユーザーには無言棄却に見えていた＝2026-07-14 人間テスト所見）。
            // 制約: Snackbar ホストは本棚ルートのみ＝発見側の取込画面から投入した場合は Channel が
            // buffer し、本棚へ戻った時点で表示される（重複の事実は時間が経っても有効なので許容）。
            // 表示名の解決は ContentProvider への query のためメインスレッドを避け IO で行う。
            (application as? NovelReaderApplication)?.let { app ->
                app.applicationScope.launch {
                    val name = resolveDisplayName(uri)
                    // aggregationKey: 複数PDF一括再取込で全件重複のとき、UI 手前で「N件は取り込み済みです」
                    // へ集約する同型印（2026-07-16 実機確定の連続再表示の対処＝aggregateErrorEvents）。
                    if (app.isAppInForeground) {
                        app.emitError(
                            duplicateMessage(name),
                            aggregationKey = com.novelreader.viewmodel.AppErrorEvent.KEY_DUPLICATE_IMPORT,
                        )
                    // 取込中の二重投入ガード＝上書き確認は積まれない（純粋な棄却）ためテレポート先が無い。
                    } else showDuplicateNotification(name, toOverwriteConfirm = false)
                }
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
            // 全消し(clearPendingJobs)との直列化は addPendingJob（実体は PendingJobStore）内の pendingJobMutex が担う（旧 pendingJobDispatcher 撤去）。
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
            // 内部理由（dataSync FGS の実行時間上限）は読み手に無関係で行動不能なため文言から落とす
            // （UX監査 errtext 08§B①）。ユーザーが取れる行動（開き直せば再開）だけを残す。
            it.emitError("変換が中断されました。アプリを開き直すと再開します。")
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
                            val bookJob = launch { processSingleUri(uri, item.ncode, item.overwrite, myGeneration) }
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
                    lock.withLock { totalCount = 0; doneCount = 0; isStopping = false }
                    // 進行中通知（NOTIFICATION_ID＝FGS 通知そのもの）は無条件で REMOVE する。
                    // 終端通知（完了/取込済み/失敗）は専用 ID で別途投稿済みのため道連れにならない。
                    // なぜ無条件か: 旧実装は終端通知を NOTIFICATION_ID へ上書き投稿し「上書き済みなら
                    // REMOVE をスキップすれば残る」前提だったが、FGS 通知は stopSelf() のサービス破棄で
                    // システムが ID ごと除去するためこの前提が成立せず、「変換が終わった瞬間に完了通知が
                    // 消える」実機事象を起こしていた（2026-07-14 OPPO 実測。残すには別 ID 投稿が必要）。
                    ServiceCompat.stopForeground(this@PdfProcessingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun processSingleUri(uri: Uri, ncode: String? = null, overwrite: Boolean = false, myGeneration: Int = loopGeneration) {
        val app = application as NovelReaderApplication
        val repository = app.repository
        // この本の位置（分子）は開始時点のスナップショットで固定する。
        // doneCount は完了時（finally）にしか増えないため、処理中は常にこの本の番号を指す。
        val currentNumber = lock.withLock { doneCount + 1 }
        // 抽出 step0（PdfBookExtractor の表紙メタ読み取り）で実タイトルが判明するまでの即時フォールバック表示名。
        val displayName = resolveDisplayName(uri)
        // 変換開始直後にバナー/通知へ表示名を出す（step0 のコールバックを待たない即時フィードバック）。
        app.updateProcessingState(
            ProcessingState(true, 0, 4, 0f, "準備中…", displayName, currentNumber, lock.withLock { totalCount })
        )

        try {
            // ncode（縦書きPDF取り込み ADR 0011 経由のみ非 null）を新規登録時の紐付けとして伝搬する。
            // overwrite は上書き確認済みの再投入のみ true（重複を弾かず既存行へ差し替える）。
            val result = repository.addBook(uri, ncode?.let { Ncode(it) }, overwrite, onProgress = { step, stepLocalPercent, phase, title ->
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
                        // 新規登録は前面/背面を問わず「変換完了」を通知する。タップで該当の本の
                        // 読書画面へ deep link するため bookId も渡す（M11）。
                        // なぜ §86（前面では出さない＝本棚出現が報告）を撤回したか（2026-07-14 ユーザー裁定）:
                        // ColorOS は「バックグラウンドアクティビティを許可」ON でも Hans が FGS を背面数秒で
                        // 凍結し、アプリ帰還時に解凍→「前面で完走」が常態＝背面限定の通知は事実上永久に出ない
                        // （docs/knowledge/ の ColorOS Hans 凍結知見を参照）。ユーザーは通知タップでの着手を
                        // 要望しており、本棚出現との二重報告は許容と裁定。
                        // restored=true（本文欠落からの再取込＝既存行を保持したまま本文だけ再生成）は
                        // 「追加」でなく「復元」と告げる。in-app 側（BookshelfViewModel の Web 取込）は
                        // 既に同じ出し分けを持っており、Service 経由（PDF 取込）だけが復元でも
                        // 「変換完了／追加しました」を出していた＝同じ出来事の呼び名が導線で食い違っていた。
                        is com.novelreader.repository.BookRepository.AddBookResult.Added ->
                            showCompletionNotification(outcome.book.id, outcome.book.title, outcome.restored, overwrite)
                        // 既に蔵書済み: 重複拒否の撤廃（2026-08-05 仕様）＝棄却通知で終わりにせず
                        // 「上書きしますか」の確認を UI へ依頼する（BookshelfViewModel が状態に保持し、
                        // 本棚ルートのダイアログで確認→「上書きする」で overwrite=true の再投入が戻ってくる）。
                        // 背面時は従来どおりトレイ通知でも「取込済み」を知らせる: ダイアログはアプリ内に
                        // しか出せず、通知が無いと背面取込の結末が無言になるため（通知から戻ると確認が待っている）。
                        is com.novelreader.repository.BookRepository.AddBookResult.Duplicate -> {
                            app.requestOverwritePrompt(
                                com.novelreader.viewmodel.OverwriteRequest.Pdf(
                                    uri.toString(), ncode, outcome.existing.title,
                                ),
                            )
                            // 上書き確認が積まれた直後の通知＝タップで確認ダイアログへ直接届かせる
                            // （二段経路〔帰還時確認〕の短絡・U1 残り 2026-08-06）。
                            if (!app.isAppInForeground) showDuplicateNotification(outcome.existing.title, toOverwriteConfirm = true)
                        }
                    }
                    app.updateProcessingState(null)
                },
                onFailure = { e ->
                    // 原文はログに残す（握り潰さず診断性を維持）。ユーザーには平易な日本語のみ出す（M8）。
                    Log.e(TAG, "PDF処理失敗", e)
                    val msg = normalizeImportErrorMessage(e)
                    showErrorNotification(msg)
                    // 決定的失敗（暗号化/破損）は同一 URI を再投入しても必ず同じ失敗を再走するだけで
                    // 直らないため「再試行」を出さない（retryUri=null＝『閉じる』のみ・UX監査 errtext 08§D）。
                    // 一過性の可能性がある容量不足・権限失効等は再試行を残す。retryUri 付与時、この URI は
                    // finally で activeUris から release されるため再投入は重複扱いにならない（M7）。
                    val retryUri = if (isDeterministicFailure(e)) null else uri.toString()
                    app.emitError(msg, retryUri)
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
     *  既に前面なら onNewIntent 経由で対象を差し替える。 */
    private fun openBookIntent(bookId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
            // 完了通知は tag=bookId でスタックするため、Intent を本ごとに filterEquals で区別させる。
            // data が無いと同一 requestCode の PendingIntent が共有され（extras は filterEquals の対象外）、
            // FLAG_UPDATE_CURRENT が全通知の bookId を最後の1冊へ上書き＝古い通知のタップも最新の本へ飛ぶ。
            data = Uri.parse("novelreader://book/$bookId")
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
    /** 再試行しても直らない決定的（持続性）失敗か。暗号化 PDF・破損 PDF は同一ファイルなら
     *  必ず同じ失敗を再走するため、無効な「再試行」アフォーダンスを出さない判定に使う（UX監査 errtext 08§D）。
     *  容量不足・権限失効は状況が変われば通り得るため決定的扱いにしない。 */
    private fun isDeterministicFailure(e: Throwable): Boolean =
        e is BookImportError.EncryptedPdf || e is BookImportError.CorruptedPdf

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
            // 更新のたびに音/バイブを鳴らさない（進捗更新は最初の1回だけ通知アラート・UX監査 notify §2）。
            // 本チャネルは IMPORTANCE_LOW で元々無音だが、通知の性格を明示し将来の重要度変更にも安全側へ倒す。
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
        // 停止中は連打防止のため「停止」アクションを出さない
        if (!isStopping) {
            builder.addAction(0, "停止", stopPendingIntent())
        }
        return builder.build()
    }

    private fun updateProgressNotification(progress: Int, text: String, current: Int = 1, total: Int = 1, title: String = "", isStopping: Boolean = false) {
        // 表示（progress/title/停止フラグ）が前回と同一なら再 notify しない（%不変の高頻度更新を間引く）。
        // lock で compare-and-set をアトミックにする（onProgress は IO、ACTION_STOP はメインから呼ぶため）。
        val changed = lock.withLock {
            val diff = progress != lastNotifiedProgress ||
                title != lastNotifiedTitle ||
                isStopping != lastNotifiedStopping
            if (diff) {
                lastNotifiedProgress = progress
                lastNotifiedTitle = title
                lastNotifiedStopping = isStopping
            }
            diff
        }
        if (!changed) return
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

    /** 変換完了通知。NOTIFICATION_ID（FGS 通知）とは必ず別 ID で投稿する: 同 ID だとサービス停止時に
     *  システムが FGS 通知ごと除去し「完了した瞬間に通知が消える」（2026-07-14 実機バグの真因）。
     *  tag=bookId で冊ごとに分離し、複数冊バッチでも完了通知が互いを上書きせずスタックする。
     *
     *  @param restored 本文欠落からの復元（[com.novelreader.repository.BookRepository.AddBookResult.Added.restored]）か。
     *   新規登録と復元は「本が使える状態になった」点で同義のため同じ通知・同じ deep link を使い、
     *   文言だけ出し分ける。復元側の本文は in-app Snackbar（BookshelfViewModel）の
     *   「〜を復元しました」と同一の言い回しに揃える＝同じ出来事を導線ごとに別の言葉で呼ばない。 */
    // restored/overwrite に既定値を付けない: 既定 false は新しい呼び出しが配線を忘れても無音で「変換完了」に
    // 化ける欠陥クラス（skins/ShelfFace.kt 冒頭の束の設計と同じ理由）＝全呼び出しで明示させる。
    // overwrite は「上書き確認からの再投入」印。同じ restored 経路でも出来事が違う（欠けた本文の復旧でなく
    // 生きた本文の置換）ため呼び名を分ける＝in-app 側（BookshelfViewModel の Web 取込）と同じ出し分け。
    // overwrite=true でも restored=false（確認〜再投入の間に本が削除され新規登録へ化けた稀ケース）は
    // 事実どおり「追加しました」と告げる。
    private fun showCompletionNotification(bookId: String, title: String, restored: Boolean, overwrite: Boolean) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle(
                when {
                    overwrite && restored -> "上書き完了"
                    restored -> "復元完了"
                    else -> "変換完了"
                },
            )
            .setContentText(
                when {
                    overwrite && restored -> "$title を上書きしました"
                    restored -> "$title を復元しました"
                    else -> "$title を追加しました"
                },
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            // タップで該当の本の読書画面へ deep link する（M11）。
            .setContentIntent(openBookIntent(bookId))
            .build()
        notificationManager().notify(bookId, COMPLETION_NOTIFICATION_ID, notification)
    }

    /** 重複フィードバックの共通文言（Snackbar とトレイ通知で言い回しを揃える）。 */
    private fun duplicateMessage(title: String) = "「$title」は既に取り込み済みです"

    /** 二重取込のフィードバック（UX監査 F-G・背面時のみ）。「変換完了」と誤解させないよう文面を分ける。
     *  FGS 通知（NOTIFICATION_ID）とは別 ID＝進行中の ongoing 通知を潰さず、サービス停止の
     *  道連れ除去（完了通知バグと同根）も受けない。
     *
     *  @param toOverwriteConfirm 上書き確認（OverwriteRequest）が積まれた重複か。true ならタップで
     *   確認ダイアログのホスト（本棚ページ）へ直接テレポートさせる（U1 残り 2026-08-06）。false は
     *   取込中の二重投入ガード＝確認が存在しないため従来どおり「アプリを開くだけ」。
     *   既定値を付けない: 既定 false は新しい呼び出しが配線を忘れても無音で旧挙動（ただ開くだけ）に
     *   化ける欠陥クラス（restored/overwrite と同じ理由）＝全呼び出しで明示させる。 */
    private fun showDuplicateNotification(title: String, toOverwriteConfirm: Boolean) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("取込済み")
            .setContentText(duplicateMessage(title))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(if (toOverwriteConfirm) overwriteConfirmIntent() else openAppIntent())
            .build()
        notificationManager().notify(DUPLICATE_NOTIFICATION_ID, notification)
    }

    /** 「取込済み（上書き確認待ち）」通知タップ用 PendingIntent。着地の機序と extras 設計の why は
     *  [OverwriteConfirmTeleport] に集約。requestCode=3 は openApp(0)/stop(1)/openBook(2) と分けて
     *  PendingIntent の取り違えを防ぐ。data 不要＝テレポート Intent は全件同一内容（旗のみ）で、
     *  むしろ共有される方が正しい（openBookIntent が data で分けるのは bookId が冊ごとに違うため）。 */
    private fun overwriteConfirmIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 3, OverwriteConfirmTeleport.launchIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, NovelReaderApplication.CHANNEL_ID)
            .setContentTitle("変換失敗")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        // FGS 通知と別 ID にしないとサービス停止時に道連れ除去される（完了通知バグと同根）。
        notificationManager().notify(ERROR_NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.novelreader.action.START_PROCESSING"
        const val ACTION_STOP = "com.novelreader.action.STOP_PROCESSING"
        // 縦書きPDF取り込み（ADR 0011）で、取り込む本に紐付ける ncode を Intent で運ぶ extra キー。
        const val EXTRA_NCODE = "com.novelreader.extra.NCODE"
        // 上書き確認済みの再投入（重複拒否の撤廃・2026-08-05）を示す extra キー。true のとき repository の
        // 重複判定を外し、既存行を保持したまま本文を差し替える（BookRepository.addBook の overwrite 契約）。
        const val EXTRA_OVERWRITE = "com.novelreader.extra.OVERWRITE"
        // 進行中（FGS）通知専用。終端通知（完了/取込済み/失敗）をこの ID で出してはならない:
        // FGS 通知になった通知はサービス停止時にシステムが除去するため、投稿した瞬間に消える
        // （2026-07-14 実機バグの真因）。新着話通知は 2001（NewEpisodeCheckWorker）。
        const val NOTIFICATION_ID = 1001
        // 二重取込の通知は進行中変換の ongoing 通知（NOTIFICATION_ID）を潰さないよう別 ID にする。
        const val DUPLICATE_NOTIFICATION_ID = 1002
        // 変換完了通知（tag=bookId と組で冊ごとにスタック。取り下げも同じ tag+ID で行う）。
        const val COMPLETION_NOTIFICATION_ID = 1003
        // 変換失敗通知（複数失敗は上書き＝最後の1件のみ表示。詳細は in-app Snackbar の再試行が担う）。
        const val ERROR_NOTIFICATION_ID = 1004
        private const val TAG = "PdfProcessingService"
    }
}

/**
 * 処理キューの1要素。取り込み対象 URI と、それに紐付ける ncode（縦書きPDF取り込み ADR 0011 経由のみ非 null）。
 * べき等ガード（activeUris）は URI キーのままにし、ncode は insert 時の付帯情報としてのみ運ぶ。
 */
// overwrite: 上書き確認済みの再投入（既存行へ差し替え）。既定を持たせない＝積む側で常に明示させる
// （restored と同じ「配線忘れが無音で通常取込に化ける」欠陥クラスの予防）。
private data class QueuedUri(val uri: Uri, val ncode: String?, val overwrite: Boolean)

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
