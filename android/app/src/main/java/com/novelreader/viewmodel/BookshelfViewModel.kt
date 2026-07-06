package com.novelreader.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** PDF取込時のエラー種別。UI層でユーザー向けメッセージに変換する。 */
sealed class BookImportError(val userMessage: String) : Exception(userMessage) {
    class EncryptedPdf        : BookImportError("パスワード付きPDFは現在サポートしていません")
    class CorruptedPdf        : BookImportError("PDFファイルが破損しているか、読み取れません")
    class InsufficientStorage : BookImportError("ストレージの空き容量が不足しています")
    class UriPermissionDenied : BookImportError("ファイルへのアクセス権限がありません。もう一度ファイルを選択してください")
    class StorageWriteFailure : BookImportError("ファイルの書き込みに失敗しました")
    class Unknown(val detail: String?) : BookImportError("PDF処理に失敗しました")
}

data class ProcessingState(
    val isProcessing: Boolean = false,
    val stepIndex: Int = 0,
    val stepTotal: Int = 4,
    val stepLocalPercent: Float = 0f,
    val phase: String = "",
    // 変換中の本のタイトル（step0 で判明する実タイトル。判明前は表示名フォールバック）。
    val title: String = "",
    // キュー情報（通知と同じ「N件目/全M件」をアプリ内バナーにも出すため）
    val queueCurrent: Int = 1,
    val queueTotal: Int = 1,
    // 停止操作後、処理中の1冊が完了するまでの「停止しています…」状態。
    val isStopping: Boolean = false,
)

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelReaderApplication
    private val repository = app.repository

    val books: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progressMap: StateFlow<Map<String, String>> = repository.allProgress
        .map { list -> list.associate { it.bookId to it.lastReadFilename } }
        //                                                    ↑ mainのフィールド名（labの lastRead ではない）
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        // WhileSubscribed(5_000) に統一（Lazily はサブスクライバーゼロでもDBクエリが流れ続けるため）

    // Application の StateFlow を購読して processingState を提供
    val processingState: StateFlow<ProcessingState> = app.processingState
        .map { it ?: ProcessingState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessingState())

    // エラーは一度きりのイベント。Application の Channel を購読してそのまま UI へ流す。
    val errorEvents: Flow<String> = app.errorEvents

    // 進捗（章移動＋章内スクロール位置）の保存要求を単一チャネルに集約する。
    // なぜ1本に統合するか: 以前は章移動用とスクロール用で2本のチャネル＋2コルーチンに
    // 分かれていたが、両者は同じ progress 行を REPLACE で上書きするため、
    // 2チャネル跨ぎでは書き込み順序が保証されず（順序保証はチャネル内のみ）、
    // 章送り直後に旧章のスクロール書き込みが後着すると lastReadFilename が
    // 旧章へ巻き戻る競合があった。単一チャネルにすることで「最後に送られた操作＝
    // 最新のユーザー操作」が確実に最後に書き込まれる。
    // CONFLATED により中間値は捨てられ最新値のみが処理される（単一行の現在位置
    // 表現としてこの破棄は意味的に正しい）。
    private val progressChannel = Channel<ProgressEntity>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (p in progressChannel) {
                repository.saveScrollPosition(p.bookId, p.lastReadFilename, p.scrollIndex, p.scrollOffset)
            }
        }
    }

    fun addBook(uri: Uri) {
        // 強制終了からの再開（起動時リカバリの再投入）にはプロセスを跨いで有効な読み取り権限が
        // 必要なため、intent の FLAG_GRANT（一時権限＝プロセス消滅で失効）に加えて永続権限を取る。
        // picker は OpenDocument なので取得可能だが、プロバイダによっては SecurityException を
        // 投げるため防御する（取れなくても通常の変換は一時権限で成立し、再開だけが不可になる）。
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_START
            data = uri
            // content:// URI の読み取り権限を Service に委譲
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    // 変換の全体停止。キュー待ちを破棄し、処理中の1冊もページ境界で即中断する
    // （純 Kotlin 化で割り込みが可能になった）。Service へ STOP を送るだけ。
    fun cancelProcessing() {
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_STOP
        }
        // 既に前面で動作中の FGS への命令送信。新規起動が不要なため startService を使う。
        getApplication<Application>().startService(intent)
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) }
    }

    suspend fun getLastRead(bookId: String): String? = repository.getLastRead(bookId)

    suspend fun getProgress(bookId: String): ProgressEntity? = repository.getProgress(bookId)

    // 章移動時の保存。スクロール位置は default 0 のまま送ることで章先頭にリセットする。
    fun saveProgress(bookId: String, filename: String) {
        progressChannel.trySend(ProgressEntity(bookId, filename))
    }

    fun saveScrollPosition(bookId: String, filename: String, scrollIndex: Int, scrollOffset: Int) {
        progressChannel.trySend(ProgressEntity(bookId, filename, scrollIndex, scrollOffset))
    }
}
