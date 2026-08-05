package com.novelreader.repository

import android.content.Context
import com.novelreader.data.BookDao
import com.novelreader.data.BookEntity
import com.novelreader.data.PendingJobDao
import com.novelreader.data.ProgressDao
import com.novelreader.data.ProgressEntity
import com.novelreader.data.WebNovelEntity
import com.novelreader.data.WebReadingProgressDao
import com.novelreader.domain.activeWebNovels
import com.novelreader.domain.mergeShelfItems
import com.novelreader.parser.ChapterHtmlParser
import com.novelreader.pdf.RawChapter
import com.novelreader.repository.BookRepository.AddBookResult
import com.novelreader.scrape.HealthProbe
import com.novelreader.scrape.NovelSiteAdapter
import com.novelreader.scrape.ScrapeStructureException
import com.novelreader.scrape.ScrapedChapterRef
import com.novelreader.scrape.ScrapedToc
import com.novelreader.scrape.ScrapedWorkMeta
import com.novelreader.scrape.SiteAdapterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * addWebBook（汎用Web小説DL基盤・P3）の JVM 単体テスト。**実ネットワークを一切張らない**:
 * 固定 TOC/本文を返す [FakeAdapter] を [SiteAdapterRegistry] へ DI し、URL 解決→取得→HTML 生成→登録の
 * 全経路を検証する。HTML の読み戻し契約は既存 [ChapterHtmlParser]（PDF 蔵書と共通）で確認する。
 */
class AddWebBookTest {

    private lateinit var context: Context
    private lateinit var bookDao: BookDao
    private lateinit var progressDao: ProgressDao
    private lateinit var pendingJobDao: PendingJobDao
    private lateinit var webReadingProgressDao: WebReadingProgressDao
    private lateinit var adapter: FakeAdapter

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        bookDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        // clamp（上書き後の読書位置丸め）が読む行。relaxed の自動値でなく「進捗なし」を既定に固定し、
        // clamp 系テストだけが実行値を上書きする（自動生成 mock が返ると clamp 判定が偶発的に走る）。
        coEvery { progressDao.getProgress(any()) } returns null
        pendingJobDao = mockk(relaxed = true)
        webReadingProgressDao = mockk(relaxed = true)
        adapter = FakeAdapter()
    }

    /** BookRepositoryTest と同じ構成流儀（DAO 個別注入・runInTransaction 素通し）に registry DI を足して組む。
     *  webNovelDao は既定のまま＝本テストが触らない経路（Room の遅延 open で実クエリは走らない）。 */
    private fun newRepo(
        registry: SiteAdapterRegistry = SiteAdapterRegistry(adapters = listOf(adapter)),
    ): DefaultBookRepository = DefaultBookRepository(
        context, bookDao, progressDao, pendingJobDao,
        webReadingProgressDao = webReadingProgressDao,
        runInTransaction = { block -> block() },
        registry = registry,
    )

    // ── ① 取込成功: PDF 蔵書と同契約の HTML・BookEntity 各列 ─────────────────────────

    @Test
    fun `addWebBook - 成功で Added を返し HTML と BookEntity 列が揃う`() = runTest {
        val filesDir = createTempDir(prefix = "addWebBookFiles")
        try {
            every { context.filesDir } returns filesDir
            coEvery { bookDao.findBySourceUrl(any()) } returns null // 未登録＝取込に進む

            val progress = mutableListOf<Pair<Int, String>>()
            val result = newRepo().addWebBook(FakeAdapter.WORK_URL) { i, msg -> progress.add(i to msg) }

            assertTrue("Web 取込は成功する", result.isSuccess)
            val added = result.getOrThrow() as AddBookResult.Added
            val book = added.book

            // BookEntity の出所列・ncode・タイトル/著者。
            assertEquals("https://faketest.example/works/1", book.sourceUrl)
            assertEquals("faketest", book.sourceSite)
            assertNull("Web 取込は ncode を持たない", book.ncode)
            assertNull("PDF 削除用の sourceUri は触らない", book.sourceUri)
            assertEquals("テスト作品", book.title)
            assertEquals("テスト著者", book.author)

            // contentSha256 は定義（各章 title + "\n" + body.join("\n") + "\n" の連結の UTF-8 SHA-256）から
            // 独立に組んだ期待値と一致（HTML 変換前の生本文＝中間ルビ記法のままで計算されること）。
            val expectedConcat =
                "第一話 出会い\nこれは|親《よみ》のテスト本文。\n二段落目。\n" +
                "第二話 邂逅\n本文二の一。\n本文二の二。\n" +
                "第三話 再会\n本文三の一。\n"
            assertEquals(sha256Of(expectedConcat), book.contentSha256)

            // 既存 ChapterHtmlParser（PDF 蔵書と共通）で index.html/chap_N.html が読める＝バイト同契約。
            val dir = File(book.htmlDirPath)
            val toc = ChapterHtmlParser.parseToc(File(dir, "index.html"))
            assertEquals(3, toc.size)
            assertEquals("第一話 出会い", toc[0].title)
            assertEquals("chap_1.html", toc[0].fileName)
            for (n in 1..3) {
                val chap = ChapterHtmlParser.parse(File(dir, "chap_$n.html"))
                assertTrue("chap_$n.html が読める", chap != null)
            }
            assertEquals("第一話 出会い", ChapterHtmlParser.parse(File(dir, "chap_1.html"))!!.title)

            coVerify(exactly = 1) { bookDao.insertBook(any()) }
            assertEquals("目次1回だけ取得", 1, adapter.fetchTocCount)
            assertEquals("全3章を取得", 3, adapter.fetchChapterCount)
            // onProgress は章 i/N 粒度で全章ぶん呼ばれる。
            assertEquals(listOf(1 to "章 1/3 取得中", 2 to "章 2/3 取得中", 3 to "章 3/3 取得中"), progress)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── ② 同一 URL 再取込: 重い取得の前に Duplicate（fetch を呼ばない）─────────────────

    @Test
    fun `addWebBook - 既登録の作品URLは fetch せず Duplicate を返す`() = runTest {
        // Duplicate の前提＝「既登録かつ本文実体あり」。復元モード導入（2026-07-29 案B/C）で重複判定が
        // hasContent（index.html 実在）を見るようになったため、fixture は実体ファイルまで作って前提を明示する
        // （旧 fixture は存在しないパス "/p" ＝暗黙に「本文欠落」を表してしまい、復元経路へ流れて意味が変わる）。
        val filesDir = createTempDir(prefix = "webDupFiles")
        try {
            every { context.filesDir } returns filesDir
            val dir = File(filesDir, "novels/id01").apply { mkdirs() }
            File(dir, "index.html").writeText("<html>alive</html>")
            val existing = BookEntity(
                "id01", "既存作品", dir.absolutePath, "既存著者",
                sourceUrl = "https://faketest.example/works/1", sourceSite = "faketest",
            )
            coEvery { bookDao.findBySourceUrl("https://faketest.example/works/1") } returns existing

            val result = newRepo().addWebBook(FakeAdapter.WORK_URL)

            val dup = result.getOrThrow() as AddBookResult.Duplicate
            assertEquals(existing, dup.existing)
            // 重複ガードは目次・本文の取得より前で弾く（相手サイトへ無駄アクセスしない）。
            assertEquals("目次取得は走らない", 0, adapter.fetchTocCount)
            assertEquals("章取得は走らない", 0, adapter.fetchChapterCount)
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── ②' 本文欠落→再取込の復元モード（2026-07-29 案B④/案C）: 既登録でも本文が無ければ再取得して
    //     既存行へ復元する（id 不変・insertBook を呼ばない＝読書位置/栞/読了/追加日が残る）─────────

    @Test
    fun `addWebBook - 既登録でも本文欠落なら Duplicate にせず再取得して既存行へ復元する`() = runTest {
        val filesDir = createTempDir(prefix = "webRestoreFiles")
        try {
            every { context.filesDir } returns filesDir
            // 既存行: 本文実体なし（ディレクトリ不存在）＝Auto Backup が DB のみ復元した姿。
            val existing = BookEntity(
                "webid001", "テスト作品", File(filesDir, "novels/webid001").absolutePath, "テスト著者",
                addedAt = 42L, sourceUrl = FakeAdapter.WORK_URL, sourceSite = "faketest",
            )
            coEvery { bookDao.findBySourceUrl(FakeAdapter.WORK_URL) } returns existing

            val result = newRepo().addWebBook(FakeAdapter.WORK_URL)

            val added = result.getOrThrow() as AddBookResult.Added
            assertTrue("復元フラグが立つ", added.restored)
            assertEquals("id は不変＝重複行を作らない", "webid001", added.book.id)
            assertEquals("出所は不変", FakeAdapter.WORK_URL, added.book.sourceUrl)
            // 再取得は実際に走る（重複ガードの「fetch しない」とは逆＝復元は取得が本体）。
            assertEquals(1, adapter.fetchTocCount)
            assertEquals(3, adapter.fetchChapterCount)
            // 本文は既存 id の規約ディレクトリへ再生成される。
            assertTrue(File(filesDir, "novels/webid001/index.html").exists())
            // 部分 UPDATE のみ＝insertBook（REPLACE＝全列巻き戻しリスク）を通らない。sourceUri は Web 本で常に null。
            coVerify(exactly = 1) { bookDao.updateRestoredContent("webid001", any(), any(), null) }
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── ②'' 上書きモード（重複拒否の撤廃・2026-08-05 仕様）: overwrite=true は本文が生きていても
    //     Duplicate にせず、既存行を保持したまま再取得して本文を差し替える（読書位置/栞/追加日が残る）──

    @Test
    fun `addWebBook - overwrite は本文が生きていても既存行へ再取得で差し替える（行数不変・content 更新）`() = runTest {
        val filesDir = createTempDir(prefix = "webOverwriteFiles")
        try {
            every { context.filesDir } returns filesDir
            // 既存行: 本文実体あり（＝overwrite=false なら Duplicate になる前提を明示する fixture）。
            val dir = File(filesDir, "novels/id01").apply { mkdirs() }
            File(dir, "index.html").writeText("<html>old</html>")
            val existing = BookEntity(
                "id01", "テスト作品", dir.absolutePath, "テスト著者",
                addedAt = 42L, sourceUrl = FakeAdapter.WORK_URL, sourceSite = "faketest",
            )
            coEvery { bookDao.findBySourceUrl(FakeAdapter.WORK_URL) } returns existing

            val result = newRepo().addWebBook(FakeAdapter.WORK_URL, overwrite = true)

            val added = result.getOrThrow() as AddBookResult.Added
            assertTrue("上書きは復元経路（既存行保持の部分 UPDATE）を通る", added.restored)
            assertEquals("id は不変＝行数不変（重複行を作らない）", "id01", added.book.id)
            // 再取得は実際に走り、本文一式が同じ id ディレクトリへ作り直される。
            assertEquals(1, adapter.fetchTocCount)
            assertEquals(3, adapter.fetchChapterCount)
            val toc = ChapterHtmlParser.parseToc(File(dir, "index.html"))
            assertEquals("旧本文が新しい一式（3章）へ置き換わる", 3, toc.size)
            // content（htmlDirPath/contentSha256）は部分 UPDATE で更新・insertBook は通らない
            // ＝progress 行・addedAt・栞列に触れない（読書位置/栞/追加日の保持は UPDATE 対象外性で担保）。
            coVerify(exactly = 1) { bookDao.updateRestoredContent("id01", any(), any(), null) }
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `addWebBook - overwrite で話数が減ったら読書位置を最終章の先頭へ clamp する`() = runTest {
        val filesDir = createTempDir(prefix = "webClampFiles")
        try {
            every { context.filesDir } returns filesDir
            val dir = File(filesDir, "novels/id01").apply { mkdirs() }
            File(dir, "index.html").writeText("<html>old</html>")
            val existing = BookEntity(
                "id01", "テスト作品", dir.absolutePath, "テスト著者",
                sourceUrl = FakeAdapter.WORK_URL, sourceSite = "faketest",
            )
            coEvery { bookDao.findBySourceUrl(FakeAdapter.WORK_URL) } returns existing
            // 旧版で第5話まで読んでいた進捗。新しい一式は3章（FakeAdapter）＝chap_5.html が消える。
            coEvery { progressDao.getProgress("id01") } returns
                ProgressEntity("id01", "chap_5.html", scrollIndex = 7, scrollOffset = 30, lastReadAt = 99L, reachedEnd = true)

            newRepo().addWebBook(FakeAdapter.WORK_URL, overwrite = true).getOrThrow()

            // 最終章（chap_3）の先頭（0,0）へ丸める。lastReadAt は既存値のまま＝「最近読んだ順」を動かさない。
            // reachedEnd は updatePosition が触らないクエリ設計（ProgressDao）なので巻き戻らない。
            coVerify(exactly = 1) { progressDao.updatePosition("id01", "chap_3.html", 0, 0, 99L) }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `addWebBook - overwrite でも読書位置が新章数の範囲内なら進捗に触らない`() = runTest {
        val filesDir = createTempDir(prefix = "webNoClampFiles")
        try {
            every { context.filesDir } returns filesDir
            val dir = File(filesDir, "novels/id01").apply { mkdirs() }
            File(dir, "index.html").writeText("<html>old</html>")
            val existing = BookEntity(
                "id01", "テスト作品", dir.absolutePath, "テスト著者",
                sourceUrl = FakeAdapter.WORK_URL, sourceSite = "faketest",
            )
            coEvery { bookDao.findBySourceUrl(FakeAdapter.WORK_URL) } returns existing
            coEvery { progressDao.getProgress("id01") } returns
                ProgressEntity("id01", "chap_2.html", scrollIndex = 7, scrollOffset = 30, lastReadAt = 99L)

            newRepo().addWebBook(FakeAdapter.WORK_URL, overwrite = true).getOrThrow()

            // 範囲内（新3章の chap_2）は無変更＝スクロール位置も読書順も一切動かさない。
            coVerify(exactly = 0) { progressDao.updatePosition(any(), any(), any(), any(), any()) }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── clamp 判定の純関数（clampedChapterFilename）──────────────────────────────

    @Test
    fun `clampedChapterFilename - 範囲外は最終章・境界と範囲内は null・規約外と章数0も null`() {
        // 範囲外（話数減）→ 最終章のファイル名へ丸める。
        assertEquals("chap_3.html", clampedChapterFilename("chap_5.html", 3))
        // 境界（ちょうど最終章）と範囲内は丸め不要。
        assertNull(clampedChapterFilename("chap_3.html", 3))
        assertNull(clampedChapterFilename("chap_1.html", 3))
        // 規約外のファイル名は判断材料が無い＝安全側で触らない。
        assertNull(clampedChapterFilename("index.html", 3))
        // 章数0（生成失敗級の異常）は丸め先が存在しない＝触らない。
        assertNull(clampedChapterFilename("chap_5.html", 0))
    }

    // ── ③ 規約ゲート: Blocked / Unsupported は Result.failure ────────────────────────

    @Test
    fun `addWebBook - Blocked（なろう）は失敗で返す`() = runTest {
        // ncode.syosetu.com は SiteAdapterRegistry の blockedHosts＝自前DL不可（公式サイト送り）。
        val result = newRepo().addWebBook("https://ncode.syosetu.com/n1234ab/")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("fetch は走らない", 0, adapter.fetchTocCount)
    }

    @Test
    fun `addWebBook - Unsupported（未知サイト）は失敗で返す`() = runTest {
        // どのアダプタにも一致しない未知ホスト＝Unsupported。
        val result = newRepo().addWebBook("https://unknown-site.example/novel/1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, adapter.fetchTocCount)
    }

    // ── ④ ルビ中間記法 → <ruby> 変換（既存 HtmlExporter/ChapterProcessor 経路を通ること）─────

    @Test
    fun `addWebBook - 中間ルビ記法が chap HTML で ruby タグへ変換される`() = runTest {
        val filesDir = createTempDir(prefix = "addWebBookRuby")
        try {
            every { context.filesDir } returns filesDir
            coEvery { bookDao.findBySourceUrl(any()) } returns null

            val book = (newRepo().addWebBook(FakeAdapter.WORK_URL).getOrThrow() as AddBookResult.Added).book
            // base(1文字) と reading(2文字) の長さ不一致ゆえ 1 つの <ruby> にまとまる（applyRuby 仕様）。
            val chap1 = File(book.htmlDirPath, "chap_1.html").readText(Charsets.UTF_8)
            assertTrue(
                "|親《よみ》 が <ruby>親<rt>よみ</rt></ruby> へ変換される",
                chap1.contains("<ruby>親<rt>よみ</rt></ruby>"),
            )
            assertTrue("中間記法のパイプが本文に残らない", !chap1.contains("|親《よみ》"))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    // ── ⑥ 自然昇格の通し固定（2026-07-29 発見バグ）: 「本棚に置く」済みの同一作品を URL 共有で
    //     取り込んでも本棚に2枚並ばない。addWebBook は ncode を推定で書かない（BookEntity.ncode の
    //     人間確定原則・なろう URL は blockedHosts で本経路を通れない＝取込は対応サイト形から行われる）ため、
    //     同定は表示層（ShelfItems の題名＋作者一致昇格）が担う——取込→棚データ供給点の結線をここで固定する。──

    @Test
    fun `addWebBook - 本棚に置いた同一作品を URL 共有取込しても本棚に2枚並ばない（題名・作者で昇格）`() = runTest {
        val filesDir = createTempDir(prefix = "webPromoteFiles")
        try {
            every { context.filesDir } returns filesDir
            coEvery { bookDao.findBySourceUrl(any()) } returns null
            // 挿入される BookEntity を捕捉する（relaxed の既定応答でなく実引数で棚を組むため）。
            val inserted = slot<BookEntity>()
            coEvery { bookDao.insertBook(capture(inserted)) } returns Unit
            // 「本棚に置く」済みの同一作品＝なろう発見面が作った web_novels 行（ncode あり）。
            // 題名・作者は FakeAdapter が返す作品メタと同一＝クロス投稿の同一作品を模す。
            val shelved = WebNovelEntity(
                ncode = "N1234AB", title = "テスト作品", writer = "テスト著者",
                generalAllNo = 3, addedAt = 100,
            )

            val result = newRepo().addWebBook(FakeAdapter.WORK_URL)

            assertTrue(result.isSuccess)
            val book = inserted.captured
            assertNull("ncode は推定で書かない（人間確定原則は不変）", book.ncode)
            // 供給点（activeWebNovels）で web 行が落ち、棚は蔵書カード1枚だけになる。
            val active = activeWebNovels(listOf(book), listOf(shelved))
            assertTrue("同一作品の web 行は正味一覧から落ちる", active.isEmpty())
            val items = mergeShelfItems(listOf(book), emptyMap(), active)
            assertEquals(listOf("book:${book.id}"), items.map { it.key })
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private fun sha256Of(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /**
     * 固定 TOC（3章）＋固定本文を返すテスト用アダプタ（ネットワーク非依存）。
     * canonicalWorkUrl は faketest.example のみ受理し、それ以外は null（＝Registry で Unsupported）。
     * 第1章本文に中間ルビ記法 `|親《よみ》` を1箇所含める（テスト④の変換検証用）。
     */
    private class FakeAdapter : NovelSiteAdapter {
        override val siteKey: String = "faketest"
        override val displayName: String = "テストサイト"

        var fetchTocCount = 0
        var fetchChapterCount = 0

        private val refs = listOf(
            ScrapedChapterRef("第一話 出会い", "$WORK_URL/episodes/1"),
            ScrapedChapterRef("第二話 邂逅", "$WORK_URL/episodes/2"),
            ScrapedChapterRef("第三話 再会", "$WORK_URL/episodes/3"),
        )
        private val bodies = mapOf(
            "$WORK_URL/episodes/1" to
                RawChapter("第一話 出会い", mutableListOf("これは|親《よみ》のテスト本文。", "二段落目。")),
            "$WORK_URL/episodes/2" to
                RawChapter("第二話 邂逅", mutableListOf("本文二の一。", "本文二の二。")),
            "$WORK_URL/episodes/3" to
                RawChapter("第三話 再会", mutableListOf("本文三の一。")),
        )

        override fun canonicalWorkUrl(inputUrl: String): String? {
            val host = runCatching { java.net.URI(inputUrl.trim()).host?.lowercase() }.getOrNull() ?: return null
            return if (host == "faketest.example") WORK_URL else null
        }

        override suspend fun fetchToc(workUrl: String): ScrapedToc {
            fetchTocCount++
            return ScrapedToc(ScrapedWorkMeta("テスト作品", "テスト著者", workUrl), refs)
        }

        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter {
            fetchChapterCount++
            return bodies.getValue(ref.chapterUrl)
        }

        // 破損監視・層3 の自己診断宣言（本テストは probe を実行しないが IF 実装のため必須）。
        override val healthProbe: HealthProbe = HealthProbe(WORK_URL, minChapters = 1)

        companion object {
            const val WORK_URL = "https://faketest.example/works/1"
        }
    }

    // ── ⑤ 破損監視・層1: 空本文は ScrapeStructureException で Result.failure（登録しない）─────────────

    @Test
    fun `addWebBook - 全章空本文は ScrapeStructureException を Result_failure に載せ登録しない`() = runTest {
        val filesDir = createTempDir(prefix = "addWebBookBlank")
        try {
            every { context.filesDir } returns filesDir
            coEvery { bookDao.findBySourceUrl(any()) } returns null

            val result = newRepo(SiteAdapterRegistry(adapters = listOf(BlankBodyAdapter())))
                .addWebBook(BlankBodyAdapter.WORK_URL)

            assertTrue("構造疑いは失敗で返る", result.isFailure)
            // 例外型が呼び出し側（ViewModel）まで保たれ、層2 のフォールバック分岐が効く。
            assertTrue(
                "ScrapeStructureException が保たれる: ${result.exceptionOrNull()}",
                result.exceptionOrNull() is ScrapeStructureException,
            )
            // 破損取込は蔵書に載せない（本棚にゴミ本を残さない）。
            coVerify(exactly = 0) { bookDao.insertBook(any()) }
        } finally {
            filesDir.deleteRecursively()
        }
    }

    /**
     * 目次は正常（2章）だが全章の本文が全行 blank を返すアダプタ（ScrapeIntegrity 条件② の再現）。
     * canonicalWorkUrl は blankbody.example のみ受理する。
     */
    private class BlankBodyAdapter : NovelSiteAdapter {
        override val siteKey: String = "blankbody"
        override val displayName: String = "空本文サイト"

        private val refs = listOf(
            ScrapedChapterRef("第一話", "$WORK_URL/episodes/1"),
            ScrapedChapterRef("第二話", "$WORK_URL/episodes/2"),
        )

        override fun canonicalWorkUrl(inputUrl: String): String? {
            val host = runCatching { java.net.URI(inputUrl.trim()).host?.lowercase() }.getOrNull() ?: return null
            return if (host == "blankbody.example") WORK_URL else null
        }

        override suspend fun fetchToc(workUrl: String): ScrapedToc =
            ScrapedToc(ScrapedWorkMeta("空作品", null, workUrl), refs)

        // 全行 blank（空文字＋全角空白のみ）＝実文字 0。破損時にセレクタが空を返す状況を模す。
        override suspend fun fetchChapter(ref: ScrapedChapterRef): RawChapter =
            RawChapter(ref.title, mutableListOf("", "　", ""))

        override val healthProbe: HealthProbe = HealthProbe(WORK_URL, minChapters = 1)

        companion object {
            const val WORK_URL = "https://blankbody.example/works/9"
        }
    }
}
