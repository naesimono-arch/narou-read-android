package com.novelreader.repository

import android.net.Uri
import com.novelreader.data.BookEntity
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.model.Ncode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [BookRepository] のインメモリ実装。JVM 単体テストで ViewModel を検証するための差し替え用。
 *
 * なぜ必要か: 本番実装 [DefaultBookRepository] は Room（AppDatabase の static シングルトン）と
 * PDFBox/Android コンテキストに依存し JVM で生成できない。ViewModel を実データフローで検証するには
 * Room・PDFBox を伴わない軽量な代替が要るため、interface 抽出とセットでこの Fake を用意する。
 *
 * 挙動は「テストが必要とする最小限」に絞る（実 PDF 抽出・永続 URI 権限の副作用は持たない）。
 * 蔵書・進捗・pending_jobs はメモリ上の可変状態として保持し、Flow で観測できるようにする。
 */
class FakeBookRepository : BookRepository {

    // allBooks/allProgress は StateFlow で公開し、テストから value 差し替え → ViewModel へ流せるようにする。
    private val booksState = MutableStateFlow<List<BookEntity>>(emptyList())
    private val progressState = MutableStateFlow<List<ProgressEntity>>(emptyList())

    override val allBooks: Flow<List<BookEntity>> = booksState
    override val allProgress: Flow<List<ProgressEntity>> = progressState

    // pending_jobs の代替（uri をキーに保持。enqueue 順の再現は不要な粒度なので LinkedHashMap で挿入順維持）。
    private val pendingJobs = LinkedHashMap<String, PendingJobEntity>()

    /** addBook の戻り値を差し込むためのフック（実抽出を持たないため、テストが期待結果を指定する）。 */
    var addBookResult: Result<BookRepository.AddBookResult> =
        Result.failure(NotImplementedError("addBookResult をテストで設定してください"))

    /** テストのための蔵書プリセット（allBooks へ即時反映）。 */
    fun setBooks(books: List<BookEntity>) { booksState.value = books }

    /** テストのための進捗プリセット（allProgress へ即時反映）。 */
    fun setProgress(progress: List<ProgressEntity>) { progressState.value = progress }

    // ncode 引数は ADR 0011 の縦書きPDF取り込み経路で使うが、Fake は addBookResult を返すだけの
    // スタブのため受け取っても副作用は持たない（挙動検証は DefaultBookRepository/Service 側で行う）。
    override suspend fun addBook(
        pdfUri: Uri,
        ncode: Ncode?,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit,
    ): Result<BookRepository.AddBookResult> = addBookResult

    override suspend fun addPendingJob(uri: String, displayName: String) {
        pendingJobs[uri] = PendingJobEntity(uri, displayName, pendingJobs.size.toLong())
    }

    override suspend fun getPendingJobs(): List<PendingJobEntity> = pendingJobs.values.toList()

    override suspend fun removePendingJob(uri: String) { pendingJobs.remove(uri) }

    override suspend fun clearPendingJobs() { pendingJobs.clear() }

    override suspend fun cleanOrphanHtmlDirs() { /* Fake はファイルシステムを持たない（no-op） */ }

    override suspend fun releaseOrphanedPermissions(keepUris: Set<String>) { /* 永続権限を持たない（no-op） */ }

    override suspend fun deleteBook(book: BookEntity) {
        booksState.value = booksState.value.filterNot { it.id == book.id }
        progressState.value = progressState.value.filterNot { it.bookId == book.id }
    }

    override suspend fun linkNcode(bookId: String, ncode: Ncode?) {
        booksState.value = booksState.value.map {
            if (it.id == bookId) it.copy(ncode = ncode?.value) else it
        }
    }

    override suspend fun getLastRead(bookId: String): String? =
        progressState.value.firstOrNull { it.bookId == bookId }?.lastReadFilename

    override suspend fun getProgress(bookId: String): ProgressEntity? =
        progressState.value.firstOrNull { it.bookId == bookId }

    override suspend fun saveProgress(bookId: String, filename: String) {
        upsertProgress(ProgressEntity(bookId, filename))
    }

    override suspend fun saveScrollPosition(
        bookId: String,
        filename: String,
        scrollIndex: Int,
        scrollOffset: Int,
    ) {
        upsertProgress(ProgressEntity(bookId, filename, scrollIndex, scrollOffset))
    }

    // 同一 bookId は置換（REPLACE 相当）。progress は bookId が主キーの1行モデルのため。
    private fun upsertProgress(entity: ProgressEntity) {
        progressState.value = progressState.value.filterNot { it.bookId == entity.bookId } + entity
    }
}
