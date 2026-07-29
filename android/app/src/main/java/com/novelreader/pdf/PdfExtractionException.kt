package com.novelreader.pdf

import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.IOException

/**
 * PDF 抽出中に発生するユーザー向けエラーの基底。
 * 移植元: submission-B の EncryptedPdfError / CorruptedPdfError（PdfExtractor.kt）＋ app.py の InsufficientStorageError。
 *
 * Chaquopy 版は BookRepository.classifyError が PyException のメッセージ文字列で分類していたが、
 * ネイティブ版は PDFBox が型で暗号化/破損を投げるため、これらの Kotlin 型で分類する
 * （移行は完了済み＝現在の分岐は PdfBookImporter.classifyError にある）。
 */
sealed class PdfExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 暗号化（パスワード保護）PDF。移植元 submission-B EncryptedPdfError。 */
class EncryptedPdfError(message: String, cause: Throwable? = null) : PdfExtractionException(message, cause)

/** 構造破損で解析不能な PDF。移植元 submission-B CorruptedPdfError。 */
class CorruptedPdfError(message: String, cause: Throwable? = null) : PdfExtractionException(message, cause)

/** 保存領域不足（HTML 書き出し時の ENOSPC 等）。移植元 app.py InsufficientStorageError。 */
class InsufficientStorageError(message: String, cause: Throwable? = null) : PdfExtractionException(message, cause)

/**
 * 低レベル例外をユーザー向け [PdfExtractionException] へ分類する（移植元 app.py process_pdf の except 節）。
 *
 * ネイティブ PDFBox は暗号化/破損を主に型で投げるが、型に載らない経路の取りこぼしを防ぐため文字列判定も併用する:
 * - 暗号化は **InvalidPasswordException（型）** に加え、**メッセージに "password" を含む場合**も拾う
 *   （型が付かず素の IOException として上がる暗号化エラーの経路が Chaquopy 時代に実在したため、
 *   移植時にこの文字列判定を温存した。ネイティブ経路でも同様に起きるかは未確認だが、
 *   誤検知しても「暗号化」と案内するだけで実害が小さいため防御的に残す）。
 * - `InvalidPasswordException` は `IOException` のサブクラスのため、汎用 IOException より**先に**判定する。
 * - ENOSPC（保存領域不足）は書き出し時の IOException なので、破損（IOException→Corrupted）より**先に**メッセージで判定する。
 *
 * 既に分類済みの [PdfExtractionException] と、上記いずれにも該当しない未知例外は、型・トレースを保持したまま素通しする
 * （app.py の bare `raise` 相当＝「予期しないエラー」を誤って「破損」に化けさせない）。
 */
internal fun classifyPdfError(e: Throwable): Throwable = when {
    e is PdfExtractionException -> e
    e is InvalidPasswordException -> EncryptedPdfError(e.message ?: "encrypted", e)
    isPasswordError(e) -> EncryptedPdfError(e.message ?: "encrypted", e)
    isNoSpaceLeft(e) -> InsufficientStorageError(e.message ?: "no space left on device", e)
    e is IOException -> CorruptedPdfError(e.message ?: "corrupted", e)
    else -> e
}

// 型に載らない暗号化エラーの取りこぼし防止（大小文字を無視してメッセージを見る）。
private fun isPasswordError(e: Throwable): Boolean =
    e.message?.contains("password", ignoreCase = true) == true

// ENOSPC 判定。容量不足に専用の例外型が無く、実装により文言が
// "No space left on device" / "[Errno 28]" / "ENOSPC (No space left on device)" と割れるため 3 形すべて拾う。
private fun isNoSpaceLeft(e: Throwable): Boolean {
    val msg = e.message ?: return false
    return "No space left on device" in msg || "[Errno 28]" in msg || "ENOSPC" in msg
}
