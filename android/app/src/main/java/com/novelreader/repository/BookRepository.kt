package com.novelreader.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chaquo.python.Python
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class BookRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val bookDao = db.bookDao()
    private val progressDao = db.progressDao()

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun interface ProgressCallback {
        fun onProgress(step: Int, stepLocalPercent: Float, phase: String)
    }

    /** PDFをキャッシュにコピーし、Chaquopy経由でHTML生成後にRoomへ登録する。 */
    suspend fun addBook(
        pdfUri: Uri,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String) -> Unit = { _, _, _ -> },
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

                // ③ Chaquopy経由で Python 処理（コールバックでフェーズ進捗を通知）
                val python = Python.getInstance()
                val title = python.getModule("app")
                    .callAttr(
                        "process_pdf",
                        tempFile.absolutePath,
                        bookId,
                        outputDir.absolutePath,
                        true,
                        ProgressCallback { step, stepLocalPercent, phase -> onProgress(step, stepLocalPercent, phase) },
                    )
                    .toString()

                // ④ Roomに登録
                val book = BookEntity(bookId, title, outputDir.absolutePath)
                bookDao.insertBook(book)
                book
            } finally {
                if (!tempFile.delete()) Log.w(TAG, "一時ファイルの削除に失敗: ${tempFile.absolutePath}")
            }
        }
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

    suspend fun saveProgress(bookId: String, filename: String) = withContext(Dispatchers.IO) {
        progressDao.saveProgress(ProgressEntity(bookId, filename))
    }

    companion object {
        private const val TAG = "BookRepository"
    }
}
