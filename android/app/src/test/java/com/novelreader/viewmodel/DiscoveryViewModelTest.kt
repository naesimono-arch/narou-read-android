package com.novelreader.viewmodel

import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import com.novelreader.narou.model.NarouOrder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class DiscoveryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: NovelReaderApplication
    private lateinit var mockRepo: NovelApiRepository
    private lateinit var viewModel: DiscoveryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)

        every { mockApp.novelApiRepository } returns mockRepo
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ensureHomeLoaded - ロードに成功した場合、homeState が Content に遷移し、取得データが格納されること`() = runTest {
        val dummyNovels = listOf(
            NarouNovel(title = "小説タイトル", ncode = "N1234AB", novelType = 1, end = 1)
        )
        val dummyResult = DiscoveryResult(allcount = 120, novels = dummyNovels)
        coEvery { mockRepo.discover(any()) } returns dummyResult

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.homeState.value
        assertTrue(state is DiscoveryUiState.Content)
        state as DiscoveryUiState.Content
        assertEquals(120, state.allcount)
        assertEquals(dummyNovels, state.novels)
    }

    @Test
    fun `ensureHomeLoaded - VM生成だけでは通信せず、2回呼んでも discover は1回だけ実行されること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(NarouNovel(title = "t")))

        viewModel = DiscoveryViewModel(mockApp)
        testDispatcher.scheduler.advanceUntilIdle()
        // VM 生成のみ（画面未表示相当）では通信しない＝上位共有 VM が本棚起動時に API を叩かない設計の要
        coVerify(exactly = 0) { mockRepo.discover(any()) }

        viewModel.ensureHomeLoaded()
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.discover(any()) }
    }

    @Test
    fun `ensureHomeLoaded - データが空だった場合、homeState が Empty に遷移すること`() = runTest {
        val dummyResult = DiscoveryResult(allcount = 0, novels = emptyList())
        coEvery { mockRepo.discover(any()) } returns dummyResult

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.homeState.value
        assertTrue(state is DiscoveryUiState.Empty)
    }

    @Test
    fun `ensureHomeLoaded - API取得が NarouApiException で失敗した場合、homeState が Error に遷移し、エラーメッセージが設定されること`() = runTest {
        val exception = NarouApiException("テスト用エラーメッセージ", Exception())
        coEvery { mockRepo.discover(any()) } throws exception

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.homeState.value
        assertTrue(state is DiscoveryUiState.Error)
        state as DiscoveryUiState.Error
        assertEquals("テスト用エラーメッセージ", state.message)
    }

    @Test
    fun `setHomeOrder - orderが変わると該当orderのクエリで再取得され、homeOrderとhomeStateが更新されること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(10, listOf(NarouNovel(title = "週間1位")))

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        val daily = listOf(NarouNovel(title = "日間1位"))
        coEvery { mockRepo.discover(match { it.order == NarouOrder.DAILY }) } returns DiscoveryResult(5, daily)

        viewModel.setHomeOrder(NarouOrder.DAILY)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NarouOrder.DAILY, viewModel.homeOrder.value)
        val state = viewModel.homeState.value
        assertTrue(state is DiscoveryUiState.Content)
        assertEquals(daily, (state as DiscoveryUiState.Content).novels)
        coVerify(exactly = 1) { mockRepo.discover(match { it.order == NarouOrder.DAILY }) }
    }

    @Test
    fun `setHomeOrder - 同じorderを再選択しても再取得しないこと`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(10, listOf(NarouNovel(title = "t")))

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setHomeOrder(NarouOrder.WEEKLY) // 既定値と同じ
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.discover(any()) }
    }

    @Test
    fun `openResult - 文脈が保存され、そのクエリで取得した結果が resultState に入ること`() = runTest {
        val hits = listOf(NarouNovel(title = "検索ヒット"))
        coEvery { mockRepo.discover(match { it.word == "薬師" }) } returns DiscoveryResult(42, hits)

        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "「薬師」",
            query = com.novelreader.narou.model.DiscoveryQuery(word = "薬師"),
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ctx, viewModel.resultContext.value)
        val state = viewModel.resultState.value
        assertTrue(state is DiscoveryUiState.Content)
        assertEquals(hits, (state as DiscoveryUiState.Content).novels)
        assertEquals(42, state.allcount)
    }

    @Test
    fun `executeSearch - ドラフトが実行可能なら結果文脈が差し替わり、不可なら何も起きないこと`() = runTest {
        val hits = listOf(NarouNovel(title = "ヒット"))
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, hits)

        viewModel = DiscoveryViewModel(mockApp)

        // 空ドラフト（word 空・フィルタなし）は実行不可
        assertEquals(false, viewModel.executeSearch())
        assertEquals(null, viewModel.resultContext.value)

        viewModel.setSearchDraft(SearchDraft(word = "薬師", inTitle = true))
        assertEquals(true, viewModel.executeSearch())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("「薬師」", viewModel.resultContext.value?.title)
        assertEquals("薬師", viewModel.resultContext.value?.query?.word)
        assertTrue(viewModel.resultState.value is DiscoveryUiState.Content)
    }

    @Test
    fun `executeSearch - 検索語が履歴ストアへ追加され、条件のみの検索では追加されないこと`() = runTest {
        val mockStore = mockk<com.novelreader.narou.SearchHistoryStore>(relaxed = true)
        every { mockApp.searchHistoryStore } returns mockStore
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(NarouNovel(title = "t")))

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.setSearchDraft(SearchDraft(word = " 薬師 "))
        viewModel.executeSearch()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockStore.addRecent("薬師") }

        // 条件のみ（語なし）は履歴に残さない
        viewModel.setSearchDraft(
            SearchDraft(filters = SearchFilters(sasie = "1-"))
        )
        viewModel.executeSearch()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockStore.addRecent(any()) }
    }

    @Test
    fun `searchFromHistory - 履歴語がドラフトへ移り即実行されること`() = runTest {
        val mockStore = mockk<com.novelreader.narou.SearchHistoryStore>(relaxed = true)
        every { mockApp.searchHistoryStore } returns mockStore
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(NarouNovel(title = "t")))

        viewModel = DiscoveryViewModel(mockApp)
        assertEquals(true, viewModel.searchFromHistory("辺境"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("辺境", viewModel.searchDraft.value.word)
        assertEquals("「辺境」", viewModel.resultContext.value?.title)
    }

    @Test
    fun `openResult - 取得失敗時は resultState が Error になり refreshResult で再試行されること`() = runTest {
        coEvery { mockRepo.discover(any()) } throws NarouApiException("通信エラー", Exception())

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.openResult(ResultContext(title = "t", query = com.novelreader.narou.model.DiscoveryQuery()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.resultState.value is DiscoveryUiState.Error)

        val recovered = listOf(NarouNovel(title = "復帰"))
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, recovered)
        viewModel.refreshResult()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.resultState.value
        assertTrue(state is DiscoveryUiState.Content)
        assertEquals(recovered, (state as DiscoveryUiState.Content).novels)
    }
}
