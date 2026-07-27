package com.novelreader.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.novelreader.NovelReaderApplication
import com.novelreader.domain.SearchDraft
import com.novelreader.domain.SearchFilters
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.model.DiscoveryPage
import com.novelreader.narou.model.DiscoveryQuery
import com.novelreader.discovery.model.SerialState
import com.novelreader.discovery.model.WorkSummary
import com.novelreader.discovery.model.workSummary
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouOrder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
            workSummary(title = "小説タイトル", ncode = "N1234AB", serialState = SerialState.ONGOING)
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
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))

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
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(10, listOf(workSummary(title = "週間1位")))

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        val daily = listOf(workSummary(title = "日間1位"))
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
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(10, listOf(workSummary(title = "t")))

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setHomeOrder(NarouOrder.WEEKLY) // 既定値と同じ
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.discover(any()) }
    }

    @Test
    fun `openResult - 文脈が保存され、そのクエリで取得した結果が resultState に入ること`() = runTest {
        val hits = listOf(workSummary(title = "検索ヒット"))
        coEvery { mockRepo.discover(match { it.word == "薬師" }) } returns DiscoveryResult(42, hits)

        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "「薬師」",
            source = ResultSource.SEARCH,
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
        val hits = listOf(workSummary(title = "ヒット"))
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
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))

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
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))

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
        viewModel.openResult(ResultContext(title = "t", source = ResultSource.SEARCH, query = com.novelreader.narou.model.DiscoveryQuery()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.resultState.value is DiscoveryUiState.Error)

        val recovered = listOf(workSummary(title = "復帰"))
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, recovered)
        viewModel.refreshResult()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.resultState.value
        assertTrue(state is DiscoveryUiState.Content)
        assertEquals(recovered, (state as DiscoveryUiState.Content).novels)
    }

    @Test
    fun `changeResultOrder - 並び順のみ変更され、再ロードされること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))
        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "「薬師」",
            subtitle = "サブタイトル",
            source = ResultSource.SEARCH,
            query = com.novelreader.narou.model.DiscoveryQuery(word = "薬師", order = NarouOrder.WEEKLY)
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.changeResultOrder(NarouOrder.TOTAL)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedCtx = viewModel.resultContext.value
        assertEquals("「薬師」", updatedCtx?.title)
        assertEquals("サブタイトル", updatedCtx?.subtitle)
        assertEquals(ResultSource.SEARCH, updatedCtx?.source)
        assertEquals(NarouOrder.TOTAL, updatedCtx?.query?.order)
        assertEquals("薬師", updatedCtx?.query?.word)
        coVerify { mockRepo.discover(match { it.order == NarouOrder.TOTAL }) }
    }

    @Test
    fun `changeResultGenreFilter - GENRE発の場合、大ジャンル変更でタイトルが更新され再ロードされること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))
        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "「薬師」",
            subtitle = "サブタイトル",
            source = ResultSource.GENRE,
            query = com.novelreader.narou.model.DiscoveryQuery(word = "薬師")
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.changeResultGenreFilter(biggenres = setOf(1), genres = emptySet()) // 恋愛
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedCtx = viewModel.resultContext.value
        assertEquals("恋愛", updatedCtx?.title)
        assertEquals("サブタイトル", updatedCtx?.subtitle)
        assertEquals(ResultSource.GENRE, updatedCtx?.source)
        assertEquals(setOf(1), updatedCtx?.query?.biggenres)
        assertEquals(emptySet<Int>(), updatedCtx?.query?.genres)
        coVerify { mockRepo.discover(match { it.biggenres == setOf(1) }) }
    }

    @Test
    fun `changeResultGenreFilter - GENRE発の場合、詳細ジャンル変更でタイトルが更新され再ロードされること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))
        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "「薬師」",
            subtitle = "サブタイトル",
            source = ResultSource.GENRE,
            query = com.novelreader.narou.model.DiscoveryQuery(word = "薬師")
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.changeResultGenreFilter(biggenres = emptySet(), genres = setOf(101)) // 異世界〔恋愛〕
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedCtx = viewModel.resultContext.value
        assertEquals("異世界〔恋愛〕", updatedCtx?.title)
        assertEquals("サブタイトル", updatedCtx?.subtitle)
        assertEquals(ResultSource.GENRE, updatedCtx?.source)
        assertEquals(setOf(101), updatedCtx?.query?.genres)
        assertEquals(emptySet<Int>(), updatedCtx?.query?.biggenres)
        coVerify { mockRepo.discover(match { it.genres == setOf(101) }) }
    }

    @Test
    fun `changeResultGenreFilter - SEARCH発の場合、ジャンル変更してもタイトルが維持され再ロードされること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))
        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "「薬師」",
            subtitle = "サブタイトル",
            source = ResultSource.SEARCH,
            query = com.novelreader.narou.model.DiscoveryQuery(word = "薬師")
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.changeResultGenreFilter(biggenres = setOf(1), genres = emptySet()) // 恋愛
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedCtx = viewModel.resultContext.value
        assertEquals("「薬師」", updatedCtx?.title) // 維持されること
        assertEquals("サブタイトル", updatedCtx?.subtitle)
        assertEquals(ResultSource.SEARCH, updatedCtx?.source)
        assertEquals(setOf(1), updatedCtx?.query?.biggenres)
        assertEquals(emptySet<Int>(), updatedCtx?.query?.genres)
        coVerify { mockRepo.discover(match { it.biggenres == setOf(1) }) }
    }

    @Test
    fun `changeResultGenreFilter - GENRE発の場合、両方空への変更でタイトルがすべての作品になり再ロードされること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))
        viewModel = DiscoveryViewModel(mockApp)
        val ctx = ResultContext(
            title = "恋愛",
            subtitle = "サブタイトル",
            source = ResultSource.GENRE,
            query = com.novelreader.narou.model.DiscoveryQuery(biggenres = setOf(1))
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.changeResultGenreFilter(biggenres = emptySet(), genres = emptySet())
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedCtx = viewModel.resultContext.value
        assertEquals("すべての作品", updatedCtx?.title)
        assertEquals("サブタイトル", updatedCtx?.subtitle)
        assertEquals(ResultSource.GENRE, updatedCtx?.source)
        assertEquals(emptySet<Int>(), updatedCtx?.query?.biggenres)
        assertEquals(emptySet<Int>(), updatedCtx?.query?.genres)
        coVerify { mockRepo.discover(match { it.biggenres.isEmpty() && it.genres.isEmpty() }) }
    }

    @Test
    fun `setHomeOrder - 先行ロードの遅い応答が後発タブの結果を上書きしないこと`() = runTest {
        // なぜこの回帰テストか: ロードが前ジョブをキャンセルしないと、遅い旧クエリの応答が後着して
        // 「タブは新・リスト本体は旧」の食い違い表示になる（応答の完了順は要求順を保証しない）。
        coEvery { mockRepo.discover(match { it.order == NarouOrder.WEEKLY }) } coAnswers {
            delay(1_000) // 旧クエリ＝低速応答を模す
            DiscoveryResult(1, listOf(workSummary(title = "週間の結果")))
        }
        coEvery { mockRepo.discover(match { it.order == NarouOrder.DAILY }) } returns
            DiscoveryResult(1, listOf(workSummary(title = "日間の結果")))

        viewModel = DiscoveryViewModel(mockApp)
        viewModel.ensureHomeLoaded()                // WEEKLY 発火（delay で保留中）
        viewModel.setHomeOrder(NarouOrder.DAILY)    // 切替＝DAILY は即完了
        testDispatcher.scheduler.advanceUntilIdle() // 保留中の WEEKLY を進める（キャンセル済なら状態を書かない）

        val state = viewModel.homeState.value
        assertTrue(state is DiscoveryUiState.Content)
        assertEquals("日間の結果", (state as DiscoveryUiState.Content).novels.first().title)
    }

    // ── process death 復元（F-C / F-E）──

    @Test
    fun `F-C - openResult が ResultContext を SavedStateHandle へ退避すること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(1, listOf(workSummary(title = "t")))
        val handle = SavedStateHandle()
        viewModel = DiscoveryViewModel(mockApp, handle)
        val ctx = ResultContext(
            title = "「薬師」",
            source = ResultSource.SEARCH,
            query = DiscoveryQuery(word = "薬師", order = NarouOrder.TOTAL),
        )
        viewModel.openResult(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        // SavedStateHandle に同値の文脈が退避されている
        assertEquals(ctx, handle.get<ResultContext>("result_context"))
    }

    @Test
    fun `F-C - SavedStateHandle に文脈があれば init で復元し再ロードすること（process death 復帰）`() = runTest {
        val restored = ResultContext(
            title = "「薬師」",
            source = ResultSource.SEARCH,
            query = DiscoveryQuery(word = "薬師"),
        )
        val recovered = listOf(workSummary(title = "復帰した結果"))
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(7, recovered)

        // process death 相当: 文脈が入った SavedStateHandle で VM を再生成
        val handle = SavedStateHandle(mapOf("result_context" to restored))
        viewModel = DiscoveryViewModel(mockApp, handle)
        testDispatcher.scheduler.advanceUntilIdle()

        // 旧実装は文脈がメモリのみで復元不能→画面が前へ強制退去だった。復元されて再取得されること。
        assertEquals(restored, viewModel.resultContext.value)
        val state = viewModel.resultState.value
        assertTrue(state is DiscoveryUiState.Content)
        assertEquals(recovered, (state as DiscoveryUiState.Content).novels)
        coVerify { mockRepo.discover(match { it.word == "薬師" }) }
    }

    @Test
    fun `F-E - setSearchDraft が SavedStateHandle へ退避し init で復元されること`() = runTest {
        val handle = SavedStateHandle()
        viewModel = DiscoveryViewModel(mockApp, handle)
        val draft = SearchDraft(
            word = "辺境",
            inTitle = true,
            inKeyword = true,
            filters = SearchFilters(sasie = "1-"),
        )
        viewModel.setSearchDraft(draft)

        // 退避されている
        assertEquals(draft, handle.get<SearchDraft>("search_draft"))

        // process death 相当: 同じハンドルで再生成すると復元される
        val revived = DiscoveryViewModel(mockApp, handle)
        assertEquals(draft, revived.searchDraft.value)
    }

    // ── カスタム文字数/読了時間の SSOT（生入力テキストを draft へ畳んだ後の不変条件） ──

    @Test
    fun `setLengthCustomText - 生入力テキストと filters_length を一括更新すること`() = runTest {
        viewModel = DiscoveryViewModel(mockApp)
        // "1"〜"10"（万字）→ 10000-100000 へスケールして送出値に組み立てる
        viewModel.setLengthCustomText("1", "10")
        val d = viewModel.searchDraft.value
        assertEquals("1", d.lengthCustomMin)
        assertEquals("10", d.lengthCustomMax)
        assertEquals("10000-100000", d.filters.length)
    }

    @Test
    fun `setLengthCustomText - 全角数字を正規化し time を排他クリアすること`() = runTest {
        viewModel = DiscoveryViewModel(mockApp)
        viewModel.setSearchDraft(SearchDraft(filters = SearchFilters(time = "-30")))
        viewModel.setLengthCustomText("１", "") // 全角の1
        val d = viewModel.searchDraft.value
        assertEquals("1", d.lengthCustomMin)
        assertEquals("10000-", d.filters.length)
        // withLength が文字数/読了時間の排他を保証する（time を null に落とす）
        assertNull(d.filters.time)
    }

    @Test
    fun `toggleLengthStep - 段の消灯で length が null に戻るとき生入力テキストも掃くこと`() = runTest {
        viewModel = DiscoveryViewModel(mockApp)
        // 真にカスタムな値 "3"（=30000-。段境界に一致しない）を入れて生テキストを残す
        viewModel.setLengthCustomText("3", "")
        assertEquals("30000-", viewModel.searchDraft.value.filters.length)
        // 段 0（"-10000"）を点灯 → 生テキストは保持（next!=null）
        viewModel.toggleLengthStep(0)
        assertEquals("-10000", viewModel.searchDraft.value.filters.length)
        assertEquals("3", viewModel.searchDraft.value.lengthCustomMin)
        // 段 0 を消灯 → length=null。旧 LaunchedEffect が担っていた生テキストの掃き取りを VM が代替する
        viewModel.toggleLengthStep(0)
        val d = viewModel.searchDraft.value
        assertNull(d.filters.length)
        assertEquals("", d.lengthCustomMin)
        assertEquals("", d.lengthCustomMax)
    }

    // ── フルページング（F-J） ──

    private fun novelList(count: Int, prefix: String): List<WorkSummary> =
        (1..count).map { workSummary(title = "$prefix$it", ncode = "$prefix$it") }

    private fun openSearchResult(vm: DiscoveryViewModel) {
        vm.openResult(
            ResultContext(
                title = "「薬師」",
                source = ResultSource.SEARCH,
                query = DiscoveryQuery(word = "薬師"),
            )
        )
    }

    @Test
    fun `初回ロード - 続きがあれば paging=Idle、全件なら Complete になること`() = runTest {
        // 続きがある（総数 > 取得数）
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(100, novelList(2, "a"))
        viewModel = DiscoveryViewModel(mockApp)
        openSearchResult(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()
        val idle = viewModel.resultState.value
        assertTrue(idle is DiscoveryUiState.Content)
        assertEquals(PagingState.Idle, (idle as DiscoveryUiState.Content).paging)

        // 全件（総数 == 取得数）
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(2, novelList(2, "a"))
        viewModel.refreshResult()
        testDispatcher.scheduler.advanceUntilIdle()
        val complete = viewModel.resultState.value
        assertTrue(complete is DiscoveryUiState.Content)
        assertEquals(PagingState.Complete, (complete as DiscoveryUiState.Content).paging)
    }

    @Test
    fun `loadMoreResults - 追加ページが既存結果へ追記され総数到達で Complete になること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(5, novelList(2, "a"))
        viewModel = DiscoveryViewModel(mockApp)
        openSearchResult(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        // 2ページ目（offset=2）: まだ続く
        coEvery { mockRepo.discoverPage(any(), 2) } returns DiscoveryPage(5, novelList(2, "b"), false)
        viewModel.loadMoreResults()
        testDispatcher.scheduler.advanceUntilIdle()
        val s2 = viewModel.resultState.value as DiscoveryUiState.Content
        assertEquals(4, s2.novels.size)
        assertEquals(listOf("a1", "a2", "b1", "b2"), s2.novels.map { it.ncode })
        assertEquals(PagingState.Idle, s2.paging)
        assertEquals(5, s2.allcount) // 総数はページングで変わらない

        // 3ページ目（offset=4）: これで全件到達
        coEvery { mockRepo.discoverPage(any(), 4) } returns DiscoveryPage(5, novelList(1, "c"), false)
        viewModel.loadMoreResults()
        testDispatcher.scheduler.advanceUntilIdle()
        val s3 = viewModel.resultState.value as DiscoveryUiState.Content
        assertEquals(5, s3.novels.size)
        assertEquals(PagingState.Complete, s3.paging)
    }

    @Test
    fun `loadMoreResults - API取得上限に達すると ApiLimitReached になり結果は保持されること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(5000, novelList(30, "a"))
        viewModel = DiscoveryViewModel(mockApp)
        openSearchResult(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        // 追加ページで reachedApiLimit=true（st>2000 相当を Repository が通知）
        coEvery { mockRepo.discoverPage(any(), 30) } returns DiscoveryPage(5000, novelList(30, "b"), true)
        viewModel.loadMoreResults()
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.resultState.value as DiscoveryUiState.Content
        assertEquals(60, s.novels.size) // 取得済みは追記され保持
        assertEquals(PagingState.ApiLimitReached, s.paging)
    }

    @Test
    fun `loadMoreResults - 追加失敗時は既存結果を保持し LoadMoreError で再試行できること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(100, novelList(2, "a"))
        viewModel = DiscoveryViewModel(mockApp)
        openSearchResult(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        // 追加取得が失敗
        coEvery { mockRepo.discoverPage(any(), 2) } throws NarouApiException("追加失敗", Exception())
        viewModel.loadMoreResults()
        testDispatcher.scheduler.advanceUntilIdle()

        val failed = viewModel.resultState.value as DiscoveryUiState.Content
        assertEquals(2, failed.novels.size) // 既存結果は捨てない
        assertTrue(failed.paging is PagingState.LoadMoreError)
        assertEquals("追加失敗", (failed.paging as PagingState.LoadMoreError).message)

        // 再試行（LoadMoreError からも発火できる）で成功
        coEvery { mockRepo.discoverPage(any(), 2) } returns DiscoveryPage(100, novelList(2, "b"), false)
        viewModel.loadMoreResults()
        testDispatcher.scheduler.advanceUntilIdle()

        val recovered = viewModel.resultState.value as DiscoveryUiState.Content
        assertEquals(4, recovered.novels.size)
        assertEquals(PagingState.Idle, recovered.paging)
    }

    @Test
    fun `loadMoreResults - 読み込み中の再発火は無視され discoverPage は1回だけ呼ばれること`() = runTest {
        coEvery { mockRepo.discover(any()) } returns DiscoveryResult(100, novelList(2, "a"))
        viewModel = DiscoveryViewModel(mockApp)
        openSearchResult(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        // 応答を遅延させ、LoadingMore 中に連打しても二重取得しないことを固定する（高速スクロール・連打）
        coEvery { mockRepo.discoverPage(any(), 2) } coAnswers {
            delay(1_000)
            DiscoveryPage(100, novelList(2, "b"), false)
        }
        viewModel.loadMoreResults() // LoadingMore へ→launch
        viewModel.loadMoreResults() // 再入ガードで無視されるべき
        viewModel.loadMoreResults()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.discoverPage(any(), 2) }
    }

    @Test
    fun `loadMoreResults - Content でない状態では何もしないこと`() = runTest {
        coEvery { mockRepo.discover(any()) } throws NarouApiException("通信エラー", Exception())
        viewModel = DiscoveryViewModel(mockApp)
        openSearchResult(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.resultState.value is DiscoveryUiState.Error)

        viewModel.loadMoreResults() // Error 状態からは何も起きない
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { mockRepo.discoverPage(any(), any()) }
    }
}
