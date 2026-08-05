package com.novelreader.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import com.novelreader.pdf.CorruptedPdfError
import com.novelreader.pdf.EncryptedPdfError
import com.novelreader.pdf.InsufficientStorageError
import com.novelreader.pdf.PdfProgress
import com.novelreader.viewmodel.BookImportError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class BookRepositoryTest {

    private lateinit var bookDao: BookDao
    private lateinit var progressDao: ProgressDao
    private lateinit var pendingJobDao: PendingJobDao
    // Web読書位置の record↔get 往復と ncode 正規化一致を実DBなしで検証するため、保存する Fake を注入する
    // （mockk relaxed は get で常に null を返し往復を観測できないため）。
    private lateinit var webReadingProgressDao: FakeWebReadingProgressDao
    private lateinit var context: Context
    // 実装クラスを直接組み立てる（internal な findExistingBook/classifyError 等を検証するため）。
    // interface BookRepository には出さない実装詳細メソッドなので DefaultBookRepository 型で受ける。
    private lateinit var repository: DefaultBookRepository

    // deleteBook が取込時 cache PDF の相乗り削除で context.cacheDir を読むため、実ディレクトリを stub する。
    // relaxed mockk の cacheDir はモック File を返すが、その内部 path フィールドは null のままで
    // File(parent, child) コンストラクタが NPE になる（mockk はフィールドまでは stub しない）＝実 File が必須。
    private lateinit var testCacheDir: File

    @After
    fun tearDown() {
        testCacheDir.deleteRecursively()
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        testCacheDir = createTempDir(prefix = "repoTestCache")
        every { context.cacheDir } returns testCacheDir
        bookDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        // clamp（上書き/復元後の読書位置丸め）が読む行。relaxed の自動生成 mock でなく「進捗なし」を
        // 既定に固定し、clamp を検証するテストだけが実値を上書きする（AddWebBookTest と同流儀）。
        coEvery { progressDao.getProgress(any()) } returns null
        pendingJobDao = mockk(relaxed = true)
        webReadingProgressDao = FakeWebReadingProgressDao()
        // 検証対象の DAO だけ明示注入する（webNovelDao 等の残りはデフォルト引数のまま＝
        // 本テストが触る経路では呼ばれないので実 Room に落ちても評価されない）。
        // runInTransaction は素通しラムダに差し替える: 本番は AppDatabase.withTransaction で実 Room に依存するため、
        // JVM 単体テストでは block を即実行してトランザクション内の DAO 呼び出しだけを検証する。
        repository = DefaultBookRepository(
            context, bookDao, progressDao, pendingJobDao,
            webReadingProgressDao = webReadingProgressDao,
            runInTransaction = { block -> block() },
        )
    }

    /** record↔get の往復と ncode 正規化一致を検証するためのインメモリ Fake（実 Room 非依存）。
     *  upsert で保存し get で引くだけ＝正規化は Repository 側（trim().uppercase()）の責務なので Fake は素通し。 */
    private class FakeWebReadingProgressDao : WebReadingProgressDao {
        private val store = mutableMapOf<String, WebReadingProgressEntity>()
        override fun getAll(): Flow<List<WebReadingProgressEntity>> = flowOf(store.values.toList())
        override suspend fun get(ncode: String): WebReadingProgressEntity? = store[ncode]
        override suspend fun upsert(progress: WebReadingProgressEntity) { store[progress.ncode] = progress }
        override suspend fun deleteByNcode(ncode: String) { store.remove(ncode) }
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
        repository.deleteBook(book, deleteSource = false)
        coVerify { bookDao.deleteById("id01") }
        coVerify { progressDao.deleteByBookId("id01") }
    }

    @Test
    fun `getLastRead - progressDao の戻り値をそのまま返す`() = runTest {
        coEvery { progressDao.getLastRead("id01") } returns "chapter_01.html"
        val result = repository.getLastRead(BookId("id01"))
        assertEquals("chapter_01.html", result)
    }

    @Test
    fun `getLastRead - 未読の場合は null を返す`() = runTest {
        coEvery { progressDao.getLastRead("id02") } returns null
        val result = repository.getLastRead(BookId("id02"))
        assertNull(result)
    }

    @Test
    fun `saveProgress - 位置列だけを updatePosition で更新する（reachedEnd を消さない）`() = runTest {
        // 全列 REPLACE をやめ insertIfAbsent（新規行作成）＋ updatePosition（位置列のみ更新）の2手で保存する。
        // reachedEnd を touch しない updatePosition が呼ばれること＝『了』印を位置保存で潰さない保証を固定する。
        // lastReadAt は書き込み時刻（System.currentTimeMillis()）で非決定的なため any() で受ける（章移動＝スクロール 0,0）。
        repository.saveProgress(BookId("id01"), ChapterFilename("chapter_02.html"))
        coVerify {
            progressDao.updatePosition("id01", "chapter_02.html", 0, 0, any())
        }
    }

    // ── 処理キューの永続化（pending_jobs）─────────────────────────────────
    // Uri.parse は JVM の android.jar スタブ（returnDefaultValues=true）で null を返すため、
    // Uri を経由するメソッドは mockkStatic で決定的に stub する。

    @Test
    fun `addPendingJob - dao に uri と displayName が記帳される`() = runTest {
        repository.addPendingJob("content://docs/1", "テスト本")
        // enqueuedAt は記帳時刻（System.currentTimeMillis()）で非決定的なため識別子のみ検証
        coVerify {
            pendingJobDao.insert(match { it.uri == "content://docs/1" && it.displayName == "テスト本" })
        }
    }

    @Test
    fun `getPendingJobs - dao の戻り値をそのまま返す`() = runTest {
        val jobs = listOf(PendingJobEntity("content://docs/1", "本A", 1L))
        coEvery { pendingJobDao.getAll() } returns jobs
        assertEquals(jobs, repository.getPendingJobs())
    }

    @Test
    fun `removePendingJob - 記帳の削除と永続権限の解放が行われる`() = runTest {
        mockkStatic(Uri::class)
        try {
            val uri = mockk<Uri>(relaxed = true)
            // settlePendingJob は Uri→String の round-trip（pdfUri.toString()）で deleteByUri を
            // 呼ぶため、relaxed 既定の "Uri(#N)" ではなく実文字列を返すよう stub する
            every { uri.toString() } returns "content://docs/1"
            every { Uri.parse("content://docs/1") } returns uri
            repository.removePendingJob("content://docs/1")
            coVerify { pendingJobDao.deleteByUri("content://docs/1") }
            coVerify { context.contentResolver.releasePersistableUriPermission(uri, any()) }
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `clearPendingJobs - 全行の権限解放後に deleteAll される`() = runTest {
        mockkStatic(Uri::class)
        try {
            val uri1 = mockk<Uri>(relaxed = true)
            val uri2 = mockk<Uri>(relaxed = true)
            every { Uri.parse("content://docs/1") } returns uri1
            every { Uri.parse("content://docs/2") } returns uri2
            coEvery { pendingJobDao.getAll() } returns listOf(
                PendingJobEntity("content://docs/1", "本A", 1L),
                PendingJobEntity("content://docs/2", "本B", 2L),
            )
            repository.clearPendingJobs()
            coVerify { context.contentResolver.releasePersistableUriPermission(uri1, any()) }
            coVerify { context.contentResolver.releasePersistableUriPermission(uri2, any()) }
            coVerify { pendingJobDao.deleteAll() }
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    // ── べき等ガード（同一PDF二重取込・UX監査 F-G 公理3）───────────────────
    // 抽出後のタイトル＋著者で既存蔵書を照合する純判定（addBook から切り出し）。

    @Test
    fun `findExistingBook - 一致する蔵書があればそれを返す`() = runTest {
        val existing = BookEntity("id01", "タイトルA", "/path/a", "著者A")
        coEvery { bookDao.findByTitleAndAuthor("タイトルA", "著者A") } returns existing
        assertEquals(existing, repository.findExistingBook("タイトルA", "著者A"))
    }

    @Test
    fun `findExistingBook - 一致が無ければ null を返す`() = runTest {
        coEvery { bookDao.findByTitleAndAuthor(any(), any()) } returns null
        assertNull(repository.findExistingBook("未登録タイトル", "著者X"))
    }

    // ── 内容ハッシュによる二重変換の変換前遮断（F-G 恒久策）─────────────────────
    // 別 URI・同内容の再取込を「抽出前」に弾くため、PDF バイト列の SHA-256 で既存蔵書を照合する
    // 純判定（addBook の①' から切り出し）。ここが「別URI・同内容 → 変換前に遮断」の判定核。

    @Test
    fun `findExistingBookByHash - 同一ハッシュの蔵書があれば変換前に遮断できる`() = runTest {
        // 別パス（別URI）から取り込んだが中身が同一の PDF ＝ 同じ contentSha256 を持つ既存蔵書がヒットする。
        val existing = BookEntity("id01", "タイトルA", "/path/a", "著者A", contentSha256 = "deadbeef")
        coEvery { bookDao.findByContentSha256("deadbeef") } returns existing
        assertEquals(existing, repository.findExistingBookByHash("deadbeef"))
    }

    @Test
    fun `findExistingBookByHash - 一致が無ければ null（新規として変換に進む）`() = runTest {
        coEvery { bookDao.findByContentSha256(any()) } returns null
        assertNull(repository.findExistingBookByHash("0011223344"))
    }

    // ── SHA-256 ハッシュ計算（内容指紋の純関数）───────────────────────────────
    // ストリーミングで digest を確定する。既知テストベクタで正しさを固定する。

    @Test
    fun `sha256Hex - 空入力は SHA-256 の既知ベクタを返す`() {
        // SHA-256("") = e3b0c442...b855（RFC/NIST 既知ベクタ）
        val hex = sha256Hex(byteArrayOf().inputStream())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex)
    }

    @Test
    fun `sha256Hex - abc は SHA-256 の既知ベクタを返す（バッファ境界跨ぎの担保も兼ねる）`() {
        // SHA-256("abc") = ba7816bf...15ad（NIST 既知ベクタ）。小文字16進・64桁で返ること。
        val hex = sha256Hex("abc".toByteArray(Charsets.US_ASCII).inputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
    }

    // ── 孤立HTML掃除 ──────────────────────────────────────────────────────

    @Test
    fun `cleanOrphanHtmlDirs - books に無い bookId のディレクトリだけ削除される`() = runTest {
        // 実ファイルシステム（一時ディレクトリ）で突合ロジックを end-to-end に検証する
        val filesDir = createTempDir(prefix = "cleanOrphanTest")
        try {
            val novels = java.io.File(filesDir, "novels")
            val kept = java.io.File(novels, "aaa11111").apply { mkdirs(); resolve("index.html").writeText("x") }
            val orphan = java.io.File(novels, "bbb22222").apply { mkdirs(); resolve("chap_1.html").writeText("x") }
            every { context.filesDir } returns filesDir
            coEvery { bookDao.getAllBookIds() } returns listOf("aaa11111")

            repository.cleanOrphanHtmlDirs()

            assertTrue("DB に在る本のディレクトリは残る", kept.exists())
            assertFalse("DB に無い書きかけディレクトリは消える", orphan.exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanOrphanHtmlDirs - novels ディレクトリ不在でも例外にならない`() = runTest {
        val filesDir = createTempDir(prefix = "cleanOrphanEmpty")
        try {
            every { context.filesDir } returns filesDir
            coEvery { bookDao.getAllBookIds() } returns emptyList()
            repository.cleanOrphanHtmlDirs() // listFiles() が null を返す経路（?. で素通り）
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── 失敗取込の権限リーク回収（孤児の永続 URI 権限判定）─────────────────────
    // 取込失敗時は M7 再試行のため pending_jobs 行だけ消し永続権限を残す。再試行されず終わった分が
    // 次回起動時に「pending_jobs 非紐付け」の孤児として残るのを、この純関数が差集合で回収対象に選ぶ。

    @Test
    fun `orphanedPermissionUris - pending 非紐付けの権限だけを回収対象にする`() {
        val persisted = setOf("content://docs/failed", "content://docs/resuming")
        val keep = setOf("content://docs/resuming") // 再開待ちで pending_jobs に残る URI
        // 失敗して再試行されなかった URI だけが孤児として解放対象になる
        assertEquals(setOf("content://docs/failed"), orphanedPermissionUris(persisted, keep))
    }

    @Test
    fun `orphanedPermissionUris - pending が空なら全ての永続権限が孤児`() {
        // リークの典型形: pending_jobs 行ゼロ＋失敗取込の権限だけが残ったケース
        val persisted = setOf("content://docs/1", "content://docs/2")
        assertEquals(persisted, orphanedPermissionUris(persisted, emptySet()))
    }

    @Test
    fun `orphanedPermissionUris - 全て pending 紐付けなら回収対象なし（誤解放しない）`() {
        // 再開対象の権限を誤って解放しないことの回帰: keep に全て含まれれば差集合は空
        val persisted = setOf("content://docs/a", "content://docs/b")
        assertTrue(orphanedPermissionUris(persisted, persisted).isEmpty())
    }

    // ── Web読書位置の ncode 正規化（record↔get 往復一致）───────────────────────
    // record と get の ncode を trim().uppercase() で揃えないと「続きから」が空振りする load-bearing ロジック。
    // 参照側 ShelfItems（webReadingProgress[n.ncode.trim().uppercase()]）と同じ正規化であることを回帰で固定する。

    @Test
    fun `recordWebReadingEpisode→getWebReadingProgress - 同じ ncode で往復一致する`() = runTest {
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 7)
        val got = repository.getWebReadingProgress(Ncode("N1234AB"))
        assertEquals(7, got?.lastReadEpisode)
    }

    @Test
    fun `recordWebReadingEpisode→getWebReadingProgress - 大小文字・空白ゆらぎでも正規化一致で引ける`() = runTest {
        // 記録は小文字、照会は大文字＋前後空白＝ncode の表記ゆれ。両者 trim().uppercase() で同一キーになり一致する
        // （この一致が崩れると「読んだのに続きから読むが出ない」空振りになる）。
        repository.recordWebReadingEpisode(Ncode("n1234ab"), 3)
        val got = repository.getWebReadingProgress(Ncode("  N1234AB  "))
        assertEquals(3, got?.lastReadEpisode)
        // 保存キー自体が正規化済み（ShelfItems 参照側の trim().uppercase() と一致する形）であることも確認:
        // 未正規化キーではヒットせず、正規化キーでのみ引ける＝保存時に正規化されている証明。
        assertNull("未正規化キーではヒットしない（保存時正規化の証明）", webReadingProgressDao.get("n1234ab"))
        assertEquals(3, webReadingProgressDao.get("N1234AB")?.lastReadEpisode)
    }

    // ── Web読書位置の furthest-wins（参照ジャンプで先端が後退しない）───────────────────
    // 目次から前の話を新規リンクで開いて退出しても、到達済み最遠話より前進しない記録は無視する
    // （UX監査 continuity・公理14/公理6）。task_diary #56 の reachedByBack ガードが取り逃す
    // 「前方への新規ロードで小さい話数」経路を、記録の単一集約点で塞ぐことの回帰。

    @Test
    fun `recordWebReadingEpisode - 前進した話数は更新される`() = runTest {
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 10)
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 51)
        assertEquals(51, repository.getWebReadingProgress(Ncode("N1234AB"))?.lastReadEpisode)
    }

    @Test
    fun `recordWebReadingEpisode - 後退（目次から前の話を確認）では先端が保たれる`() = runTest {
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 51)
        // 目次から第10話を確認しに開いた＝より小さい話数の記録要求。先端51は後退させない。
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 10)
        assertEquals(51, repository.getWebReadingProgress(Ncode("N1234AB"))?.lastReadEpisode)
    }

    @Test
    fun `recordWebReadingEpisode - 同じ話数の再記録は先端を変えない`() = runTest {
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 20)
        repository.recordWebReadingEpisode(Ncode("N1234AB"), 20)
        assertEquals(20, repository.getWebReadingProgress(Ncode("N1234AB"))?.lastReadEpisode)
    }

    @Test
    fun `recordWebReadingEpisode - 初回記録は無条件に挿入される`() = runTest {
        repository.recordWebReadingEpisode(Ncode("N9999ZZ"), 5)
        assertEquals(5, repository.getWebReadingProgress(Ncode("N9999ZZ"))?.lastReadEpisode)
    }

    // ── Web読書位置の削除経路（UX監査 privacy・削除の完全性）──────────────────────────
    // 本削除/カード除去で履歴が相乗り削除されること・ただし他参照が残るなら保持されること・
    // 起動時 orphan 掃除で完全化されることを固定する。webNovelDao は既定が実 Room のため各テストで注入する。

    /** books/web_novels の getAll を任意にスタブした webNovelDao/bookDao を注入したリポジトリを組む。 */
    private fun repoWith(
        books: List<BookEntity>,
        webNovels: List<WebNovelEntity>,
        webProgressDao: FakeWebReadingProgressDao,
    ): DefaultBookRepository {
        val localBookDao = mockk<BookDao>(relaxed = true)
        val localWebNovelDao = mockk<WebNovelDao>(relaxed = true)
        every { localBookDao.getAllBooks() } returns flowOf(books)
        every { localWebNovelDao.getAll() } returns flowOf(webNovels)
        return DefaultBookRepository(
            context, localBookDao, progressDao, pendingJobDao,
            webNovelDao = localWebNovelDao,
            webReadingProgressDao = webProgressDao,
            runInTransaction = { block -> block() },
        )
    }

    @Test
    fun `removeWebNovel - 他参照が無ければ Web読書位置も相乗り削除される`() = runTest {
        val dao = FakeWebReadingProgressDao()
        dao.upsert(WebReadingProgressEntity("N1234AB", 12, 0L))
        val repo = repoWith(books = emptyList(), webNovels = emptyList(), webProgressDao = dao)
        repo.removeWebNovel(Ncode("N1234AB"))
        assertNull("カードを外したら位置履歴も消える", dao.get("N1234AB"))
    }

    @Test
    fun `removeWebNovel - 同 ncode を紐付けた蔵書が残れば位置履歴は保持される`() = runTest {
        val dao = FakeWebReadingProgressDao()
        dao.upsert(WebReadingProgressEntity("N1234AB", 12, 0L))
        // まだ ncode=N1234AB を紐付けた PDF 蔵書が棚に在る＝「続きから」に要るので消さない。
        val repo = repoWith(
            books = listOf(BookEntity("id01", "本A", "/p", "著A", ncode = "N1234AB")),
            webNovels = emptyList(),
            webProgressDao = dao,
        )
        repo.removeWebNovel(Ncode("N1234AB"))
        assertEquals(12, dao.get("N1234AB")?.lastReadEpisode)
    }

    @Test
    fun `deleteBook - 紐付いた Web読書位置も他参照が無ければ相乗り削除される`() = runTest {
        val dao = FakeWebReadingProgressDao()
        dao.upsert(WebReadingProgressEntity("N1234AB", 8, 0L))
        val book = BookEntity("id01", "本A", "/nonexistent/path", "著A", ncode = "N1234AB")
        // deleteBook 後の snapshot（books/web_novels とも空）で未参照と判定される。
        val repo = repoWith(books = emptyList(), webNovels = emptyList(), webProgressDao = dao)
        repo.deleteBook(book, deleteSource = false)
        assertNull(dao.get("N1234AB"))
    }

    // ── 取込時 cache PDF の相乗り削除（cache/pdf_import/<ncode>.pdf・2026-08-05）──────────
    // 「いつ消すか」の設計正本＝LibraryDeleter.deleteBook のコメント。ここはその契約
    // （最後の1冊で消える／同 ncode の本が残る間は AutoCachePdf の復旧資源として残る）を固定する。

    @Test
    fun `deleteBook - 同 ncode の最後の1冊を消すと取込時cache PDF も相乗り削除される`() = runTest {
        val cacheDir = createTempDir(prefix = "narouCache")
        try {
            every { context.cacheDir } returns cacheDir
            val pdf = File(NarouPdfCache.dir(cacheDir), "n1234ab.pdf").apply {
                parentFile!!.mkdirs(); writeText("pdf")
            }
            val book = BookEntity("id01", "本A", "/nonexistent/path", "著A", ncode = "N1234AB")
            val repo = repoWith(books = emptyList(), webNovels = emptyList(), webProgressDao = FakeWebReadingProgressDao())
            repo.deleteBook(book, deleteSource = false)
            assertFalse("残骸を残さない（実機で数十MB/冊の堆積を実測）", pdf.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `deleteBook - 同 ncode の蔵書が残る間は cache PDF を残す（AutoCachePdf の復旧資源）`() = runTest {
        val cacheDir = createTempDir(prefix = "narouCacheKeep")
        try {
            every { context.cacheDir } returns cacheDir
            val pdf = File(NarouPdfCache.dir(cacheDir), "n1234ab.pdf").apply {
                parentFile!!.mkdirs(); writeText("pdf")
            }
            val book = BookEntity("id01", "本A", "/nonexistent/path", "著A", ncode = "N1234AB")
            val survivor = BookEntity("id02", "本A'", "/nonexistent/path2", "著A", ncode = "n1234ab") // 表記ゆれでも同一作品
            val repo = repoWith(books = listOf(survivor), webNovels = emptyList(), webProgressDao = FakeWebReadingProgressDao())
            repo.deleteBook(book, deleteSource = false)
            assertTrue("残る本の唯一の復旧資源＝消してはならない", pdf.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `sweepOrphanNarouPdfCache - 蔵書非対応かつ pending 非参照の cache PDF だけ回収する`() = runTest {
        val cacheDir = createTempDir(prefix = "narouCacheSweep")
        mockkStatic(Uri::class)
        try {
            every { context.cacheDir } returns cacheDir
            val dir = NarouPdfCache.dir(cacheDir).apply { mkdirs() }
            val kept = File(dir, "n1234ab.pdf").apply { writeText("pdf") }     // 蔵書あり
            val pending = File(dir, "n7777xx.pdf").apply { writeText("pdf") }  // 再開待ち DL 実体
            val orphan = File(dir, "n9999zz.pdf").apply { writeText("pdf") }   // 残骸
            val pendingUriStr = "content://com.novelreader.fileprovider/pdf_import/n7777xx.pdf"
            coEvery { pendingJobDao.getAll() } returns listOf(PendingJobEntity(pendingUriStr, "n7777xx.pdf", 0L))
            val pendingUri = mockk<Uri>(relaxed = true)
            every { Uri.parse(pendingUriStr) } returns pendingUri
            every { pendingUri.lastPathSegment } returns "n7777xx.pdf"
            val repo = repoWith(
                books = listOf(BookEntity("id01", "本A", "/p", "著A", ncode = "N1234AB")),
                webNovels = emptyList(), webProgressDao = FakeWebReadingProgressDao(),
            )
            assertEquals(1, repo.sweepOrphanNarouPdfCache())
            assertTrue(kept.exists())
            assertTrue("再開予定の DL 実体を消すと変換の再開が壊れる", pending.exists())
            assertFalse(orphan.exists())
        } finally {
            unmockkStatic(Uri::class)
            cacheDir.deleteRecursively()
        }
    }

    // ── 取込元PDF削除（deleteSource）─────────────────────────────────────
    // 本削除時に取込元 PDF 本体（SAF ドキュメント）も消すオプション。sourceUri を持つ本だけが対象。
    // DocumentsContract.deleteDocument / Uri は static のため mockkStatic で決定的に stub する。

    @Test
    fun `deleteBook - deleteSource=true かつ取込元URIあり＝deleteDocument 実行し Deleted・権限も解放`() = runTest {
        mockkStatic(Uri::class, DocumentsContract::class)
        try {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns "content://docs/src1"
            every { Uri.parse("content://docs/src1") } returns uri
            every { DocumentsContract.deleteDocument(any(), uri) } returns true
            val book = BookEntity("id01", "本A", "/nonexistent/path", sourceUri = "content://docs/src1")

            val outcome = repository.deleteBook(book, deleteSource = true)

            assertEquals(SourceDeleteOutcome.Deleted, outcome)
            coVerify { DocumentsContract.deleteDocument(any(), uri) }
            // 本が消えた＝取込元 URI 権限は保持不要。削除の後に解放される。
            coVerify { context.contentResolver.releasePersistableUriPermission(uri, any()) }
        } finally {
            unmockkStatic(Uri::class, DocumentsContract::class)
        }
    }

    @Test
    fun `deleteBook - deleteSource=true でも deleteDocument が false なら Failed（権限は解放）`() = runTest {
        mockkStatic(Uri::class, DocumentsContract::class)
        try {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns "content://docs/src2"
            every { Uri.parse("content://docs/src2") } returns uri
            // 既に移動/削除済み等でプロバイダが false（or 例外）を返すケース。runCatching で吸収し Failed。
            every { DocumentsContract.deleteDocument(any(), uri) } returns false
            val book = BookEntity("id02", "本B", "/nonexistent/path", sourceUri = "content://docs/src2")

            val outcome = repository.deleteBook(book, deleteSource = true)

            assertEquals(SourceDeleteOutcome.Failed, outcome)
            coVerify { context.contentResolver.releasePersistableUriPermission(uri, any()) }
        } finally {
            unmockkStatic(Uri::class, DocumentsContract::class)
        }
    }

    @Test
    fun `deleteBook - deleteSource=false は取込元を消さず権限だけ解放（NotRequested）`() = runTest {
        mockkStatic(Uri::class, DocumentsContract::class)
        try {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns "content://docs/src3"
            every { Uri.parse("content://docs/src3") } returns uri
            val book = BookEntity("id03", "本C", "/nonexistent/path", sourceUri = "content://docs/src3")

            val outcome = repository.deleteBook(book, deleteSource = false)

            assertEquals(SourceDeleteOutcome.NotRequested, outcome)
            coVerify(exactly = 0) { DocumentsContract.deleteDocument(any(), any()) }
            // 本削除に伴い権限は解放（本が消えれば取込元権限の保持は不要＝孤児化を防ぐ）。
            coVerify { context.contentResolver.releasePersistableUriPermission(uri, any()) }
        } finally {
            unmockkStatic(Uri::class, DocumentsContract::class)
        }
    }

    @Test
    fun `deleteBook - sourceUri が null なら取込元削除は一切しない（NoSource）`() = runTest {
        mockkStatic(DocumentsContract::class)
        try {
            val book = BookEntity("id04", "本D", "/nonexistent/path") // sourceUri=null（既定）
            val outcome = repository.deleteBook(book, deleteSource = true)
            assertEquals(SourceDeleteOutcome.NoSource, outcome)
            coVerify(exactly = 0) { DocumentsContract.deleteDocument(any(), any()) }
        } finally {
            unmockkStatic(DocumentsContract::class)
        }
    }

    @Test
    fun `getPersistedSourceUris - dao の返す一覧を Set で返す（重複は畳む）`() = runTest {
        coEvery { bookDao.getPersistedSourceUris() } returns listOf("content://a", "content://b", "content://a")
        assertEquals(setOf("content://a", "content://b"), repository.getPersistedSourceUris())
    }

    @Test
    fun `pruneOrphanWebReadingProgress - 棚に紐付かない履歴だけ回収する`() = runTest {
        val dao = FakeWebReadingProgressDao()
        dao.upsert(WebReadingProgressEntity("N0001AA", 3, 0L)) // 孤児（どこからも参照されない）
        dao.upsert(WebReadingProgressEntity("N0002BB", 5, 0L)) // 蔵書が紐付け
        dao.upsert(WebReadingProgressEntity("N0003CC", 7, 0L)) // Webカードが在る
        val repo = repoWith(
            books = listOf(BookEntity("id01", "本B", "/p", "著B", ncode = "N0002BB")),
            webNovels = listOf(WebNovelEntity("N0003CC", "作品C", "著C", 7, 0L)),
            webProgressDao = dao,
        )
        val pruned = repo.pruneOrphanWebReadingProgress()
        assertEquals(1, pruned)
        assertNull("孤児は消える", dao.get("N0001AA"))
        assertEquals(5, dao.get("N0002BB")?.lastReadEpisode)
        assertEquals(7, dao.get("N0003CC")?.lastReadEpisode)
    }

    @Test
    fun `orphanedWebProgressNcodes - keep に無い ncode だけ回収対象`() {
        assertEquals(
            setOf("N0001AA"),
            orphanedWebProgressNcodes(setOf("N0001AA", "N0002BB"), setOf("N0002BB")),
        )
    }

    @Test
    fun `orphanedWebProgressNcodes - 保存契約違反の過去行（小文字・空白）も storageKey 突合で生存判定する`() {
        // 2026-07-27 是正の固定: keep は storageKey 形・all は DB 生値。無正規化の差集合だと
        // " n0002bb " は「生きている本の読書位置」なのに孤児扱いで削除されてしまう。
        assertEquals(
            setOf("N0001AA"),
            orphanedWebProgressNcodes(setOf("N0001AA", " n0002bb "), setOf("N0002BB")),
        )
    }

    @Test
    fun `orphanedWebProgressNcodes - 孤児の戻り値は削除キーに使う生値のまま`() {
        assertEquals(
            setOf(" n0009zz "),
            orphanedWebProgressNcodes(setOf(" n0009zz ", "N0002BB"), setOf("N0002BB")),
        )
    }

    // ── 取込前の空き容量チェック（UX監査 add・10-H）────────────────────────────
    // 必要見込み = max(pdfSize×係数, フロア) を空きが下回れば false（＝変換に入らず容量不足エラーへ）。

    @Test
    fun `hasEnoughStorageFor - 空きが概算所要以上なら true`() {
        val pdf = 10L * 1024 * 1024 // 10MiB
        // 必要見込み = max(10MiB×2, 8MiB) = 20MiB。ちょうど 20MiB 空きなら足りる。
        assertTrue(hasEnoughStorageFor(usableBytes = 20L * 1024 * 1024, pdfSizeBytes = pdf))
    }

    @Test
    fun `hasEnoughStorageFor - 空きが概算所要を下回れば false`() {
        val pdf = 10L * 1024 * 1024
        assertFalse(hasEnoughStorageFor(usableBytes = 19L * 1024 * 1024, pdfSizeBytes = pdf))
    }

    @Test
    fun `hasEnoughStorageFor - 極小PDFでも最低フロア(8MiB)は要求する`() {
        // pdfSize×2 = 2KiB でもフロア 8MiB が下限。7MiB 空きでは false。
        assertFalse(hasEnoughStorageFor(usableBytes = 7L * 1024 * 1024, pdfSizeBytes = 1024L))
        assertTrue(hasEnoughStorageFor(usableBytes = 8L * 1024 * 1024, pdfSizeBytes = 1024L))
    }

    // ── 破損PDFの隔離（UX監査 measure・C表#8 interlock）─────────────────────────
    // 抽出が例外を投げる fake engine を注入し、破損PDFが「本棚に出ない・書きかけHTML削除・pending削除」で
    // 隔離される（現行データ無傷）ことを repository 層で assert する。addBook が engine 差替可能になったことの回帰。

    @Test
    fun `addBook - 破損PDFは隔離される（未insert・書きかけ削除・pending削除）`() = runTest {
        val filesDir = createTempDir(prefix = "addBookFiles")
        val cacheDir = createTempDir(prefix = "addBookCache")
        try {
            every { context.filesDir } returns filesDir
            every { context.cacheDir } returns cacheDir
            val pdfUri = mockk<Uri>(relaxed = true)
            every { pdfUri.toString() } returns "content://docs/corrupt"
            every { context.contentResolver.openInputStream(pdfUri) } returns
                ByteArrayInputStream("dummy pdf bytes".toByteArray())
            // 内容ハッシュ照合は不一致（新規として抽出に進む）。抽出で破損例外を投げる。
            coEvery { bookDao.findByContentSha256(any()) } returns null

            val throwingExtract: (File, String, File, PdfProgress) -> BookMeta =
                { _, _, _, _ -> throw CorruptedPdfError("bad structure") }
            val repo = DefaultBookRepository(
                context, bookDao, progressDao, pendingJobDao,
                webReadingProgressDao = FakeWebReadingProgressDao(),
                runInTransaction = { block -> block() },
                extractBook = throwingExtract,
            )

            val result = repo.addBook(pdfUri)

            assertTrue("破損は失敗として返る", result.isFailure)
            assertTrue(
                "容量不足でなく破損として分類される",
                result.exceptionOrNull() is BookImportError.CorruptedPdf,
            )
            // 書きかけ HTML ディレクトリが残らない（隔離＝孤立本を棚に残さない）
            val novels = File(filesDir, "novels")
            assertTrue("書きかけHTMLディレクトリは残らない", novels.listFiles().isNullOrEmpty())
            // 本棚に出ない: insertBook は一度も呼ばれない
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
            // 永続キューの記帳は落とす（M7 再試行のため権限は残す＝settle でなく deleteByUri のみ）
            coVerify { pendingJobDao.deleteByUri("content://docs/corrupt") }
        } finally {
            filesDir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }

    // ── 本文欠落→再取込の復元モード（2026-07-29 案B/C）──────────────────────────────
    // 契約: 既存行を保持し本文だけ再生成（id 不変・insertBook を呼ばない・進捗 DAO に触れない＝
    // 読書位置/栞/読了/追加日が残る）。本文が実在する既存本は従来どおり Duplicate（挙動不変の回帰）。

    /** 復元テスト共通の repo 組み立て: 抽出 fake は outputDir へ index.html を書き、渡された bookId を記録する。 */
    private fun restoreRepoWith(
        extractedIds: MutableList<String>,
        meta: BookMeta = BookMeta("復元本", "著者R"),
    ): DefaultBookRepository {
        val fakeExtract: (File, String, File, PdfProgress) -> BookMeta = { _, bookId, outputDir, _ ->
            extractedIds.add(bookId)
            outputDir.mkdirs()
            File(outputDir, "index.html").writeText("<html>restored</html>")
            meta
        }
        return DefaultBookRepository(
            context, bookDao, progressDao, pendingJobDao,
            webReadingProgressDao = FakeWebReadingProgressDao(),
            runInTransaction = { block -> block() },
            extractBook = fakeExtract,
        )
    }

    @Test
    fun `addBook - ハッシュ一致でも本文欠落なら Duplicate にせず既存行へ復元する（id 不変・進捗無傷）`() = runTest {
        val filesDir = createTempDir(prefix = "restoreFiles")
        val cacheDir = createTempDir(prefix = "restoreCache")
        try {
            every { context.filesDir } returns filesDir
            every { context.cacheDir } returns cacheDir
            val pdfUri = mockk<Uri>(relaxed = true)
            every { pdfUri.toString() } returns "content://docs/restore1"
            val pdfBytes = "restore pdf bytes".toByteArray()
            every { context.contentResolver.openInputStream(pdfUri) } returns ByteArrayInputStream(pdfBytes)
            val hash = sha256Hex(ByteArrayInputStream(pdfBytes))

            // 既存行: 本文実体なし（ディレクトリ不存在）＝実機で起きた Auto Backup 後の姿。
            val existing = BookEntity(
                "olde1234", "復元本", File(filesDir, "novels/olde1234").absolutePath, "著者R",
                addedAt = 123L, contentSha256 = hash, shioriTipIndex = 3, shioriLenFrac = 0.4f,
                sourceUri = "content://docs/original",
            )
            coEvery { bookDao.findByContentSha256(hash) } returns existing

            val extractedIds = mutableListOf<String>()
            val result = restoreRepoWith(extractedIds).addBook(pdfUri)

            val added = result.getOrThrow() as BookRepository.AddBookResult.Added
            assertTrue("復元フラグが立つ", added.restored)
            assertEquals("id は不変＝重複行を作らない", "olde1234", added.book.id)
            // 本文は既存 id の規約ディレクトリへ再生成される。
            assertTrue(File(filesDir, "novels/olde1234/index.html").exists())
            assertEquals("抽出は既存 id で走る", listOf("olde1234"), extractedIds)
            // 部分 UPDATE のみ＝insertBook（REPLACE＝全列巻き戻しリスク）を通らない。
            // sourceUri は今回権限を取り直していない（persistedUriPermissions 空）ため既存値を温存する。
            coVerify(exactly = 1) {
                bookDao.updateRestoredContent(
                    "olde1234", File(filesDir, "novels/olde1234").absolutePath, hash, "content://docs/original",
                )
            }
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
            // 進捗（読書位置・栞・読了）には一切触れない＝「そのまま残ります」の実装保証。
            coVerify(exactly = 0) { progressDao.deleteByBookId(any()) }
        } finally {
            filesDir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `addBook - ハッシュ一致で本文が実在すれば従来どおり Duplicate（復元は発動しない）`() = runTest {
        val filesDir = createTempDir(prefix = "dupFiles")
        val cacheDir = createTempDir(prefix = "dupCache")
        try {
            every { context.filesDir } returns filesDir
            every { context.cacheDir } returns cacheDir
            val pdfUri = mockk<Uri>(relaxed = true)
            every { pdfUri.toString() } returns "content://docs/dup1"
            val pdfBytes = "dup pdf bytes".toByteArray()
            every { context.contentResolver.openInputStream(pdfUri) } returns ByteArrayInputStream(pdfBytes)
            val hash = sha256Hex(ByteArrayInputStream(pdfBytes))

            // 既存行: 本文実体あり（index.html を実置き）。
            val dir = File(filesDir, "novels/live5678").apply { mkdirs() }
            File(dir, "index.html").writeText("<html>alive</html>")
            val existing = BookEntity("live5678", "生存本", dir.absolutePath, "著", contentSha256 = hash)
            coEvery { bookDao.findByContentSha256(hash) } returns existing

            val extractedIds = mutableListOf<String>()
            val result = restoreRepoWith(extractedIds).addBook(pdfUri)

            val dup = result.getOrThrow() as BookRepository.AddBookResult.Duplicate
            assertEquals(existing, dup.existing)
            assertEquals("重い抽出は走らない（変換前遮断は不変）", emptyList<String>(), extractedIds)
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
            coVerify(exactly = 0) { bookDao.updateRestoredContent(any(), any(), any(), any()) }
        } finally {
            filesDir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }

    // ── 上書きモード（重複拒否の撤廃・2026-08-05 仕様）: overwrite=true は本文が実在しても
    //    Duplicate にせず、既存行を保持したまま再変換で差し替える（Web 経路 AddWebBookTest ②'' の PDF 版）──

    @Test
    fun `addBook - overwrite はハッシュ一致・本文実在でも既存行へ再変換で差し替える（行数不変）`() = runTest {
        val filesDir = createTempDir(prefix = "pdfOverwriteFiles")
        val cacheDir = createTempDir(prefix = "pdfOverwriteCache")
        try {
            every { context.filesDir } returns filesDir
            every { context.cacheDir } returns cacheDir
            val pdfUri = mockk<Uri>(relaxed = true)
            every { pdfUri.toString() } returns "content://docs/ovw1"
            val pdfBytes = "ovw pdf bytes".toByteArray()
            every { context.contentResolver.openInputStream(pdfUri) } returns ByteArrayInputStream(pdfBytes)
            val hash = sha256Hex(ByteArrayInputStream(pdfBytes))

            // 既存行: 本文実体あり＝overwrite=false なら上のテストのとおり Duplicate になる前提。
            val dir = File(filesDir, "novels/live5678").apply { mkdirs() }
            File(dir, "index.html").writeText("<html>alive</html>")
            val existing = BookEntity("live5678", "生存本", dir.absolutePath, "著", contentSha256 = hash)
            coEvery { bookDao.findByContentSha256(hash) } returns existing

            val extractedIds = mutableListOf<String>()
            val result = restoreRepoWith(extractedIds).addBook(pdfUri, overwrite = true)

            val added = result.getOrThrow() as BookRepository.AddBookResult.Added
            assertTrue("上書きは復元経路（既存行保持の部分 UPDATE）を通る", added.restored)
            assertEquals("id は不変＝行数不変（重複行を作らない）", "live5678", added.book.id)
            // 抽出は既存 id で実際に走り、同じ規約ディレクトリへ本文が作り直される。
            assertEquals(listOf("live5678"), extractedIds)
            assertTrue(File(filesDir, "novels/live5678/index.html").exists())
            coVerify(exactly = 1) { bookDao.updateRestoredContent("live5678", dir.absolutePath, hash, any()) }
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
            // 進捗行の削除は通らない＝読書位置/栞/読了/追加日が残る（復元と同じ実装保証）。
            coVerify(exactly = 0) { progressDao.deleteByBookId(any()) }
        } finally {
            filesDir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun `addBook - 旧取込（ハッシュNULL）の欠落本は題名·著者一致で既存行へ復元し規約位置へ移し替える`() = runTest {
        val filesDir = createTempDir(prefix = "titleRestoreFiles")
        val cacheDir = createTempDir(prefix = "titleRestoreCache")
        try {
            every { context.filesDir } returns filesDir
            every { context.cacheDir } returns cacheDir
            val pdfUri = mockk<Uri>(relaxed = true)
            every { pdfUri.toString() } returns "content://docs/restore3"
            val pdfBytes = "old import pdf bytes".toByteArray()
            every { context.contentResolver.openInputStream(pdfUri) } returns ByteArrayInputStream(pdfBytes)

            // 旧取込行: contentSha256 NULL（v11 前）＝ハッシュ遮断を素通りし、抽出後の題名＋著者で合流する。
            coEvery { bookDao.findByContentSha256(any()) } returns null
            val existing = BookEntity(
                "oldnull9", "復元本", File(filesDir, "novels/oldnull9").absolutePath, "著者R",
                contentSha256 = null, sourceUri = null,
            )
            coEvery { bookDao.findByTitleAndAuthor("復元本", "著者R") } returns existing

            val extractedIds = mutableListOf<String>()
            val result = restoreRepoWith(extractedIds).addBook(pdfUri)

            val added = result.getOrThrow() as BookRepository.AddBookResult.Added
            assertTrue(added.restored)
            assertEquals("oldnull9", added.book.id)
            // 新IDで抽出された一式が既存 id の規約位置へ移し替わる（孤立ディレクトリを残さない）。
            assertTrue(File(filesDir, "novels/oldnull9/index.html").exists())
            assertEquals("novels 配下は既存 id の1ディレクトリだけ", listOf("oldnull9"),
                File(filesDir, "novels").listFiles()!!.map { it.name })
            // 旧取込行に今回の内容指紋が初めて焼かれる（以後は変換前遮断が効くようになる）。
            assertEquals(sha256Hex(ByteArrayInputStream(pdfBytes)), added.book.contentSha256)
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
            coVerify(exactly = 1) { bookDao.updateRestoredContent("oldnull9", any(), any(), null) }
        } finally {
            filesDir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }
}
