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

    /** addBook の取込結果。同一PDFの二重取込（UX監査 F-G 公理3べき等性）を呼び出し側で
     *  区別できるよう、新規登録と重複スキップを型で分ける（Service の通知文面を分岐させる）。 */
    sealed interface AddBookResult {
        /** 新規に蔵書登録した本。 */
        data class Added(val book: BookEntity) : AddBookResult
        /** 既に同一の本が蔵書済みのため登録をスキップした（変換成果は破棄済み）。既存の本を返す。 */
        data class Duplicate(val existing: BookEntity) : AddBookResult
    }

    /** べき等ガードの純判定を切り出したもの: 抽出後のタイトル＋著者に一致する既存蔵書を返す
     *  （無ければ null）。実 PDF 抽出を挟まず単体テストできるよう addBook 本体から分離する。 */
    internal suspend fun findExistingBook(title: String, author: String): BookEntity? =
        bookDao.findByTitleAndAuthor(title, author)

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
    ): Result<AddBookResult> = withContext(Dispatchers.IO) {
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

                // ④ べき等ガード（UX監査 F-G 公理3）: 抽出後のタイトル＋著者で既存蔵書を照合する。
                // 既に同じ本があれば二重登録しない。books は取込元 URI/サイズを持たない（スキーマに無い）ため
                // 変換完了まで判定できず、この段階で書きかけ HTML を破棄する（本棚に孤立本を残さない）。
                // なお同一 URL の連続投入は Service 側のキュー重複ガードで変換前に弾く（本ガードは URI が
                // 変わる再選択・別セッション再取込の受け皿）。
                val existing = bookDao.findByTitleAndAuthor(meta.title, meta.author)
                if (existing != null) {
                    outputDir.deleteRecursively()
                    // 変換の成否が確定した（＝重複と判明）ので pending_jobs を落とす。DB 書き込みを伴わない
                    // が settlePendingJob は権限解放も行うため、登録成功時と同じく NonCancellable で保護する。
                    withContext(NonCancellable) { settlePendingJob(pdfUri) }
                    extractionScope.ensureActive()
                    AddBookResult.Duplicate(existing)
                } else {
                    // ⑤ Room 登録のみ NonCancellable で保護する。
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
                    AddBookResult.Added(book)
                }
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
                // 起動のたびに同じエラーが再走するループになる）。キャンセルは上で rethrow 済み＝対象外で、
                // 停止操作時の扱いは Service の ACTION_STOP（全消し）が決める。
                // なぜ settlePendingJob ではなく pending_jobs 行の削除のみか（M7 再試行の成立）:
                // settlePendingJob は永続 URI 権限も返すが、それだと失敗 Snackbar の「再試行」が
                // 同一 URI を再投入したとき openInputStream が権限喪失で必ず再失敗する（＝再試行が形骸化）。
                // 権限を残せばユーザー起点の再試行が機能する。再試行しない場合に権限が1件残るのは
                // 端末上限内の軽微なコストで、権限リーク回避より再試行の成立を優先する。
                // （成功/重複/停止時は従来どおり settlePendingJob で権限も返す＝ここだけの例外扱い。）
                withContext(NonCancellable) { pendingJobDao.deleteByUri(pdfUri.toString()) }
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

    /**
     * 起動時クリーンアップ: pending_jobs にも紐付かない「孤児」の永続 URI 権限を解放する（恒久リーク回収）。
     *
     * なぜこれが必要か（root cause）: 取込失敗時は M7 の「再試行」を成立させるため、addBook の失敗経路が
     * settlePendingJob（権限解放込み）ではなく pending_jobs 行の削除のみを行い、永続 URI 権限を意図的に
     * 残す。しかし再試行 Snackbar はプロセス生存中にしか出せないため、再試行されないまま終わった失敗分の
     * 権限は「pending_jobs 行が無い＝どの経路でも解放されない」恒久リークになり、端末上限(128件)へ向けて
     * 溜まり続ける。そこで次回アプリ起動時に「pending_jobs 非紐付けの永続権限＝もう再試行され得ない失敗
     * 取込の置き土産」として回収する。
     *
     * なぜ pending_jobs 非紐付けだけで孤児と断定できるか: 永続権限を取るのは PDF 取込の1経路のみ
     * （BookshelfViewModel.addBook の takePersistableUriPermission）。books は取込元 URI を持たない
     * （スキーマに無い）ため、変換完了済みの本は元 URI の権限を二度と要さない（成功時に settle 済み）。
     * よって「まだ処理が要る URI は必ず pending_jobs 行を持つ」不変条件が成り立ち、行が無ければ孤児。
     *
     * 【誤解放しない根拠】変換中の URI を誤って解放しないこと: 本メソッドは runStartupRecoveryOnce の
     * processingState==null ガード下（Service 非稼働）でのみ呼ばれ、かつ起動直後で新規取込より前に走る。
     * 稼働中に処理される URI は enqueue 時に必ず pending_jobs 行を持つため keepUris に含まれ、解放対象外。
     * 再開対象（resumable）の URI も pending_jobs 行を持つので keepUris で保護される。
     *
     * @param keepUris 現在の pending_jobs が保持する URI 集合（この集合に含まれる権限は残す）。
     */
    suspend fun releaseOrphanedPermissions(keepUris: Set<String>) = withContext(Dispatchers.IO) {
        val persistedReadUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        orphanedPermissionUris(persistedReadUris, keepUris).forEach { uri ->
            releasePersistedPermission(Uri.parse(uri))
        }
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        bookDao.deleteById(book.id)
        progressDao.deleteByBookId(book.id)
        if (!File(book.htmlDirPath).deleteRecursively()) {
            Log.w(TAG, "HTMLディレクトリの削除に失敗: ${book.htmlDirPath}")
        }
    }

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。ユーザー確定操作からのみ呼ぶ。
    suspend fun linkNcode(bookId: String, ncode: String?) = withContext(Dispatchers.IO) {
        bookDao.updateNcode(bookId, ncode)
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

/**
 * 起動時に解放すべき「孤児」の永続 URI 権限を判定する純関数（テスト対象）。
 * 持続化された読み取り権限のうち pending_jobs 非紐付けのもの＝もう再試行され得ない失敗取込の
 * 置き土産を差集合で選ぶ。UI・contentResolver 非依存で回収ロジックの中核を単体テストするため分離する
 * （releaseOrphanedPermissions が persistedUriPermissions 取得と実解放の副作用を担い、判定はここ）。
 */
internal fun orphanedPermissionUris(
    persistedReadUris: Set<String>,
    keepUris: Set<String>,
): Set<String> = persistedReadUris - keepUris
