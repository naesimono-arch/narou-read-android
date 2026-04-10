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

    val errorMessage: StateFlow<String?> = app.errorState

    // なぜ Channel.CONFLATED を使うか:
    // viewModelScope.launch ごとに独立コルーチンを発火すると、章ナビ連打時に
    // IO dispatcher のスレッドプール上で書き込み順序が逆転し、最終意図と
    // 異なるファイル名が保存される可能性がある。Channel で FIFO キューに変換し
    // 単一コルーチンで逐次処理することで順序を保証する。
    // CONFLATED により中間値は捨てられ最新値のみが処理される。
    private val progressChannel = Channel<Pair<String, String>>(Channel.CONFLATED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for ((bookId, filename) in progressChannel) {
                repository.saveProgress(bookId, filename)
            }
        }
    }

    fun addBook(uri: Uri) {
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_START
            data = uri
            // content:// URI の読み取り権限を Service に委譲
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) }
    }

    suspend fun getLastRead(bookId: String): String? = repository.getLastRead(bookId)

    fun saveProgress(bookId: String, filename: String) {
        progressChannel.trySend(bookId to filename)
    }

    fun clearError() { app.clearError() }
}
