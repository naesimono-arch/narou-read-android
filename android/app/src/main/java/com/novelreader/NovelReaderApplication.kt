package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.novelreader.diagnostics.CrashReporter
import com.novelreader.diagnostics.DiagnosticsRecorder
import com.novelreader.diagnostics.DiagnosticsStore
import com.novelreader.diagnostics.JankTracker
import com.novelreader.diagnostics.SessionWatch
import com.novelreader.narou.DataStoreSearchHistoryStore
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.SearchHistoryStore
import com.novelreader.repository.BookRepository
import com.novelreader.repository.DefaultBookRepository
import com.novelreader.viewmodel.AppErrorEvent
import com.novelreader.viewmodel.ProcessingSource
import com.novelreader.viewmodel.ProcessingState
import com.novelreader.viewmodel.ProcessingStateHub
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NovelReaderApplication : Application(), androidx.work.Configuration.Provider {

    /** WorkManager の on-demand 初期化用設定（manifest で自動初期化を無効化済み）。
     *  なぜ on-demand か: 自動初期化（androidx.startup）は Robolectric で走らず、
     *  onCreate の WorkManager.getInstance が JVM UI テスト全滅を招くため（NewEpisodeCheckWorker 導入時に実測）。 */
    override val workManagerConfiguration: androidx.work.Configuration =
        androidx.work.Configuration.Builder().build()

    /** 書籍データアクセス層のシングルトン（Service/ViewModel 共用） */
    val repository: BookRepository by lazy { DefaultBookRepository(this) }

    /** プロセス生存期間のバックグラウンド作業用スコープ。Service の scope は onDestroy/onTimeout で
     *  cancel されるため、Service 破棄と無関係に完遂させたい書き込み（pending_jobs の記帳・全消し）と
     *  起動時リカバリはこちらで走らせる。Application はプロセスと同寿命なので cancel 不要。 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // pending_jobs 記帳の直列化は repository 層の Mutex（PendingJobStore.pendingJobMutex）へ移した。
    // 旧実装はここに Dispatchers.IO.limitedParallelism(1) を置いて投入順 FIFO を狙ったが、各リポジトリ
    // メソッドの withContext(Dispatchers.IO) 再ディスパッチ＋Room suspend DAO の内部再ディスパッチで
    // 単一スロットが DB 着地を1件も直列化できず（coroutine 意味論として不成立）、「追加直後に停止」で
    // 破棄済みジョブが復活する窓が残っていた。ロックを DAO 呼び出し完了まで保持する Mutex で恒久修正した
    // （詳細は PendingJobStore.pendingJobMutex の why 参照）。呼び出し側は素の applicationScope.launch でよい。

    /** なろうAPIを利用したディスカバリ用リポジトリのシングルトン（既存 repository と別系統） */
    val novelApiRepository: NovelApiRepository by lazy { NovelApiRepository() }

    /** 端末内診断（クラッシュ／異常終了）の採取係。外部送信は一切しない＝保管は filesDir 配下のみ。 */
    val diagnostics: DiagnosticsRecorder by lazy {
        DiagnosticsRecorder(this, DiagnosticsStore(java.io.File(filesDir, "diagnostics")))
    }

    /** 前面セッションの開閉監視（異常終了の推定）。設定は他と同じ app_prefs へ置く。 */
    val sessionWatch: SessionWatch by lazy {
        SessionWatch(getSharedPreferences(PrefKeys.FILE_APP_PREFS, MODE_PRIVATE), diagnostics)
    }

    /** 実利用のフレーム落ち計測（画面別）。window への接続は MainActivity が行う。 */
    val jankTracker: JankTracker by lazy { JankTracker() }

    /** 検索履歴＋ピン留め（発見機能 D1）のシングルトン。 */
    val searchHistoryStore: SearchHistoryStore by lazy { DataStoreSearchHistoryStore(this) }

    /** サービス↔ViewModel間の処理状態共有（書き込みは updateProcessingState のみ）。
     *  供給元別スロット（PDF=Service／WEB=BookshelfViewModel）を持つハブに委譲し、並走時の
     *  相互上書きを構造的に断つ（分離を選んだ理由＝ProcessingStateHub の KDoc）。 */
    private val processingStateHub = ProcessingStateHub()
    val processingState: StateFlow<ProcessingState?> get() = processingStateHub.displayState

    // エラーは一度きりのイベント。StateFlow だと構成変更（画面回転）で再表示され、
    // 複数購読時に重複する恐れがあるため、単一コンシューマ向けの Channel で配送する。
    // 受信時に消費され状態として残らないので clearError は不要。
    // 取込失敗は retryUri を伴い、UI が Snackbar に「再試行」を出せるようにする（M7）。
    private val _errorEvents = Channel<AppErrorEvent>(Channel.BUFFERED)
    val errorEvents: Flow<AppErrorEvent> = _errorEvents.receiveAsFlow()

    /** source 既定 PDF: 既存の PdfProcessingService 呼び出し（多数）を不変に保つための互換既定。
     *  Web 取込（BookshelfViewModel.importWebNovel）だけが明示的に WEB を渡す。 */
    fun updateProcessingState(state: ProcessingState?, source: ProcessingSource = ProcessingSource.PDF) {
        processingStateHub.update(source, state)
    }

    /** 指定供給元の生スロット値（表示合成でなく自スロットだけを読む口。用途＝ProcessingStateHub.stateOf）。 */
    fun processingStateOf(source: ProcessingSource): ProcessingState? = processingStateHub.stateOf(source)

    /** retryUri を渡すと UI 側で「再試行」アクション付き Snackbar になる（取込失敗時）。
     *  openUrl を渡すと「公式サイトで読む」アクション付きになる（破損監視・層2＝構造疑いの逃げ道）。
     *  transient=true は取込完了/取込済みのような一過性の情報通知＝UI 側で actionLabel を付けず
     *  Short で自動消滅させる目印（actionLabel 付き Snackbar は Material3 で duration 既定が Indefinite に
     *  なり画面へ残留するため。案d の残留バグ対処）。
     *  aggregationKey は同型メッセージの一括投入（複数PDF再取込→全件「取り込み済み」等）を UI 手前で
     *  「N件は取り込み済みです」へ集約するための同型印（AppErrorEvent.aggregationKey を参照）。
     *  復元系の情報通知・案内はいずれも既定（文言＋「閉じる」で残置＝挙動不変）。 */
    fun emitError(
        msg: String,
        retryUri: String? = null,
        openUrl: String? = null,
        transient: Boolean = false,
        aggregationKey: String? = null,
    ) {
        _errorEvents.trySend(AppErrorEvent(msg, retryUri, openUrl, transient, aggregationKey))
    }

    /** バッファ済みエラーイベントを今あるだけ吸い出す（無ければ空リスト・待たない）。
     *  用途は同型スナックバーの集約（BookshelfViewModel.errorEvents）だけ:
     *  Channel は単一コンシューマ前提のため、この吸い出しは errorEvents を collect している
     *  同じコルーチンの中からのみ呼ぶこと（並行して呼ぶとイベントの順序・取り合いが壊れる）。 */
    fun drainPendingErrorEvents(): List<AppErrorEvent> {
        val drained = mutableListOf<AppErrorEvent>()
        while (true) {
            drained += _errorEvents.tryReceive().getOrNull() ?: break
        }
        return drained
    }

    // 起動時リカバリの多重実行ガード。Activity 再作成のたびに呼ばれても実処理はプロセスごとに1回。
    private val recoveryStarted = AtomicBoolean(false)

    // アプリ全体が前面（少なくとも1つの Activity が STARTED）かどうか。
    // なぜフラグ方式か（ProcessLifecycleOwner.currentState を都度読まない理由）: 変換完了通知は IO
    // ディスパッチャの onProgress/fold コールバックから判定される（PdfProcessingService）。LifecycleRegistry
    // の状態読みは本来メインスレッド前提のため、メインで走る ProcessLifecycleOwner の observer が
    // @Volatile へ書き、Service 側はスレッド安全にこの値を読むだけにする。
    @Volatile
    var isAppInForeground = false
        private set

    /** アプリ起動時の復旧処理: ①孤立HTML掃除 ②強制終了（OEM kill/OOM/onTimeout）で残った
     *  未完了ジョブの検出 → 通知＋再開。
     *
     *  トリガーを MainActivity.onCreate に置く（Application.onCreate ではなく）理由:
     *  プロセス再生成は Service 起動経路等でも起こり、その文脈では FGS のバックグラウンド起動
     *  制限（Android 12+）で再開の startForegroundService が例外になるため、「前面にいることが
     *  ほぼ保証される」Activity 起動時に限定する。
     *  START_STICKY による Service 自動再起動を使わない理由: 再起動直後の startForeground が
     *  同制限で失敗しうる上、ユーザー不在のまま黙って重い変換が再走するより、アプリを開いた
     *  タイミングで snackbar＋バナー付きで可視的に再開する方が電池・挙動の予測可能性で優る。 */
    fun runStartupRecoveryOnce() {
        if (!recoveryStarted.compareAndSet(false, true)) return
        applicationScope.launch {
            // Service 稼働中（プロセス生存のまま Activity 再入）なら何もしない:
            // pending_jobs はいままさに処理中のキューの写しなので、再投入すると同じ本が
            // 二重変換される。掃除も処理中の本の出力ディレクトリを誤削除しうるため見送る
            // （書きかけが残っても次回起動時に拾われる）。
            if (processingState.value != null) return@launch
            repository.cleanOrphanHtmlDirs()
            // Web 読書位置履歴（web_reading_progress）の孤児掃除（UX監査 privacy Major）。
            // なぜここか: cleanOrphanHtmlDirs と同じ「起動時・Service 非稼働」の安全窓で、
            // 蔵書(books.ncode)にも本棚(web_novels)にも参照されない行だけを回収する。
            repository.pruneOrphanWebReadingProgress()
            // 取込時 cache PDF（pdf_import/）の孤児掃除。削除時カスケード（LibraryDeleter.deleteBook）の
            // 取りこぼし（カスケード導入前の残骸・削除途中の kill）を同じ安全窓で回収する。
            // pending_jobs 参照分の保護は repository 実装が担う（DefaultBookRepository のコメント）。
            repository.sweepOrphanNarouPdfCache()
            val pending = repository.getPendingJobs()
            // 再開にはプロセスを跨いで有効な読み取り権限が要る。takePersistableUriPermission は
            // addBook 時に取得済みのはずだが、プロバイダ非対応・ユーザーによる権限取消で
            // 失われていることがあるため、生きているものだけ再開する。
            val persisted = contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
            // partition・keepUris の導出は純関数へ集約（measure §E: 回復パスを JVM テストで固定するため）。
            val plan = StartupRecovery.computePlan(pending, persisted)
            // 失敗取込の権限リーク回収（恒久リーク対策・root cause）: 取込失敗時は M7 の再試行成立の
            // ため addBook が pending_jobs 行だけ消し永続 URI 権限を残すが、再試行 Snackbar はプロセス
            // 生存中しか出せないため、再試行されずに終わった分の権限が次回起動時に「pending_jobs 非紐付け」
            // として孤立し恒久リークする（端末上限128件へ）。ここで解放する。pending が空でも走らせる必要が
            // あるため、下の early return より前に置く（リークの典型形＝pending_jobs 行ゼロ＋孤児権限1件）。
            // keepPermissionUris（＝現在の pending URI 全体・空 pending なら空集合）に加え、取込元PDF削除機能で
            // books が保持する取込元 URI（sourceUri）も keep へ合流させる。これを足さないと、変換完了後も本の
            // 生存中ずっと保持すべき取込元権限を毎起動で誤解放し、その後の取込元PDF削除が権限失効で失敗する
            // （releaseOrphanedPermissions の KDoc「keepUris の構成」参照）。
            repository.releaseOrphanedPermissions(plan.keepPermissionUris + repository.getPersistedSourceUris())
            if (pending.isEmpty()) return@launch
            plan.lost.forEach { repository.removePendingJob(it.uri) }
            if (plan.lost.isNotEmpty()) {
                val names = plan.lost.joinToString("、") { "「${it.displayName.ifEmpty { "不明" }}」" }
                emitError("中断された $names の変換を再開できませんでした。もう一度ファイルを選択してください")
            }
            if (plan.resumable.isEmpty()) return@launch
            emitError("中断されていた変換 ${plan.resumable.size} 件を再開します")
            // getPendingJobs は enqueue 昇順を返すため、この順で再投入すれば元のキュー順が保たれる。
            // Service 側の ACTION_START が同じ URI を REPLACE で再記帳するので二重行にもならない。
            plan.resumable.forEach { job ->
                val intent = Intent(this@NovelReaderApplication, PdfProcessingService::class.java).apply {
                    action = PdfProcessingService.ACTION_START
                    data = Uri.parse(job.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ContextCompat.startForegroundService(this@NovelReaderApplication, intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 端末内診断をどの初期化よりも先に立ち上げる（外部送信ゼロ・記録先は filesDir/diagnostics）。
        // 最初に置く理由: 以降の初期化（PDFBox 資産ロード・WorkManager 等）で落ちた場合も記録を残すため。
        // クラッシュを記録したらセッションを閉じ、次回起動で「異常終了」として二重に数えない。
        CrashReporter.install(diagnostics) { sessionWatch.onCrashRecorded() }
        sessionWatch.onProcessStart()
        // PDFBox-Android のフォント/CMap 資産ローダを初期化する。ToUnicode CMap 非搭載の CID フォントを
        // グリフ→Unicode 解決するのに AAR 同梱資産を使うため、あらゆる PDDocument.load より前に一度だけ必要
        // （task_diary #31）。PdfProcessingService は MainActivity 無しでも走る（プロセス再生成・サービス起動
        // 経路）ため、全コンポーネントより先に必ず走る Application で先行初期化し、Service が最初の PDF を
        // 処理する前に init 済みを保証する。
        PDFBoxResourceLoader.init(applicationContext)
        createNotificationChannel()
        // アプリ前面/背面を追跡する（変換完了通知の二重報告抑止・公理13-D §86）。
        // Application.onCreate はメインスレッドのため observer 登録の前提を満たす。
        // 同じ前面/背面の signal を診断のセッション開閉にも使う（観測点を増やさない）。
        // 背面へ回った時点でセッションを閉じるのは、そこから先の OEM kill は Android の正常動作で
        // 異常として数えるべきでないため（数えると EMUI/ColorOS 端末で偽陽性だらけになる）。
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                sessionWatch.onForeground()
                jankTracker.setTrackingEnabled(true)
            }
            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                sessionWatch.onBackground()
                // 背面では計測しない（描画が無く電池を食うだけ）。区間の集計はここで吐き出して捨てる
                // ＝プロセスが背面で kill されても、そこまでの計測は残る。
                jankTracker.setTrackingEnabled(false)
                jankTracker.flushTo(diagnostics.store)
            }
        })
        // U1 新着チェックは既定 OFF のオプトイン（UX監査 C3・公理13）。ユーザーが明示 ON にしたときだけ
        // 定期実行を仕込む。OFF なら背景照会（日次 ncode 群の syosetu 送信）自体が走らない。
        if (NewEpisodeNotificationPreference.isEnabled(this)) {
            scheduleNewEpisodeCheck()
        }
    }

    /** 新着通知トグルの ON/OFF に応じて定期実行を仕込む/取り消す（状態層＝
     *  [NewEpisodeNotificationPreference]、実スケジュール切替＝ここ）。UI（トグル）から呼ぶ。 */
    fun setNewEpisodeNotificationEnabled(enabled: Boolean) {
        NewEpisodeNotificationPreference.setEnabled(this, enabled)
        if (enabled) scheduleNewEpisodeCheck() else cancelNewEpisodeCheck()
    }

    /** 定期チェックを取り消す（トグル OFF 時）。以降 Worker は起動されず背景照会も止まる。 */
    private fun cancelNewEpisodeCheck() {
        androidx.work.WorkManager.getInstance(this)
            .cancelUniqueWork(NewEpisodeCheckWorker.UNIQUE_WORK_NAME)
    }

    /** U1 新着話チェックの定期スケジュール（1日1回・ネットワーク接続時のみ）。
     *  KEEP: 起動のたびに周期をリセットしない（REPLACE だと毎起動で「次回実行が24h後」へ
     *  先送りされ続け、毎日開くユーザーほど一度も走らなくなる逆転が起きる）。 */
    private fun scheduleNewEpisodeCheck() {
        val request = androidx.work.PeriodicWorkRequestBuilder<NewEpisodeCheckWorker>(
            24, java.util.concurrent.TimeUnit.HOURS,
        )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NewEpisodeCheckWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PDF変換",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        // U1 新着話のお知らせは PDF変換と性格が違う「ユーザーに届けたい知らせ」だが、非時間性の更新
        // （1日遅れても実害なし）を音付きで割り込ませるのは公理13違反のため IMPORTANCE_LOW（無音・
        // ヘッドアップ無し）にする。オプトインで ON にしたユーザーにも push は静かに届ける。
        // 注意（Android の仕様）: チャネル作成後に importance を下げても既存インストールには反映されない
        // （ユーザーがチャネル設定で変えた値が優先される）。新規インストールにこの LOW が効く。
        val episodeChannel = NotificationChannel(
            NEW_EPISODE_CHANNEL_ID,
            "新着話のお知らせ",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(episodeChannel)
    }

    /** 変換完了通知を取り下げる（UX監査 §87 stale 通知）。deep link で該当の本へ着地したら
     *  「用が済んだ」ため呼ぶ。完了通知は tag=bookId＋COMPLETION_NOTIFICATION_ID で冊ごとに
     *  スタックするため、着地した本の通知だけを tag 指定で取り下げる（他の冊の完了通知は残す）。 */
    fun cancelCompletionNotification(bookId: String) {
        NotificationManagerCompat.from(this)
            .cancel(bookId, PdfProcessingService.COMPLETION_NOTIFICATION_ID)
    }

    /** 指定 ncode の新着話通知を取り下げる（UX監査 §87）。該当の本を開いたら呼ぶ。
     *  tag は Worker と同じ正規化規則で組む（大小/前後空白のズレで取り違えないため）。 */
    fun cancelNewEpisodeNotification(ncode: String) {
        NotificationManagerCompat.from(this).cancel(
            NewEpisodeCheckWorker.notificationTag(ncode),
            NewEpisodeCheckWorker.NEW_EPISODE_NOTIFICATION_ID,
        )
    }

    companion object {
        const val CHANNEL_ID = "pdf_processing_channel"
        const val NEW_EPISODE_CHANNEL_ID = "new_episode_channel"
    }
}
