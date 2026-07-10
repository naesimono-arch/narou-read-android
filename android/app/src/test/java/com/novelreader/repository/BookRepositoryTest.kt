package com.novelreader.repository

import android.content.Context
import android.net.Uri
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.BookLabelDao
import com.novelreader.data.BookLabelEntity
import com.novelreader.data.LabelDao
import com.novelreader.data.LabelEntity
import com.novelreader.data.PendingJobDao
import com.novelreader.data.PendingJobEntity
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.pdf.CorruptedPdfError
import com.novelreader.pdf.EncryptedPdfError
import com.novelreader.pdf.InsufficientStorageError
import com.novelreader.viewmodel.BookImportError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BookRepositoryTest {

    private lateinit var bookDao: BookDao
    private lateinit var progressDao: ProgressDao
    private lateinit var pendingJobDao: PendingJobDao
    private lateinit var labelDao: LabelDao
    private lateinit var bookLabelDao: BookLabelDao
    private lateinit var context: Context
    // 実装クラスを直接組み立てる（internal な findExistingBook/classifyError 等を検証するため）。
    // interface BookRepository には出さない実装詳細メソッドなので DefaultBookRepository 型で受ける。
    private lateinit var repository: DefaultBookRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        bookDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        pendingJobDao = mockk(relaxed = true)
        labelDao = mockk(relaxed = true)
        bookLabelDao = mockk(relaxed = true)
        // U2: deleteBook が bookLabelDao.deleteForBook を呼ぶようになったため mock を明示注入する
        // （デフォルト引数は AppDatabase.getDatabase＝実 Room の DAO に落ち、JVM テストで実クエリが走る）。
        repository = DefaultBookRepository(
            context, bookDao, progressDao, pendingJobDao,
            labelDao = labelDao, bookLabelDao = bookLabelDao,
        )
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
        // U2: ラベル付与の junction 掃除も同便で行われる（FK なし設計のアプリ層クリーンアップ）
        coVerify { bookLabelDao.deleteForBook("id01") }
    }

    @Test
    fun `createLabel - assignToBookId 付きなら name 逆引きでその本へ即付与する（同名既存でも付与される）`() = runTest {
        // 同名既存を模す: insert は IGNORE で -1・findByName は既存ラベルを返す
        coEvery { labelDao.insert(any()) } returns -1L
        coEvery { labelDao.findByName("異世界") } returns LabelEntity("l1", "異世界", 0L)

        repository.createLabel("  異世界  ", assignToBookId = "id01")

        // trim された name で逆引きし、既存ラベル l1 への付与 junction が入る
        coVerify { bookLabelDao.insert(BookLabelEntity(bookId = "id01", labelId = "l1")) }
    }

    @Test
    fun `createLabel - 空白のみの名前は何もしない`() = runTest {
        repository.createLabel("   ", assignToBookId = "id01")
        coVerify(exactly = 0) { labelDao.insert(any()) }
        coVerify(exactly = 0) { bookLabelDao.insert(any()) }
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
}
