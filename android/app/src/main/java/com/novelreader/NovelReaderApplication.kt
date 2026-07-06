package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.novelreader.repository.BookRepository
import com.novelreader.viewmodel.ProcessingState
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NovelReaderApplication : Application() {

    /** 書籍データアクセス層のシングルトン（Service/ViewModel 共用） */
    val repository: BookRepository by lazy { BookRepository(this) }

    /** プロセス生存期間のバックグラウンド作業用スコープ。Service の scope は onDestroy/onTimeout で
     *  cancel されるため、Service 破棄と無関係に完遂させたい書き込み（pending_jobs の記帳・全消し）と
     *  起動時リカバリはこちらで走らせる。Application はプロセスと同寿命なので cancel 不要。 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** pending_jobs 記帳の直列化用ディスパッチャ（並列度1＝投入順 FIFO）。
     *  enqueue の記帳（insert）と明示停止の全消し（deleteAll）を素の IO プールで並行させると、
     *  「PDF 追加直後に停止」の操作列で insert が全消しの後に着地し、破棄済みジョブの記帳が
     *  残って次回起動のリカバリが勝手に再開してしまう。onStartCommand（メインスレッドで直列）
     *  から本ディスパッチャへ投入することで、命令の到着順どおりに記帳を直列実行させる。 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingJobDispatcher = Dispatchers.IO.limitedParallelism(1)

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

    // 起動時リカバリの多重実行ガード。Activity 再作成のたびに呼ばれても実処理はプロセスごとに1回。
    private val recoveryStarted = AtomicBoolean(false)

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
            val pending = repository.getPendingJobs()
            if (pending.isEmpty()) return@launch
            // 再開にはプロセスを跨いで有効な読み取り権限が要る。takePersistableUriPermission は
            // addBook 時に取得済みのはずだが、プロバイダ非対応・ユーザーによる権限取消で
            // 失われていることがあるため、生きているものだけ再開する。
            val persisted = contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
                .toSet()
            val (resumable, lost) = pending.partition { it.uri in persisted }
            lost.forEach { repository.removePendingJob(it.uri) }
            if (lost.isNotEmpty()) {
                val names = lost.joinToString("、") { "「${it.displayName.ifEmpty { "不明" }}」" }
                emitError("中断された $names の変換を再開できませんでした。もう一度ファイルを選択してください")
            }
            if (resumable.isEmpty()) return@launch
            emitError("中断されていた変換 ${resumable.size} 件を再開します")
            // getPendingJobs は enqueue 昇順を返すため、この順で再投入すれば元のキュー順が保たれる。
            // Service 側の ACTION_START が同じ URI を REPLACE で再記帳するので二重行にもならない。
            resumable.forEach { job ->
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
        // PDFBox-Android のフォント/CMap 資産ローダを初期化する。ToUnicode CMap 非搭載の CID フォントを
        // グリフ→Unicode 解決するのに AAR 同梱資産を使うため、あらゆる PDDocument.load より前に一度だけ必要
        // （task_diary #31）。PdfProcessingService は MainActivity 無しでも走る（プロセス再生成・サービス起動
        // 経路）ため、全コンポーネントより先に必ず走る Application で先行初期化し、Service が最初の PDF を
        // 処理する前に init 済みを保証する。
        PDFBoxResourceLoader.init(applicationContext)
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
