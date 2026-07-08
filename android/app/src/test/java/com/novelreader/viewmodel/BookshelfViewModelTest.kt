package com.novelreader.viewmodel

import android.content.Intent
import androidx.core.content.ContextCompat
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.data.BookEntity
import com.novelreader.data.ProgressEntity
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import com.novelreader.narou.model.Ncode
import com.novelreader.repository.BookRepository
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

        viewModel = BookshelfViewModel(mockApp)
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
        coVerify { mockRepository.deleteBook(book) }
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

        val vm = BookshelfViewModel(fakeApp)
        // getLastRead は repository.getLastRead への素の委譲＝Fake のインメモリ状態がそのまま返る
        assertEquals("chapter_03.html", vm.getLastRead("id01"))
        assertNull(vm.getLastRead("unknown"))
    }

    @Test
    fun `getLastRead - repository の戻り値をそのまま返す`() = runTest {
        coEvery { mockRepository.getLastRead("id01") } returns "chapter_01.html"
        val result = viewModel.getLastRead("id01")
        assertEquals("chapter_01.html", result)
    }

    @Test
    fun `getLastRead - 未読の場合は null を返す`() = runTest {
        coEvery { mockRepository.getLastRead("id02") } returns null
        val result = viewModel.getLastRead("id02")
        assertNull(result)
    }

    // 進捗保存は単一チャネルに統合され、章移動・スクロール双方とも
    // repository.saveScrollPosition で書き込まれる（章移動はスクロール 0,0 = 章先頭）。
    @Test
    fun `saveProgress - 章移動はスクロール0で saveScrollPosition が呼ばれる`() = runTest {
        viewModel.saveProgress("id01", "chapter_02.html")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.saveScrollPosition("id01", "chapter_02.html", 0, 0) }
    }

    @Test
    fun `saveScrollPosition - 指定位置で saveScrollPosition が呼ばれる`() = runTest {
        viewModel.saveScrollPosition("id01", "chapter_02.html", 5, 120)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { mockRepository.saveScrollPosition("id01", "chapter_02.html", 5, 120) }
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
        val novelA = mockk<NarouNovel>()
        val books = listOf(
            BookEntity("id01", "本A", "/p/a", ncode = "N1111AA"),
            BookEntity("id02", "本B", "/p/b"), // ncode null → 照会対象外
        )
        every { mockRepository.allBooks } returns flowOf(books)
        coEvery { mockNovelApiRepository.novelDetail(Ncode("N1111AA")) } returns novelA
        viewModel = BookshelfViewModel(mockApp)

        // WhileSubscribed のため能動的に購読して上流の一括照会を起動する
        val job = launch { viewModel.newEpisodeNovelMap.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("N1111AA" to novelA), viewModel.newEpisodeNovelMap.value)
        coVerify(exactly = 1) { mockNovelApiRepository.novelDetail(Ncode("N1111AA")) }
        job.cancel()
    }

    @Test
    fun `newEpisodeNovelMap - NarouApiException は握り潰しその ncode を除外する`() = runTest {
        val novelA = mockk<NarouNovel>()
        val books = listOf(
            BookEntity("id01", "本A", "/p/a", ncode = "N1111AA"),
            BookEntity("id02", "本B", "/p/b", ncode = "N2222BB"), // 失敗する
        )
        every { mockRepository.allBooks } returns flowOf(books)
        coEvery { mockNovelApiRepository.novelDetail(Ncode("N1111AA")) } returns novelA
        coEvery { mockNovelApiRepository.novelDetail(Ncode("N2222BB")) } throws
            NarouApiException("オフライン", RuntimeException())
        viewModel = BookshelfViewModel(mockApp)

        val job = launch { viewModel.newEpisodeNovelMap.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // 失敗した ncode は落ち、成功分だけが残る（本棚を通信エラーで騒がせない）
        assertEquals(mapOf("N1111AA" to novelA), viewModel.newEpisodeNovelMap.value)
        job.cancel()
    }
}
