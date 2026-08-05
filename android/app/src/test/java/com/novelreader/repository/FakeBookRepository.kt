package com.novelreader.repository

import android.net.Uri
import com.novelreader.data.BookEntity
import com.novelreader.data.NewEpisodeMarkEntity
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
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

    // U1 新着チェックの基準値（本番は Worker が書き UI が読む）。テストは setNewEpisodeMarks で直接与える。
    private val newEpisodeMarksState = MutableStateFlow<List<NewEpisodeMarkEntity>>(emptyList())
    override val newEpisodeMarks: Flow<List<NewEpisodeMarkEntity>> = newEpisodeMarksState

    /** テストのための基準値プリセット（Web 蔵書の「続きあり」バッジ判定の入力）。 */
    fun setNewEpisodeMarks(marks: List<NewEpisodeMarkEntity>) { newEpisodeMarksState.value = marks }

    // (b) Web由来・未取込カードのインメモリ代替（Room と同じく addedAt 降順で観測させる）。
    private val webNovelsState = MutableStateFlow<List<WebNovelEntity>>(emptyList())
    override val webNovels: Flow<List<WebNovelEntity>> = webNovelsState

    /** テストのための Web 作品プリセット（webNovels へ即時反映）。 */
    fun setWebNovels(novels: List<WebNovelEntity>) { webNovelsState.value = novels }

    override suspend fun putWebNovel(novel: WebNovelEntity) {
        webNovelsState.value =
            (webNovelsState.value.filterNot { it.ncode == novel.ncode } + novel)
                .sortedByDescending { it.addedAt }
    }

    override suspend fun removeWebNovel(ncode: Ncode) {
        val normalized = ncode.value.trim().uppercase()
        webNovelsState.value = webNovelsState.value.filterNot { it.ncode == normalized }
    }

    // 機能②: なろうWebView読書の読書位置のインメモリ代替（ncode→最後に開いた話）。
    private val webReadingProgressState = MutableStateFlow<List<WebReadingProgressEntity>>(emptyList())
    override val webReadingProgress: Flow<List<WebReadingProgressEntity>> = webReadingProgressState

    /** テストのための読書位置プリセット。 */
    fun setWebReadingProgress(list: List<WebReadingProgressEntity>) { webReadingProgressState.value = list }

    override suspend fun recordWebReadingEpisode(ncode: Ncode, episode: Int) {
        // 本番と同じく trim+uppercase 正規化＋last-wins 上書き（lastReadAt はテスト決定性のため 0 固定）。
        val normalized = ncode.value.trim().uppercase()
        webReadingProgressState.value =
            webReadingProgressState.value.filterNot { it.ncode == normalized } +
                WebReadingProgressEntity(normalized, episode, lastReadAt = 0L)
    }

    override suspend fun getWebReadingProgress(ncode: Ncode): WebReadingProgressEntity? {
        val normalized = ncode.value.trim().uppercase()
        return webReadingProgressState.value.firstOrNull { it.ncode == normalized }
    }

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
        overwrite: Boolean,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit,
    ): Result<BookRepository.AddBookResult> = addBookResult

    /** addWebBook の戻り値を差し込むためのフック（実ネットワーク/HTML生成を持たないスタブ。addBookResult と同流儀）。 */
    var addWebBookResult: Result<BookRepository.AddBookResult> =
        Result.failure(NotImplementedError("addWebBookResult をテストで設定してください"))

    /** addWebBook 呼び出しの記録（url→overwrite）。上書き確認フロー（確認→overwrite=true 再投入・
     *  キャンセル→再投入なし）を VM テストが呼び出し実績で検証するためのフック。 */
    val addWebBookCalls = mutableListOf<Pair<String, Boolean>>()

    // Web 取込の実挙動検証は DefaultBookRepository 側（AddWebBookTest）で行うため、Fake は結果を返すだけ。
    override suspend fun addWebBook(
        inputUrl: String,
        overwrite: Boolean,
        onProgress: ((Int, String) -> Unit)?,
    ): Result<BookRepository.AddBookResult> {
        addWebBookCalls += inputUrl to overwrite
        return addWebBookResult
    }

    override suspend fun addPendingJob(uri: String, displayName: String) {
        pendingJobs[uri] = PendingJobEntity(uri, displayName, pendingJobs.size.toLong())
    }

    override suspend fun getPendingJobs(): List<PendingJobEntity> = pendingJobs.values.toList()

    override suspend fun removePendingJob(uri: String) { pendingJobs.remove(uri) }

    override suspend fun clearPendingJobs() { pendingJobs.clear() }

    override suspend fun cleanOrphanHtmlDirs() { /* Fake はファイルシステムを持たない（no-op） */ }

    override suspend fun sweepOrphanNarouPdfCache(): Int = 0 // Fake はファイルシステムを持たない（no-op）

    override suspend fun releaseOrphanedPermissions(keepUris: Set<String>) { /* 永続権限を持たない（no-op） */ }

    override suspend fun getPersistedSourceUris(): Set<String> =
        booksState.value.mapNotNull { it.sourceUri }.toSet()

    override suspend fun pruneOrphanWebReadingProgress(): Int {
        val keep = booksState.value.mapNotNull { it.ncode?.trim()?.uppercase() }.toSet() +
            webNovelsState.value.map { it.ncode.trim().uppercase() }.toSet()
        val orphans = webReadingProgressState.value.filterNot { it.ncode in keep }
        webReadingProgressState.value = webReadingProgressState.value.filter { it.ncode in keep }
        return orphans.size
    }

    override suspend fun deleteBook(book: BookEntity, deleteSource: Boolean): SourceDeleteOutcome {
        booksState.value = booksState.value.filterNot { it.id == book.id }
        progressState.value = progressState.value.filterNot { it.bookId == book.id }
        // Fake は実ファイル/権限を持たない＝取込元削除は実行不能。結末だけ模す（sourceUri の有無と要求で分岐）。
        return when {
            book.sourceUri == null -> SourceDeleteOutcome.NoSource
            !deleteSource -> SourceDeleteOutcome.NotRequested
            else -> SourceDeleteOutcome.Deleted
        }
    }

    override suspend fun linkNcode(bookId: BookId, ncode: Ncode?) {
        // Fake のインメモリ実体（BookEntity.id）は String のため .value で照合・比較する。
        booksState.value = booksState.value.map {
            if (it.id == bookId.value) it.copy(ncode = ncode?.value) else it
        }
    }

    override suspend fun getLastRead(bookId: BookId): String? =
        progressState.value.firstOrNull { it.bookId == bookId.value }?.lastReadFilename

    override suspend fun getProgress(bookId: BookId): ProgressEntity? =
        progressState.value.firstOrNull { it.bookId == bookId.value }

    // 本番（insertIfAbsent＋updatePosition）と同じく reachedEnd を sticky に保つ＝位置更新で消さない。
    override suspend fun markReachedEnd(bookId: BookId) {
        progressState.value = progressState.value.map {
            if (it.bookId == bookId.value) it.copy(reachedEnd = true) else it
        }
    }

    override suspend fun saveProgress(bookId: BookId, filename: ChapterFilename) {
        upsertProgress(ProgressEntity(bookId.value, filename.value))
    }

    override suspend fun saveScrollPosition(
        bookId: BookId,
        filename: ChapterFilename,
        scrollIndex: Int,
        scrollOffset: Int,
    ) {
        upsertProgress(ProgressEntity(bookId.value, filename.value, scrollIndex, scrollOffset))
    }

    // 同一 bookId は置換（1行モデル）。ただし reachedEnd は既存値を引き継ぐ＝位置更新で読了実績を消さない
    // （本番の updatePosition が reachedEnd 列を touch しないのと同じ挙動を Fake でも再現する）。
    private fun upsertProgress(entity: ProgressEntity) {
        val prev = progressState.value.firstOrNull { it.bookId == entity.bookId }
        val merged = entity.copy(reachedEnd = prev?.reachedEnd ?: entity.reachedEnd)
        progressState.value = progressState.value.filterNot { it.bookId == entity.bookId } + merged
    }
}
