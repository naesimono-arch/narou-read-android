package com.novelreader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.viewmodel.BookImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao = AppDatabase.getDatabase(context).bookDao(),
    private val progressDao: ProgressDao = AppDatabase.getDatabase(context).progressDao(),
) {

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allProgress: Flow<List<ProgressEntity>> = progressDao.getAllProgress()

    fun interface ProgressCallback {
        // title: step0 で判明する実タイトル（判明前は空文字）。UI の変換中タイトル表示に使う。
        fun onProgress(step: Int, stepLocalPercent: Float, phase: String, title: String)
    }

    /** Python/Kotlin の例外をユーザー向けエラー種別に変換する */
    internal fun classifyError(e: Throwable): Throwable {
        if (e is PyException) {
            val msg = e.message ?: ""
            return when {
                msg.contains("EncryptedPdfError")        -> BookImportError.EncryptedPdf()
                msg.contains("InsufficientStorageError") -> BookImportError.InsufficientStorage()
                msg.contains("CorruptedPdfError")        -> BookImportError.CorruptedPdf()
                else                                     -> BookImportError.Unknown(msg)
            }
        }
        val msg = e.message ?: ""
        return when {
            msg.contains("PDFファイルを開けません")      -> BookImportError.UriPermissionDenied()
            msg.contains("出力ディレクトリの作成に失敗")  -> BookImportError.StorageWriteFailure()
            msg.contains("No space left on device")     -> BookImportError.InsufficientStorage()
            else                                        -> BookImportError.Unknown(msg)
        }
    }

    /** PDFをキャッシュにコピーし、Chaquopy経由でHTML生成後にRoomへ登録する。 */
    suspend fun addBook(
        pdfUri: Uri,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit = { _, _, _, _ -> },
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val bookId = UUID.randomUUID().toString().take(8)

            // ① 一時ファイルにコピー（try-finally で確実に削除する）
            val tempFile = File(context.cacheDir, "temp_$bookId.pdf")
            try {
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: throw IOException("PDFファイルを開けません（URI権限が失われた可能性があります）")
                inputStream.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                // ② 出力先ディレクトリを確定
                val outputDir = File(context.filesDir, "novels/$bookId")
                if (!outputDir.mkdirs() && !outputDir.exists()) {
                    throw IOException("出力ディレクトリの作成に失敗しました: ${outputDir.absolutePath}")
                }

                // ③ Chaquopy経由で Python 処理 + ④ Room登録を NonCancellable で一体化
                // Python は JNI ブロッキング呼び出しのためキャンセル不能。
                // HTML生成後・DB登録前にキャンセルされると孤立ファイルが残るため両者を同一ブロックに含める。
                val book = withContext(NonCancellable) {
                    val python = Python.getInstance()
                    val result = python.getModule("app")
                        .callAttr(
                            "process_pdf",
                            tempFile.absolutePath,
                            bookId,
                            outputDir.absolutePath,
                            ProgressCallback { step, stepLocalPercent, phase, title -> onProgress(step, stepLocalPercent, phase, title) },
                        )
                    val resultList = result.asList()
                    val title = resultList.getOrNull(0)?.toString() ?: "無題"
                    val author = resultList.getOrNull(1)?.toString() ?: ""
                    // addedAt に追加時刻をスタンプし、本棚の最近活動順ソート（未読本の基準）に使う。
                    val b = BookEntity(bookId, title, outputDir.absolutePath, author, addedAt = System.currentTimeMillis())
                    bookDao.insertBook(b)
                    b
                }
                // NonCancellable ブロック完了後にキャンセルを確認
                // （NonCancellable 内では ensureActive() が機能しないため必ず外側で呼ぶ）
                currentCoroutineContext().ensureActive()
                book
            } finally {
                if (!tempFile.delete()) Log.w(TAG, "一時ファイルの削除に失敗: ${tempFile.absolutePath}")
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                // コルーチンのキャンセルはエラーに変換せず素通しする。
                // runCatching は CancellationException も捕捉するため、ここで rethrow しないと
                // ensureActive() 等が投げたキャンセルが classifyError() で Unknown エラーに化け、
                // 呼び出し側で不要なエラー通知が出てキャンセルの静かな伝播が壊れる。
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "addBook 失敗", e)
                Result.failure(classifyError(e))
            },
        )
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        bookDao.deleteById(book.id)
        progressDao.deleteByBookId(book.id)
        if (!File(book.htmlDirPath).deleteRecursively()) {
            Log.w(TAG, "HTMLディレクトリの削除に失敗: ${book.htmlDirPath}")
        }
    }

    suspend fun getLastRead(bookId: String): String? =
        withContext(Dispatchers.IO) { progressDao.getLastRead(bookId) }

    suspend fun getProgress(bookId: String): ProgressEntity? =
        withContext(Dispatchers.IO) { progressDao.getProgress(bookId) }

    // 章を切り替えたときの進捗保存。スクロール位置は 0 にリセットする
    // （別の章へ移ったので前章のスクロール位置は引き継がない）。
    // lastReadAt を書き込み時刻でスタンプし、本棚の最近読書順ソートに使う。
    suspend fun saveProgress(bookId: String, filename: String) = withContext(Dispatchers.IO) {
        progressDao.saveProgress(ProgressEntity(bookId, filename, lastReadAt = System.currentTimeMillis()))
    }

    // 章内スクロール位置の保存。lastReadFilename も一緒に書き込むことで
    // 「どの章のどの位置か」を1行で表現する（REPLACE で上書き）。
    // lastReadAt も毎回スタンプ（単一チャネル統合の最終1書き込みに自然に乗る）。
    suspend fun saveScrollPosition(
        bookId: String,
        filename: String,
        scrollIndex: Int,
        scrollOffset: Int,
    ) = withContext(Dispatchers.IO) {
        progressDao.saveProgress(
            ProgressEntity(bookId, filename, scrollIndex, scrollOffset, lastReadAt = System.currentTimeMillis())
        )
    }

    companion object {
        private const val TAG = "BookRepository"
    }
}
