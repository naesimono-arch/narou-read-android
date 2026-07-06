package com.novelreader.viewmodel

import com.novelreader.NovelReaderApplication
import com.novelreader.narou.NarouApiException
import com.novelreader.narou.NovelApiRepository
import com.novelreader.narou.model.DiscoveryResult
import com.novelreader.narou.model.NarouNovel
import io.mockk.coEvery
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
    fun `初期化時 - ロードに成功した場合、uiState が Content に遷移し、取得データが格納されること`() = runTest {
        val dummyNovels = listOf(
            NarouNovel(title = "小説タイトル", ncode = "N1234AB", novelType = 1, end = 1)
        )
        val dummyResult = DiscoveryResult(allcount = 120, novels = dummyNovels)
        coEvery { mockRepo.weeklyRanking(any()) } returns dummyResult

        viewModel = DiscoveryViewModel(mockApp)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DiscoveryUiState.Content)
        state as DiscoveryUiState.Content
        assertEquals(120, state.allcount)
        assertEquals(dummyNovels, state.novels)
    }

    @Test
    fun `初期化時 - データが空だった場合、uiState が Empty に遷移すること`() = runTest {
        val dummyResult = DiscoveryResult(allcount = 0, novels = emptyList())
        coEvery { mockRepo.weeklyRanking(any()) } returns dummyResult

        viewModel = DiscoveryViewModel(mockApp)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DiscoveryUiState.Empty)
    }

    @Test
    fun `初期化時 - API取得が NarouApiException で失敗した場合、uiState が Error に遷移し、エラーメッセージが設定されること`() = runTest {
        val exception = NarouApiException("テスト用エラーメッセージ", Exception())
        coEvery { mockRepo.weeklyRanking(any()) } throws exception

        viewModel = DiscoveryViewModel(mockApp)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DiscoveryUiState.Error)
        state as DiscoveryUiState.Error
        assertEquals("テスト用エラーメッセージ", state.message)
    }
}
