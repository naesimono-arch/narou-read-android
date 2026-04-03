package com.novelreader.repository

import android.content.Context
import com.chaquo.python.PyException
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.viewmodel.BookImportError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BookRepositoryTest {

    private lateinit var bookDao: BookDao
    private lateinit var progressDao: ProgressDao
    private lateinit var context: Context
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        bookDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        repository = BookRepository(context, bookDao, progressDao)
    }

    // ── classifyError: PyException 側 ──────────────────────────────────────

    @Test
    fun `classifyError - PyException EncryptedPdfError を EncryptedPdf に変換する`() {
        val e = mockk<PyException> { every { message } returns "EncryptedPdfError: password required" }
        val result = repository.classifyError(e)
        assert(result is BookImportError.EncryptedPdf)
    }

    @Test
    fun `classifyError - PyException InsufficientStorageError を InsufficientStorage に変換する`() {
        val e = mockk<PyException> { every { message } returns "InsufficientStorageError: disk full" }
        val result = repository.classifyError(e)
        assert(result is BookImportError.InsufficientStorage)
    }

    @Test
    fun `classifyError - PyException CorruptedPdfError を CorruptedPdf に変換する`() {
        val e = mockk<PyException> { every { message } returns "CorruptedPdfError: bad structure" }
        val result = repository.classifyError(e)
        assert(result is BookImportError.CorruptedPdf)
    }

    @Test
    fun `classifyError - PyException 未知メッセージを Unknown に変換する`() {
        val e = mockk<PyException> { every { message } returns "SomeOtherError: unexpected" }
        val result = repository.classifyError(e)
        assert(result is BookImportError.Unknown)
    }

    // ── classifyError: 非PyException 側 ───────────────────────────────────

    @Test
    fun `classifyError - PDFファイルを開けません を UriPermissionDenied に変換する`() {
        val e = IOException("PDFファイルを開けません（URI権限が失われた可能性があります）")
        val result = repository.classifyError(e)
        assert(result is BookImportError.UriPermissionDenied)
    }

    @Test
    fun `classifyError - 出力ディレクトリの作成に失敗 を StorageWriteFailure に変換する`() {
        val e = IOException("出力ディレクトリの作成に失敗しました: /data/user/0/com.novelreader/files/novels/abc")
        val result = repository.classifyError(e)
        assert(result is BookImportError.StorageWriteFailure)
    }

    @Test
    fun `classifyError - No space left on device を InsufficientStorage に変換する`() {
        val e = IOException("No space left on device")
        val result = repository.classifyError(e)
        assert(result is BookImportError.InsufficientStorage)
    }

    // ── DAO委譲 ───────────────────────────────────────────────────────────

    @Test
    fun `deleteBook - bookDao と progressDao の両方が呼ばれる`() = runTest {
        val book = BookEntity("id01", "テスト本", "/nonexistent/path")
        repository.deleteBook(book)
        coVerify { bookDao.deleteById("id01") }
        coVerify { progressDao.deleteByBookId("id01") }
    }

    @Test
    fun `getLastRead - progressDao の戻り値をそのまま返す`() = runTest {
        coEvery { progressDao.getLastRead("id01") } returns "chapter_01.html"
        val result = repository.getLastRead("id01")
        assertEquals("chapter_01.html", result)
    }

    @Test
    fun `getLastRead - 未読の場合は null を返す`() = runTest {
        coEvery { progressDao.getLastRead("id02") } returns null
        val result = repository.getLastRead("id02")
        assertNull(result)
    }

    @Test
    fun `saveProgress - progressDao に ProgressEntity を渡して呼ぶ`() = runTest {
        repository.saveProgress("id01", "chapter_02.html")
        coVerify { progressDao.saveProgress(ProgressEntity("id01", "chapter_02.html")) }
    }
}
