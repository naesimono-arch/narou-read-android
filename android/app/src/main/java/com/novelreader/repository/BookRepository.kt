package com.novelreader.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.PendingJobDao
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.pdf.CorruptedPdfError
import com.novelreader.pdf.EncryptedPdfError
import com.novelreader.pdf.InsufficientStorageError
import com.novelreader.pdf.PdfBookExtractor
import com.novelreader.viewmodel.BookImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val pendingJobDao: PendingJobDao = AppDatabase.getDatabase(context).pendingJobDao(),
) {

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allProgress: Flow<List<ProgressEntity>> = progressDao.getAllProgress()

    /**
     * 抽出例外・IO 例外をユーザー向けエラー種別に変換する。
     *
     * ネイティブ PDFBox 経路は暗号化/破損/容量不足を [com.novelreader.pdf.PdfExtractionException] の
     * サブ**型**で投げる（facade PdfBookExtractor が内部で classifyPdfError 済み）ため型で分岐する。
     * Chaquopy 版は PyException のメッセージ文字列で分類していたが、型の方が堅牢なため文字列マッチは廃止した。
     * facade を通らない例外（URI 権限喪失・出力ディレクトリ生成失敗）は BookRepository 自身が投げる
     * IOException なので、従来どおりメッセージで拾う（else 節）。
     */
    internal fun classifyError(e: Throwable): Throwable = when (e) {
        is EncryptedPdfError        -> BookImportError.EncryptedPdf()
        is InsufficientStorageError -> BookImportError.InsufficientStorage()
        is CorruptedPdfError        -> BookImportError.CorruptedPdf()
        else -> {
            val msg = e.message ?: ""
            when {
                msg.contains("PDFファイルを開けません")      -> BookImportError.UriPermissionDenied()
                msg.contains("出力ディレクトリの作成に失敗")  -> BookImportError.StorageWriteFailure()
                msg.contains("No space left on device")     -> BookImportError.InsufficientStorage()
                else                                        -> BookImportError.Unknown(msg)
            }
        }
    }

    /** PDFをキャッシュにコピーし、ネイティブ(PDFBox)抽出でHTML生成後にRoomへ登録する。 */
    suspend fun addBook(
        pdfUri: Uri,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit = { _, _, _, _ -> },
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        // withContext(Dispatchers.IO) の CoroutineScope を捕捉する。抽出の進捗コールバック（非 suspend）から
        // キャンセルを確認するために使う（下記 ③）。
        val extractionScope = this
        runCatching {
            val bookId = UUID.randomUUID().toString().take(8)

            // ① 一時ファイルにコピー（try-finally で確実に削除する）
            val tempFile = File(context.cacheDir, "temp_$bookId.pdf")
            // catch から参照するため try の外で宣言する（②で確定・③の失敗時に掃除）。
            val outputDir = File(context.filesDir, "novels/$bookId")
            try {
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: throw IOException("PDFファイルを開けません（URI権限が失われた可能性があります）")
                inputStream.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                // ② 出力先ディレクトリを確定
                if (!outputDir.mkdirs() && !outputDir.exists()) {
                    throw IOException("出力ディレクトリの作成に失敗しました: ${outputDir.absolutePath}")
                }

                // ③ ネイティブ(PDFBox)抽出で HTML を生成する。
                // Chaquopy(JNI) は割り込み不能だったが、純 Kotlin 実行なので中断可能。processPages は本文ページ
                // 毎に onProgress を呼ぶため、進捗通知のたびに ensureActive() を通せば本文抽出中でも「停止」で
                // 割り込める（handover A① の NonCancellable 制約を緩和）。processPages 自体はコルーチン非依存の
                // 純ロジックに保つため、既に全層へ通っている進捗コールバックへ相乗りしてキャンセルを確認する。
                val meta = try {
                    PdfBookExtractor.process(tempFile, bookId, outputDir) { step, stepLocalPercent, phase, title ->
                        extractionScope.ensureActive()
                        onProgress(step, stepLocalPercent, phase, title)
                    }
                } catch (e: Throwable) {
                    // 抽出が中断/失敗したら書きかけの出力ディレクトリを消す（本棚に出ない孤立 HTML を残さない）。
                    // 旧実装は抽出全体を NonCancellable で包んで孤立を防いでいたが、緩和で抽出中のキャンセルを
                    // 許すため、その代替としてこの明示クリーンアップで担保する（DB 登録前のみ発火）。
                    outputDir.deleteRecursively()
                    throw e
                }

                // ④ Room 登録のみ NonCancellable で保護する。
                // HTML 生成済み→DB 登録前の一瞬でキャンセルされると本棚に出ない孤立本になるため、この最終確定
                // だけは中断不能に保つ（抽出全体を包んでいた旧 NonCancellable の縮小）。
                val book = withContext(NonCancellable) {
                    // addedAt に追加時刻をスタンプし、本棚の最近活動順ソート（未読本の基準）に使う。
                    val b = BookEntity(bookId, meta.title, outputDir.absolutePath, meta.author, addedAt = System.currentTimeMillis())
                    bookDao.insertBook(b)
                    // 変換が確定したので永続キュー（pending_jobs）の記帳を落とす。insertBook と同じ
                    // NonCancellable 内で連続実行し、「本は登録されたのに pending が残る」→ 次回起動の
                    // リカバリが同じ本を再変換して重複登録する窓を最小化する（完全排他には DAO 跨ぎの
                    // トランザクション統合が要るが、テスト用の DAO 分離注入を保つため窓の最小化で妥協。
                    // この数msにプロセス kill が当たる確率は実用上無視できる）。
                    settlePendingJob(pdfUri)
                    b
                }
                // NonCancellable ブロック完了後にキャンセルを確認
                // （NonCancellable 内では ensureActive() が機能しないため必ず外側で呼ぶ）
                extractionScope.ensureActive()
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
                // 失敗が確定した本は再開対象から外す（破損PDF等は再試行しても失敗を繰り返すだけで、
                // 起動のたびに同じエラーが再走するループになる。ユーザーにはこの直後エラー通知が
                // 出るので、必要なら選び直してもらう）。キャンセルは上で rethrow 済み＝対象外で、
                // 停止操作時の扱いは Service の ACTION_STOP（全消し）が決める。
                withContext(NonCancellable) { settlePendingJob(pdfUri) }
                Result.failure(classifyError(e))
            },
        )
    }

    // ── 処理キューの永続化（pending_jobs）───────────────────────────────
    // enqueue 時に記帳し、変換の成否確定時に削除する。残っている行 = 強制終了
    // （OEM kill/OOM/onTimeout）で中断された未完了ジョブとして起動時リカバリが検出する。

    /** enqueue の記帳。REPLACE のため再開時の再投入でも二重行にならない。 */
    suspend fun addPendingJob(uri: String, displayName: String) = withContext(Dispatchers.IO) {
        pendingJobDao.insert(PendingJobEntity(uri, displayName, System.currentTimeMillis()))
    }

    /** 未完了ジョブ一覧（enqueue 順）。起動時リカバリの検出用。 */
    suspend fun getPendingJobs(): List<PendingJobEntity> =
        withContext(Dispatchers.IO) { pendingJobDao.getAll() }

    /** 再開不能と判明したジョブの除去（権限喪失時など）。永続権限も返す。 */
    suspend fun removePendingJob(uri: String) = withContext(Dispatchers.IO) {
        settlePendingJob(Uri.parse(uri))
    }

    /** 全ジョブの除去（ユーザーの明示停止＝「再開してほしくない」意思の反映）。 */
    suspend fun clearPendingJobs() = withContext(Dispatchers.IO) {
        // deleteAll の前に各行の永続権限を返す（行を先に消すと解放対象の URI が分からなくなる）
        pendingJobDao.getAll().forEach { releasePersistedPermission(Uri.parse(it.uri)) }
        pendingJobDao.deleteAll()
    }

    /** books テーブルに存在しない bookId の HTML ディレクトリを削除する（孤立HTML掃除）。
     *  強制終了（OEM kill/OOM）ではプロセスごと消えるため addBook 内 catch のクリーンアップが
     *  走らず、書きかけの novels/<bookId>/ が残り得る。DB 登録（NonCancellable の最終確定）が
     *  完了の境界なので「DB に無い = 未完了の書きかけ」と判定して安全に消せる。
     *  【前提】Service 非稼働時に呼ぶこと（処理中の本の出力ディレクトリを誤削除しないため。
     *  呼び出し側 runStartupRecoveryOnce が processingState で判定する）。 */
    suspend fun cleanOrphanHtmlDirs() = withContext(Dispatchers.IO) {
        val novelsDir = File(context.filesDir, "novels")
        val validIds = bookDao.getAllBookIds().toSet()
        novelsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in validIds) {
                if (dir.deleteRecursively()) Log.i(TAG, "孤立HTMLを掃除: ${dir.name}")
                else Log.w(TAG, "孤立HTMLの削除に失敗: ${dir.absolutePath}")
            }
        }
    }

    /** pending_jobs の記帳を消し、再開用に取得した永続 URI 権限も返す。
     *  変換の成否が確定した時点（成功=Room 登録済み／失敗=エラー通知確定）で呼ぶ。 */
    private suspend fun settlePendingJob(pdfUri: Uri) {
        pendingJobDao.deleteByUri(pdfUri.toString())
        releasePersistedPermission(pdfUri)
    }

    /** takePersistableUriPermission（BookshelfViewModel.addBook）の対。永続権限は端末全体で
     *  上限があるため用が済んだら返す。未取得（プロバイダ非対応等）だと SecurityException に
     *  なるので防御する（返せなくても実害は上限消費のみ）。 */
    private fun releasePersistedPermission(pdfUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
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
