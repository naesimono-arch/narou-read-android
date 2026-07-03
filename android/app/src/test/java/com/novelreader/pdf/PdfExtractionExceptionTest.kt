package com.novelreader.pdf

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * classifyPdfError の分類テスト。
 * 移植元: test_logic.py TestProcessPdf のエラー変換／再送出ケース（8件）を型ベースへ翻案。
 *
 * 注: InvalidPasswordException（型）分岐は、tom_roush 版のコンストラクタが package-private で
 * テストから生成できないため直接検証しない。同分岐が拾う暗号化は "password" メッセージ経路でも
 * 拾われる（Python 忠実）ため、そちらで暗号化分類を担保する。
 */
class PdfExtractionExceptionTest {

    @Test fun passwordMessageBecomesEncrypted() {
        // メッセージに "password" を含む → EncryptedPdfError（Python test_password_in_message 相当）
        val result = classifyPdfError(IOException("The password is incorrect"))
        assertTrue(result is EncryptedPdfError)
    }

    @Test fun noSpaceMessageBecomesStorage() {
        // "No space left on device" → InsufficientStorageError
        val result = classifyPdfError(IOException("No space left on device"))
        assertTrue(result is InsufficientStorageError)
    }

    @Test fun errno28BecomesStorage() {
        // "[Errno 28]" → InsufficientStorageError
        val result = classifyPdfError(IOException("[Errno 28] No space left on device"))
        assertTrue(result is InsufficientStorageError)
    }

    @Test fun enospcBecomesStorage() {
        // Android 形の "ENOSPC (No space left on device)" も拾う
        val result = classifyPdfError(IOException("write failed: ENOSPC (No space left on device)"))
        assertTrue(result is InsufficientStorageError)
    }

    @Test fun genericIoBecomesCorrupted() {
        // ENOSPC でない一般 IOException（構造破損）→ CorruptedPdfError。
        // pdfminer の PDFSyntaxError 等（Python）に対応する native の解析失敗経路。
        val result = classifyPdfError(IOException("Error: Invalid xref table"))
        assertTrue(result is CorruptedPdfError)
    }

    @Test fun alreadyEncryptedPassesThroughSameInstance() {
        // 既に分類済みなら包み直さず同一インスタンスを素通し（cause/トレース保持）
        val original = EncryptedPdfError("already typed")
        assertSame(original, classifyPdfError(original))
    }

    @Test fun alreadyCorruptedPassesThroughSameInstance() {
        val original = CorruptedPdfError("already typed")
        assertSame(original, classifyPdfError(original))
    }

    @Test fun unknownExceptionPassesThroughSameInstance() {
        // どの条件にも当てはまらない例外はラップせず素通し（Python: bare raise ＝ ValueError 等）
        val original = IllegalStateException("unexpected internal error")
        assertSame(original, classifyPdfError(original))
    }
}
