package com.novelreader.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * PDF DL の再試行分類 [PdfImportViewModel.isDownloadRetryable] の回帰（UX監査 add+errtext）。
 * 一過性（timeout/DNS/瞬断・5xx/429）だけ再試行し、恒久失敗（4xx）は再試行しないことを固定する。
 */
class PdfImportRetryTest {

    @Test
    fun `timeout など一般 IOException は再試行対象`() {
        assertTrue(PdfImportViewModel.isDownloadRetryable(SocketTimeoutException("read timeout")))
        assertTrue(PdfImportViewModel.isDownloadRetryable(IOException("connection reset")))
    }

    @Test
    fun `5xx や 429 の RetryableDownloadException は再試行対象`() {
        assertTrue(PdfImportViewModel.isDownloadRetryable(RetryableDownloadException("HTTP 503", null)))
        assertTrue(PdfImportViewModel.isDownloadRetryable(RetryableDownloadException("HTTP 429", 2_000L)))
    }

    @Test
    fun `4xx の FatalDownloadException は再試行しない`() {
        assertFalse(PdfImportViewModel.isDownloadRetryable(FatalDownloadException("HTTP 404")))
    }

    @Test
    fun `IOException でない例外は再試行しない`() {
        assertFalse(PdfImportViewModel.isDownloadRetryable(IllegalStateException("bug")))
    }

    @Test
    fun `RetryableDownloadException は Retry-After を保持する`() {
        val e = RetryableDownloadException("HTTP 429", 3_000L)
        assertEquals(3_000L, e.retryAfterMs)
    }
}
