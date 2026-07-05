package com.novelreader.repository

import android.content.Context
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.pdf.CorruptedPdfError
import com.novelreader.pdf.EncryptedPdfError
import com.novelreader.pdf.InsufficientStorageError
import com.novelreader.viewmodel.BookImportError
import io.mockk.coEvery
import io.mockk.coVerify
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

    // ── classifyError: PdfExtractionException 型分岐 ──────────────────────
    // ネイティブ PDFBox 経路は暗号化/破損/容量不足を型で投げる（Chaquopy 版の PyException 文字列マッチは廃止）。

    @Test
    fun `classifyError - EncryptedPdfError を EncryptedPdf に変換する`() {
        val result = repository.classifyError(EncryptedPdfError("password required"))
        assert(result is BookImportError.EncryptedPdf)
    }

    @Test
    fun `classifyError - InsufficientStorageError を InsufficientStorage に変換する`() {
        val result = repository.classifyError(InsufficientStorageError("disk full"))
        assert(result is BookImportError.InsufficientStorage)
    }

    @Test
    fun `classifyError - CorruptedPdfError を CorruptedPdf に変換する`() {
        val result = repository.classifyError(CorruptedPdfError("bad structure"))
        assert(result is BookImportError.CorruptedPdf)
    }

    @Test
    fun `classifyError - 未知例外を Unknown に変換する`() {
        val result = repository.classifyError(RuntimeException("SomeOtherError: unexpected"))
        assert(result is BookImportError.Unknown)
    }

    // ── classifyError: メッセージ分岐（facade を通らない BookRepository 由来の IOException）──

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
        // lastReadAt は書き込み時刻（System.currentTimeMillis()）で非決定的なため完全一致は使わず、
        // 識別子フィールドのみ検証する（章移動なのでスクロール位置は 0,0）。
        coVerify {
            progressDao.saveProgress(
                match {
                    it.bookId == "id01" && it.lastReadFilename == "chapter_02.html" &&
                        it.scrollIndex == 0 && it.scrollOffset == 0
                }
            )
        }
    }
}
