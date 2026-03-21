package com.novelreader.repository

import android.content.Context
import android.net.Uri
import com.chaquo.python.Python
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val bookDao = db.bookDao()
    private val progressDao = db.progressDao()

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun interface ProgressCallback {
        fun onProgress(percent: Int, phase: String)
    }

    /** PDFをキャッシュにコピーし、Chaquopy経由でHTML生成後にRoomへ登録する。 */
    suspend fun addBook(
        pdfUri: Uri,
        onProgress: (percent: Int, phase: String) -> Unit = { _, _ -> },
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val bookId = UUID.randomUUID().toString().take(8)

            // ① 一時ファイルにコピー
            val tempFile = File(context.cacheDir, "temp_$bookId.pdf")
            context.contentResolver.openInputStream(pdfUri)!!.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            // ② 出力先ディレクトリを確定
            val outputDir = File(context.filesDir, "novels/$bookId").also { it.mkdirs() }

            // ③ Chaquopy経由で Python 処理（コールバックでフェーズ進捗を通知）
            val python = Python.getInstance()
            val title = python.getModule("app")
                .callAttr(
                    "process_pdf",
                    tempFile.absolutePath,
                    bookId,
                    outputDir.absolutePath,
                    true,
                    ProgressCallback { percent, phase -> onProgress(percent, phase) },
                )
                .toString()

            tempFile.delete()

            // ④ Roomに登録
            val book = BookEntity(bookId, title, outputDir.absolutePath)
            bookDao.insertBook(book)
            book
        }
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        bookDao.deleteById(book.id)
        progressDao.deleteByBookId(book.id)
        File(book.htmlDirPath).deleteRecursively()
    }

    suspend fun getLastRead(bookId: String): String? =
        withContext(Dispatchers.IO) { progressDao.getLastRead(bookId) }

    suspend fun saveProgress(bookId: String, filename: String) = withContext(Dispatchers.IO) {
        progressDao.saveProgress(ProgressEntity(bookId, filename))
    }
}
