package com.novelreader.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.novelreader.data.AppDatabase
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.PendingJobDao
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelDao
import com.novelreader.data.WebNovelEntity
import com.novelreader.data.WebReadingProgressDao
import com.novelreader.data.WebReadingProgressEntity
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.model.Ncode
import com.novelreader.pdf.BookMeta
import com.novelreader.pdf.PdfBookExtractor
import com.novelreader.pdf.PdfProgress
import com.novelreader.scrape.SiteAdapterRegistry
import com.novelreader.repository.BookRepository.AddBookResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [BookRepository] の本番実装。Room（AppDatabase の DAO 群）に永続化し、PDFBox でネイティブ抽出する。
 *
 * なぜ interface [BookRepository] と分離したか: 本クラスは AppDatabase.getDatabase（static シングルトン）
 * と PdfProcessingService/PDFBoxResourceLoader の Android 依存を引くため JVM 単体テストで直接使えない。
 * 利用側（Application/ViewModel/Service）を interface 型参照にし、テストでは軽量な FakeBookRepository へ
 * 差し替えられるようにするための実装分離（挙動は従来 BookRepository のまま不変）。
 *
 * 責務分割（2026-07-27 構造リファクタ）: 本クラスに約6責務（PDF取込・Web取込・pending_jobs 永続キュー・
 * 削除/カスケード掃除・読書進捗永続化・薄い DAO 委譲）が同居して789行に肥大したため、責務ごとの協力クラス
 * （[PdfBookImporter]/[WebBookImporter]/[PendingJobStore]/[LibraryDeleter]/[ReadingProgressStore]）へ
 * 委譲抽出した。本クラスは薄いファサードとして残す＝公開シグネチャ（interface・コンストラクタ・
 * internal のテスト継ぎ目）は不変で、呼び出し側と既存テストへの波及を持たない。
 */
class DefaultBookRepository(
    private val context: Context,
    private val bookDao: BookDao = AppDatabase.getDatabase(context).bookDao(),
    private val progressDao: ProgressDao = AppDatabase.getDatabase(context).progressDao(),
    private val pendingJobDao: PendingJobDao = AppDatabase.getDatabase(context).pendingJobDao(),
    private val webNovelDao: WebNovelDao = AppDatabase.getDatabase(context).webNovelDao(),
    private val webReadingProgressDao: WebReadingProgressDao = AppDatabase.getDatabase(context).webReadingProgressDao(),
    // なぜトランザクション実行を関数注入にするか（テスト可能な原子性）:
    // deleteBook の books削除＋progress削除を1トランザクションに束ねて「孤児progress行」を防ぐ。だが本クラスは
    // DAO 個別注入で JVM 単体テストする設計（クラス doc 参照）のため、実 AppDatabase.withTransaction に直接依存すると
    // テストで Room に落ちてしまう。トランザクション境界だけ関数で受け、本番は Room の withTransaction、テストは
    // 素通しラムダ（block を即実行）へ差し替えられるようにする（DAO 分離注入と同じ思想の延長）。
    private val runInTransaction: suspend (block: suspend () -> Unit) -> Unit = { block ->
        AppDatabase.getDatabase(context).withTransaction(block)
    },
    // 抽出の差替継ぎ目（UX監査 measure・破損PDF隔離のテスト可能化）: 本番は PdfBookExtractor.process の
    // 実 PDFBox 経路（engine 固定の public 版）。JVM 単体テストでは例外を投げる fake を注入し、隔離
    // （書きかけ outputDir 削除・BookEntity 未 insert・pending_jobs 行削除）が repository 層で成立することを
    // assert できるようにする。DAO/runInTransaction と同じ「Android 依存を関数で受ける」注入思想の延長。
    private val extractBook: (pdfFile: File, bookId: String, outputDir: File, onProgress: PdfProgress) -> BookMeta =
        { pdfFile, bookId, outputDir, onProgress -> PdfBookExtractor.process(pdfFile, bookId, outputDir, onProgress) },
    // Web 取込のサイト解決（URL→アダプタ）。extractBook/DAO と同じ「Android/ネットワーク依存を注入で差し替える」
    // 流儀: 本番は実アダプタ（KakuyomuAdapter＝OkHttp）を束ねた既定 registry、JVM 単体テストでは固定 TOC/本文を
    // 返す fake アダプタを載せた registry を注入し、実ネットワーク無しで addWebBook 全経路を検証できるようにする。
    private val registry: SiteAdapterRegistry = SiteAdapterRegistry(),
) : BookRepository {

    // ── 協力クラス（責務分割の実体。生成はコンストラクタ注入された依存をそのまま横流し）──────────
    // なぜ協力クラスを外から注入しないか: コンストラクタの公開シグネチャを分割前と不変に保ち、
    // 手書き DI（NovelReaderApplication）と既存テストの生成コードを一切変えないため。テストの差替継ぎ目は
    // 従来どおり DAO/関数の粒度で足りている（協力クラスは同じ注入物から決定的に組み上がる）。
    private val pendingJobs = PendingJobStore(context, pendingJobDao)
    private val pdfImporter = PdfBookImporter(context, bookDao, pendingJobs, extractBook)
    private val webImporter = WebBookImporter(context, bookDao, registry)
    private val libraryDeleter = LibraryDeleter(context, bookDao, progressDao, webNovelDao, webReadingProgressDao, runInTransaction)
    private val progressStore = ReadingProgressStore(progressDao, webReadingProgressDao)

    override val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    override val allProgress: Flow<List<ProgressEntity>> = progressDao.getAllProgress()
    override val webNovels: Flow<List<WebNovelEntity>> = webNovelDao.getAll()

    override suspend fun putWebNovel(novel: WebNovelEntity) = webNovelDao.insert(novel)

    // 実装は LibraryDeleter（storageKey 正規化と相乗り削除の why はそちら）。
    override suspend fun removeWebNovel(ncode: Ncode) = libraryDeleter.removeWebNovel(ncode)

    override val webReadingProgress: Flow<List<WebReadingProgressEntity>> = webReadingProgressDao.getAll()

    // 実装は ReadingProgressStore（storageKey 正規化・furthest-wins の why はそちら）。
    override suspend fun recordWebReadingEpisode(ncode: Ncode, episode: Int) =
        progressStore.recordWebReadingEpisode(ncode, episode)

    override suspend fun getWebReadingProgress(ncode: Ncode): WebReadingProgressEntity? =
        progressStore.getWebReadingProgress(ncode)

    /** べき等ガードの純判定を切り出したもの: 抽出後のタイトル＋著者に一致する既存蔵書を返す
     *  （無ければ null）。実 PDF 抽出を挟まず単体テストできるよう addBook 本体から分離する。 */
    internal suspend fun findExistingBook(title: String, author: String): BookEntity? =
        bookDao.findByTitleAndAuthor(title, author)

    /** 内容ハッシュ照合の純判定を切り出したもの（addBook の「変換前遮断」で使う）。
     *  実 PDF 抽出を挟まず単体テストできるよう addBook 本体から分離する（title＋author 版
     *  findExistingBook と対）。同一 SHA-256 を持つ既存蔵書があれば返す（無ければ null）。 */
    internal suspend fun findExistingBookByHash(contentSha256: String): BookEntity? =
        bookDao.findByContentSha256(contentSha256)

    /** 抽出例外・IO 例外をユーザー向けエラー種別に変換する（実装と分類規則の why は
     *  [PdfBookImporter.classifyError]）。テストが internal API として参照するため facade にも残す。 */
    internal fun classifyError(e: Throwable): Throwable = pdfImporter.classifyError(e)

    // 実装は PdfBookImporter（取込パイプライン各段の why はそちら）。
    override suspend fun addBook(
        pdfUri: Uri,
        ncode: Ncode?,
        onProgress: (step: Int, stepLocalPercent: Float, phase: String, title: String) -> Unit,
    ): Result<AddBookResult> = pdfImporter.addBook(pdfUri, ncode, onProgress)

    // 実装は WebBookImporter（P3 裁定＝pending_jobs を使わない理由などはそちら）。
    override suspend fun addWebBook(
        inputUrl: String,
        onProgress: ((Int, String) -> Unit)?,
    ): Result<AddBookResult> = webImporter.addWebBook(inputUrl, onProgress)

    // ── 処理キューの永続化（pending_jobs）: 実装は PendingJobStore（Mutex 直列化の why はそちら）──
    override suspend fun addPendingJob(uri: String, displayName: String) = pendingJobs.add(uri, displayName)
    override suspend fun getPendingJobs(): List<PendingJobEntity> = pendingJobs.getAll()
    override suspend fun removePendingJob(uri: String) = pendingJobs.remove(uri)
    override suspend fun clearPendingJobs() = pendingJobs.clearAll()
    override suspend fun releaseOrphanedPermissions(keepUris: Set<String>) =
        pendingJobs.releaseOrphanedPermissions(keepUris)

    // ── 削除とカスケード掃除: 実装は LibraryDeleter（原子性・SAF 削除・孤児回収の why はそちら）──
    override suspend fun cleanOrphanHtmlDirs(): Unit = libraryDeleter.cleanOrphanHtmlDirs()
    override suspend fun deleteBook(book: BookEntity, deleteSource: Boolean): SourceDeleteOutcome =
        libraryDeleter.deleteBook(book, deleteSource)
    override suspend fun pruneOrphanWebReadingProgress(): Int = libraryDeleter.pruneOrphanWebReadingProgress()

    override suspend fun getPersistedSourceUris(): Set<String> = withContext(Dispatchers.IO) {
        bookDao.getPersistedSourceUris().toSet()
    }

    // PDF↔Web継続読書: なろう作品との紐付け（null で解除）。ユーザー確定操作からのみ呼ぶ。
    override suspend fun linkNcode(bookId: BookId, ncode: Ncode?) = withContext(Dispatchers.IO) {
        // 境界変換点: Room(BookDao) の bookId/ncode 列は String のまま＝ここで .value へほどく。
        // null（解除）は null のまま渡す。
        bookDao.updateNcode(bookId.value, ncode?.value)
    }

    // ── 読書進捗の永続化: 実装は ReadingProgressStore（2手保存・sticky reachedEnd の why はそちら）──
    override suspend fun getLastRead(bookId: BookId): String? = progressStore.getLastRead(bookId)
    override suspend fun getProgress(bookId: BookId): ProgressEntity? = progressStore.getProgress(bookId)
    override suspend fun markReachedEnd(bookId: BookId) = progressStore.markReachedEnd(bookId)
    override suspend fun saveProgress(bookId: BookId, filename: ChapterFilename) =
        progressStore.saveProgress(bookId, filename)
    override suspend fun saveScrollPosition(
        bookId: BookId,
        filename: ChapterFilename,
        scrollIndex: Int,
        scrollOffset: Int,
    ) = progressStore.saveScrollPosition(bookId, filename, scrollIndex, scrollOffset)
}
