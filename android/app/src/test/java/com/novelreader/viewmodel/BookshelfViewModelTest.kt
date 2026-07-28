package com.novelreader.viewmodel

import android.content.Intent
import androidx.core.content.ContextCompat
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.model.BookId
import com.novelreader.model.ChapterFilename
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.discovery.model.workDetail
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.repository.BookRepository
import com.novelreader.repository.SourceDeleteOutcome
import com.novelreader.scrape.ScrapeStructureException
import com.novelreader.scrape.SiteAdapterRegistry
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class BookshelfViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: NovelReaderApplication
    private lateinit var mockRepository: BookRepository
    private lateinit var mockNovelApiRepository: NovelApiRepository
    private lateinit var viewModel: BookshelfViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        mockNovelApiRepository = mockk(relaxed = true)
        mockApp = mockk(relaxed = true)

        every { mockApp.repository } returns mockRepository
        every { mockApp.novelApiRepository } returns mockNovelApiRepository
        every { mockApp.processingState } returns MutableStateFlow<ProcessingState?>(null).asStateFlow()
        every { mockApp.errorEvents } returns emptyFlow()
        every { mockRepository.allBooks } returns flowOf(emptyList())
        // (b) uiState は allBooks と webNovels の combine になった。webNovels を stub しないと
        // relaxed mock の Flow が emit せず combine が一度も発火しない（books 派生も止まる）ため、
        // 既定は空リストの即時 emit にする（Web カード関連のテストは各自上書きする）。
        every { mockRepository.webNovels } returns flowOf(emptyList())
        // 機能②: combine は webReadingProgress も束ねる（3本目）。同じ理由で空の即時 emit を stub しないと
        // 未 emit で combine が発火せず books 派生（newEpisodeNovelMap 等）が止まる。
        every { mockRepository.webReadingProgress } returns flowOf(emptyList())

        // ioDispatcher に testDispatcher を渡す: progressChannel 消費・deleteBook 等の fire-and-forget な
        // IO 書き込みを testDispatcher の scheduler 上で回し、advanceUntilIdle が消費完了を待てるようにする
        // （素の Dispatchers.IO だと管理外スレッドで走り coVerify とレースしてフレーキーになる）。
        viewModel = BookshelfViewModel(mockApp, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 初期状態 ──────────────────────────────────────────────────────────
    // stateIn(WhileSubscribed) の初期値はサブスクライバー不要で .value から取得可能

    @Test
    fun `初期状態 - books が emptyList を返す`() {
        assertEquals(emptyList<BookEntity>(), viewModel.books.value)
    }

    // F-O: cold start の空フラッシュ対策。DB 初回発行前は Loading であり、
    // Content(空) と区別できること（未購読時の初期値 .value で確認）。
    @Test
    fun `初期状態 - uiState が Loading を返す`() {
        assertTrue(viewModel.uiState.value is BookshelfUiState.Loading)
    }

    @Test
    fun `初期状態 - processingState が ProcessingState() を返す`() {
        assertEquals(ProcessingState(), viewModel.processingState.value)
    }

    // ── DAO委譲 ───────────────────────────────────────────────────────────

    @Test
    fun `deleteBook - repository の deleteBook が呼ばれる`() = runTest {
        val book = BookEntity("id01", "テスト本", "/nonexistent/path")
        viewModel.deleteBook(book)
        testDispatcher.scheduler.advanceUntilIdle()
        // 単体 deleteBook は取込元を消さない（deleteSource=false 既定）。
        coVerify { mockRepository.deleteBook(book, false) }
    }

    @Test
    fun `deleteBooks - 取込元削除に失敗した本があれば emitError で通知する`() = runTest {
        val book = BookEntity("id01", "本A", "/p", sourceUri = "content://docs/src1")
        coEvery { mockRepository.deleteBook(book, true) } returns SourceDeleteOutcome.Failed

        viewModel.deleteBooks(listOf(book), deleteSource = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.deleteBook(book, true) }
        // 失敗は握りつぶさず Snackbar（Application.emitError）で知らせる（本削除自体は成立）。
        verify { mockApp.emitError(any(), any()) }
    }

    @Test
    fun `deleteBooks - 全て成功なら emitError を出さない`() = runTest {
        val book = BookEntity("id02", "本B", "/p", sourceUri = "content://docs/src2")
        coEvery { mockRepository.deleteBook(book, true) } returns SourceDeleteOutcome.Deleted

        viewModel.deleteBooks(listOf(book), deleteSource = true)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { mockApp.emitError(any(), any()) }
    }

    // ── Fake 実装での結線確認 ────────────────────────────────────────────
    // interface BookRepository 抽出により、Room/PDFBox 非依存の FakeBookRepository を差し込んで
    // ViewModel を実データフローで検証できることを示す（mockk のスタブ検証とは別の担保）。
    @Test
    fun `FakeBookRepository - getLastRead が Fake のインメモリ進捗を返す`() = runTest {
        val fake = com.novelreader.repository.FakeBookRepository()
        fake.setProgress(listOf(ProgressEntity("id01", "chapter_03.html")))
        val fakeApp = mockk<NovelReaderApplication>(relaxed = true)
        every { fakeApp.repository } returns fake
        every { fakeApp.novelApiRepository } returns mockNovelApiRepository
        every { fakeApp.processingState } returns MutableStateFlow<ProcessingState?>(null).asStateFlow()
        every { fakeApp.errorEvents } returns emptyFlow()

        val vm = BookshelfViewModel(fakeApp, testDispatcher)
        // getLastRead は repository.getLastRead への素の委譲＝Fake のインメモリ状態がそのまま返る
        assertEquals("chapter_03.html", vm.getLastRead(BookId("id01")))
        assertNull(vm.getLastRead(BookId("unknown")))
    }

    @Test
    fun `getLastRead - repository の戻り値をそのまま返す`() = runTest {
        coEvery { mockRepository.getLastRead(BookId("id01")) } returns "chapter_01.html"
        val result = viewModel.getLastRead(BookId("id01"))
        assertEquals("chapter_01.html", result)
    }

    @Test
    fun `getLastRead - 未読の場合は null を返す`() = runTest {
        coEvery { mockRepository.getLastRead(BookId("id02")) } returns null
        val result = viewModel.getLastRead(BookId("id02"))
        assertNull(result)
    }

    // 進捗保存は単一チャネルに統合され、章移動・スクロール双方とも
    // repository.saveScrollPosition で書き込まれる（章移動はスクロール 0,0 = 章先頭）。
    @Test
    fun `saveProgress - 章移動はスクロール0で saveScrollPosition が呼ばれる`() = runTest {
        viewModel.saveProgress(BookId("id01"), ChapterFilename("chapter_02.html"))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.saveScrollPosition(BookId("id01"), ChapterFilename("chapter_02.html"), 0, 0) }
    }

    @Test
    fun `saveScrollPosition - 指定位置で saveScrollPosition が呼ばれる`() = runTest {
        viewModel.saveScrollPosition(BookId("id01"), ChapterFilename("chapter_02.html"), 5, 120)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.saveScrollPosition(BookId("id01"), ChapterFilename("chapter_02.html"), 5, 120) }
    }

    // ── addBook ───────────────────────────────────────────────────────────

    @Test
    fun `addBook - Intent の action と data と FLAG_GRANT_READ_URI_PERMISSION を検証する`() {
        // Android スタブは Intent のフィールドセットを実装しないため mockkConstructor で代替
        mockkConstructor(Intent::class)
        mockkStatic(ContextCompat::class)
        try {
            // apply ブロック内で呼ばれるメソッドを stub（setAction は Intent を返す）
            every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)
            every { anyConstructed<Intent>().setData(any()) } returns mockk(relaxed = true)
            every { anyConstructed<Intent>().addFlags(any()) } returns mockk(relaxed = true)
            justRun { ContextCompat.startForegroundService(any(), any()) }

            val uri = mockk<android.net.Uri>(relaxed = true)
            viewModel.addBook(uri)

            verify { ContextCompat.startForegroundService(any(), any()) }
            verify { anyConstructed<Intent>().setAction(PdfProcessingService.ACTION_START) }
            verify { anyConstructed<Intent>().setData(uri) }
            verify { anyConstructed<Intent>().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } finally {
            unmockkConstructor(Intent::class)
            unmockkStatic(ContextCompat::class)
        }
    }

    // ── P3 取込導線: 共有/リンクからの Web 小説取込ルーティング＋実取込 ───────────────

    @Test
    fun `resolveWebImport - kakuyomu は Supported`() {
        val r = viewModel.resolveWebImport("https://kakuyomu.jp/works/16816927859675616240/episodes/1")
        assertTrue(r is SiteAdapterRegistry.Resolution.Supported)
    }

    @Test
    fun `resolveWebImport - なろうは Blocked`() {
        // 本文の機械取得が規約違反（ADR 0010/0012）＝自前 DL せず公式サイトへ逃がす対象。
        val r = viewModel.resolveWebImport("https://ncode.syosetu.com/n1234ab/")
        assertTrue(r is SiteAdapterRegistry.Resolution.Blocked)
    }

    @Test
    fun `resolveWebImport - 未知サイトは Unsupported`() {
        assertEquals(SiteAdapterRegistry.Resolution.Unsupported, viewModel.resolveWebImport("https://example.com/x"))
    }

    // 案d（2026-07-23）: 取込中は ProcessingBanner（isProcessing 駆動）で見せる＝「取り込み中」Snackbar は
    // 発行しない。バナーは set（isProcessing=true）してから finally で必ず clear（null）する。
    @Test
    fun `importWebNovel - Added はバナーを set→clear し完了 Snackbar を出す（取込中 Snackbar は出さない）`() = runTest {
        val book = BookEntity("id01", "テスト作品", "/p/a")
        coEvery { mockRepository.addWebBook(any(), any()) } returns
            Result.success(BookRepository.AddBookResult.Added(book))

        viewModel.importWebNovel("https://kakuyomu.jp/works/123")
        testDispatcher.scheduler.advanceUntilIdle()

        // 旧「取り込み中です…」Snackbar は廃止（残留バグの真因）。
        verify(exactly = 0) { mockApp.emitError("取り込み中です…") }
        // 取込中バナーを set（isProcessing=true）→ finally で clear（null）。
        verify { mockApp.updateProcessingState(match { it?.isProcessing == true }) }
        verify { mockApp.updateProcessingState(null) }
        // 完了は一過性の情報通知（transient=true）＝UI 側で Short 自動消滅。
        verify { mockApp.emitError("「テスト作品」を追加しました", transient = true) }
        coVerify { mockRepository.addWebBook("https://kakuyomu.jp/works/123", any()) }
    }

    // onProgress（章 i/N 取得中）を ProcessingState.phase へ流し、バナー副見出しへ進捗を出す。
    @Test
    fun `importWebNovel - onProgress の章進捗をバナー phase へ流す`() = runTest {
        val book = BookEntity("id01", "テスト作品", "/p/a")
        coEvery { mockRepository.addWebBook(any(), any()) } answers {
            // addWebBook の第2引数（onProgress）を取り出して章進捗を1回コールバックする。
            secondArg<((Int, String) -> Unit)?>()?.invoke(2, "章 2/5 取得中")
            Result.success(BookRepository.AddBookResult.Added(book))
        }

        viewModel.importWebNovel("https://kakuyomu.jp/works/123")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { mockApp.updateProcessingState(match { it?.phase == "章 2/5 取得中" }) }
    }

    @Test
    fun `importWebNovel - Duplicate は取込済み Snackbar を出しバナーを clear する`() = runTest {
        val existing = BookEntity("id01", "既存作品", "/p/a")
        coEvery { mockRepository.addWebBook(any(), any()) } returns
            Result.success(BookRepository.AddBookResult.Duplicate(existing))

        viewModel.importWebNovel("https://kakuyomu.jp/works/123")
        testDispatcher.scheduler.advanceUntilIdle()

        // 取込済みも一過性の情報通知（transient=true）。
        verify { mockApp.emitError("取り込み済みです", transient = true) }
        verify { mockApp.updateProcessingState(null) }
    }

    @Test
    fun `importWebNovel - 失敗は失敗 Snackbar を出す（再試行なし・バナーは clear）`() = runTest {
        coEvery { mockRepository.addWebBook(any(), any()) } returns
            Result.failure(RuntimeException("network down"))

        viewModel.importWebNovel("https://kakuyomu.jp/works/123")
        testDispatcher.scheduler.advanceUntilIdle()

        // 失敗系は従来どおり「閉じる」付きで残置（transient なし＝既定 false）。
        verify { mockApp.emitError("取り込みに失敗しました") }
        // 失敗経路でも finally でバナーを必ず畳む。
        verify { mockApp.updateProcessingState(null) }
    }

    // 破損監視・層2: 構造変更の疑い（ScrapeStructureException）は「公式サイトで読む」逃げ道つきで通知する。
    @Test
    fun `importWebNovel - 構造疑いは公式サイト逃げ道つき Snackbar を出す`() = runTest {
        coEvery { mockRepository.addWebBook(any(), any()) } returns
            Result.failure(ScrapeStructureException("本文が全章で空"))

        viewModel.importWebNovel("https://kakuyomu.jp/works/123")
        testDispatcher.scheduler.advanceUntilIdle()

        // 構造疑い専用文言＋openUrl（作品URLを外部ブラウザで開く）で通知し、一般失敗文言は出さない。
        verify {
            mockApp.emitError(
                "取得に失敗しました。サイト構造が変わった可能性があります",
                openUrl = "https://kakuyomu.jp/works/123",
            )
        }
        verify(exactly = 0) { mockApp.emitError("取り込みに失敗しました") }
    }

    // ── なろう紐付け候補検索（旧 NcodeLinkSheet の produceState を VM へ移設）─────────────

    @Test
    fun `searchNcodeCandidates - 成功時に Success となり inTitle=title順=件数20 で discover を叩く`() = runTest {
        val result = DiscoveryResult(allcount = 5, novels = emptyList())
        coEvery { mockNovelApiRepository.discover(any()) } returns result

        viewModel.searchNcodeCandidates("転生")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.ncodeSearchState.value
        assertTrue(state is NcodeSearchUiState.Success)
        assertEquals(result, (state as NcodeSearchUiState.Success).result)
        // 旧 produceState と同一のクエリ形（word=入力・inTitle=true・order=TOTAL・limit=20）で叩くこと
        coVerify {
            mockNovelApiRepository.discover(match {
                it.word == "転生" && it.inTitle && it.order == NarouOrder.TOTAL && it.limit == 20
            })
        }
    }

    @Test
    fun `searchNcodeCandidates - 空白クエリは通信せず空の Success を返す`() = runTest {
        viewModel.searchNcodeCandidates("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.ncodeSearchState.value
        assertTrue(state is NcodeSearchUiState.Success)
        state as NcodeSearchUiState.Success
        assertEquals(0, state.result.allcount)
        assertTrue(state.result.novels.isEmpty())
        coVerify(exactly = 0) { mockNovelApiRepository.discover(any()) }
    }

    @Test
    fun `searchNcodeCandidates - NarouApiException は Error に落とし userMessage を保持する`() = runTest {
        coEvery { mockNovelApiRepository.discover(any()) } throws
            NarouApiException("ネットワークに接続できません", RuntimeException())

        viewModel.searchNcodeCandidates("なろう")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.ncodeSearchState.value
        assertTrue(state is NcodeSearchUiState.Error)
        assertEquals("ネットワークに接続できません", (state as NcodeSearchUiState.Error).message)
    }

    @Test
    fun `retryNcodeSearch - 直近クエリで検索し直す`() = runTest {
        coEvery { mockNovelApiRepository.discover(any()) } returns DiscoveryResult(1, emptyList())

        viewModel.searchNcodeCandidates("最遊記")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.retryNcodeSearch()
        testDispatcher.scheduler.advanceUntilIdle()

        // 再試行は直近クエリ（最遊記）でもう一度叩く＝計2回
        coVerify(exactly = 2) {
            mockNovelApiRepository.discover(match { it.word == "最遊記" })
        }
    }

    // ── 続きありバッジ用の詳細一括照会（旧 BookCard の produceState を VM へ移設）───────────

    @Test
    fun `newEpisodeNovelMap - 紐付け済みの本ごとに novelDetail を照会し ncode 別のMapを作る`() = runTest {
        // 詳細（WorkDetail）で返り、Map に載るのはバッジ計算に要る要約（summary）＝それを assert する。
        val summaryA = workSummary(ncode = "N1111AA")
        val novelA = workDetail(summary = summaryA)
        val books = listOf(
            BookEntity("id01", "本A", "/p/a", ncode = "N1111AA"),
            BookEntity("id02", "本B", "/p/b"), // ncode null → 照会対象外
        )
        every { mockRepository.allBooks } returns flowOf(books)
        coEvery { mockNovelApiRepository.novelDetail(Ncode("N1111AA")) } returns novelA
        viewModel = BookshelfViewModel(mockApp, testDispatcher)

        // WhileSubscribed のため能動的に購読して上流の一括照会を起動する
        val job = launch { viewModel.newEpisodeNovelMap.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("N1111AA" to summaryA), viewModel.newEpisodeNovelMap.value)
        coVerify(exactly = 1) { mockNovelApiRepository.novelDetail(Ncode("N1111AA")) }
        job.cancel()
    }

    @Test
    fun `newEpisodeNovelMap - NarouApiException は握り潰しその ncode を除外する`() = runTest {
        val summaryA = workSummary(ncode = "N1111AA")
        val novelA = workDetail(summary = summaryA)
        val books = listOf(
            BookEntity("id01", "本A", "/p/a", ncode = "N1111AA"),
            BookEntity("id02", "本B", "/p/b", ncode = "N2222BB"), // 失敗する
        )
        every { mockRepository.allBooks } returns flowOf(books)
        coEvery { mockNovelApiRepository.novelDetail(Ncode("N1111AA")) } returns novelA
        coEvery { mockNovelApiRepository.novelDetail(Ncode("N2222BB")) } throws
            NarouApiException("オフライン", RuntimeException())
        viewModel = BookshelfViewModel(mockApp, testDispatcher)

        val job = launch { viewModel.newEpisodeNovelMap.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // 失敗した ncode は落ち、成功分だけが残る（本棚を通信エラーで騒がせない）
        assertEquals(mapOf("N1111AA" to summaryA), viewModel.newEpisodeNovelMap.value)
        job.cancel()
    }

    // ── In-App Review トリガ（読了 reachedEnd false→true 遷移・監督裁定 2026-07-29）─────────
    // ReviewManager 実物は Play 開発者サービス必須で JVM から叩けないため、ここでは
    // 「打診イベント（reviewPromptEvents）がいつ流れるか」の契約だけを固定する
    // （実表示は内部テストトラックで確認＝handover）。

    @Test
    fun `markReachedEnd - 未読了からの遷移で打診イベントが1回だけ流れる`() = runTest {
        coEvery { mockRepository.getProgress(BookId("b1")) } returns
            ProgressEntity(bookId = "b1", lastReadFilename = "chap_9.html", reachedEnd = false)
        val received = mutableListOf<Unit>()
        val job = launch { viewModel.reviewPromptEvents.collect { received.add(it) } }

        viewModel.markReachedEnd(BookId("b1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, received.size)
        coVerify(exactly = 1) { mockRepository.markReachedEnd(BookId("b1")) }
        job.cancel()
    }

    @Test
    fun `markReachedEnd - 既読了の再到達では打診しない（記録の冪等 UPDATE は行う）`() = runTest {
        coEvery { mockRepository.getProgress(BookId("b1")) } returns
            ProgressEntity(bookId = "b1", lastReadFilename = "chap_9.html", reachedEnd = true)
        val received = mutableListOf<Unit>()
        val job = launch { viewModel.reviewPromptEvents.collect { received.add(it) } }

        viewModel.markReachedEnd(BookId("b1"))
        testDispatcher.scheduler.advanceUntilIdle()

        // 既読了本の再読は false→true 遷移ではない＝満足ピークの初回読了だけに絞る契約。
        assertEquals(0, received.size)
        // sticky マーク自体は従来どおり冪等に呼ぶ（読了記録の挙動を変えない）。
        coVerify(exactly = 1) { mockRepository.markReachedEnd(BookId("b1")) }
        job.cancel()
    }

    @Test
    fun `markReachedEnd - 同一セッションでは2冊目の初回読了でも打診しない`() = runTest {
        coEvery { mockRepository.getProgress(any()) } returns null // 行なし＝未読了扱い
        val received = mutableListOf<Unit>()
        val job = launch { viewModel.reviewPromptEvents.collect { received.add(it) } }

        viewModel.markReachedEnd(BookId("b1"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.markReachedEnd(BookId("b2"))
        testDispatcher.scheduler.advanceUntilIdle()

        // セッション1回制限（reviewPromptArmed）: 連続読了でも打診の連打はしない。
        assertEquals(1, received.size)
        // 読了記録そのものは2冊とも通常どおり行われる（打診制限は記録に波及しない）。
        coVerify(exactly = 1) { mockRepository.markReachedEnd(BookId("b1")) }
        coVerify(exactly = 1) { mockRepository.markReachedEnd(BookId("b2")) }
        job.cancel()
    }

    @Test
    fun `markReachedEnd - 進捗行なし(null)は未読了と同義で打診する`() = runTest {
        coEvery { mockRepository.getProgress(BookId("b1")) } returns null
        val received = mutableListOf<Unit>()
        val job = launch { viewModel.reviewPromptEvents.collect { received.add(it) } }

        viewModel.markReachedEnd(BookId("b1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, received.size)
        job.cancel()
    }
}
